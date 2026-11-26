package jp.miyavi.android.clawdroid.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ClaudeCliUsageTest {
    private fun time(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()
    private val now = time("2026-11-26T16:00:00+09:00[Asia/Tokyo]")
    private val output = """
        You are currently using your subscription to power your Claude Code usage
        Current session: 55% used · resets Nov 26, 6pm (Asia/Tokyo)
        Current week (all models): 51% used · resets Nov 30, 2am (Asia/Tokyo)
        Current week (Fable): 0% used · resets Nov 30, 2am (Asia/Tokyo)
    """.trimIndent()

    @Test fun onlyClaudeCliProviderIsEligible() {
        assertTrue(isClaudeCliModel("claude-cli/opus"))
        assertTrue(isClaudeCliModel("CLAUDE-CLI/sonnet"))
        assertFalse(isClaudeCliModel("anthropic/claude-opus"))
        assertFalse(isClaudeCliModel("openai/gpt"))
        assertFalse(isClaudeCliModel("claude-opus"))
        assertFalse(isClaudeCliModel(null))
    }

    @Test fun gatewayRuntimeDistinguishesCliFromApiForTheSameModel() {
        val model = "anthropic/claude-opus-5-5"
        val agent = Parse.agent(Json.parseToJsonElement(
            """{"id":"architect","model":{"primary":"$model"},"agentRuntime":{"id":"claude-cli"}}""",
        ).jsonObject)!!
        fun session(runtime: String) = Parse.session(Json.parseToJsonElement(
            """{"key":"agent:architect:main","modelProvider":"anthropic","model":"claude-opus-5-5","agentRuntime":{"id":"$runtime"}}""",
        ).jsonObject)!!

        assertTrue(usesClaudeCli(session("claude-cli"), null, null))
        assertFalse(usesClaudeCli(session("openclaw"), agent, null))
        assertTrue(usesClaudeCli(null, agent, null))
        assertFalse(usesClaudeCli(null, agent, "openai/gpt"))
    }

    @Test fun readsBothWindowsAndHostTimezone() {
        val usage = parseClaudeUsage(output, now)!!
        assertEquals(listOf("claude-cli", "anthropic"), usage.ids)
        assertEquals(listOf("5h", "Week"), usage.windows.map { it.label })
        assertEquals(0.55f, usage.windows[0].usedFraction, 0.001f)
        assertEquals(0.51f, usage.windows[1].usedFraction, 0.001f)
        assertEquals(time("2026-11-26T18:00:00+09:00[Asia/Tokyo]"), usage.windows[0].resetAt)
        assertEquals(time("2026-11-30T02:00:00+09:00[Asia/Tokyo]"), usage.windows[1].resetAt)
    }

    @Test fun handlesMidnightAndYearRollover() {
        val text = output.replace("Nov 26, 6pm", "12am").replace("Nov 30, 2am", "Jan 2, 9:05am")
        val usage = parseClaudeUsage(text, time("2026-12-31T16:00:00+09:00[Asia/Tokyo]"))!!
        assertEquals(time("2027-01-01T00:00:00+09:00[Asia/Tokyo]"), usage.windows[0].resetAt)
        assertEquals(time("2027-01-02T09:05:00+09:00[Asia/Tokyo]"), usage.windows[1].resetAt)
    }

    @Test fun rejectsMissingOrInvalidUsage() {
        for (text in listOf(
            "Not logged in", output.replace("55%", "101%"), output.replace("6pm", "13pm"),
            output.replace("Nov 30", "Feb 30"), output.replace("Asia/Tokyo", "Unknown/Zone"),
            output.substringBefore("Current week (all models)"),
        )) assertNull(text, parseClaudeUsage(text, now))
        assertNotNull(parseClaudeUsage(output.replace("55%", "0%").replace("51%", "100%"), now))
    }

    @Test fun waitsForCompleteFrameAndRejectsFailedCommands() {
        val command = claudeUsageCommand("n0nce")
        assertNull(parseClaudeUsageOutput(command, "n0nce", now))
        assertNull(parseClaudeUsageOutput("\u001eother:start\u001e$output\u001eother:end:0\u001e", "n0nce", now))
        val start = "\u001en0nce:start\u001e\r\n\u001b[32m$output\u001b[0m\r\n"
        assertNull(parseClaudeUsageOutput(start, "n0nce", now))
        assertNull(parseClaudeUsageOutput(start + "\u001en0nce:end:0", "n0nce", now))
        assertEquals(0.55f, parseClaudeUsageOutput(command + start + "\u001en0nce:end:0\u001e", "n0nce", now)!!.single().windows[0].usedFraction)
        assertEquals(emptyList<ProviderUsage>(), parseClaudeUsageOutput(start + "\u001en0nce:end:1\u001e", "n0nce", now))
    }

    @Test fun generatedCommandRunsTheExactUsageCommand() {
        assumeTrue(File("/bin/sh").canExecute())
        val dir = kotlin.io.path.createTempDirectory("claude-usage").toFile()
        try {
            File(dir, "claude").apply {
                writeText("#!/bin/sh\n[ \"\$*\" = '-p /usage --no-session-persistence' ] || exit 9\ncat <<'USAGE'\n$output\nUSAGE\n")
                setExecutable(true)
            }
            val process = ProcessBuilder("/bin/sh").redirectErrorStream(true).apply {
                environment()["PATH"] = dir.path + ":" + System.getenv("PATH")
            }.start()
            process.outputStream.use {
                it.write(claudeUsageCommand("check").replace("\r", "\n").replace("stty -echo; ", "").toByteArray())
            }
            assertTrue(process.waitFor(5, TimeUnit.SECONDS))
            val framed = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue())
            assertEquals(0.55f, parseClaudeUsageOutput(framed, "check", now)!!.single().windows[0].usedFraction)
        } finally {
            dir.deleteRecursively()
        }
    }
}
