package jp.miyavi.android.clawdroid.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** コンテキストの圧縮などのお知らせを、利用者の吹き出しにしない（マスター指定 2026-11-24）。 */
class SystemNoticeParseTest {
    private fun parse(json: String) = Parse.message(Json.parseToJsonElement(json).jsonObject, 0)!!

    @Test fun claudeCliCompactSummary_isCompactionNotice() {
        val m = parse("""{"role":"user","content":"This session is being continued from a previous conversation that ran out of context.","provenance":{"kind":"internal_system","sourceTool":"cli_harness_context"}}""")
        assertEquals(SystemNotice.Compaction(null, null), m.notice)
        assertEquals(ChatMessage.Role.SYSTEM, m.role)
    }

    @Test fun otherHarnessContext_isInjectedContext() {
        val m = parse("""{"role":"user","content":"extra","provenance":{"kind":"internal_system","sourceTool":"cli_harness_context"}}""")
        assertEquals(SystemNotice.InjectedContext, m.notice)
    }

    @Test fun openclawCompactionMarker_keepsTokens() {
        val m = parse("""{"role":"system","content":"summary","__openclaw":{"kind":"compaction","id":"c1","tokensBefore":120000,"tokensAfter":18000}}""")
        assertEquals(SystemNotice.Compaction(120000, 18000), m.notice)
    }

    @Test fun ordinaryUserMessage_hasNoNotice() {
        val m = parse("""{"role":"user","content":"hello"}""")
        assertNull(m.notice)
        assertEquals(ChatMessage.Role.USER, m.role)
    }

    private fun raw(json: String) = Parse.message(Json.parseToJsonElement(json).jsonObject, 0)

    @Test fun cliLocalCommandStdout_isHidden() {
        assertNull(raw("""{"role":"user","content":"<local-command-stdout>Compacted PostCompact [hook]</local-command-stdout>","__openclaw":{"importedFrom":"claude-cli"}}"""))
    }

    @Test fun gatewayCompactedReply_isHidden_butFailureStays() {
        assertNull(raw("""{"role":"assistant","model":"gateway-injected","content":[{"type":"text","text":"\u2699\ufe0f Compaction finished (resulting context unknown)"}]}"""))
        assertEquals(ChatMessage.Role.ASSISTANT, raw("""{"role":"assistant","model":"gateway-injected","content":[{"type":"text","text":"\u2699\ufe0f Compaction failed: x"}]}""")!!.role)
    }

    @Test fun compactCommand_detection() {
        assertTrue(isCompactCommand(" /compact "))
        assertTrue(isCompactCommand("/compact keep the API notes"))
        assertFalse(isCompactCommand("/compacted"))
    }
}
