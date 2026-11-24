package jp.miyavi.android.clawdroid.feature.files

/**
 * 論理行の先頭の位置（spec 005 data-model の LineIndex）。行の区切りは LF・CRLF・CR。
 * 行番号の表示・行へ移る・今の行の強調に使う。
 */
class LineIndex private constructor(private val starts: IntArray, private val length: Int) {
    val lineCount: Int get() = starts.size

    /** 行番号の列の桁数。 */
    val gutterDigits: Int get() = lineCount.toString().length

    fun start(line: Int): Int = starts[line.coerceIn(0, starts.lastIndex)]

    /** 行の終わり（区切りの文字は含めない）。 */
    fun end(line: Int, text: CharSequence): Int {
        if (line >= starts.lastIndex) return length
        var end = starts[line + 1]
        if (end > 0 && text[end - 1] == '\n') end--
        if (end > starts[line] && text[end - 1] == '\r') end--
        return end
    }

    /** [offset] を含む行（0 始まり）。 */
    fun lineOf(offset: Int): Int {
        var lo = 0
        var hi = starts.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    companion object {
        fun of(text: CharSequence): LineIndex {
            val starts = ArrayList<Int>().apply { add(0) }
            var i = 0
            while (i < text.length) {
                when (text[i]) {
                    '\n' -> starts += i + 1
                    '\r' -> if (i + 1 < text.length && text[i + 1] == '\n') { starts += i + 2; i++ } else starts += i + 1
                }
                i++
            }
            return LineIndex(starts.toIntArray(), text.length)
        }
    }
}

/** 表示の幅で数えた桁数（全角は 2 桁）。折り返さない表示の横幅に使う。 */
fun displayColumns(text: CharSequence, start: Int, end: Int): Int {
    var columns = 0
    for (i in start until end) {
        val c = text[i]
        columns += when {
            c == '\t' -> TAB_COLUMNS
            c.code >= WIDE_FROM -> 2
            else -> 1
        }
    }
    return columns
}

private const val TAB_COLUMNS = 4
private const val WIDE_FROM = 0x1100
