package jp.miyavi.android.sumahoclaw.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunOutputUsageTest {
    @Test fun cumulativeUsageUpdatesWithoutAssistantTextAndIgnoresStaleReplay() {
        fun usage(seq: Int, count: Int) = checkNotNull(parseRunOutputUsage(Json.parseToJsonElement(
            """{"runId":"a","seq":$seq,"stream":"usage","data":{"outputTokens":$count}}""",
        ).jsonObject))
        val initial = ChatState("s", "agent", running = true, activeRunId = "a")
        assertNull(initial.reportedOutputTokens)
        val first = initial.copy(usageByRun = initial.usageByRun.withUsage(usage(1, 42)))
        assertEquals(42L, first.reportedOutputTokens)
        val next = first.copy(usageByRun = first.usageByRun.withUsage(usage(2, 1400)).withUsage(usage(1, 42)))
        assertEquals(1400L, next.reportedOutputTokens)
        assertNull(next.copy(activeRunId = "b").reportedOutputTokens)
    }
}
