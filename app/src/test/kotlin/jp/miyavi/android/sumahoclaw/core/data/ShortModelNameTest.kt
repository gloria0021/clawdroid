package jp.miyavi.android.sumahoclaw.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortModelNameTest {
    @Test fun names() {
        assertEquals("Opus 5.5", shortModelName("anthropic/claude-opus-5-5"))
        assertEquals("Opus 5.5", shortModelName("claude-opus-5-5"))
        assertEquals("Sonnet 5.5", shortModelName("anthropic/claude-sonnet-5-5"))
        assertEquals("GPT 5.5", shortModelName("openai/gpt-5.5"))
        assertEquals("Gemini 3 Pro", shortModelName("google/gemini-3-pro"))
    }
}
