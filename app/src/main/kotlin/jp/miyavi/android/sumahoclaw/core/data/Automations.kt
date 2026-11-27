package jp.miyavi.android.sumahoclaw.core.data

import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import jp.miyavi.android.sumahoclaw.core.gateway.array
import jp.miyavi.android.sumahoclaw.core.gateway.asObject
import jp.miyavi.android.sumahoclaw.core.gateway.bool
import jp.miyavi.android.sumahoclaw.core.gateway.int
import jp.miyavi.android.sumahoclaw.core.gateway.long
import jp.miyavi.android.sumahoclaw.core.gateway.obj
import jp.miyavi.android.sumahoclaw.core.gateway.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/** 自動化の実行の予定（`cron.ts` の CronSchedule）。 */
sealed interface CronSchedule {
    /** [staggerMs] は画面では触らず、書き戻すときにそのまま残す。 */
    data class Cron(val expr: String, val tz: String?, val staggerMs: Long? = null) : CronSchedule
    data class Every(val everyMs: Long) : CronSchedule
    data class At(val at: String) : CronSchedule
    /** event / on-exit / stream。アプリでは見せるだけで編集しない。 */
    data class Other(val kind: String) : CronSchedule
}

/** 実行する中身（`cron.ts` の CronPayload）。 */
sealed interface CronPayload {
    data class AgentTurn(val message: String, val model: String?, val thinking: String?, val timeoutSeconds: Long?) : CronPayload
    data class SystemEvent(val text: String) : CronPayload
    /** command / script。アプリでは見せるだけで編集しない。 */
    data class Other(val kind: String, val preview: String?) : CronPayload
}

enum class CronDeliveryMode(val wire: String?) { UNSET(null), NONE("none"), ANNOUNCE("announce"), WEBHOOK("webhook") }

enum class CronRunStatus(val wire: String) {
    OK("ok"), ERROR("error"), SKIPPED("skipped");

