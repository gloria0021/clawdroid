package jp.miyavi.android.sumahoclaw.core.data

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

    @Test fun resetCommands_areResetNotices_keepingRunId() {
        val soft = parse("""{"role":"user","content":"/reset soft","idempotencyKey":"r1:user"}""")
        assertEquals(SystemNotice.Reset(ResetKind.SOFT), soft.notice)
        assertEquals(ChatMessage.Role.SYSTEM, soft.role)
        // 送信の受け付け待ちとの突き合わせに使うので、送信 ID は残す
        assertEquals("r1", soft.runId)
        assertEquals(SystemNotice.Reset(ResetKind.RESET), parse("""{"role":"user","content":" /reset "}""").notice)
        assertEquals(SystemNotice.Reset(ResetKind.NEW), parse("""{"role":"user","content":"/new"}""").notice)
        // 続けて文を書いたもの・アシスタントの発言はふつうのまま
        assertNull(parse("""{"role":"user","content":"/reset soft then continue"}""").notice)
        assertNull(parse("""{"role":"assistant","content":"/reset"}""").notice)
    }

    @Test fun gatewayResetMarker_isResetNotice() {
        val m = parse("""{"role":"system","content":[{"type":"text","text":"Reset"}],"__openclaw":{"kind":"reset","id":"x"}}""")
        assertEquals(SystemNotice.Reset(ResetKind.UNKNOWN), m.notice)
    }

    @Test fun startupPromptAndResetAcks_areHidden() {
        assertNull(raw("""{"role":"user","content":"A new session was started via /new or /reset. Execute your Session Startup sequence now","__openclaw":{"importedFrom":"claude-cli"}}"""))
        assertNull(raw("""{"role":"assistant","model":"gateway-injected","content":[{"type":"text","text":"\u2705 Session reset."}]}"""))
        assertNull(raw("""{"role":"assistant","content":[{"type":"text","text":"\u2705 New session started."}]}"""))
    }

    @Test fun adjacentResetMarker_isMergedIntoCommand() {
        val marker = parse("""{"role":"system","content":"Reset","__openclaw":{"kind":"reset","id":"b"}}""")
        val command = parse("""{"role":"user","content":"/reset","idempotencyKey":"r2:user"}""")
        val hello = parse("""{"role":"user","content":"hello"}""")
        assertEquals(listOf(command, hello), withoutDuplicateResets(listOf(marker, command, hello)))
        assertEquals(listOf(command, hello), withoutDuplicateResets(listOf(command, marker, hello)))
        // 単独の区切り（自動の切り替えなど）は残す
        assertEquals(listOf(hello, marker, hello), withoutDuplicateResets(listOf(hello, marker, hello)))
    }
}
