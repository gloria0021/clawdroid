package jp.miyavi.android.clawdroid.feature.files

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** 更新日時の出し方（たった今 / n 分前 / n 時間前 / 日時、年が違えば年も）。 */
class DateDetailTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val now = ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val minute = 60_000L

    private fun ago(ms: Long) = updatedAgo(now - ms, now, zone)

    @Test fun underAMinute_isJustNow() = assertEquals(UpdatedAgo.JustNow, ago(59_000))
    @Test fun slightlyInFuture_isJustNow() = assertEquals(UpdatedAgo.JustNow, ago(-5_000))
    @Test fun minutes() = assertEquals(UpdatedAgo.Minutes(59), ago(59 * minute + 59_000))
    @Test fun hours() = assertEquals(UpdatedAgo.Hours(23), ago(24 * 60 * minute - 1))
    @Test fun aDayOrMore_isDateTime() = assertEquals(UpdatedAgo.DateTime(withYear = false), ago(24 * 60 * minute))
    @Test fun lastYear_hasYear() = assertEquals(
        UpdatedAgo.DateTime(withYear = true),
        updatedAgo(ZonedDateTime.of(2025, 12, 31, 23, 0, 0, 0, zone).toInstant().toEpochMilli(), now, zone),
    )
}
