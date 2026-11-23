package jp.miyavi.android.clawdroid.core.settings

import jp.miyavi.android.clawdroid.core.data.Agent
import org.junit.Assert.assertEquals
import org.junit.Test

/** サイドパネルに出すエージェントの並びと表示。 */
class AgentLayoutTest {
    private fun agent(id: String) = Agent(id, id.uppercase(), null, null, null, false)
    private val agents = listOf(agent("a"), agent("b"), agent("c"))

    @Test fun defaultLayout_keepsGatewayOrder() {
        assertEquals(listOf("a", "b", "c"), AgentLayout().visible(agents).map { it.id })
    }

    @Test fun order_isApplied_andNewAgentsGoLast() {
        val layout = AgentLayout(order = listOf("c", "a"))
        assertEquals(listOf("c", "a", "b"), layout.ordered(agents).map { it.id })
    }

    @Test fun hidden_areFilteredOut_butNeverAll() {
        val layout = AgentLayout(hidden = setOf("b"))
        assertEquals(listOf("a", "c"), layout.visible(agents).map { it.id })
        // 最後の 1 体は隠せない
        val one = AgentLayout(hidden = setOf("a", "b"))
        assertEquals(one, one.withVisibility("c", visible = false, agents = agents))
        // 保存が壊れて全員隠れていても、全員を出す
        assertEquals(3, AgentLayout(hidden = setOf("a", "b", "c")).visible(agents).size)
    }

    @Test fun reordered_keepsHidden() {
        val layout = AgentLayout(hidden = setOf("b")).reordered(listOf("b", "c", "a"))
        assertEquals(listOf("c", "a"), layout.visible(agents).map { it.id })
        assertEquals(listOf("b", "c", "a"), layout.ordered(agents).map { it.id })
    }

    @Test fun atMostFourAreShown_andHidingDoesNotPullInAnother() {
        val many = (1..6).map { agent("a$it") }
        assertEquals(listOf("a1", "a2", "a3", "a4"), AgentLayout().visible(many).map { it.id })
        // 1 体隠しても、5 体目は代わりに出てこない
        val hidden = AgentLayout().withVisibility("a2", visible = false, agents = many)
        assertEquals(listOf("a1", "a3", "a4"), hidden.visible(many).map { it.id })
        // 空いた枠に別のエージェントを出せる。5 体目は出せない
        val swapped = hidden.withVisibility("a6", visible = true, agents = many)
        assertEquals(listOf("a1", "a3", "a4", "a6"), swapped.visible(many).map { it.id })
        assertEquals(swapped, swapped.withVisibility("a5", visible = true, agents = many))
    }
}
