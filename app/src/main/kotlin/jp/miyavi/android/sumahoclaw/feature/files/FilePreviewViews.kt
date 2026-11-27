package jp.miyavi.android.sumahoclaw.feature.files

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.appMarkdownPadding
import jp.miyavi.android.sumahoclaw.core.designsystem.appMarkdownTypography
import jp.miyavi.android.sumahoclaw.feature.chat.message.rememberChatLinkOpener
import jp.miyavi.android.sumahoclaw.feature.chat.message.webUri
import kotlin.math.roundToInt
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType

/**
 * Markdown のプレビュー。最上位の塊を 1 項目ずつ並べ（会話と同じ見た目）、検索の一致は段落と見出しの中に色を付け、
 * いまの一致を含む塊まで送る。
 */
@Composable
internal fun MarkdownPreview(blocks: List<MarkdownBlock>, search: FileSearch?, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val target = search?.currentOffset?.let { blockIndexOf(blocks, it) }
    // 利用者が送った一致へだけ動かす（AGENTS.md 2.8）
    LaunchedEffect(target, search?.current) {
        if (target != null && target >= 0) listState.animateScrollToItem(target)
    }
    val components = rememberSearchComponents(search?.matchedQuery.orEmpty().takeIf { search?.matches?.isNotEmpty() == true }.orEmpty())
    val colors = markdownColor()
    val typography = appMarkdownTypography()
    val padding = appMarkdownPadding()
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = modifier.fillMaxSize(),
    ) {
        items(blocks, key = { it.start }) { block ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().padding(horizontal = Spacing.l)) {
                    SelectionContainer {
                        Markdown(block.state, colors = colors, typography = typography, padding = padding, components = components)
                    }
                }
            }
        }
    }
}

/** 検索中だけ、段落と見出しの部品を一致に色を付けるものに差し替える。 */
@Composable
private fun rememberSearchComponents(query: String): MarkdownComponents {
    val scheme = MaterialTheme.colorScheme
    val highlight = remember(scheme) { SpanStyle(background = scheme.tertiaryContainer, color = scheme.onTertiaryContainer) }
    return remember(query, highlight) {
        if (query.isEmpty()) {
            jp.miyavi.android.sumahoclaw.feature.chat.message.linkAwareComponents()
        } else {
            markdownComponents(
                paragraph = { HighlightedText(it, it.node, it.typography.paragraph, query, highlight) },
                heading1 = { HighlightedText(it, it.headingContent(), it.typography.h1, query, highlight) },
                heading2 = { HighlightedText(it, it.headingContent(), it.typography.h2, query, highlight) },
                heading3 = { HighlightedText(it, it.headingContent(), it.typography.h3, query, highlight) },
                heading4 = { HighlightedText(it, it.headingContent(), it.typography.h4, query, highlight) },
                heading5 = { HighlightedText(it, it.headingContent(), it.typography.h5, query, highlight) },
                heading6 = { HighlightedText(it, it.headingContent(), it.typography.h6, query, highlight) },
                table = { jp.miyavi.android.sumahoclaw.feature.chat.message.WrappingMarkdownTable(it) },
            )
        }
    }
}

private fun MarkdownComponentModel.headingContent(): ASTNode = node.findChildOfType(MarkdownTokenTypes.ATX_CONTENT) ?: node

/** ライブラリの段落と同じ組み立てに、一致の色を足したもの。 */
@Composable
private fun HighlightedText(model: MarkdownComponentModel, node: ASTNode, style: TextStyle, query: String, highlight: SpanStyle) {
    val decorate = remember(query, highlight) {
        { built: androidx.compose.ui.text.AnnotatedString ->
            val matches = findMatches(built.text, query)
            if (matches.isEmpty()) built
            else androidx.compose.ui.text.AnnotatedString.Builder(built).apply {
                matches.forEach { addStyle(highlight, it, it + query.length) }
            }.toAnnotatedString()
        }
    }
    // リンクの長押しメニューも、検索中にそのまま使えるように
    jp.miyavi.android.sumahoclaw.feature.chat.message.LinkAwareText(model, node, style, decorate)
}

/** HTML のプレビューの検索の結果と、前後への送り（WebView の検索をそのまま使う）。 */
@Stable
internal class HtmlFindState {
    internal var webView: WebView? = null
    /** いまの一致（1 から。無ければ 0）。 */
    var active by mutableIntStateOf(0)
        internal set
    var count by mutableIntStateOf(0)
        internal set

    fun next(forward: Boolean) {
        webView?.findNext(forward)
    }
}

/**
 * HTML のプレビュー（Web 版と同じく、スクリプトは動かす）。端末のファイル・アプリの中身には触れさせず、
 * リンクはブラウザで開く。上下は浮く面と重ならないように空ける。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun HtmlPreview(html: String, query: String, find: HtmlFindState, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val openLink by rememberUpdatedState(rememberChatLinkOpener(onFailure = {}))
    val textZoom = (LocalDensity.current.fontScale * FULL_TEXT_ZOOM).roundToInt()
    key(html) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    settings.textZoom = textZoom
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url
                            if (url.scheme == "about" || url.scheme == "data") return false // ページ内の移動
                            if (request.isForMainFrame && request.hasGesture()) webUri(url.toString())?.let(openLink)
                            return true
                        }
                    }
                    setFindListener { active, count, _ ->
                        find.count = count
                        find.active = if (count == 0) 0 else active + 1
                    }
                    find.webView = this
                    val offlinePolicy = """<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data: blob:; style-src 'unsafe-inline' data:; script-src 'unsafe-inline' data:; font-src data:; form-action 'none'; base-uri 'none'">"""
                    loadDataWithBaseURL(null, offlinePolicy + html, "text/html", "utf-8", null)
                }
            },
            update = { it.settings.textZoom = textZoom },
            onRelease = {
                if (find.webView === it) find.webView = null
                it.destroy()
            },
            modifier = modifier.fillMaxSize().padding(contentPadding),
        )
    }
    LaunchedEffect(query, html) {
        val webView = find.webView ?: return@LaunchedEffect
        if (query.isEmpty()) {
            webView.clearMatches()
            find.count = 0
            find.active = 0
        } else {
            webView.findAllAsync(query)
        }
    }
}

private const val FULL_TEXT_ZOOM = 100
