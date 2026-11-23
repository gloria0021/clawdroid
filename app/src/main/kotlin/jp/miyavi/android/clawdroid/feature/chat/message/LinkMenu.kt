package jp.miyavi.android.clawdroid.feature.chat.message

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.DpOffset
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownText
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.designsystem.GlassDropdownMenu
import kotlinx.coroutines.launch
import org.intellij.markdown.ast.ASTNode

/** リンクの長押しメニューの操作（画面ごとに Snackbar を持つので、画面で用意して渡す）。 */
@Immutable
class LinkActions(
    val copy: (String) -> Unit,
    val download: (String) -> Unit,
    val openInBrowser: (String) -> Unit,
)

val LocalLinkActions = staticCompositionLocalOf<LinkActions?> { null }

/**
 * リンクの長押しメニューの操作を作る。コピーはクリップボード、ダウンロードは端末の DownloadManager（Download フォルダに保存、
 * 完了は通知で知らせる）、ブラウザで開くは外部のブラウザ（ふつうに押したときの Custom Tabs とは別）。
 */
@Composable
fun rememberLinkActions(snackbar: SnackbarHostState): LinkActions {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.link_copied)
    val started = stringResource(R.string.link_download_started)
    val failed = stringResource(R.string.link_download_failed)
    val noBrowser = stringResource(R.string.link_no_browser)
    return remember(context, clipboard, snackbar) {
        LinkActions(
            copy = { url ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, url)))
                    snackbar.showSnackbar(copied)
                }
            },
            download = { url ->
                val ok = enqueueDownload(context, url)
                scope.launch { snackbar.showSnackbar(if (ok) started else failed) }
            },
            openInBrowser = { url ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    scope.launch { snackbar.showSnackbar(noBrowser) }
                }
            },
        )
    }
}

private fun enqueueDownload(context: Context, url: String): Boolean = runCatching {
    val uri = Uri.parse(url)
    if (uri.scheme != "http" && uri.scheme != "https") return false
    val name = URLUtil.guessFileName(url, null, null)
    val request = DownloadManager.Request(uri)
        .setTitle(name)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
    context.getSystemService(DownloadManager::class.java).enqueue(request)
    true
}.getOrDefault(false)

/** 段落を、リンクを長押しできるものに差し替えた部品（会話とファイルのプレビューで使う）。 */
fun linkAwareComponents(): MarkdownComponents = markdownComponents(
    paragraph = { LinkAwareText(it, it.node, it.typography.paragraph) },
)

/**
 * Markdown の 1 段落を、ライブラリと同じ組み立てで描き、リンクの上の操作だけを受け取る。
 * - 押す: これまでどおりリンクを開く
 * - 長押し: リンクのメニュー（コピー・ダウンロード・ブラウザで開く）
 * リンク以外の所は触らない（文字の選択・スクロールはそのまま）。[decorate] で検索の色付けなどを足せる。
 */
@Composable
fun LinkAwareText(
    model: MarkdownComponentModel,
    node: ASTNode,
    style: TextStyle,
    decorate: (AnnotatedString) -> AnnotatedString = { it },
) {
    val settings = annotatorSettings()
    val text = remember(model.content, node, style, settings, decorate) {
        decorate(
            buildAnnotatedString {
                pushStyle(style.toSpanStyle())
                buildMarkdownAnnotatedString(model.content, node, settings)
                pop()
            },
        )
    }
    val actions = LocalLinkActions.current
    if (actions == null) {
        MarkdownText(text, node, Modifier, style)
        return
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var menu by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    val uriHandler by rememberUpdatedState(LocalUriHandler.current)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    Box {
        MarkdownText(
            content = text,
            node = node,
            modifier = Modifier.pointerInput(text) {
                awaitEachGesture {
                    // 先に見て、リンクの上だけ自分で受け取る（選択やリンクの既定の処理より前）
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val url = layout?.linkAt(text, down.position) ?: return@awaitEachGesture
                    down.consume()
                    var result = PressResult.CANCELLED
                    val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                result = PressResult.TAP
                                break
                            }
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                            change.consume()
                        }
                    }
                    if (finished == null) {
                        // 長押し: メニューを出し、指を離すまでの動きは自分で受け取る
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = url to down.position
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                        }
                    } else if (result == PressResult.TAP) {
                        uriHandler.openUri(url)
                    }
                }
            },
            style = style,
            onTextLayout = { result, _ -> layout = result },
        )
        val opened = menu
        GlassDropdownMenu(
            expanded = opened != null,
            onDismissRequest = { menu = null },
            offset = opened?.second?.let { with(density) { DpOffset(it.x.toDp(), (it.y - (layout?.size?.height ?: 0)).toDp()) } } ?: DpOffset.Zero,
        ) {
            val url = opened?.first.orEmpty()
            LinkMenuItem(R.drawable.ic_huge_copy, R.string.link_copy) { menu = null; actions.copy(url) }
            LinkMenuItem(R.drawable.ic_huge_download, R.string.link_download) { menu = null; actions.download(url) }
            LinkMenuItem(R.drawable.ic_huge_globe, R.string.link_open_browser) { menu = null; actions.openInBrowser(url) }
        }
    }
}

private enum class PressResult { TAP, CANCELLED }

@Composable
private fun LinkMenuItem(icon: Int, label: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
    )
}

/** 指の位置にある URL のリンク（行の外・文字の外なら null）。 */
private fun TextLayoutResult.linkAt(text: AnnotatedString, position: Offset): String? {
    val line = getLineForVerticalPosition(position.y)
    if (position.x < getLineLeft(line) || position.x > getLineRight(line)) return null
    val offset = getOffsetForPosition(position)
    return text.getLinkAnnotations(offset, offset + 1).firstNotNullOfOrNull { (it.item as? LinkAnnotation.Url)?.url }
}
