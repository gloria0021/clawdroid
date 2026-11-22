package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.bool
import jp.miyavi.android.clawdroid.core.gateway.int
import jp.miyavi.android.clawdroid.core.gateway.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class AgentAutomation(val id: String, val name: String, val enabled: Boolean)

/** Read-only inventory; Gateway applies ownership filtering before pagination. */
internal suspend fun listAgentAutomations(
    agentId: String,
    request: suspend (JsonObject) -> JsonElement?,
): List<AgentAutomation> {
    require(agentId.isNotBlank())
    val jobs = linkedMapOf<String, AgentAutomation>()
    var offset = 0
    repeat(50) {
        val page = request(buildJsonObject {
            put("agentId", agentId)
            put("includeDisabled", true)
            put("sortBy", "name")
            put("limit", 100)
            put("offset", offset)
        }) as? JsonObject ?: error("Invalid cron list response")
        val rows = page["jobs"] as? JsonArray ?: error("Missing cron jobs")
        for (row in rows) {
            val job = row as? JsonObject ?: continue
            // Never render a job with an explicit conflicting owner.
            if (job.string("agentId")?.let { !it.equals(agentId, ignoreCase = true) } == true) continue
            val id = job.string("id") ?: continue
            jobs[id] = AgentAutomation(id, job.string("name") ?: id, job.bool("enabled") == true)
        }
        val next = page.int("nextOffset") ?: return jobs.values.toList()
        check(next > offset) { "Invalid cron list page offset" }
        offset = next
    }
    error("Too many cron list pages")
}
