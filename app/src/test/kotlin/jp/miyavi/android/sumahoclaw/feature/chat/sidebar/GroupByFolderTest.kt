package jp.miyavi.android.sumahoclaw.feature.chat.sidebar

import jp.miyavi.android.sumahoclaw.core.data.SessionSummary
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

    @Test fun worktreeSession_groupsUnderItsRepository() {
        val worktree = session("wt", "/h/.openclaw/worktrees/cc99/refactor", 80)
            .copy(worktreeBranch = "openclaw/refactor", worktreeRoot = "/w/app")
        val groups = groupByFolder(listOf(session("a1", "/w/app", 10), worktree, session("b1", "/w/boards", 50)))
        assertEquals(listOf("/w/app", "/w/boards"), groups.map { it.first })
        assertEquals(listOf("wt", "a1"), groups.first().second.map { it.key })
    }

    private fun wt(key: String, updated: Long) = session(key, "/h/wt/$key", updated).copy(worktreeBranch = "openclaw/$key", worktreeRoot = "/w/app")

    @Test fun development_flatWithoutWorktrees_treeWithWorktrees() {
        val rows = developmentRows(
            groupByFolder(listOf(session("main", "/w/app", 50), wt("refactor", 80), wt("fix", 60), session("b", "/w/boards", 40))),
        )
        assertEquals(
            listOf(
                DevRow.Session(session("main", "/w/app", 50)),
                DevRow.Session(wt("refactor", 80), TreeBranch.MIDDLE),
                DevRow.Session(wt("fix", 60), TreeBranch.LAST),
                DevRow.Session(session("b", "/w/boards", 40)),
            ),
            rows,
        )
    }

    @Test fun development_worktreesWithoutMain_useHeader() {
        val rows = developmentRows(groupByFolder(listOf(wt("refactor", 80))))
        assertEquals(listOf(DevRow.Header("/w/app", 1), DevRow.Session(wt("refactor", 80), TreeBranch.LAST, "/w/app")), rows)
    }
}
