package jp.miyavi.android.sumahoclaw.feature.chat

import jp.miyavi.android.sumahoclaw.core.data.ChatMessage
import jp.miyavi.android.sumahoclaw.core.data.Parse
import jp.miyavi.android.sumahoclaw.core.gateway.Frames
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** 履歴を取り直しても、変わっていない行は前と同じインスタンスを使い回す（表示済みの行を組み立て直さない）。 */
class ChatRowsTest {
    private fun message(json: String): ChatMessage = Parse.message(Frames.json.parseToJsonElement(json).jsonObject, 0)!!
    private fun user(id: String, text: String) = message("""{"role":"user","content":"$text","__openclaw":{"id":"$id"}}""")
    private fun reply(id: String, text: String) = message("""{"role":"assistant","content":"$text","__openclaw":{"id":"$id"}}""")
    private fun tool(id: String) = message(
        """{"role":"assistant","content":[{"type":"toolCall","id":"c-$id","name":"read","arguments":{}}],"__openclaw":{"id":"$id"}}""",
    )

    private var parses = 0
    private fun build(messages: List<ChatMessage>, previous: List<ChatRow> = emptyList()) =
        buildChatRows(messages, previous, { "t" }) { parses++; emptyList() }

    /** 取り直すたびに Parse し直したのと同じ（中身は同じで別のインスタンス）。 */
    private fun refetched(vararg messages: ChatMessage) = messages.map { it.copy() }

    @Test fun sameHistory_reusesEveryRowAndSkipsMarkdown() {
        val first = build(listOf(user("u1", "hi"), reply("a1", "hello")))
        parses = 0
        val second = build(refetched(user("u1", "hi"), reply("a1", "hello")), first)
        assertEquals(2, second.size)
        second.indices.forEach { assertSame(first[it], second[it]) }
        assertEquals(0, parses)
    }

    @Test fun appendedMessage_keepsExistingRows() {
        val first = build(listOf(user("u1", "hi"), reply("a1", "hello")))
        parses = 0
        val second = build(refetched(user("u1", "hi"), reply("a1", "hello"), user("u2", "next")), first)
        assertSame(first[0], second[0])
        assertSame(first[1], second[1])
        assertEquals("next", second[2].last.message.text)
        assertEquals(1, parses)
    }

    @Test fun changedMessage_rebuildsOnlyThatRow() {
        val first = build(listOf(user("u1", "hi"), reply("a1", "hel")))
        val second = build(refetched(user("u1", "hi"), reply("a1", "hello")), first)
        assertSame(first[0], second[0])
        assertNotSame(first[1], second[1])
        assertEquals(first[1].key, second[1].key)
    }

    @Test fun toolsJoinTheNextReply_andRowGrowsWhenReplyArrives() {
        val first = build(listOf(user("u1", "go"), tool("t1")))
        assertEquals(2, first.size)
        assertEquals(1, first[1].tools.size)
        val second = build(refetched(user("u1", "go"), tool("t1"), reply("a1", "done")), first)
        assertEquals(2, second.size)
        // 同じ行（key は最初のツール）に応答が入り、応答の前のツールとして持つ
        assertEquals(first[1].key, second[1].key)
        assertEquals("done", second[1].last.message.text)
        assertEquals(1, second[1].tools.size)
        assertSame(first[1].items[0], second[1].items[0])
    }
}
