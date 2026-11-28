package jp.miyavi.android.sumahoclaw.feature.chat.message

import android.content.ClipData
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.LocalInboundImages
import jp.miyavi.android.sumahoclaw.core.data.MessagePart
import jp.miyavi.android.sumahoclaw.core.designsystem.AppBottomSheet
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.LocalScreenHazeState
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.appMarkdownPadding
import jp.miyavi.android.sumahoclaw.core.designsystem.appMarkdownTypography
import jp.miyavi.android.sumahoclaw.core.designsystem.codeTextStyle
import jp.miyavi.android.sumahoclaw.core.designsystem.sheetFieldColor
import jp.miyavi.android.sumahoclaw.feature.files.MarkdownBlock
import jp.miyavi.android.sumahoclaw.feature.files.markdownBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 会話の中で開いて見られる文字の添付。Markdown は描いて、それ以外は等幅のまま見せる。 */
internal enum class TextPreview { MARKDOWN, PLAIN }

/**
 * 押したらすぐ中身を見せる添付か（画像と同じく確認に使うもの。マスター指定 2026-11-28）。
 * 大きさが分かっていて上限を超えるものは、これまでどおり保存して外のアプリで開く。
 */
internal fun textPreviewOf(file: MessagePart.File): TextPreview? {
    if ((file.size ?: 0) > TEXT_PREVIEW_MAX_BYTES) return null
    val extension = file.name.substringAfterLast('.', "").lowercase()
    val mime = file.mimeType?.substringBefore(';')?.trim()?.lowercase()
    return when {
        extension in MARKDOWN_EXTENSIONS || mime == "text/markdown" -> TextPreview.MARKDOWN
        extension in PLAIN_EXTENSIONS || mime == "text/plain" -> TextPreview.PLAIN
        else -> null
    }
}

/** 見せる中身（Markdown は塊に分けて解析済み、文字は行のまとまりに分けたもの）。 */
private sealed interface PreviewContent {
    data object Loading : PreviewContent
    data object Failed : PreviewContent
    @Immutable data class Markdown(val text: String, val blocks: List<MarkdownBlock>) : PreviewContent
    @Immutable data class Plain(val text: String, val chunks: List<String>) : PreviewContent
}

