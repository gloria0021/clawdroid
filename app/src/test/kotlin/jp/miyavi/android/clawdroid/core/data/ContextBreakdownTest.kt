package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.Frames
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextBreakdownTest {
    private val report = Frames.json.parseToJsonElement(
        """{"systemPrompt":{"chars":45270},"injectedWorkspaceFiles":[{"injectedChars":2000},{"injectedChars":12302}],
           "skills":{"promptChars":16016},"tools":{"schemaChars":17231,"listChars":0}}""",
    ).jsonObject

    @Test fun categories_matchControlUiMath() {
        val b = ContextBreakdownParser.parse(report, used = 50_000)
        assertEquals(3576L, b.files)        // 14302 / 4
        assertEquals(4308L, b.tools)        // 17231 / 4
        assertEquals(4004L, b.skills)       // 16016 / 4
        assertEquals(3738L, b.system)       // (45270 - 14302 - 16016 - 0) / 4
        assertEquals(50_000L - 15625L, b.messages) // used - (45270 + 17231) / 4
    }

    @Test fun messagesUnknown_whenFixedExceedsUsed() {
        assertNull(ContextBreakdownParser.parse(report, used = 1_000).messages)
    }

    @Test fun brands() {
        assertEquals(ProviderBrand.CLAUDE, providerBrand(listOf("claude-cli", "anthropic"), "Claude"))
        assertEquals(ProviderBrand.OPENAI, providerBrand(listOf("openai"), "OpenAI"))
        assertNull(providerBrand(listOf("ollama"), "Ollama"))
    }
}
