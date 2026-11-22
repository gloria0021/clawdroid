package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.Frames
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseTest {
    private fun obj(text: String) = Frames.json.parseToJsonElement(text).jsonObject

    @Test fun session_titleAndFolderAndFlags() {
        val s = Parse.session(obj("""{"key":"agent:a:1","agentId":"a","derivedTitle":"Fix bug","execCwd":"/home/x/proj","status":"running","unread":true,"createdVia":"operator","lastActivityAt":5}"""))!!
        assertEquals("Fix bug", s.title)
        assertEquals("/home/x/proj", s.cwd)
        assertTrue(s.running)
        assertTrue(s.unread)
        assertFalse(s.isAutomation)
        assertEquals(5L, s.updatedAt)
    }

    @Test fun session_labelWinsOverDerivedTitle_andCronIsAutomation() {
        val s = Parse.session(obj("""{"key":"k","label":"Mine","derivedTitle":"Auto","createdVia":"cron","hasActiveRun":false}"""))!!
        assertEquals("Mine", s.title)
        assertTrue(s.isAutomation)
        assertFalse(s.running)
    }

    @Test fun session_withoutKey_isNull() {
        assertNull(Parse.session(obj("""{"label":"x"}""")))
    }

    @Test fun message_textAndToolParts() {
        val m = Parse.message(
            obj(
                """{"role":"assistant","content":[{"type":"text","text":"Hello"},{"type":"tool_use","id":"t1","name":"exec","input":{"command":"ls -la"}}],"__openclaw":{"id":"e1","runId":"r1"},"timestamp":10}""",
            ),
            0,
        )!!
        assertEquals("e1", m.id)
        assertEquals(ChatMessage.Role.ASSISTANT, m.role)
        assertEquals("Hello", m.text)
        val tool = m.parts[1] as MessagePart.ToolCall
        assertEquals("exec", tool.name)
        assertEquals("ls -la", tool.detail)
        assertFalse(tool.isResult)
    }

    @Test fun message_toolResultRole() {
        val m = Parse.message(obj("""{"role":"toolResult","toolName":"exec","toolCallId":"t1","content":[{"type":"text","text":"ok"}],"isError":true}"""), 3)!!
        assertEquals(ChatMessage.Role.TOOL_RESULT, m.role)
        val tool = m.parts.single() as MessagePart.ToolCall
        assertTrue(tool.isResult)
        assertTrue(tool.isError)
        assertEquals("ok", tool.result)
    }

    @Test fun message_userStringContent_andUnknownRoleIgnored() {
        assertEquals("hi", Parse.message(obj("""{"role":"user","content":"hi"}"""), 0)!!.text)
        assertNull(Parse.message(obj("""{"role":"weird","content":"x"}"""), 0))
    }

    @Test fun agent_identityNameAndSystemKind() {
        val a = Parse.agent(obj("""{"id":"architect","identity":{"name":"Moonshot","emoji":"🚀"},"model":{"primary":"claude"},"kind":"agent"}"""))!!
        assertEquals("Moonshot", a.name)
        assertEquals("🚀", a.emoji)
        assertFalse(a.isSystem)
        assertTrue(Parse.agent(obj("""{"id":"sys","kind":"system"}"""))!!.isSystem)
    }
}
