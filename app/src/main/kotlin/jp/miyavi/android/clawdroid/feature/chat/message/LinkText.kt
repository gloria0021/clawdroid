package jp.miyavi.android.clawdroid.feature.chat.message

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import jp.miyavi.android.clawdroid.ClawdroidApp
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.LinkPreview
import jp.miyavi.android.clawdroid.core.data.findWebLinks
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** 見出しにしたリンクの、文字の中での位置（下の点線を引く範囲）。 */
private data class LinkLabel(val start: Int, val end: Int)

/**
 * 本文の Web のリンクを「ファビコン ＋ ページのタイトル ＋ 下の点線」で出す（ChatGPT のリンクの出し方に倣う。マスター指定 2026-11-26）。
 * タイトルは送ったあとに端末から読む。読めるまでと読めないときは、ドメインを出す。押すとアプリ内のブラウザで開く。
 */
@Composable
internal fun LinkText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val links = remember(text) { findWebLinks(text).map { text.substring(it) }.distinct() }
    if (links.isEmpty()) {
        Text(text, style = style, modifier = modifier)
        return
    }
    val loader = (LocalContext.current.applicationContext as ClawdroidApp).container.linkPreviews
    val previews by produceState(links.associateWith { loader.cached(it) }, links) {
        value = coroutineScope { links.map { url -> async { url to loader.load(url) } }.awaitAll() }.toMap()
    }
    val (annotated, labels) = remember(text, previews) { linkAnnotated(text, previews) }
    val inline = remember(previews) {
        links.mapIndexed { index, url ->
            iconId(index) to InlineTextContent(Placeholder(ICON_EM.em, ICON_EM.em, PlaceholderVerticalAlign.TextCenter)) {
                Favicon(previews[url])
            }
        }.toMap()
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val color = LocalContentColor.current
    Text(
        annotated,
        style = style,
        inlineContent = inline,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val result = layout ?: return@drawBehind
            val dash = Spacing.xs.toPx() / 2
            val effect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
            val stroke = Dimens.hairline.toPx()
            labels.forEach { label ->
                val first = result.getLineForOffset(label.start)
                val last = result.getLineForOffset((label.end - 1).coerceAtLeast(label.start))
                for (line in first..last) {
                    val from = if (line == first) result.getHorizontalPosition(label.start, true) else result.getLineLeft(line)
                    val to = if (line == last) result.getHorizontalPosition(label.end, true) else result.getLineRight(line)
                    val y = result.getLineBaseline(line) + stroke * 3
                    drawLine(color, Offset(minOf(from, to), y), Offset(maxOf(from, to), y), stroke, pathEffect = effect)
                }
            }
        },
    )
}

@Composable
private fun Favicon(preview: LinkPreview?) {
    val icon = preview?.icon
    var failed by remember(icon) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().clip(androidx.compose.material3.MaterialTheme.shapes.extraSmall)) {
        if (icon == null || failed) {
            Icon(painterResource(R.drawable.ic_huge_globe), contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** リンクを「アイコン ＋ 見出し」に置き換えた文字と、見出しの位置。 */
private fun linkAnnotated(text: String, previews: Map<String, LinkPreview?>): Pair<AnnotatedString, List<LinkLabel>> {
    val labels = mutableListOf<LinkLabel>()
    val order = mutableListOf<String>()
    val annotated = buildAnnotatedString {
        var cursor = 0
        findWebLinks(text).forEach { range ->
            append(text.substring(cursor, range.first))
            val url = text.substring(range)
            val index = order.indexOf(url).takeIf { it >= 0 } ?: order.size.also { order += url }
            withLink(LinkAnnotation.Url(url)) {
                appendInlineContent(iconId(index), url)
                append(" ")
                val start = length
                append(previews[url]?.title ?: domainOf(url))
                labels += LinkLabel(start, length)
            }
            cursor = range.last + 1
        }
        append(text.substring(cursor))
    }
    return annotated to labels
}

private fun domainOf(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.")

private fun iconId(index: Int) = "linkIcon$index"

/** ファビコンの大きさ（文字の高さに対する割合）。 */
private const val ICON_EM = 1.1f
