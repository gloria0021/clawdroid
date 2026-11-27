package jp.miyavi.android.sumahoclaw.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `skills.status` の解析・状態・並び順（spec 004 US3）。
 * データはマスターの Gateway の実際の応答（2026-11-24）から 4 件を写し、パスを伏せたもの。
 */
class SkillsTest {
    private val sample = Json.parseToJsonElement(
        """
        {"workspaceDir":"/x","managedSkillsDir":"/y","agentId":"a","skills":[
          {"name":"weather","description":"Get forecasts.\nMore text","emoji":"🌤","skillKey":"weather","filePath":"/s/weather/SKILL.md",
           "source":"openclaw-bundled","bundled":true,"eligible":true,"disabled":false,"blockedByAllowlist":false,"blockedByAgentFilter":false,
           "platformIncompatible":false,"missing":{"bins":[],"anyBins":[],"env":[],"config":[],"os":[]},"install":[],"configChecks":[]},
          {"name":"1password","description":"Use op.","emoji":"🔐","skillKey":"1password","filePath":"/s/1p/SKILL.md","source":"openclaw-bundled",
           "eligible":false,"disabled":false,"blockedByAllowlist":true,"blockedByAgentFilter":false,
           "missing":{"bins":["op"],"anyBins":[],"env":[],"config":[],"os":[]}},
          {"name":"apple-notes","skillKey":"apple-notes","eligible":false,"disabled":true,"missing":{"os":["darwin"]}},
          {"name":"Alpha","skillKey":"alpha","eligible":true,"disabled":false,"unknownField":{"x":1}}
        ]}
        """.trimIndent(),
    ) as JsonObject

    @Test fun parse_statesAndOrder() {
        val skills = SkillParse.status(sample)
        // 同じ状態の中では出どころの順（同梱の weather が、出どころの分からない Alpha より先）
        assertEquals(listOf("weather", "Alpha", "1password", "apple-notes"), skills.map { it.name })
        assertEquals(listOf(SkillState.ENABLED, SkillState.ENABLED, SkillState.UNMET, SkillState.DISABLED), skills.map { it.state })
    }

    @Test fun parse_missingAndBlocks() {
        val op = SkillParse.status(sample).first { it.skillKey == "1password" }
        assertEquals(listOf("op"), op.missing.bins)
        assertTrue(op.blockedByAllowlist)
        assertTrue(op.enabled) // 要件が足りないだけで、無効ではない
        val notes = SkillParse.status(sample).first { it.skillKey == "apple-notes" }
        assertEquals(listOf("darwin"), notes.missing.os)
        assertFalse(notes.enabled)
    }

    @Test fun origin_agentOnlyFirst_withinState() {
        val obj = Json.parseToJsonElement(
            """{"skills":[
              {"name":"a-bundled","source":"openclaw-bundled","bundled":true,"eligible":true,"disabled":false},
              {"name":"b-shared","source":"openclaw-managed","eligible":true,"disabled":false},
              {"name":"c-mine","source":"openclaw-workspace","eligible":true,"disabled":false},
              {"name":"d-learned","source":"openclaw-workshop","eligible":true,"disabled":false},
              {"name":"e-personal","source":"agents-skills-personal","eligible":true,"disabled":false}
            ]}""",
        ) as JsonObject
        val skills = SkillParse.status(obj)
        assertEquals(listOf("c-mine", "d-learned", "b-shared", "e-personal", "a-bundled"), skills.map { it.name })
        assertEquals(listOf(true, true, false, false, false), skills.map { it.origin.agentOnly })
        assertEquals(SkillOrigin.WORKSHOP, skills[1].origin)
    }

    @Test fun parse_unknownStateHasNoToggle() {
        val obj = Json.parseToJsonElement("""{"skills":[{"name":"x"}]}""") as JsonObject
        val skill = SkillParse.status(obj).single()
        assertNull(skill.state)
        assertFalse(skill.enabled)
        assertEquals("x", skill.skillKey)
    }
}
