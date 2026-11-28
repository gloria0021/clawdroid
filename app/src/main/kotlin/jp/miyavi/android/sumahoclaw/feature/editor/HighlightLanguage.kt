package jp.miyavi.android.sumahoclaw.feature.editor

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import jp.miyavi.android.sumahoclaw.feature.files.CodeHighlighter
import jp.miyavi.android.sumahoclaw.feature.files.CodeLanguage
import jp.miyavi.android.sumahoclaw.feature.files.TokenKind
import jp.miyavi.android.sumahoclaw.feature.files.TokenSpan

/**
 * ビューアと同じ色分け（`CodeHighlighter`）を Sora の言語として差し込む（spec 008 research R2）。
 * 解析は Sora の裏の糸で全文をまとめて行い、打鍵が止まってから走らせる。補完・自動の括弧は使わない（spec の範囲外）。
 */
class HighlightLanguage(
    private val language: CodeLanguage?,
    /** これを超える行数では、最初の 1 回だけ色を付け、あとは Sora が位置をずらして使い回す（打鍵を優先。憲章 I）。 */
    private val maxLiveLines: Int = MAX_LIVE_HIGHLIGHT_LINES,
) : EmptyLanguage() {
    private val manager = Analyzer()

    override fun getAnalyzeManager(): AnalyzeManager = manager

    override fun destroy() {
        manager.destroy()
    }

    private inner class Analyzer : SimpleAnalyzeManager<Unit>() {
        @Volatile private var highlightedOnce = false

        override fun analyze(text: StringBuilder, delegate: Delegate<Unit>): Styles? {
            val lang = language ?: return Styles(spansOf(text, emptyList()))
            if (highlightedOnce) {
                // 打鍵が止まるのを待つ（止まる前に次の入力が来たら、この回は捨てられる）
                Thread.sleep(HIGHLIGHT_DEBOUNCE_MS)
                if (delegate.isCancelled) return null
                if (lineCountOf(text) > maxLiveLines) return null
            }
            val content = text.toString()
            val tokens = CodeHighlighter.spans(lang, content)
            if (delegate.isCancelled) return null
            highlightedOnce = true
            return Styles(spansOf(content, tokens))
        }
    }

    companion object {
        /** 編集中も色分けを作り直す行数の上限（quickstart G2 で見直す）。 */
        const val MAX_LIVE_HIGHLIGHT_LINES = 20_000
        private const val HIGHLIGHT_DEBOUNCE_MS = 150L
    }
}

/** 種類ごとの Sora の色の ID（足りないものは独自の ID。[editorColorScheme] で色を入れる）。 */
internal fun colorIdOf(kind: TokenKind): Int = when (kind) {
    TokenKind.KEYWORD -> EditorColorScheme.KEYWORD
    TokenKind.STRING -> COLOR_STRING
    TokenKind.LITERAL -> EditorColorScheme.LITERAL
    TokenKind.COMMENT -> EditorColorScheme.COMMENT
    TokenKind.METADATA -> EditorColorScheme.ANNOTATION
    TokenKind.PUNCTUATION -> EditorColorScheme.OPERATOR
    TokenKind.TYPE -> EditorColorScheme.IDENTIFIER_NAME
    TokenKind.FUNCTION -> EditorColorScheme.FUNCTION_NAME
}

/** 文字列の色（Sora に専用の ID が無いので足す。既存の ID と重ならない値）。 */
internal const val COLOR_STRING = 300

/** 区間の切り替わり（位置 → 色）。同じ位置の切り替わりは後のものを残す。 */
internal data class StyleChange(val line: Int, val column: Int, val colorId: Int)

/**
 * 文字の位置の区間を、行・列の切り替わりの並びにする（Sora の `MappedSpans` に詰める順）。
 * 改行は LF・CRLF・CR のどれでも行を分ける（Sora が貼り付けで CRLF の行を持つことがあるため）。
 */
internal fun styleChanges(text: CharSequence, tokens: List<TokenSpan>): List<StyleChange> {
    val lineStarts = ArrayList<Int>().apply { add(0) }
    var i = 0
    while (i < text.length) {
        when (text[i]) {
            '\r' -> { if (i + 1 < text.length && text[i + 1] == '\n') i++; lineStarts.add(i + 1) }
            '\n' -> lineStarts.add(i + 1)
        }
        i++
    }
    fun position(offset: Int): Pair<Int, Int> {
        var line = lineStarts.binarySearch(offset)
        if (line < 0) line = -line - 2
        return line to offset - lineStarts[line]
    }
    val events = ArrayList<Pair<Int, Int>>(tokens.size * 2)
    for (t in tokens.sortedBy { it.start }) {
        if (t.end <= t.start) continue
        if (events.isNotEmpty() && t.start < events.last().first) continue // 重なりは先のものを残す
        if (events.isNotEmpty() && events.last().first == t.start) events.removeAt(events.lastIndex)
        events += t.start to colorIdOf(t.kind)
        events += t.end to EditorColorScheme.TEXT_NORMAL
    }
    return events.map { (offset, color) ->
        val (line, column) = position(offset.coerceAtMost(text.length))
        StyleChange(line, column, color)
    }
}

/** 行の数（Sora と同じく LF・CRLF・CR で分ける）。 */
internal fun lineCountOf(text: CharSequence): Int {
    var n = 1
    var i = 0
    while (i < text.length) {
        when (text[i]) {
            '\r' -> { if (i + 1 < text.length && text[i + 1] == '\n') i++; n++ }
            '\n' -> n++
        }
        i++
    }
    return n
}

internal fun spansOf(text: CharSequence, tokens: List<TokenSpan>): MappedSpans {
    val total = lineCountOf(text)
    val builder = MappedSpans.Builder(total)
    for (c in styleChanges(text, tokens)) builder.addIfNeeded(c.line, c.column, c.colorId.toLong())
    builder.addNormalIfNull()
    builder.determine(total - 1)
    return builder.build()
}
