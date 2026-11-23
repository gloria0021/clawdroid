package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.feature.automation.ScheduleSummary
import jp.miyavi.android.clawdroid.feature.automation.summarizeCron
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自動化の読み取り・予定の言葉・更新の差分（spec の cron.*）。 */
class AutomationsTest {
    private val sample = Json.parseToJsonElement(
        """
        {"id":"j1","name":"OpenClaw更新チェック","description":"毎日確認","enabled":true,"agentId":"kino",
         "configRevision":"rev-1","schedule":{"kind":"cron","expr":"0 0 * * *","tz":"Asia/Tokyo"},
         "sessionTarget":"isolated","wakeMode":"now",
         "payload":{"kind":"agentTurn","message":"確認して","thinking":"high","timeoutSeconds":600},
         "delivery":{"mode":"announce"},
         "state":{"nextRunAtMs":2000,"lastRunAtMs":1000,"lastRunStatus":"error","lastError":"boom"}}
        """.trimIndent(),
    ).jsonObject

    @Test fun parse_readsScheduleStateAndPayload() {
        val job = parseAutomation(sample)!!
        assertEquals("OpenClaw更新チェック", job.name)
        assertEquals(CronSchedule.Cron("0 0 * * *", "Asia/Tokyo"), job.schedule)
        assertEquals(CronPayload.AgentTurn("確認して", null, "high", 600), job.payload)
        assertEquals(CronDeliveryMode.ANNOUNCE, job.delivery)
        assertEquals(2000L, job.nextRunAtMs)
        assertEquals(CronRunStatus.ERROR, job.lastRunStatus)
        assertEquals("boom", job.lastError)
        assertEquals("rev-1", job.configRevision)
    }

    @Test fun list_followsPages_andSkipsOtherOwners() = runBlocking {
        val pages = listOf(
            """{"jobs":[{"id":"a","name":"A","enabled":true,"schedule":{"kind":"every","everyMs":3600000},"payload":{"kind":"systemEvent","text":"t"}}],"nextOffset":1}""",
            """{"jobs":[{"id":"b","name":"B","enabled":false,"agentId":"other","schedule":{"kind":"at","at":"x"},"payload":{"kind":"systemEvent","text":"t"}}]}""",
        )
        var call = 0
        val jobs = listAutomations("kino") { Json.parseToJsonElement(pages[call++]) }
        assertEquals(listOf("a"), jobs.map { it.id })
        assertEquals(CronSchedule.Every(3_600_000), jobs.single().schedule)
    }

    @Test fun cronSummaries() {
        assertEquals(ScheduleSummary.Daily(0, 0), summarizeCron("0 0 * * *"))
        assertEquals(ScheduleSummary.Daily(4, 30), summarizeCron("30 4 * * *"))
        assertEquals(ScheduleSummary.Weekdays(9, 0), summarizeCron("0 9 * * 1-5"))
        assertEquals(ScheduleSummary.Weekly(listOf(1, 3), 9, 0), summarizeCron("0 9 * * MON,WED"))
        assertEquals(ScheduleSummary.Monthly(1, 8, 0), summarizeCron("0 8 1 * *"))
        assertEquals(ScheduleSummary.EveryMinutes(15), summarizeCron("*/15 * * * *"))
        assertEquals(ScheduleSummary.Hourly(5), summarizeCron("5 * * * *"))
        assertEquals(ScheduleSummary.EveryHours(3, 0), summarizeCron("0 */3 * * *"))
        assertEquals(ScheduleSummary.Raw("0 0 1 1 *"), summarizeCron("0 0 1 1 *"))
    }

    @Test fun patch_containsOnlyChanges() {
        val job = parseAutomation(sample)!!
        val before = AutomationDraft.of(job)
        assertTrue(buildAutomationPatch(job, before, before).isEmpty())

        val after = before.copy(
            name = "新しい名前",
            prompt = "変えた",
            thinking = "",
            schedule = before.schedule.copy(cronExpr = "0 9 * * *"),
        )
        val patch = buildAutomationPatch(job, before, after)
        assertEquals(setOf("name", "payload", "schedule"), patch.keys)
        val payload = patch["payload"] as JsonObject
        assertEquals("agentTurn", payload["kind"]!!.jsonPrimitive.content)
        assertEquals("変えた", payload["message"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, payload["thinking"]) // 空にしたら既定に戻す
        assertFalse("model" in payload)
        val schedule = patch["schedule"] as JsonObject
        assertEquals("0 9 * * *", schedule["expr"]!!.jsonPrimitive.content)
        assertEquals("Asia/Tokyo", schedule["tz"]!!.jsonPrimitive.content)
    }

    @Test fun everyDraft_usesTheLargestWholeUnit() {
        val draft = ScheduleDraft.of(CronSchedule.Every(2 * 86_400_000L))
        assertEquals(ScheduleDraft.EveryUnit.DAYS, draft.everyUnit)
        assertEquals("2", draft.everyAmount)
        assertEquals(CronSchedule.Every(2 * 86_400_000L), draft.toSchedule())
        assertFalse(draft.copy(everyAmount = "0").valid)
        assertNull(ScheduleDraft.of(CronSchedule.Other("event")).toSchedule())
    }
}