/**
 * 文字の添付の中身（共通のボトムシート。AGENTS.md 2.5）。入力欄の「コピーしたテキスト」のタイルと同じ形で、
 * 送ったあとの .txt / .md も会話の中ですぐ確かめられるようにする。長ければ中の面だけをスクロールする。
 * 下の行は コピー ｜ ダウンロード（保存中は進みと中止、保存できたあとは 開く）。
 * コピーは [COPY_MAX_CHARS] 文字まで（それより長いとアプリ間の受け渡しの上限を超えうるので、理由を出して保存へ導く。CJ レビュー 2026-11-28）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FilePreviewSheet(
    file: MessagePart.File,
    format: TextPreview,
    messageId: String,
    meta: String,
    saved: Boolean,
    /** 保存中か。押すと中止になるので、ボタンに進みと「中止」を出す。 */
    saving: Boolean,
    /** 保存の進み（0〜1。分からなければ null）。 */
    progress: Float?,
    /** 保存を始める。保存中に押すと中止。 */
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val source = LocalInboundImages.current
    val hazeState = LocalScreenHazeState.current ?: rememberHazeState()
    val content by produceState<PreviewContent>(PreviewContent.Loading, file.url, messageId, source, format) {
        val text = source?.text(file.url, messageId, TEXT_PREVIEW_MAX_BYTES)
        // 解析は画面のスレッドの外で一度だけ（AGENTS.md 1）
        value = when {
            text == null -> PreviewContent.Failed
            format == TextPreview.MARKDOWN -> withContext(Dispatchers.Default) { PreviewContent.Markdown(text, markdownBlocks(text)) }
            else -> withContext(Dispatchers.Default) { PreviewContent.Plain(text, plainChunks(text)) }
        }
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AppBottomSheet(
        onDismissRequest = onDismiss,
        hazeState = hazeState,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Surface(
            color = sheetFieldColor(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            val inner = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s)
            when (val c = content) {
                PreviewContent.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = Dimens.imagePlaceholder), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(Spacing.xl + Spacing.s))
                }
                PreviewContent.Failed -> Box(Modifier.fillMaxWidth().heightIn(min = Dimens.imagePlaceholder).padding(inner), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.chat_file_preview_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is PreviewContent.Markdown -> {
                    val colors = markdownColor()
                    val typography = appMarkdownTypography()
                    val padding = appMarkdownPadding()
                    val components = linkAwareComponents()
                    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        items(c.blocks, key = { it.start }) { block ->
                            SelectionContainer {
                                Markdown(block.state, colors = colors, typography = typography, padding = padding, components = components)
                            }
                        }
                    }
                }
                is PreviewContent.Plain -> {
                    val style = codeTextStyle()
                    LazyColumn(contentPadding = inner) {
                        itemsIndexed(c.chunks, key = { index, _ -> index }) { _, chunk ->
                            SelectionContainer { Text(chunk, style = style, color = MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
        ) {
            val text = when (val c = content) {
                is PreviewContent.Markdown -> c.text
                is PreviewContent.Plain -> c.text
                else -> null
            }
            val tooLong = text != null && text.length > COPY_MAX_CHARS
            if (tooLong) {
                Text(
                    stringResource(R.string.chat_file_copy_too_long),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                )
            }
            TextButton(
                enabled = text != null && !tooLong,
                onClick = { text?.let { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(file.name, it))) } } },
            ) { Text(stringResource(R.string.action_copy)) }
            when {
                saved -> FilledTonalButton(onClick = onOpen) { Text(stringResource(R.string.chat_file_open)) }
                saving -> FilledTonalButton(onClick = onSave) {
                    if (progress != null) {
                        val shown by animateFloatAsState(progress, MaterialTheme.motionScheme.fastSpatialSpec(), label = "previewSaveProgress")
                        CircularWavyProgressIndicator(progress = { shown }, modifier = Modifier.size(Dimens.smallIcon))
                    } else {
                        LoadingIndicator(Modifier.size(Dimens.smallIcon))
                    }
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(R.string.files_download_cancel_cd))
                }
                else -> FilledTonalButton(onClick = onSave) { Text(stringResource(R.string.chat_file_save_cd)) }
            }
        }
    }
}

/**
 * 長い文字を一覧の項目に分ける（1 つの Text に全部を載せない）。[linesPerChunk] 行か [charsPerChunk] 文字で区切り、
 * 改行の無い長い行（1 行の JSON など）も [charsPerChunk] 文字ずつに分ける（CJ レビュー 2026-11-28）。
 */
internal fun plainChunks(text: String, linesPerChunk: Int = LINES_PER_CHUNK, charsPerChunk: Int = CHARS_PER_CHUNK): List<String> {
    val chunks = ArrayList<String>()
    val current = StringBuilder()
    var lines = 0
    fun flush() {
        if (lines > 0) chunks += current.toString()
        current.setLength(0)
        lines = 0
    }
    for (line in text.trimEnd('\n').split('\n')) {
        val pieces = if (line.length <= charsPerChunk) listOf(line) else line.chunked(charsPerChunk)
        for (piece in pieces) {
            if (lines >= linesPerChunk || (lines > 0 && current.length + piece.length + 1 > charsPerChunk)) flush()
            if (lines > 0) current.append('\n')
            current.append(piece)
            lines++
        }
    }
    flush()
    return chunks
}

/** 会話の中で開いて見る文字の添付の上限（これより大きければ保存して外のアプリで開く）。 */
internal const val TEXT_PREVIEW_MAX_BYTES = 2L * 1024 * 1024
private const val LINES_PER_CHUNK = 40
private const val CHARS_PER_CHUNK = 4_000
/** クリップボードに写せる長さ（UTF-16 で約 200 KB。アプリ間の受け渡しの上限 1 MB を共有するため余裕を取る）。 */
internal const val COPY_MAX_CHARS = 100_000
private val MARKDOWN_EXTENSIONS = setOf("md", "markdown")
private val PLAIN_EXTENSIONS = setOf("txt", "text", "log", "csv", "tsv", "json", "jsonl", "yaml", "yml", "toml", "xml", "ini", "cfg", "conf")
