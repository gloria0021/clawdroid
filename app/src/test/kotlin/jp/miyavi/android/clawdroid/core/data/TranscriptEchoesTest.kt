package jp.miyavi.android.clawdroid.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Claude CLI の履歴に並ぶ同じ送信の写しを外す。 */
class TranscriptEchoesTest {
    private fun user(text: String, runId: String?) = ChatMessage(
        id = "u-$text-$runId", role = ChatMessage.Role.USER, parts = listOf(MessagePart.Text(text)),
        timestamp = 1, runId = runId, isError = false, truncated = false,
    )
    private fun assistant(text: String) = ChatMessage(
        id = "a-$text", role = ChatMessage.Role.ASSISTANT, parts = listOf(MessagePart.Text(text)),
        timestamp = 1, runId = "r", isError = false, truncated = false,
    )

    @Test fun echoWithoutRunId_rightAfterTheSameSend_isDropped() {
        val c = user("トークンカウントできない？", "idem-c")
        val out = withoutTranscriptEchoes(listOf(assistant("B の答え"), c, user(" トークンカウントできない？ ", null), assistant("C の答え")))
        assertEquals(listOf("a-B の答え", c.id, "a-C の答え"), out.map { it.id })
    }

    @Test fun sameTextSentTwice_isKept() {
        val messages = listOf(user("もう一回", "idem-1"), user("もう一回", "idem-2"))
        assertEquals(messages, withoutTranscriptEchoes(messages))
    }

    @Test fun differentText_orSeparatedByReply_isKept() {
        val a = listOf(user("A", "idem-a"), user("B", null))
        assertEquals(a, withoutTranscriptEchoes(a))
        val b = listOf(user("A", "idem-a"), assistant("x"), user("A", null))
        assertEquals(b, withoutTranscriptEchoes(b))
    }
}
