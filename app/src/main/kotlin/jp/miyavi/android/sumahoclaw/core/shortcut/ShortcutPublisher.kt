package jp.miyavi.android.sumahoclaw.core.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.AgentAvatarBitmaps
import jp.miyavi.android.sumahoclaw.core.data.AgentsState
import jp.miyavi.android.sumahoclaw.core.data.SessionListState
import jp.miyavi.android.sumahoclaw.core.data.SessionsRepository
import jp.miyavi.android.sumahoclaw.core.gateway.ConnectionState
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import jp.miyavi.android.sumahoclaw.core.notify.ForegroundState
import jp.miyavi.android.sumahoclaw.core.notify.OpenIntents
import jp.miyavi.android.sumahoclaw.core.security.SecretStore
import jp.miyavi.android.sumahoclaw.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ランチャーのショートカット（spec 007）。設定の枠・セッションの一覧・エージェントの一覧を見張り、
 * 登録する・アーカイブや削除で外す・接続先が変わったら全部消す。判定は [reconcile]（純粋な関数）、Android の API を呼ぶのはここだけ。
 *
 * 裏では登録・更新が回数を制限されるので（R1）、裏で行うのは 外す・無効にする・有効に戻す と、制限中でないときの名前の更新だけ。
 * 並びが変わる登録は前に戻ったときに行う。片付けのためにセッションの一覧を読み込むことはしない（FR-021）。
 */