    companion object {
        fun of(value: String?): CronRunStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** 自動化 1 件（`cron.ts` の CronJob のうち、アプリで使うところ）。 */
data class Automation(
    val id: String,
    val name: String,
    val description: String?,
    val enabled: Boolean,
    val agentId: String?,
    val schedule: CronSchedule,
    val payload: CronPayload,
    val delivery: CronDeliveryMode,
    val nextRunAtMs: Long?,
    val lastRunAtMs: Long?,
    val lastRunStatus: CronRunStatus?,
    val lastError: String?,
    val runningAtMs: Long?,
    /** 定義の版。更新・削除のときに渡し、ほかで書き換えられていたら Gateway に断らせる。 */
    val configRevision: String?,
)

/** 実行履歴の 1 行（`cron.ts` の CronRunLogEntry）。 */
data class AutomationRun(
    val key: String,
    val runAtMs: Long,
    val status: CronRunStatus?,
    val durationMs: Long?,
    val summary: String?,
    val error: String?,
    val model: String?,
    val deliveryStatus: String?,
)

data class AutomationRunsPage(val runs: List<AutomationRun>, val nextOffset: Int?)

/** 自動化の読み書き（画面からはこれだけを見る。テストでは偽物に差し替える）。 */
interface AutomationSource {
    suspend fun list(agentId: String): List<Automation>
    suspend fun get(id: String): Automation?
    suspend fun setEnabled(job: Automation, enabled: Boolean): Automation?
    suspend fun update(job: Automation, patch: JsonObject): Automation?
    suspend fun runNow(id: String)
    suspend fun remove(job: Automation)
    suspend fun runs(id: String, offset: Int): AutomationRunsPage
}

/** 自動化の読み書き（cron.*）。書き込みは operator.admin が要る。 */
class AutomationsRepository(private val gateway: GatewayClient) : AutomationSource {
    override suspend fun list(agentId: String): List<Automation> = listAutomations(agentId) { gateway.request("cron.list", it) }

    override suspend fun get(id: String): Automation? =
        gateway.request("cron.get", buildJsonObject { put("id", id) }).asObject()?.let { it.obj("job") ?: it }?.let(::parseAutomation)

    override suspend fun setEnabled(job: Automation, enabled: Boolean): Automation? =
        update(job, buildJsonObject { put("enabled", enabled) })

    override suspend fun update(job: Automation, patch: JsonObject): Automation? {
        val result = gateway.request("cron.update", buildJsonObject {
            put("id", job.id)
            put("patch", patch)
            job.configRevision?.let { put("expectedConfigRevision", it) }
        }).asObject()
        return result?.let { it.obj("job") ?: it }?.let(::parseAutomation)
    }

    override suspend fun runNow(id: String) {
        gateway.request("cron.run", buildJsonObject {
            put("id", id)
            put("mode", "force")
        })
    }

    override suspend fun remove(job: Automation) {
        gateway.request("cron.remove", buildJsonObject {
            put("id", job.id)
            job.configRevision?.let { put("expectedConfigRevision", it) }
        })
    }

    override suspend fun runs(id: String, offset: Int): AutomationRunsPage {
        val page = gateway.request("cron.runs", buildJsonObject {
            put("id", id)
            put("view", "summary")
            put("sortDir", "desc")
            put("limit", RUNS_PAGE_SIZE)
            put("offset", offset)
        }).asObject() ?: return AutomationRunsPage(emptyList(), null)
        val runs = page.array("entries").mapNotNull { it.asObject()?.let(::parseRun) }
        val next = page.int("nextOffset")?.takeIf { page.bool("hasMore") != false && it > offset }
        return AutomationRunsPage(runs, next)
    }

    private companion object {
        const val RUNS_PAGE_SIZE = 30
    }
}

/** エージェントの自動化を、ページを辿ってすべて読む（Gateway が持ち主で絞ってから区切る）。 */
internal suspend fun listAutomations(agentId: String, request: suspend (JsonObject) -> JsonElement?): List<Automation> {
    require(agentId.isNotBlank())
    val jobs = linkedMapOf<String, Automation>()
    var offset = 0
    repeat(MAX_LIST_PAGES) {
        val page = request(buildJsonObject {
            put("agentId", agentId)
            put("includeDisabled", true)
            put("sortBy", "name")
            put("limit", 100)
            put("offset", offset)
        }) as? JsonObject ?: error("Invalid cron list response")
        for (row in page.array("jobs")) {
            val job = row.asObject() ?: continue
            // 別の持ち主が明示されている自動化は出さない
            if (job.string("agentId")?.let { !it.equals(agentId, ignoreCase = true) } == true) continue
            parseAutomation(job)?.let { jobs[it.id] = it }
        }
        val next = page.int("nextOffset") ?: return jobs.values.toList()
        check(next > offset) { "Invalid cron list page offset" }
        offset = next
    }
    return jobs.values.toList()
}

private const val MAX_LIST_PAGES = 50

internal fun parseAutomation(job: JsonObject): Automation? {
    val id = job.string("id") ?: return null
    val state = job.obj("state")
    fun stateLong(key: String) = state?.long(key) ?: job.long(key)
    return Automation(
        id = id,
        name = job.string("displayName") ?: job.string("name") ?: id,
        description = job.string("description")?.takeIf(String::isNotBlank),
        enabled = job.bool("enabled") == true,
        agentId = job.string("agentId"),
        schedule = parseSchedule(job.obj("schedule"), job.string("scheduleKind")),
        payload = parsePayload(job.obj("payload")),
        delivery = when (job.obj("delivery")?.string("mode")) {
            "none" -> CronDeliveryMode.NONE
            "announce" -> CronDeliveryMode.ANNOUNCE
            "webhook" -> CronDeliveryMode.WEBHOOK
            else -> CronDeliveryMode.UNSET
        },
        nextRunAtMs = stateLong("nextRunAtMs"),
        lastRunAtMs = stateLong("lastRunAtMs"),
        lastRunStatus = CronRunStatus.of(state?.string("lastRunStatus") ?: job.string("lastRunStatus") ?: state?.string("lastStatus")),
        lastError = state?.string("lastError") ?: job.string("lastRunError"),
        runningAtMs = state?.long("runningAtMs"),
        configRevision = job.string("configRevision"),
    )
}

private fun parseSchedule(schedule: JsonObject?, compactKind: String?): CronSchedule = when (val kind = schedule?.string("kind") ?: compactKind) {
    "cron" -> CronSchedule.Cron(schedule?.string("expr").orEmpty(), schedule?.string("tz"), schedule?.long("staggerMs"))
    "every" -> CronSchedule.Every(schedule?.long("everyMs") ?: 0L)
    "at" -> CronSchedule.At(schedule?.string("at").orEmpty())
    else -> CronSchedule.Other(kind ?: "unknown")
}

private fun parsePayload(payload: JsonObject?): CronPayload = when (val kind = payload?.string("kind")) {
    "agentTurn" -> CronPayload.AgentTurn(
        message = payload.string("message").orEmpty(),
        model = payload.string("model")?.takeIf(String::isNotBlank),
        thinking = payload.string("thinking")?.takeIf(String::isNotBlank),
        timeoutSeconds = (payload["timeoutSeconds"] as? JsonPrimitive)?.doubleOrNull?.toLong(),
    )
    "systemEvent" -> CronPayload.SystemEvent(payload.string("text").orEmpty())
    "command" -> CronPayload.Other(kind, payload.array("argv").mapNotNull { (it as? JsonPrimitive)?.content }.joinToString(" "))
    "script" -> CronPayload.Other(kind, payload.string("script"))
    else -> CronPayload.Other(kind ?: "unknown", null)
}

private fun parseRun(entry: JsonObject): AutomationRun? {
    val ts = entry.long("ts") ?: return null
    return AutomationRun(
        key = entry.string("runId") ?: "$ts",
        runAtMs = entry.long("runAtMs") ?: ts,
        status = CronRunStatus.of(entry.string("status")),
        durationMs = entry.long("durationMs"),
        summary = entry.string("summary")?.takeIf(String::isNotBlank),
        error = entry.string("error")?.takeIf(String::isNotBlank),
        model = entry.string("model"),
        deliveryStatus = entry.string("deliveryStatus"),
    )
}

/** 画面で編集する設定。読み書きできる項目だけ。 */
data class AutomationDraft(
    val name: String,
    val description: String,
    val schedule: ScheduleDraft,
    /** agentTurn の message / systemEvent の text（それ以外は null で編集しない）。 */
    val prompt: String?,
    val model: String,
    val thinking: String,
    val timeoutSeconds: String,
    val delivery: CronDeliveryMode,
) {
    val valid: Boolean
        get() = name.isNotBlank() && schedule.valid && (prompt == null || prompt.isNotBlank()) &&
            (timeoutSeconds.isBlank() || timeoutSeconds.toLongOrNull()?.let { it >= 0 } == true)

    companion object {
        fun of(job: Automation): AutomationDraft {
            val turn = job.payload as? CronPayload.AgentTurn
            return AutomationDraft(
                name = job.name,
                description = job.description.orEmpty(),
                schedule = ScheduleDraft.of(job.schedule),
                prompt = when (val p = job.payload) {
                    is CronPayload.AgentTurn -> p.message
                    is CronPayload.SystemEvent -> p.text
                    is CronPayload.Other -> null
                },
                model = turn?.model.orEmpty(),
                thinking = turn?.thinking.orEmpty(),
                timeoutSeconds = turn?.timeoutSeconds?.toString().orEmpty(),
                delivery = job.delivery,
            )
        }
    }
}

/** 予定の編集。種類を切り替えても、ほかの種類の入力は残す。 */
data class ScheduleDraft(
    val kind: Kind,
    val cronExpr: String = "",
    val timezone: String = "",
    val everyAmount: String = "",
    val everyUnit: EveryUnit = EveryUnit.HOURS,
    val at: String = "",
    /** 編集できない種類の名前。 */
    val otherKind: String? = null,
    /** 元の cron の staggerMs（編集しない）。 */
    val staggerMs: Long? = null,
) {
    enum class Kind { CRON, EVERY, AT, OTHER }
    enum class EveryUnit(val ms: Long) { MINUTES(60_000L), HOURS(3_600_000L), DAYS(86_400_000L) }

    val valid: Boolean
        get() = when (kind) {
            Kind.CRON -> cronExpr.trim().split(Regex("\\s+")).size in 5..6
            Kind.EVERY -> everyAmount.toLongOrNull()?.let { it > 0 } == true
            Kind.AT -> at.isNotBlank()
            Kind.OTHER -> true
        }

    fun toSchedule(): CronSchedule? = when (kind) {
        Kind.CRON -> CronSchedule.Cron(cronExpr.trim(), timezone.trim().ifEmpty { null }, staggerMs)
        Kind.EVERY -> everyAmount.toLongOrNull()?.let { CronSchedule.Every(it * everyUnit.ms) }
        Kind.AT -> CronSchedule.At(at.trim())
        Kind.OTHER -> null
    }

    companion object {
        fun of(schedule: CronSchedule): ScheduleDraft = when (schedule) {
            is CronSchedule.Cron -> ScheduleDraft(Kind.CRON, cronExpr = schedule.expr, timezone = schedule.tz.orEmpty(), staggerMs = schedule.staggerMs)
            is CronSchedule.Every -> {
                val unit = EveryUnit.entries.reversed().firstOrNull { schedule.everyMs % it.ms == 0L } ?: EveryUnit.MINUTES
                ScheduleDraft(Kind.EVERY, everyAmount = (schedule.everyMs / unit.ms).toString(), everyUnit = unit)
            }
            is CronSchedule.At -> ScheduleDraft(Kind.AT, at = schedule.at)
            is CronSchedule.Other -> ScheduleDraft(Kind.OTHER, otherKind = schedule.kind)
        }
    }
}

/** 変更したところだけを `cron.update` の patch にする。変更が無ければ空。 */
fun buildAutomationPatch(job: Automation, before: AutomationDraft, after: AutomationDraft): JsonObject = buildJsonObject {
    if (after.name.trim() != before.name.trim()) put("name", after.name.trim())
    if (after.description.trim() != before.description.trim()) put("description", after.description.trim())
    if (after.schedule != before.schedule) {
        when (val s = after.schedule.toSchedule()) {
            is CronSchedule.Cron -> put("schedule", buildJsonObject {
                put("kind", "cron")
                put("expr", s.expr)
                s.tz?.let { put("tz", it) }
                s.staggerMs?.let { put("staggerMs", it) }
            })
            is CronSchedule.Every -> put("schedule", buildJsonObject {
                put("kind", "every")
                put("everyMs", s.everyMs)
            })
            is CronSchedule.At -> put("schedule", buildJsonObject {
                put("kind", "at")
                put("at", s.at)
            })
            else -> Unit
        }
    }
    when (job.payload) {
        is CronPayload.AgentTurn -> {
            val changed = after.prompt != before.prompt || after.model != before.model ||
                after.thinking != before.thinking || after.timeoutSeconds != before.timeoutSeconds
            if (changed) put("payload", buildJsonObject {
                put("kind", "agentTurn")
                if (after.prompt != before.prompt) after.prompt?.let { put("message", it) }
                if (after.model != before.model) put("model", after.model.trim().ifEmpty { null }.toJson())
                if (after.thinking != before.thinking) put("thinking", after.thinking.trim().ifEmpty { null }.toJson())
                if (after.timeoutSeconds != before.timeoutSeconds) {
                    put("timeoutSeconds", after.timeoutSeconds.trim().toLongOrNull()?.let(::JsonPrimitive) ?: JsonNull)
                }
            })
        }
        is CronPayload.SystemEvent -> if (after.prompt != before.prompt) after.prompt?.let { text ->
            put("payload", buildJsonObject {
                put("kind", "systemEvent")
                put("text", text)
            })
        }
        is CronPayload.Other -> Unit
    }
    if (after.delivery != before.delivery) after.delivery.wire?.let { mode ->
        put("delivery", buildJsonObject { put("mode", mode) })
    }
}

private fun String?.toJson(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull
