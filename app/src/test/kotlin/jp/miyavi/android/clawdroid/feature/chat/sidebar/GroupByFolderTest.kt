package jp.miyavi.android.clawdroid.feature.chat.sidebar

import jp.miyavi.android.clawdroid.core.data.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** セッション履歴の作業フォルダ別のまとまり。 */
class GroupByFolderTest {
    private fun session(key: String, cwd: String?, updated: Long) = SessionSummary(
        key = key, agentId = "a", sessionId = null, title = key, cwd = cwd, model = null, thinkingLevel = null,
        running = false, unread = false, archived = false, isAutomation = false, updatedAt = updated,
        contextTokens = null, totalTokens = null, preview = null,
    )

    @Test fun groups_areOrderedByLatestUpdate_andFolderlessGoesLast() {
        val groups = groupByFolder(
            listOf(
                session("a1", "/w/app/", 10),
                session("none", null, 99),
                session("b1", "/w/boards", 50),
                session("a2", "/w/app", 70),
            ),
        )
        assertEquals(listOf("/w/app", "/w/boards", null), groups.map { it.first })
        assertEquals(listOf("a2", "a1"), groups.first().second.map { it.key })
    }
}
