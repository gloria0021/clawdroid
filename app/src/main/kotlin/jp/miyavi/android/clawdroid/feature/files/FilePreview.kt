package jp.miyavi.android.clawdroid.feature.files

import androidx.compose.runtime.Immutable
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdown
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

/** 描いて見せられる文字のファイル（ふだんはプレビュー、コードのボタンで元の文字）。 */
enum class PreviewFormat { MARKDOWN, HTML }

internal fun previewFormatOf(name: String): PreviewFormat? = when (name.substringAfterLast('.', "").lowercase()) {
    "md", "markdown" -> PreviewFormat.MARKDOWN
    "html", "htm" -> PreviewFormat.HTML
    else -> null
}

/**
 * Markdown の最上位の塊（段落・見出し・リスト・コードなど）。一覧の 1 項目として描き、検索の一致へはこの単位で送る。
 * [start] は元の文字の中の位置（項目のキーにも使う）。
 */
@Immutable
data class MarkdownBlock(val start: Int, val end: Int, val state: State)

/** Markdown を最上位の塊に分け、塊ごとに解析しておく（ViewModel で一度だけ）。 */
internal fun markdownBlocks(text: String): List<MarkdownBlock> {
    val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(text)
    return root.children
        .filter { it.type != MarkdownTokenTypes.EOL && it.type != MarkdownTokenTypes.WHITE_SPACE }
        .map { node -> MarkdownBlock(node.startOffset, node.endOffset, parseMarkdown(text.substring(node.startOffset, node.endOffset))) }
}

/** [offset] を含む塊（間の空行なら直前の塊）の位置。 */
internal fun blockIndexOf(blocks: List<MarkdownBlock>, offset: Int): Int {
    if (blocks.isEmpty()) return -1
    var low = 0
    var high = blocks.lastIndex
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (blocks[mid].start <= offset) low = mid else high = mid - 1
    }
    return low
}

/** ファイル内の検索。[matches] は [matchedQuery] で探した結果（入力中の [query] とずれることがある）。 */
@Immutable
data class FileSearch(
    val query: String = "",
    val matchedQuery: String = "",
    val matches: List<Int> = emptyList(),
    val current: Int = 0,
) {
    val currentOffset: Int? get() = matches.getOrNull(current)
}

/** 大文字と小文字を区別せず、重ならない一致の始まりを返す。 */
internal fun findMatches(text: String, query: String, limit: Int = MAX_MATCHES): List<Int> {
    if (query.isEmpty()) return emptyList()
    val found = ArrayList<Int>()
    var index = text.indexOf(query, 0, ignoreCase = true)
    while (index >= 0 && found.size < limit) {
        found += index
        index = text.indexOf(query, index + query.length, ignoreCase = true)
    }
    return found
}

/** 一致が多すぎると色付けが重くなるので、ここで打ち切る。 */
internal const val MAX_MATCHES = 5_000
