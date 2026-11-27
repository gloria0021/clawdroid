package jp.miyavi.android.sumahoclaw.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `system.info` のメモリとディスク（シートの 3 等分の数字）。 */
class HostStatsParseTest {
    private fun parse(json: String) = Parse.hostStats(Json.parseToJsonElement(json).jsonObject)

    @Test fun readsMemoryAndDisk_usedIsTotalMinusFree() {
        val stats = parse("""{"memoryTotalBytes":16000,"memoryFreeBytes":4000,"diskTotalBytes":1000,"diskAvailableBytes":150,"disks":[]}""")!!
        assertEquals(HostStats(16000, 4000, 1000, 150), stats)
        assertEquals(0.75f, stats.memoryUsedFraction, 0.0001f)
        assertEquals(0.85f, stats.diskUsedFraction, 0.0001f)
    }

    @Test fun missingOrZeroTotals_areNull() {
        assertNull(parse("""{"memoryTotalBytes":16000,"memoryFreeBytes":4000}"""))
        assertNull(parse("""{"memoryTotalBytes":0,"memoryFreeBytes":0,"diskTotalBytes":1000,"diskAvailableBytes":150}"""))
    }

    @Test fun freeLargerThanTotal_isClamped() {
        val stats = parse("""{"memoryTotalBytes":100,"memoryFreeBytes":120,"diskTotalBytes":100,"diskAvailableBytes":100}""")!!
        assertEquals(0f, stats.memoryUsedFraction, 0f)
        assertEquals(100, stats.memoryFreeBytes)
    }
}
