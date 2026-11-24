package jp.miyavi.android.clawdroid.core.notify

import jp.miyavi.android.clawdroid.core.gateway.long
import jp.miyavi.android.clawdroid.core.gateway.obj
import jp.miyavi.android.clawdroid.core.gateway.string
import kotlinx.serialization.json.JsonObject

/** 自動化の実行の失敗（spec 006 data-model）。 */
data class AutomationFailure(val jobId: String, val name: String?, val agentId: String?, val error: String?, val runAt: Long?)

/** `cron` の出来事が「実行が終わって失敗した」ものなら、その中身。そうでなければ null（R5）。 */
internal fun automationFailure(payload: JsonObject?): AutomationFailure? {
    payload ?: return null
    if (payload.string("action") != "finished") return null
    val job = payload.obj("job")
    val state = job?.obj("state")
    val status = payload.string("status") ?: state?.string("lastRunStatus") ?: state?.string("lastStatus")
    if (status != "error") return null
    val jobId = payload.string("jobId") ?: job?.string("id") ?: return null
    return AutomationFailure(
        jobId = jobId,
        name = job?.string("name"),
        agentId = job?.string("agentId"),
        error = payload.string("error")?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim(),
        runAt = payload.long("runAtMs") ?: state?.long("lastRunAtMs"),
    )
}
