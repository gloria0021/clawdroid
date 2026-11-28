package jp.miyavi.android.sumahoclaw.feature.files

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.max
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.bodyContainerColor
import jp.miyavi.android.sumahoclaw.core.designsystem.codeTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * 読むときのコード（spec 005 US5）。行ごとの LazyColumn で、見えている行だけを組み立てる（research R6）。
 * 左に行番号の列（等幅・onSurfaceVariant・右寄せ）。折り返さないときは全体を 1 つの横スクロールに載せ、行番号は左に留める。
 */
@Composable
internal fun CodeReader(
    text: String,
    spans: List<TokenSpan>,
    search: FileSearch?,
    wrap: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** 読んでいる行を書き戻す所（エディターをその行から開く。spec 008 FR-003）。 */
    readingLine: androidx.compose.runtime.MutableIntState? = null,
) {
    val colors = MaterialTheme.colorScheme
    val style = codeTextStyle()
    val index = remember(text) { LineIndex.of(text) }
    val base = remember(text, spans, colors) { highlighted(text, spans, colors) }
    val matches = search?.matches.orEmpty()
    val matchLength = search?.matchedQuery?.length ?: 0
    val current = search?.current ?: -1
    val shown = remember(base, matches, matchLength, current, colors) { base.withMatches(matches, matchLength, current, colors) }
    val lines = remember(index) { (0 until index.lineCount).toList() }
    val listState = rememberLazyListState()
    // 利用者が送った一致・指定した行へだけ動かす（読んでいる最中には動かさない。AGENTS.md 2.8）
    val matchOffset = search?.currentOffset
    LaunchedEffect(matchOffset) { matchOffset?.let { listState.animateScrollToItem((index.lineOf(it) - CONTEXT_LINES).coerceAtLeast(0)) } }
    if (readingLine != null) {
        LaunchedEffect(listState, readingLine) { snapshotFlow { listState.firstVisibleItemIndex }.collect { readingLine.intValue = it } }
    }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val charWidth = remember(style, density) { with(density) { measurer.measure("0", style).size.width.toDp() } }
    val gutterWidth = charWidth * (index.gutterDigits + 1) + Spacing.s
    val maxColumns = remember(text, index) { (0 until index.lineCount).maxOfOrNull { displayColumns(text, index.start(it), index.end(it, text)) } ?: 0 }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val horizontal = rememberScrollState()
        val contentWidth = if (wrap) maxWidth else max(maxWidth, gutterWidth + charWidth * (maxColumns + 1) + Spacing.l * 2)
        val lineBackground = bodyContainerColor()
        SelectionContainer {
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (wrap) Modifier else Modifier.horizontalScroll(horizontal))
                    .width(contentWidth),
            ) {
                items(lines, key = { it }, contentType = { "line" }) { line ->
                    val lineText = remember(shown, line) { shown.subSequence(index.start(line), index.end(line, text)) }
                    Row(Modifier.fillMaxWidth()) {
                        // 行番号は横にスクロールしても左に留める（描画の位置だけを動かす）
                        Text(
                            (line + 1).toString(),
                            style = style,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            softWrap = false,
                            // 横にスクロールした本文の上に重ねて描く（本文が行番号に食い込まない）
                            modifier = Modifier
                                .zIndex(1f)
                                .width(gutterWidth)
                                .offset { IntOffset(if (wrap) 0 else horizontal.value, 0) }
                                .background(colors.surface)
                                .padding(end = Spacing.s),
                        )
                        Text(
                            lineText,
                            style = style,
                            softWrap = wrap,
                            modifier = Modifier.weight(1f).padding(end = Spacing.l),
                        )
                    }
                }
            }
        }
    }
}

/** インデントに使う空白（ファイルの先頭の空白の最頻値。無ければ 4）。 */
internal fun indentUnit(text: CharSequence): String {
    if (text.lines().any { it.startsWith("\t") }) return "\t"
    val widths = text.lines().map { line -> line.takeWhile { it == ' ' }.length }.filter { it in 1..8 }
    val unit = widths.groupingBy { it }.eachCount().filterKeys { it == 2 || it == 4 }.maxByOrNull { it.value }?.key
    return " ".repeat(unit ?: 4)
}

/** 一致の上に残す行数。 */
private const val CONTEXT_LINES = 3
