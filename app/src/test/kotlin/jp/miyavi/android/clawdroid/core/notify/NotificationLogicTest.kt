package jp.miyavi.android.clawdroid.core.notify

import jp.miyavi.android.clawdroid.core.data.ChatMessage
import jp.miyavi.android.clawdroid.core.data.MessagePart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLogicTest {
    private fun msg(id: String, role: ChatMessage.Role, text: String? = null, error: Boolean = false, image: Boolean = false) = ChatMessage(
        id = id, role = role,
        parts = listOfNotNull(text?.let { MessagePart.Text(it) }, if (image) MessagePart.Image("u", null, "image/png") else null),
        timestamp = null, runId = null, isError = error, truncated = false,
    )

    @Test
    fun completion_onlyRunningToIdle() {
        assertTrue(isRunCompleted(true, false))
        assertFalse(isRunCompleted(null, false))
        assertFalse(isRunCompleted(false, false))
        assertFalse(isRunCompleted(true, true))
    }

    @Test
    fun replyToNotify_lastAssistantAfterLastUser() {
        val u = ChatMessage.Role.USER
        val a = ChatMessage.Role.ASSISTANT
        assertEquals("a2", replyToNotify(listOf(msg("u1", u, "q"), msg("a1", a, "x"), msg("a2", a, "y")))?.id)
        assertNull(replyToNotify(listOf(msg("a1", a, "x"), msg("u1", u, "q"))))
        assertNull(replyToNotify(listOf(msg("u1", u, "q"), msg("a1", a, "boom", error = true))))
        assertEquals("a1", replyToNotify(listOf(msg("u1", u, "q"), msg("a1", a, image = true)))?.id)
    }

    @Test
    fun text_stripsMarkdownAndTrims() {
        val m = msg("a", ChatMessage.Role.ASSISTANT, "## Done\n- **built** the `app`\n[link](http://x)\n```kotlin\nval x = 1\n```")
        assertEquals("Done built the app link val x = 1", notificationText(m, "画像"))
        assertEquals("画像", notificationText(msg("b", ChatMessage.Role.ASSISTANT, image = true), "画像"))
        val long = notificationText(msg("c", ChatMessage.Role.ASSISTANT, "あ".repeat(400)), "画像")
        assertEquals(301, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun automationFailure_onlyFinishedErrors() {
        fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject
        val failed = automationFailure(obj("""{"action":"finished","jobId":"j1","status":"error","error":"timeout\nmore","job":{"id":"j1","name":"Daily","agentId":"oracle"}}"""))
        assertEquals("Daily", failed?.name)
        assertEquals("timeout", failed?.error)
        assertEquals("j1", automationFailure(obj("""{"action":"finished","jobId":"j1","job":{"id":"j1","state":{"lastRunStatus":"error"}}}"""))?.jobId)
        assertNull(automationFailure(obj("""{"action":"finished","jobId":"j1","status":"ok"}""")))
        assertNull(automationFailure(obj("""{"action":"started","jobId":"j1","status":"error"}""")))
    }
}
