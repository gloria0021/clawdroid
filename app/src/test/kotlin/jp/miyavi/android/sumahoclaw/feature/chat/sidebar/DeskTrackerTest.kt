package jp.miyavi.android.sumahoclaw.feature.chat.sidebar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeskTrackerTest {
    private fun agent(running: Boolean = false, updatedAt: Long? = 1_000L) = OfficeAgent("a", "A", running, updatedAt = updatedAt)

    @Test
    fun firstSightIsNotActivity() {
        val t = DeskTracker()
        val a = agent()
        t.observe(listOf(a), now = 0)
        assertFalse(t.atDesk(a, 0))
        assertNull(t.nextExpiry(0))
    }

    @Test
    fun staysAtDeskTwentySecondsAfterRunEnds() {
        val t = DeskTracker()
        t.observe(listOf(agent()), now = 0)
        t.observe(listOf(agent(running = true)), now = 1_000)
        val done = agent(running = false, updatedAt = 2_000)
        t.observe(listOf(done), now = 5_000)
        assertTrue(t.atDesk(done, 5_000 + DESK_LINGER_MS - 1))
        assertFalse(t.atDesk(done, 5_000 + DESK_LINGER_MS))
        assertEquals(5_000 + DESK_LINGER_MS, t.nextExpiry(5_000))
    }

    @Test
    fun runTooShortToSeeStillGoesToDesk() {
        // 動いている瞬間を受け取れなかった短いやり取りでも、セッションが更新されたらデスクへ
        val t = DeskTracker()
        t.observe(listOf(agent(updatedAt = 1_000)), now = 0)
        val replied = agent(updatedAt = 3_000)
        t.observe(listOf(replied), now = 2_000)
        assertTrue(t.atDesk(replied, 2_000))
        assertFalse(t.atDesk(replied, 2_000 + DESK_LINGER_MS))
    }

    @Test
    fun newActivityRestartsTheWait() {
        val t = DeskTracker()
        t.observe(listOf(agent(updatedAt = 1_000)), now = 0)
        t.observe(listOf(agent(updatedAt = 2_000)), now = 1_000)
        val again = agent(updatedAt = 3_000)
        t.observe(listOf(again), now = 15_000)
        assertTrue(t.atDesk(again, 1_000 + DESK_LINGER_MS + 1))
    }
}
