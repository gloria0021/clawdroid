package jp.miyavi.android.clawdroid.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * openclaw.json のエージェントの定義を既定と重ねて読む（spec 004 FR-010）。
 * 形はマスターの Gateway の `config.get`（2026-11-24）から写し、名前とパスを伏せたもの。
 */
class AgentConfigTest {
    private val sample = Json.parseToJsonElement(
        """
        {"hash":"x","config":{
          "agents":{"ownership":"explicit",
            "defaults":{"model":"openai/gpt-6","thinkingDefault":"low","workspace":"/h/.openclaw/workspace"},
            "entries":{
              "architect":{"name":"A","workspace":"/h/.openclaw/workspace-architect","model":"anthropic/claude-opus-5-5","thinkingDefault":"medium",
                "tools":{"profile":"full","allow":["group:fs"],"alsoAllow":["web_fetch"],"deny":["bundle-mcp"],
                  "elevated":{"enabled":false},"exec":{"mode":"auto","backgroundMs":120000},"fs":{"workspaceOnly":false}}},
              "blue":{"name":"B","model":{"primary":"anthropic/claude-sonnet-5-5"},"tools":{"alsoAllow":[]}}
            }},
          "tools":{"profile":"coding","web":{"search":{"enabled":true,"provider":"parallel-free"}},"elevated":{"enabled":true},"exec":{"mode":"ask"}}
        }}
        """.trimIndent(),
    ) as JsonObject

    @Test fun ownValues_areNotInherited() {
        val c = AgentConfigParse.parse(sample, "architect")!!
        assertEquals(Configured("anthropic/claude-opus-5-5", inherited = false), c.model)
        assertEquals(Configured("medium", inherited = false), c.thinking)
        assertEquals(Configured("full", inherited = false), c.toolProfile)
        assertEquals(listOf("group:fs", "web_fetch"), c.allow)
        assertEquals(listOf("bundle-mcp"), c.deny)
        assertEquals(Configured("auto", inherited = false), c.execMode)
        assertEquals(Configured(false, inherited = false), c.elevated)
        assertEquals(Configured(false, inherited = false), c.workspaceOnly)
        assertEquals("workspace-architect", c.workspaceName)
        assertEquals(true, c.webSearch)
        assertEquals("parallel-free", c.webSearchProvider)
    }

    @Test fun missingValues_fallBackToDefaults() {
        val c = AgentConfigParse.parse(sample, "blue")!!
        assertEquals(Configured("anthropic/claude-sonnet-5-5", inherited = false), c.model)
        assertEquals(Configured("low", inherited = true), c.thinking)
        assertEquals(Configured("coding", inherited = true), c.toolProfile)
        assertEquals(Configured("ask", inherited = true), c.execMode)
        assertEquals(Configured(true, inherited = true), c.elevated)
        assertNull(c.workspaceOnly)
        assertTrue(c.allow.isEmpty())
        assertEquals("workspace", c.workspaceName)
    }

    @Test fun unknownAgent_isNull() {
        assertNull(AgentConfigParse.parse(sample, "nobody"))
        assertFalse(AgentConfigParse.parse(Json.parseToJsonElement("{}") as JsonObject, "a") != null)
    }
}
