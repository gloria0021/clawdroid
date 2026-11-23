package jp.miyavi.android.clawdroid.feature.files

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.PreviewKind
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.bodyContainerColor
import jp.miyavi.android.clawdroid.core.designsystem.codeTextStyle
import jp.miyavi.android.clawdroid.feature.chat.message.rememberBase64Bitmap
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * 開いたファイル（spec 002 FR-005〜008）。Markdown / HTML はふだんプレビュー、コードのボタンで元の文字。
 * 文字は等幅・色分け（読む）と BasicTextField（編集。編集中は色分けしない）、
 * 画像はプレビューを押すと全画面、表示できないファイルは種類・大きさとダウンロード。
 */
@Composable
internal fun FileViewer(
    state: OpenedState.Ready,
    contentPadding: PaddingValues,
    htmlFind: HtmlFindState,
    onEdit: (String, Int) -> Unit,
    onOpenImage: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val file = state.file
    when {
        state.editing -> Editor(state, contentPadding, onEdit, modifier)
        file.kind == PreviewKind.TEXT && file.text != null -> when {
            !state.showCode && state.preview == PreviewFormat.MARKDOWN ->
                MarkdownPreview(state.blocks, state.search, contentPadding, modifier)
            !state.showCode && state.preview == PreviewFormat.HTML ->
                HtmlPreview(file.text, state.search?.query.orEmpty(), htmlFind, contentPadding, modifier)
            else -> CodeView(file.text, state.spans, state.search, contentPadding, modifier)
        }
        file.kind == PreviewKind.IMAGE -> {
            val bitmap = rememberBase64Bitmap(file.imageBase64)
            Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    Image(
                        bitmap,
                        contentDescription = file.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(Spacing.l).clip(MaterialTheme.shapes.large).clickable(onClick = onOpenImage),
                    )
                } else {
                    Unsupported(file.mimeType, file.size, onDownload)
                }
            }
        }
        else -> Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Unsupported(file.mimeType, file.size, onDownload)
        }
    }
}

/** 元の文字（等幅・色分け）。検索の一致に色を付け、いまの一致まで送る。 */
@Composable
private fun CodeView(text: String, spans: List<TokenSpan>, search: FileSearch?, contentPadding: PaddingValues, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val base = remember(text, spans, colors) { highlighted(text, spans, colors) }
    val matches = search?.matches.orEmpty()
    val length = search?.matchedQuery?.length ?: 0
    val current = search?.current ?: -1
    val shown = remember(base, matches, length, current, colors) { base.withMatches(matches, length, current, colors) }

    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val positions = remember { CodePositions() }
    // 利用者が送った一致へだけ動かす（読んでいる最中のレイアウトの変化では動かさない。AGENTS.md 2.8）
    val target = search?.currentOffset
    LaunchedEffect(target) {
        val offset = target ?: return@LaunchedEffect
        val textLayout = snapshotFlow { layout }.filterNotNull().first()
        val content = positions.content ?: return@LaunchedEffect
        val textCoordinates = positions.text ?: return@LaunchedEffect
        val textTop = content.localPositionOf(textCoordinates, Offset.Zero).y
        val lineTop = textLayout.getLineTop(textLayout.getLineForOffset(offset.coerceAtMost(text.length)))
        val y = textTop + lineTop - scroll.viewportSize / MATCH_VIEWPORT_DIVISOR
        scroll.animateScrollTo(y.toInt().coerceIn(0, scroll.maxValue))
    }
    Column(modifier.fillMaxSize().verticalScroll(scroll).onPlaced { positions.content = it }.padding(contentPadding)) {
        CodeSurface {
            SelectionContainer {
                Text(
                    shown,
                    style = codeTextStyle(),
                    onTextLayout = { layout = it },
                    modifier = Modifier.onPlaced { positions.text = it },
                )
            }
        }
    }
}

/** 一致の位置を測るための置き場所（読み取りで再構成を起こさない）。 */
private class CodePositions {
    var content: LayoutCoordinates? = null
    var text: LayoutCoordinates? = null
}

/** 検索で一致した語に色を付ける（AGENTS.md 2.2: 一致は tertiaryContainer、いまの一致は tertiary）。 */
internal fun AnnotatedString.withMatches(matches: List<Int>, length: Int, current: Int, colors: ColorScheme): AnnotatedString {
    if (matches.isEmpty() || length == 0) return this
    val match = SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer)
    val focused = SpanStyle(background = colors.tertiary, color = colors.onTertiary)
    return AnnotatedString.Builder(this).apply {
        matches.forEachIndexed { index, start ->
            val end = (start + length).coerceAtMost(this@withMatches.length)
            if (start < end) addStyle(if (index == current) focused else match, start, end)
        }
    }.toAnnotatedString()
}

/** 本文の下地の角丸の面（AGENTS.md 2.4: 内側は左右 12dp・上下 8dp）。 */
@Composable
private fun CodeSurface(content: @Composable () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
            .widthIn(max = Dimens.maxReadingWidth)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(bodyContainerColor())
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
    ) { content() }
}

@Composable
private fun Editor(state: OpenedState.Ready, contentPadding: PaddingValues, onEdit: (String, Int) -> Unit, modifier: Modifier) {
    val initial = state.editText.orEmpty()
    // 入力中の文字とカーソルは手元で持ち、ViewModel に知らせる（ViewModel の値で毎回作り直さない）
    var value by remember(state.file.path) {
        mutableStateOf(TextFieldValue(initial, TextRange(state.cursor.coerceIn(0, initial.length))))
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).imePadding()) {
        CodeSurface {
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    onEdit(it.text, it.selection.start)
                },
                textStyle = codeTextStyle().copy(color = LocalContentColor.current),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            )
        }
    }
}

@Composable
private fun Unsupported(mimeType: String?, size: Long?, onDownload: () -> Unit) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(Spacing.l)) {
        Icon(painterResource(R.drawable.ic_huge_file), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Dimens.emptyStateIcon))
        Text(stringResource(R.string.files_unsupported), style = MaterialTheme.typography.bodyLarge)
        val detail = listOfNotNull(mimeType, size?.let { Formatter.formatShortFileSize(context, it) }).joinToString("  ")
        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onDownload) { Text(stringResource(R.string.files_download_cd)) }
    }
}

/** いまの一致を、表示の上から 1/3 の高さに置く。 */
private const val MATCH_VIEWPORT_DIVISOR = 3
