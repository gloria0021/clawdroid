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
import jp.miyavi.android.clawdroid.core.settings.NotificationSettings
import jp.miyavi.android.clawdroid.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    /** 常駐が要るか（通知と裏での接続がオンで、接続情報がある）。端末の起動で待つのに使う。 */
    private val serviceWanted = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** 休んでいた接続を前に戻ってつなぎ直した。このつなぎ直しでは、裏にいたあいだに終わった応答を通知しない（未読の点で分かる）。 */
    @Volatile private var quietCatchUp = false

    /**
     * @param busy 裏でも接続を保つ用事があるか（ダウンロード中など。リンクが接続に紐付く）。
     */
    fun start(busy: Flow<Boolean> = flowOf(false)) {
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
                if (connected && connectedBefore) catchUp(notify = !quietCatchUp)
                if (connected) {
                    connectedBefore = true
                    quietCatchUp = false
                }
            }
        }
        // 常時接続がオフのとき: 裏に回ってしばらくしたら接続を休み、前に戻ったらつなぎ直す（電池のため。マスター指定 2026-11-26）。
        // 切り替えた直後に届く応答は通知できるよう少し待つ。承認待ちがある・ダウンロード中は、通知のボタンや続きが効くよう保つ
        scope.launch {
            val keep = combine(
                foreground.inForeground,
                settings.notifications.map { it.enabled && it.backgroundConnection },
                approvals.requests.map { it.isNotEmpty() },
                busy,
            ) { fg, always, approving, working -> (fg || always || approving || working) to fg }
            // 待つのは前から裏へ移ったときだけ。承認やダウンロードが裏で片付いたときは、すぐ休む（CJ レビュー 2026-11-26）
            // 最後に前から裏へ移った時刻（単調な時計）。途中で別の条件が変わっても、猶予はそこから数える
            var wasForeground = false
            var leftForegroundAt = Long.MIN_VALUE / 2
            combine(keep, gateway.state.map { it == ConnectionState.Paused }) { (k, fg), paused -> Triple(k, fg, paused) }
                .distinctUntilChanged()
                .collectLatest { (k, fg, paused) ->
                    if (wasForeground && !fg) leftForegroundAt = android.os.SystemClock.elapsedRealtime()
                    wasForeground = fg
                    when {
                        k && paused -> {
                            quietCatchUp = true
                            gateway.resumeFromPause()
                        }
                        !k && !paused -> {
                            val remaining = BACKGROUND_GRACE_MS - (android.os.SystemClock.elapsedRealtime() - leftForegroundAt)
                            if (remaining > 0) delay(remaining)
                            gateway.pause()
                        }
                    }
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
                    serviceWanted.value = want
                    if (want) startService() else context.stopService(Intent(context, ConnectionService::class.java))
                }
        }
    }

    /** アプリが前に来た・端末が起動した（裏からは常駐を始められないので、ここで始め直す）。 */
    fun ensureService() {
        if (wantService) startService()
    }

    /**
     * 端末の起動（BOOT_COMPLETED）で常駐を始める。起動直後は接続情報の読み出し（暗号化した保存先）と設定の読み込みが
     * 終わっておらず、まだ「要らない」に見える。決まるまで [timeoutMs] だけ待ってから始める（起動の知らせを受けているあいだは
     * 裏からでも connectedDevice の常駐を始められる）。接続情報が無ければ、待ち切って何もしない。
     */
    suspend fun startServiceAfterBoot(timeoutMs: Long) {
        val want = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { serviceWanted.first { it } } ?: false
        if (want) startService()
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, ConnectionService::class.java))
        } catch (_: ForegroundServiceStartNotAllowedException) {
            // 裏からは始められない。次に前に来たときに ensureService で始める
        } catch (_: IllegalStateException) {
        }
    }

    private fun onSession(session: SessionSummary, notify: Boolean = true) {
        val was = running.put(session.key, session.running)
        if (notify && isRunCompleted(was, session.running)) scope.launch { notifyReply(session) }
    }

    private suspend fun catchUp(notify: Boolean) {
        val rows = runCatching {
            gateway.request("sessions.list", buildJsonObject { put("limit", CATCH_UP_LIMIT) }).asObject()?.array("sessions").orEmpty()
        }.getOrNull() ?: return
        rows.mapNotNull { (it as? JsonObject)?.let(Parse::session) }.forEach { onSession(it, notify) }
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
                if (item.key in shownApprovals) center.showApproval(item, agentName, sessionTitle, face)
            }
        }
    }

    /** テスト通知。本物の応答の通知と同じ形で、既定のエージェント（無ければ先頭）の顔と名前で出す。 */
    fun showTest() {
        val state = sessions.agents.value
        val agent = state.agents.firstOrNull { it.id == state.defaultId } ?: state.agents.firstOrNull()
        scope.launch(Dispatchers.Main.immediate) {
            center.showTest(agent?.id, agent?.name, agent?.let { avatar(it.id) })
        }
    }

    /** エージェントの顔を丸く切った画像（通知の大きなアイコン）。読めなければ null（通知は顔なしで出す）。 */
    private suspend fun avatar(agentId: String): android.graphics.Bitmap? =
        avatars.load(sessions.agents.value.agents.firstOrNull { it.id == agentId }, AVATAR_PX, circle = true)

    private fun onAutomationFailure(failure: AutomationFailure) {
        if (!config.enabled || !config.notifyAutomationFailures || foreground.inForeground.value) return
        if (!shownFailures.add("${failure.jobId}:${failure.runAt}")) return
        center.showAutomationFailure(failure)
    }

    private companion object {
        const val PREFS = "notified_replies"
        const val HISTORY_LIMIT = 6
        const val CATCH_UP_LIMIT = 200
        /** 常時接続がオフのとき、裏に回ってから接続を休むまでの待ち時間（マスター指定 2026-11-26）。 */
        const val BACKGROUND_GRACE_MS = 60_000L
        /** 通知の大きなアイコンの大きさ（端末が縮めて使う）。 */
        const val AVATAR_PX = 192
    }
}
