package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.Frames
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ParseAuthUsageTest {
    @Test fun authStatus_includesCliProviders_andSkipsThoseWithoutWindows() {
        val obj = Frames.json.parseToJsonElement(
            """{"providers":[
              {"provider":"google","displayName":"Google"},
              {"provider":"openai","displayName":"OpenAI","usage":{"providerId":"openai","plan":"plus","windows":[{"label":"5h","usedPercent":6},{"label":"Week","usedPercent":95,"resetAt":10}]}},
              {"provider":"claude-cli","displayName":"Claude","usage":{"providerId":"anthropic","windows":[{"label":"5h","usedPercent":17},{"label":"Week","usedPercent":25}]}},
              {"provider":"anthropic","displayName":"Claude","usage":{"providerId":"anthropic","windows":[{"label":"5h","usedPercent":17},{"label":"Week","usedPercent":25}]}}
            ]}""",
        ).jsonObject
        val usage = Parse.authStatusUsage(obj)
        assertEquals(listOf("OpenAI", "Claude"), usage.map { it.name })
        assertEquals("plus", usage[0].plan)
        assertEquals(listOf("claude-cli", "anthropic"), usage[1].ids)
        assertEquals(0.17f, usage[1].windows[0].usedFraction, 0.001f)
        assertEquals(10L, usage[0].windows[1].resetAt)
    }
}
