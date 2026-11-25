package jp.miyavi.android.clawdroid.core.notify

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.AgentAvatarBitmaps
import jp.miyavi.android.clawdroid.core.data.ApprovalKey
import jp.miyavi.android.clawdroid.core.data.ApprovalsRepository
import jp.miyavi.android.clawdroid.core.data.Parse
import jp.miyavi.android.clawdroid.core.data.PendingApproval
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.data.SessionsRepository
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.gateway.array
import jp.miyavi.android.clawdroid.core.gateway.asObject
import jp.miyavi.android.clawdroid.core.gateway.obj
import jp.miyavi.android.clawdroid.core.settings.LockScreenContent
import jp.miyavi.android.clawdroid.core.settings.NotificationSettings
import jp.miyavi.android.clawdroid.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/**
 * 通知のまとめ役（spec 006）。アプリ全体の出来事を見て、応答の完了・承認待ち・自動化の失敗を通知し、常駐を始める・止める。
 * 接続は既存の GatewayClient をそのまま使う（新しい接続を作らない。plan Summary）。
 */
internal class Notifier(
    private val context: Context,
    private val gateway: GatewayClient,
    private val sessions: SessionsRepository,
    private val approvals: ApprovalsRepository,
    private val settings: SettingsStore,
    val foreground: ForegroundState,
    private val scope: CoroutineScope,
    private val avatars: AgentAvatarBitmaps,
) {
    val center = NotificationCenter(context)
    private val notified = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    @Volatile private var config = NotificationSettings()
    /** 会話ごとの「実行中か」（前の状態が無い会話では完了としない）。 */
    private val running = ConcurrentHashMap<String, Boolean>()
    private val shownApprovals = mutableSetOf<ApprovalKey>()
    /** 通知のボタンで決めて、結果を出している承認（一覧から消えても結果の通知は消さない）。 */
    val decidedApprovals: MutableSet<ApprovalKey> = ConcurrentHashMap.newKeySet()
    private val shownFailures = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var wantService = false

    private val hideOnLock get() = config.lockScreenContent == LockScreenContent.HIDE

    fun start() {
        scope.launch { settings.notifications.collect { config = it } }
        scope.launch {
            gateway.events.collect { event ->
                when (event.name) {
                    "sessions.changed" -> (event.payload as? JsonObject)?.obj("session")?.let { Parse.session(it) }?.let(::onSession)
                    "cron" -> automationFailure(event.payload as? JsonObject)?.let(::onAutomationFailure)
                }
            }
        }
        // つなぎ直したら、切れていたあいだに終わった応答を補う（FR-005）
        scope.launch {
            var connectedBefore = false
            gateway.state.map { it is ConnectionState.Connected }.distinctUntilChanged().collect { connected ->
                if (connected && connectedBefore) catchUp()
                if (connected) connectedBefore = true
            }
        }
        // 画面で開いた会話の応答の通知は消す
        scope.launch {
            combine(foreground.inForeground, foreground.openSessionKey) { fg, key -> key.takeIf { fg } }.collect { key ->
                key?.let { center.cancel(NotificationCenter.replyId(it)) }
            }
        }
        // 承認待ち: アプリが裏にあるあいだだけ通知する（前ではアプリ全体のシートが出る）
        scope.launch(Dispatchers.Main.immediate) {
            combine(approvals.requests, foreground.inForeground) { list, fg -> list to fg }.collect { (list, fg) -> syncApprovals(list, fg) }
        }
        // 常駐: オンで、接続情報があるあいだ（FR-001・003）
        scope.launch {
            combine(settings.notifications.map { it.enabled && it.backgroundConnection }, gateway.state.map { it !is ConnectionState.NotConfigured && it !is ConnectionState.Disconnected }) { on, configured -> on && configured }
                .distinctUntilChanged()
                .collect { want ->
                    wantService = want
                    if (want) startService() else context.stopService(Intent(context, ConnectionService::class.java))
                }
        }
    }

    /** アプリが前に来た・端末が起動した（裏からは常駐を始められないので、ここで始め直す）。 */
    fun ensureService() {
        if (wantService) startService()
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, ConnectionService::class.java))
        } catch (_: ForegroundServiceStartNotAllowedException) {
            // 裏からは始められない。次に前に来たときに ensureService で始める
        } catch (_: IllegalStateException) {
        }
    }

    private fun onSession(session: SessionSummary) {
        val was = running.put(session.key, session.running)
        if (isRunCompleted(was, session.running)) scope.launch { notifyReply(session) }
    }

    private suspend fun catchUp() {
        val rows = runCatching {
            gateway.request("sessions.list", buildJsonObject { put("limit", CATCH_UP_LIMIT) }).asObject()?.array("sessions").orEmpty()
        }.getOrNull() ?: return
        rows.mapNotNull { (it as? JsonObject)?.let(Parse::session) }.forEach(::onSession)
    }

    private suspend fun notifyReply(session: SessionSummary) {
        if (!config.enabled || !config.notifyReplies || foreground.isViewing(session.key)) return
        val agentId = session.agentId ?: return
        val messages = runCatching {
            gateway.request("chat.history", buildJsonObject {
                put("sessionKey", session.key)
                put("agentId", agentId)
                put("limit", HISTORY_LIMIT)
            }).asObject()?.array("messages").orEmpty()
        }.getOrNull()?.mapIndexedNotNull { i, el -> (el as? JsonObject)?.let { Parse.message(it, i) } } ?: return
        val reply = replyToNotify(messages) ?: return
        // 同じ応答は重ねて出さない（つなぎ直し・プロセスの再起動を越えて）
        if (notified.getString(session.key, null) == reply.id) return
        if (foreground.isViewing(session.key)) return
        notified.edit().putString(session.key, reply.id).apply()
        val agentName = sessions.agents.value.agents.firstOrNull { it.id == agentId }?.name ?: context.getString(R.string.notify_agent_fallback)
        center.showReply(
            agentId = agentId,
            agentName = agentName,
            sessionKey = session.key,
            title = session.title ?: context.getString(R.string.session_untitled),
            text = notificationText(reply, context.getString(R.string.notify_reply_image)),
            hideOnLock = hideOnLock,
            avatar = avatar(agentId),
        )
    }

    private fun syncApprovals(list: List<PendingApproval>, inForeground: Boolean) {
        val keys = list.map { it.key }.toSet()
        // 一覧から消えた（ほかで決まった・期限切れ）ものは消す。ボタンで決めたものは結果を残す
        (shownApprovals - keys).forEach { key ->
            if (key !in decidedApprovals) center.cancelApproval(key)
            shownApprovals.remove(key)
        }
        if (inForeground || !config.enabled || !config.notifyApprovals) {
            shownApprovals.forEach(center::cancelApproval)
            shownApprovals.clear()
            return
        }
        list.filter { it.key !in shownApprovals }.forEach { item ->
            val agentName = item.agentId?.let { id -> sessions.agents.value.agents.firstOrNull { it.id == id }?.name }
            val sessionTitle = item.sessionKey?.let { sessions.find(it)?.title }
            shownApprovals += item.key
            scope.launch(Dispatchers.Main.immediate) {
                val face = item.agentId?.let { avatar(it) }
                // 顔を読むあいだに解決されていたら出さない
                if (item.key in shownApprovals) center.showApproval(item, agentName, sessionTitle, hideOnLock, face)
            }
        }
    }

    /** エージェントの顔を丸く切った画像（通知の大きなアイコン）。読めなければ null（通知は顔なしで出す）。 */
    private suspend fun avatar(agentId: String): android.graphics.Bitmap? =
        avatars.load(sessions.agents.value.agents.firstOrNull { it.id == agentId }, AVATAR_PX, circle = true)

    private fun onAutomationFailure(failure: AutomationFailure) {
        if (!config.enabled || !config.notifyAutomationFailures || foreground.inForeground.value) return
        if (!shownFailures.add("${failure.jobId}:${failure.runAt}")) return
        center.showAutomationFailure(failure, hideOnLock)
    }

    private companion object {
        const val PREFS = "notified_replies"
        const val HISTORY_LIMIT = 6
        const val CATCH_UP_LIMIT = 200
        /** 通知の大きなアイコンの大きさ（端末が縮めて使う）。 */
        const val AVATAR_PX = 192
    }
}
