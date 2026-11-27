package jp.miyavi.android.sumahoclaw.core.data

import jp.miyavi.android.sumahoclaw.core.gateway.long
import jp.miyavi.android.sumahoclaw.core.gateway.obj
import jp.miyavi.android.sumahoclaw.core.gateway.string
import kotlinx.serialization.json.JsonObject

/** A cumulative Gateway counter, not a per-event delta. */
data class RunOutputUsage(val runId: String, val sequence: Long, val tokens: Long?)

internal fun parseRunOutputUsage(payload: JsonObject): RunOutputUsage? {
    if (payload.string("stream") != "usage") return null
    val runId = payload.string("runId") ?: return null
    val sequence = payload.long("seq") ?: return null
    val data = payload.obj("data") ?: return null
    val tokens = if (data.obj("usageReport")?.string("state") == "unavailable") null
        else data.long("outputTokens")?.takeIf { it >= 0 } ?: return null
    return RunOutputUsage(runId, sequence, tokens)
}

internal fun Map<String, RunOutputUsage>.withUsage(usage: RunOutputUsage): Map<String, RunOutputUsage> {
    if (get(usage.runId)?.sequence?.let { it >= usage.sequence } == true) return this
    val recent = (entries.filterNot { it.key == usage.runId }.takeLast(31).map { it.toPair() }).toMap()
    return recent + (usage.runId to usage)
}