class ShortcutPublisher(
    private val context: Context,
    private val settings: SettingsStore,
    private val sessions: SessionsRepository,
    private val gateway: GatewayClient,
    private val secrets: SecretStore,
    private val foreground: ForegroundState,
    private val avatars: AgentAvatarBitmaps,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    /** このプロセスで最後にランチャーへ出した中身（null = まだ出していない）。 */
    private var lastPublished: List<PublishedShortcut>? = null
    /** 最後に出した名前（一覧を読んでいないセッションの名前に使う。起動時はランチャーに残っている名前から読む）。 */
    private val labels = HashMap<String, String>()

    fun start() {
        scope.launch {
            runCatching { ShortcutManagerCompat.getDynamicShortcuts(context) }.getOrNull()
                ?.forEach { info -> labels[info.id] = info.shortLabel.toString() }
            combine(settings.shortcuts, sessions.sessions, sessions.agents, foreground.inForeground) { cfg, lists, agents, fg ->
                Snapshot(cfg, lists, agents, fg)
            }.collectLatest { mutex.withLock { sync(it) } }
        }
        scope.launch {
            gateway.state.collect { state -> mutex.withLock { onConnection(state) } }
        }
    }

    private data class Snapshot(
        val cfg: ShortcutSettings,
        val lists: Map<String, SessionListState>,
        val agents: AgentsState,
        val inForeground: Boolean,
    )

    private suspend fun sync(s: Snapshot) {
        val result = reconcile(s.cfg.slots, s.lists, s.agents)
        if (result.disable.isNotEmpty()) remove(result.disable)
        if (result.enable.isNotEmpty()) runCatching { ShortcutManagerCompat.enableShortcuts(context, result.enable.map { enableInfo(it) }) }
        if (result.slots != s.cfg.slots) {
            // 保存し直すと設定の流れがもう一度届き、そこで並べ直す（保存のあいだに書き換えられても、その時点の枠で判定し直す）
            settings.updateShortcuts { it.copy(slots = reconcile(it.slots, s.lists, s.agents).slots) }
            return
        }
        // エージェントの名前・顔が分かるまで出さない（エージェント ID のままの名前で上書きしない）
        if (s.agents.loading || s.agents.agents.isEmpty()) return
        val desired = published(s.cfg.slots, s.agents.agents, s.lists, LabelTexts(context.getString(R.string.session_untitled)), labels)
        val previous = lastPublished
        if (desired == previous) return
        if (s.inForeground) {
            publishAll(desired)
        } else if (previous != null && previous.map { it.id } == desired.map { it.id } && !ShortcutManagerCompat.isRateLimitingActive(context)) {
            // 並びが同じなら、名前・顔が変わったものだけを更新する（裏でもできる範囲）
            val changed = desired.filter { it !in previous }
            if (runCatching { ShortcutManagerCompat.updateShortcuts(context, changed.map { info(it) }) }.getOrDefault(false)) remember(desired)
        }
        // それ以外は前に戻ったときに並べ直す（inForeground の変化でもう一度ここに来る）
    }

    private suspend fun publishAll(desired: List<PublishedShortcut>) {
        val infos = desired.map { info(it) }
        if (runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, infos) }.getOrDefault(false)) remember(desired)
    }

    private fun remember(desired: List<PublishedShortcut>) {
        lastPublished = desired
        desired.forEach { labels[it.id] = it.label }
    }

    /** 外す・ホーム画面に置かれた分を無効にする（どちらも回数の制限を受けない）。 */
    private fun remove(reasons: Map<String, DisableReason>) {
        runCatching { ShortcutManagerCompat.removeDynamicShortcuts(context, reasons.keys.toList()) }
        reasons.entries.groupBy({ it.value }, { it.key }).forEach { (reason, ids) ->
            runCatching { ShortcutManagerCompat.disableShortcuts(context, ids, context.getString(reason.message())) }
        }
        lastPublished = lastPublished?.filterNot { it.id in reasons.keys }
    }

    /** 接続情報を消した・別の接続先につながったら、枠を全部消す（R7）。初めての接続では接続先を覚える。 */
    private suspend fun onConnection(state: ConnectionState) {
        when (state) {
            ConnectionState.NotConfigured -> {
                // 起動直後も NotConfigured から始まるので、接続情報が本当に無いときだけ消す
                if (secrets.loadConnection() == null) clearAll()
            }
            is ConnectionState.Connected -> {
                val url = secrets.loadConnection()?.url ?: return
                var changed = false
                settings.updateShortcuts { cfg ->
                    when {
                        cfg.slots.isEmpty() -> cfg.copy(gatewayUrl = url)
                        cfg.gatewayUrl == null -> cfg.copy(gatewayUrl = url)
                        cfg.gatewayUrl != url -> { changed = true; ShortcutSettings(gatewayUrl = url) }
                        else -> cfg
                    }
                }
                if (changed) disableAll(DisableReason.GATEWAY_CHANGED)
            }
            else -> Unit
        }
    }

    private suspend fun clearAll() {
        var had = false
        settings.updateShortcuts { cfg -> had = cfg.slots.isNotEmpty(); ShortcutSettings() }
        if (had || lastPublished?.isNotEmpty() == true) disableAll(DisableReason.GATEWAY_CHANGED)
    }

    private fun disableAll(reason: DisableReason) {
        val ids = runCatching { ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_PINNED) }
            .getOrNull().orEmpty().map { it.id }
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
        if (ids.isNotEmpty()) runCatching { ShortcutManagerCompat.disableShortcuts(context, ids, context.getString(reason.message())) }
        lastPublished = emptyList()
    }

    private suspend fun info(p: PublishedShortcut): ShortcutInfoCompat {
        val intent = OpenIntents.intent(context, p.target).apply {
            action = Intent.ACTION_VIEW
            putExtra(OpenIntents.EXTRA_SHORTCUT_ID, p.id)
        }
        return ShortcutInfoCompat.Builder(context, p.id)
            .setShortLabel(p.label)
            .setLongLabel(p.label)
            .setIcon(icon(p))
            .setIntent(intent)
            .setRank(p.rank)
            .setLongLived(true)
            .build()
    }

    /** 有効に戻すだけのときの最小の中身（ID だけが使われる）。 */
    private fun enableInfo(id: String): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, id).setShortLabel(labels[id] ?: id).setIntent(Intent(Intent.ACTION_VIEW)).build()

    /** 顔の画像を正方形いっぱいに敷いた adaptive icon（形はランチャーが切る。R5）。読めなければアプリのアイコン。 */
    private suspend fun icon(p: PublishedShortcut): IconCompat {
        val agent = sessions.agents.value.agents.firstOrNull { it.id == p.agentId }
        val bitmap = avatars.load(agent, ICON_PX, circle = false)
        return if (bitmap != null) IconCompat.createWithAdaptiveBitmap(bitmap) else IconCompat.createWithResource(context, R.mipmap.ic_launcher)
    }

    private fun DisableReason.message(): Int = when (this) {
        DisableReason.ARCHIVED -> R.string.shortcut_disabled_archived
        DisableReason.DELETED -> R.string.shortcut_disabled_deleted
        DisableReason.AGENT_GONE -> R.string.shortcut_disabled_agent
        DisableReason.GATEWAY_CHANGED -> R.string.shortcut_disabled_gateway
    }

    private companion object {
        /** ショートカットのアイコンの大きさ（108dp 相当。ランチャーが縮めて使う）。 */
        const val ICON_PX = 256
    }
}
