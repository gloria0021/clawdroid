package jp.miyavi.android.sumahoclaw.core.settings

import jp.miyavi.android.sumahoclaw.core.data.Agent
import kotlinx.serialization.Serializable

/**
 * サイドパネルに出すエージェントの並びと、出さないエージェント（設定 → エージェント）。
 * [order] に無いエージェント（あとから増えたもの）は、Gateway の並びのまま後ろに付けて表示する。
 */
@Serializable
data class AgentLayout(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
)

/** 設定の並びで、すべてのエージェント（表示しないものも含む）。 */
fun AgentLayout.ordered(agents: List<Agent>): List<Agent> {
    val rank = order.withIndex().associate { (index, id) -> id to index }
    return agents.withIndex()
        .sortedWith(compareBy({ rank[it.value.id] ?: (order.size + it.index) }))
        .map { it.value }
}

/** サイドパネルに出せるエージェントの数の上限（マスター指定 2026-11-23）。 */
const val MAX_SIDEBAR_AGENTS = 4

/** サイドパネルに出すエージェント（並びの先頭から最大 4 体）。すべて隠れていたら、1 体も無くならないように先頭から出す。 */
fun AgentLayout.visible(agents: List<Agent>): List<Agent> =
    ordered(agents).filter { it.id !in hidden }.ifEmpty { ordered(agents) }.take(MAX_SIDEBAR_AGENTS)

/** [ids] の並びにする（隠しているものも含めて並びを保存する）。 */
fun AgentLayout.reordered(ids: List<String>): AgentLayout = copy(order = ids)

/**
 * 表示する・しないを切り替える。切り替えたあとは「いま出ている以外はすべて隠す」形で保存する
 * （1 体隠したときに、上限で隠れていた別のエージェントが代わりに出てこないように）。
 * 0 体になる変更と、上限（4 体）を超える変更は受け付けない。
 */
fun AgentLayout.withVisibility(id: String, visible: Boolean, agents: List<Agent>): AgentLayout {
    val shown = visible(agents).map(Agent::id)
    val next = if (visible) shown + id else shown - id
    if (next.isEmpty() || next.distinct().size > MAX_SIDEBAR_AGENTS) return this
    return copy(hidden = agents.map(Agent::id).toSet() - next.toSet())
}
