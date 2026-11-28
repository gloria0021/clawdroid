package jp.miyavi.android.sumahoclaw.core.text

import java.text.BreakIterator

/** 情報の帯の数（spec 008 FR-030・031）。 */
data class TextStats(
    /** 見た目の文字数（改行は数えない）。 */
    val chars: Int,
    /** 論理行の数（最後が改行で終わるとき、その後ろの空の行は数えない）。 */
    val lines: Int,
)

/** 文字数の数え方（書記素 = 見た目の 1 文字。絵文字・結合文字も 1）。本文は LF にそろえたものを渡す。 */
object StatusCounter {

    fun stats(text: CharSequence): TextStats = TextStats(chars = graphemes(text, 0, text.length), lines = lines(text))

    /** [start] から [end] までの書記素の数。改行は数えない。 */
    fun graphemes(text: CharSequence, start: Int, end: Int): Int {
        if (start >= end) return 0
        val s = text.subSequence(start, end).toString()
        // 速い道: 合成・サロゲート・異体字セレクタが無ければ、改行以外の文字数がそのまま書記素の数
        if (s.none(::joinsCluster)) {
            return s.count { it != '\n' }
        }
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(s)
        var count = 0
        var from = iterator.first()
        var to = iterator.next()
        while (to != BreakIterator.DONE) {
            if (!(to - from == 1 && s[from] == '\n')) count++
            from = to
            to = iterator.next()
        }
        return count
    }

    /** 前後の文字と 1 つの書記素になりうる文字（サロゲート・結合文字・異体字セレクタ・ZWJ・ハングルの字母）。 */
    private fun joinsCluster(c: Char): Boolean {
        if (c.isSurrogate() || c == '\u200D' || c in '\uFE00'..'\uFE0F' || c in '\u1160'..'\u11FF') return true
        val type = Character.getType(c).toByte()
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK
    }

    fun lines(text: CharSequence): Int {
        if (text.isEmpty()) return 1
        var n = 1
        for (i in text.indices) if (text[i] == '\n') n++
        return if (text[text.length - 1] == '\n') n - 1 else n
    }
}
