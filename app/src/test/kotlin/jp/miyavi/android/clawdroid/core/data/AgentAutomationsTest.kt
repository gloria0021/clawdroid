package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.bool
import jp.miyavi.android.clawdroid.core.gateway.int
import jp.miyavi.android.clawdroid.core.gateway.string
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentAutomationsTest {
    @Test
    fun requestsSelectedAgentOnEveryPage() = runTest {
        val offsets = mutableListOf<Int>()
        val jobs = listAgentAutomations("architect") { params ->
            assertEquals("architect", params.string("agentId"))
            assertEquals(true, params.bool("includeDisabled"))
            val offset = requireNotNull(params.int("offset"))
            offsets += offset
            Json.parseToJsonElement(if (offset == 0) {
                """{"jobs":[{"id":"one","agentId":"architect","name":"Daily","enabled":true},{"id":"other","agentId":"main","enabled":true}],"nextOffset":2}"""
            } else {
                """{"jobs":[{"id":"two","agentId":"architect","name":"Weekly","enabled":false}],"nextOffset":null}"""
            })
        }
        assertEquals(listOf(0, 2), offsets)
        assertEquals(listOf(AgentAutomation("one", "Daily", true), AgentAutomation("two", "Weekly", false)), jobs)
    }

    @Test
    fun emptyInventoryIsNotAnError() = runTest {
        val jobs = listAgentAutomations("main") { Json.parseToJsonElement("""{"jobs":[],"nextOffset":null}""") }
        assertEquals(emptyList<AgentAutomation>(), jobs)
    }
}
