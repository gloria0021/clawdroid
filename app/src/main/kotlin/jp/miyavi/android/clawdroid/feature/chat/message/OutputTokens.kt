package jp.miyavi.android.clawdroid.feature.chat.message

import java.util.Locale

/** CJK-aware text estimate, like Gateway context estimation. No model usage is inferred from elapsed time. */
internal fun estimateOutputTokens(text: String): Int {
    var weighted = 0L
    var index = 0
    while (index < text.length) {
        val cp = text.codePointAt(index)
        val units = Character.charCount(cp)
        weighted += when (cp) {
            in 0x2E80..0x9FFF, in 0xA000..0xA4FF, in 0xAC00..0xD7AF,
            in 0xF900..0xFAFF, in 0x20000..0x2FA1F -> 4
            else -> units
        }
        index += units
    }
    return ((weighted + 3) / 4).toInt()
}

internal fun formatOutputTokens(count: Int): String = formatReportedOutputTokens(count.toLong())

internal fun formatReportedOutputTokens(count: Long): String =
    if (count < 1_000) count.toString() else String.format(Locale.ROOT, "%.1fk", count / 1_000.0).replace(".0k", "k")
