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
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.flow.drop

/**
 * 開いたファイル（spec 002 FR-005〜008）。Markdown / HTML はふだんプレビュー、コードのボタンで元の文字。
 * 文字は行番号付きの等幅・色分け（読む・編集とも。spec 005 US5）、
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
    /** 編集中の入力欄（画面の上で持つ。無ければここで作る）。 */
    editor: TextFieldState? = null,
    wrap: Boolean = true,
) {
    val file = state.file
    when {
        state.editing -> CodeEditorField(editor ?: rememberEditorState(state, onEdit), file.name, contentPadding, modifier.imePadding())
        file.kind == PreviewKind.TEXT && file.text != null -> when {
            !state.showCode && state.preview == PreviewFormat.MARKDOWN ->
                MarkdownPreview(state.blocks, state.search, contentPadding, modifier)
            !state.showCode && state.preview == PreviewFormat.HTML ->
                HtmlPreview(state.htmlInline ?: file.text, state.search?.query.orEmpty(), htmlFind, contentPadding, modifier)
            else -> CodeReader(file.text, state.spans, state.search, wrap, contentPadding, modifier)
        }
        file.kind == PreviewKind.IMAGE -> {
            val bitmap = file.image ?: rememberBase64Bitmap(file.imageBase64)
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

/**
 * 編集の入力欄の状態（開いたファイルごと）。入力は ViewModel に知らせ、下書きに残す。
 * ViewModel の値で毎回作り直さない（入力中のカーソルを失わない）。
 */
@Composable
internal fun rememberEditorState(state: OpenedState.Ready, onEdit: (String, Int) -> Unit): TextFieldState {
    val initial = state.editText.orEmpty()
    val field = remember(state.file.path) { TextFieldState(initial, TextRange(state.cursor.coerceIn(0, initial.length))) }
    val latest by rememberUpdatedState(onEdit)
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() to field.selection.start }.drop(1).collect { (text, cursor) -> latest(text, cursor) }
    }
    return field
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

