package jp.miyavi.android.sumahoclaw.feature.chat.sidebar

import jp.miyavi.android.sumahoclaw.core.data.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionInfoTextTest {
    private fun session(sessionId: String?, title: String?) = SessionSummary(
        key = "agent:architect:dashboard:146ac97a", agentId = "architect", sessionId = sessionId, title = title, cwd = null, model = null,
        thinkingLevel = null, running = false, unread = false, archived = false, isAutomation = false, updatedAt = null,
        contextTokens = null, totalTokens = null, preview = null,
    )

    @Test fun idKeyAndName() {
        assertEquals(
            "SessionID: 46a2772b\nSessionKey: agent:architect:dashboard:146ac97a\nSessionName: SumahoClaw",
            sessionInfoText(session("46a2772b", "SumahoClaw"), "新しいチャット"),
        )
    }

    @Test fun untitledAndNoIdYet() {
        assertEquals(
            "SessionID: \nSessionKey: agent:architect:dashboard:146ac97a\nSessionName: 新しいチャット",
            sessionInfoText(session(null, null), "新しいチャット"),
        )
    }
}
