package jp.miyavi.android.clawdroid.feature.files

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.FileEntry
import kotlinx.coroutines.delay

/** 更新日時の出し方。1 分未満は「たった今」、1 時間未満は「n 分前」、1 日未満は「n 時間前」、それより前は日時。 */
internal sealed interface UpdatedAgo {
    data object JustNow : UpdatedAgo
    data class Minutes(val count: Long) : UpdatedAgo
    data class Hours(val count: Long) : UpdatedAgo
    /** 日時で出す。年が違えば年も付ける。 */
    data class DateTime(val withYear: Boolean) : UpdatedAgo
}

internal fun updatedAgo(updatedAtMs: Long, nowMs: Long, zone: ZoneId): UpdatedAgo {
    // 端末と Gateway の時計のずれで少し先の時刻になっても「たった今」にする
    val elapsed = (nowMs - updatedAtMs).coerceAtLeast(0)
    return when {
        elapsed < MINUTE_MS -> UpdatedAgo.JustNow
        elapsed < HOUR_MS -> UpdatedAgo.Minutes(elapsed / MINUTE_MS)
        elapsed < DAY_MS -> UpdatedAgo.Hours(elapsed / HOUR_MS)
        else -> UpdatedAgo.DateTime(
            withYear = Instant.ofEpochMilli(updatedAtMs).atZone(zone).year != Instant.ofEpochMilli(nowMs).atZone(zone).year,
        )
    }
}

/**
 * ツリーの行の 2 行目「5.6 kB · 3 分前」と、開いたファイルの更新日時。大きさと更新日時は RPC がもともと返している値で、追加の取得はしない。
 * フォルダは中の合計を数えない（重いため）ので日時だけ。書式は端末の言語に合わせ、一度だけ作って使い回す。
 */
internal class FileMetaFormat(private val context: Context, locale: Locale, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val hm = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    private val monthDayTime = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "Md$hm"), locale)
    private val yearMonthDayTime = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMd$hm"), locale)

    fun format(entry: FileEntry, nowMs: Long): String? {
        val size = entry.size?.takeUnless { entry.isDirectory }?.let { Formatter.formatShortFileSize(context, it) }
        return listOfNotNull(size, entry.updatedAt?.let { updated(it, nowMs) }).joinToString(" · ").ifEmpty { null }
    }

    fun updated(updatedAtMs: Long, nowMs: Long): String = when (val ago = updatedAgo(updatedAtMs, nowMs, zone)) {
        UpdatedAgo.JustNow -> context.getString(R.string.files_updated_just_now)
        is UpdatedAgo.Minutes -> context.getString(R.string.files_updated_minutes_ago, ago.count)
        is UpdatedAgo.Hours -> context.getString(R.string.files_updated_hours_ago, ago.count)
        is UpdatedAgo.DateTime -> (if (ago.withYear) yearMonthDayTime else monthDayTime).format(Instant.ofEpochMilli(updatedAtMs).atZone(zone))
    }
}

/** 今の時刻。「n 分前」が古くならないよう、分が変わるたびに進む。 */
@Composable
internal fun rememberMinuteClock(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
        value = System.currentTimeMillis()
    }
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS
