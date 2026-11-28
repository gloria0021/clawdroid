package jp.miyavi.android.sumahoclaw.core.settings

/**
 * 考え中のひとりごと（設定 → エージェント → 考え中のひとりごと）。編集は 1 行 = 1 つのひとりごと。
 * 末尾の「…」はアプリが回る点として付けるので、持たない（打っても外す）。
 */
object ThinkingPhrases {
    /** エディタの文字を一覧にする。前後の空白と末尾の「…」「...」を外し、空の行は捨てる。 */
    fun parse(text: CharSequence): List<String> =
        text.lines().map(::normalize).filter(String::isNotEmpty)

    /** 一覧をエディタの文字にする（1 行に 1 つ）。 */
    fun format(phrases: List<String>): String = phrases.map(::normalize).filter(String::isNotEmpty).joinToString("\n")

    /** 1 つのひとりごとを、保存する形にそろえる。 */
    fun normalize(phrase: String): String {
        var s = phrase.trim()
        while (true) {
            val next = s.removeSuffix("…").removeSuffix("...").trimEnd()
            if (next == s) return s
            s = next
        }
    }
}
