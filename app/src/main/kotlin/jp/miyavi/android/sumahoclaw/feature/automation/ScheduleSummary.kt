package jp.miyavi.android.sumahoclaw.feature.automation

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.CronSchedule

/** 予定を、人が読む形に分けたもの（文言は画面で当てる）。 */
sealed interface ScheduleSummary {
    data class Daily(val hour: Int, val minute: Int) : ScheduleSummary
    data class Weekdays(val hour: Int, val minute: Int) : ScheduleSummary
    /** [days] は 0 = 日曜 〜 6 = 土曜。 */
    data class Weekly(val days: List<Int>, val hour: Int, val minute: Int) : ScheduleSummary
    data class Monthly(val day: Int, val hour: Int, val minute: Int) : ScheduleSummary
    data class Hourly(val minute: Int) : ScheduleSummary
    data class EveryMinutes(val minutes: Int) : ScheduleSummary
    data class EveryHours(val hours: Int, val minute: Int) : ScheduleSummary
    data class Raw(val expr: String) : ScheduleSummary
    data class Interval(val everyMs: Long) : ScheduleSummary
    data class Once(val atMs: Long?, val raw: String) : ScheduleSummary
    data class Other(val kind: String) : ScheduleSummary
}

/** よくある形の cron 式だけを言葉にする。それ以外は式のまま。 */
fun summarizeSchedule(schedule: CronSchedule): ScheduleSummary = when (schedule) {
    is CronSchedule.Every -> ScheduleSummary.Interval(schedule.everyMs)
    is CronSchedule.At -> ScheduleSummary.Once(parseInstant(schedule.at), schedule.at)
    is CronSchedule.Other -> ScheduleSummary.Other(schedule.kind)
    is CronSchedule.Cron -> summarizeCron(schedule.expr)
}

internal fun summarizeCron(expr: String): ScheduleSummary {
    val fields = expr.trim().split(Regex("\\s+"))
    val raw = ScheduleSummary.Raw(expr.trim())
    if (fields.size != 5) return raw
    val (min, hour, dom, mon, dow) = fields
    val m = min.toIntOrNull()?.takeIf { it in 0..59 }
    val h = hour.toIntOrNull()?.takeIf { it in 0..23 }
    if (mon != "*") return raw
    return when {
        m != null && h != null && dom == "*" && dow == "*" -> ScheduleSummary.Daily(h, m)
        m != null && h != null && dom == "*" -> {
            val days = parseDays(dow) ?: return raw
            if (days == listOf(1, 2, 3, 4, 5)) ScheduleSummary.Weekdays(h, m) else ScheduleSummary.Weekly(days, h, m)
        }
        m != null && h != null && dow == "*" -> dom.toIntOrNull()?.takeIf { it in 1..31 }?.let { ScheduleSummary.Monthly(it, h, m) } ?: raw
        dom != "*" || dow != "*" -> raw
        m != null && hour == "*" -> ScheduleSummary.Hourly(m)
        min.startsWith("*/") && hour == "*" -> min.removePrefix("*/").toIntOrNull()?.takeIf { it > 0 }?.let(ScheduleSummary::EveryMinutes) ?: raw
        m != null && hour.startsWith("*/") -> hour.removePrefix("*/").toIntOrNull()?.takeIf { it > 0 }?.let { ScheduleSummary.EveryHours(it, m) } ?: raw
        else -> raw
    }
}

/** 曜日の指定（`1,3,5`・`1-5`・`MON`）を 0〜6 の並びにする。7 は日曜。 */
private fun parseDays(field: String): List<Int>? {
    val names = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
    fun day(token: String): Int? = token.toIntOrNull()?.takeIf { it in 0..7 }?.let { it % 7 }
        ?: names.indexOf(token.uppercase()).takeIf { it >= 0 }
    val days = sortedSetOf<Int>()
    for (part in field.split(',')) {
        val range = part.split('-')
        when (range.size) {
            1 -> days += day(range[0]) ?: return null
            2 -> {
                val from = day(range[0]) ?: return null
                val to = range[1].let { if (it == "7") 7 else day(it) } ?: return null
                if (to < from) return null
                (from..to).forEach { days += it % 7 }
            }
            else -> return null
        }
    }
    return days.toList()
}

private fun parseInstant(text: String): Long? = runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
    ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
    ?: runCatching { ZonedDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()

@Composable
fun scheduleText(schedule: CronSchedule): String {
    val time = { h: Int, m: Int -> "%d:%02d".format(h, m) }
    return when (val s = summarizeSchedule(schedule)) {
        is ScheduleSummary.Daily -> stringResource(R.string.schedule_daily, time(s.hour, s.minute))
        is ScheduleSummary.Weekdays -> stringResource(R.string.schedule_weekdays, time(s.hour, s.minute))
        is ScheduleSummary.Weekly -> {
            val names = stringArrayResourceCompat()
            stringResource(R.string.schedule_weekly, s.days.joinToString(stringResource(R.string.schedule_day_separator)) { names[it] }, time(s.hour, s.minute))
        }
        is ScheduleSummary.Monthly -> stringResource(R.string.schedule_monthly, s.day, time(s.hour, s.minute))
        is ScheduleSummary.Hourly -> stringResource(R.string.schedule_hourly, s.minute)
        is ScheduleSummary.EveryMinutes -> stringResource(R.string.schedule_every_minutes, s.minutes)
        is ScheduleSummary.EveryHours -> stringResource(R.string.schedule_every_hours, s.hours)
        is ScheduleSummary.Raw -> stringResource(R.string.schedule_cron, s.expr)
        is ScheduleSummary.Interval -> intervalText(s.everyMs)
        is ScheduleSummary.Once -> stringResource(
            R.string.schedule_once,
            s.atMs?.let { absoluteTime(androidx.compose.ui.platform.LocalContext.current, it) } ?: s.raw,
        )
        is ScheduleSummary.Other -> stringResource(R.string.schedule_other, s.kind)
    }
}

@Composable
private fun intervalText(everyMs: Long): String = when {
    everyMs % DAY_MS == 0L -> stringResource(R.string.schedule_every_days, (everyMs / DAY_MS).toInt())
    everyMs % HOUR_MS == 0L -> stringResource(R.string.schedule_every_hours, (everyMs / HOUR_MS).toInt())
    else -> stringResource(R.string.schedule_every_minutes, (everyMs / MINUTE_MS).toInt().coerceAtLeast(1))
}

@Composable
private fun stringArrayResourceCompat(): Array<String> =
    androidx.compose.ui.platform.LocalContext.current.resources.getStringArray(R.array.schedule_day_names)

/** 「11 時間後」「13 時間前」。 */
fun relativeTime(ms: Long, now: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(ms, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

/** 「11月23日 0:00」。 */
fun absoluteTime(context: Context, ms: Long): String =
    DateUtils.formatDateTime(context, ms, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL)

@Composable
fun durationText(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> stringResource(R.string.duration_seconds, seconds.toInt().coerceAtLeast(if (ms > 0) 1 else 0))
        seconds < 3600 -> stringResource(R.string.duration_minutes, (seconds / 60).toInt(), (seconds % 60).toInt())
        else -> stringResource(R.string.duration_hours, (seconds / 3600).toInt(), ((seconds % 3600) / 60).toInt())
    }
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L
