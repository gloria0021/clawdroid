package jp.miyavi.android.sumahoclaw.core.shortcut

import jp.miyavi.android.sumahoclaw.core.data.Agent
import jp.miyavi.android.sumahoclaw.core.data.AgentsState
import jp.miyavi.android.sumahoclaw.core.data.SessionListState
import jp.miyavi.android.sumahoclaw.core.notify.OpenTarget

/** ホーム画面に置かれた項目を無効にする理由（contracts/shortcut-intents.md）。 */
enum class DisableReason { ARCHIVED, DELETED, AGENT_GONE, GATEWAY_CHANGED }

/** ランチャーに出す中身。前回と同じなら登録し直さない（R1）。 */
data class PublishedShortcut(
    val id: String,
    val rank: Int,
    val label: String,
    val target: OpenTarget,
    val agentId: String,
    val avatarUrl: String?,
)

data class ReconcileResult(
    /** 保存し直す枠（変わらなければ入力と同じ）。 */
    val slots: List<ShortcutSlot>,
    /** 無効にする ID と理由（外したもの・アーカイブされたもの）。 */
    val disable: Map<String, DisableReason>,
    /** アーカイブが戻されて有効に戻す ID。 */
    val enable: Set<String>,
)

/** 名前の元になる文言（画面の外の文字列を純粋な関数に渡すため）。 */
data class LabelTexts(val untitledSession: String)

/**
 * 枠と、アプリが持つセッションの一覧・エージェントの一覧を照らし合わせる（spec 007 R4、data-model.md）。
 * 一覧を判定できるエージェント（読み込み済みで失敗していない）だけを判定し、読み込んでいないことを「消えた」と扱わない（FR-024）。
 */
fun reconcile(slots: List<ShortcutSlot>, lists: Map<String, SessionListState>, agents: AgentsState): ReconcileResult {
    val agentsKnown = !agents.loading && agents.error == null && agents.agents.isNotEmpty()
    val agentIds = agents.agents.mapTo(HashSet(), Agent::id)
    val disable = LinkedHashMap<String, DisableReason>()
    val enable = LinkedHashSet<String>()
    val next = slots.mapNotNull { slot ->
        if (agentsKnown && slot.agentId !in agentIds) {
            disable[slot.id] = DisableReason.AGENT_GONE
            return@mapNotNull null
        }
        val key = slot.watchedSessionKey ?: return@mapNotNull slot
        val list = lists[slot.agentId]?.takeIf { !it.loading && it.error == null } ?: return@mapNotNull slot
        when {
            list.sessions.any { it.key == key } || list.home?.key == key -> {
                if (slot.archived) enable += slot.id
                slot.copy(archived = false)
            }
            list.archived.any { it.key == key } -> {
                if (!slot.archived) disable[slot.id] = DisableReason.ARCHIVED
                slot.copy(archived = true)
            }
            else -> {
                disable[slot.id] = DisableReason.DELETED
                null
            }
        }
    }
    return ReconcileResult(next, disable, enable)
}

/**
 * 枠の名前（R6）。マスターが付けた名前 → 行き先からの名前（ホーム = エージェント名、フォルダ = 最後の名前、セッション = セッション名）。
 * 一覧を読んでいないあいだのセッションは [fallback]（前に出した名前）を使う。
 */
fun shortcutLabel(slot: ShortcutSlot, agent: Agent?, lists: Map<String, SessionListState>, texts: LabelTexts, fallback: String? = null): String {
    slot.customLabel?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    val agentName = agent?.name?.takeIf(String::isNotBlank) ?: slot.agentId
    return when (val d = slot.destination) {
        is ShortcutDestination.Home -> agentName
        is ShortcutDestination.Folder -> d.cwd?.let(::folderName) ?: agentName
        is ShortcutDestination.Session -> {
            val list = lists[slot.agentId]
            val row = list?.let { l -> (l.sessions + l.archived + listOfNotNull(l.home)).firstOrNull { it.key == d.sessionKey } }
            row?.title?.takeIf(String::isNotBlank) ?: fallback ?: texts.untitledSession
        }
    }
}

/** フォルダのパスの最後の名前（`/home/mi/Dev/sumahoclaw/` → `sumahoclaw`）。 */
fun folderName(path: String): String = path.trimEnd('/').substringAfterLast('/').ifEmpty { path }

/** ランチャーに出す並び（アーカイブ済みは出さない）。 */
fun published(
    slots: List<ShortcutSlot>,
    agents: List<Agent>,
    lists: Map<String, SessionListState>,
    texts: LabelTexts,
    previousLabels: Map<String, String> = emptyMap(),
): List<PublishedShortcut> =
    slots.filterNot(ShortcutSlot::archived).take(MAX_SHORTCUTS).mapIndexed { rank, slot ->
        val agent = agents.firstOrNull { it.id == slot.agentId }
        PublishedShortcut(
            id = slot.id,
            rank = rank,
            label = shortcutLabel(slot, agent, lists, texts, previousLabels[slot.id]),
            target = slot.target,
            agentId = slot.agentId,
            avatarUrl = agent?.avatarUrl,
        )
    }
