package jp.miyavi.android.sumahoclaw.feature.chat.sidebar

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import jp.miyavi.android.sumahoclaw.core.data.Agent
import jp.miyavi.android.sumahoclaw.core.data.SessionListState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * バーチャルオフィス（実験機能。spec 009）を使うか。設定 → エージェント のスイッチ。
 * オンなら、サイドパネルの頭の看板（エージェントの顔）をオフィスに替える。
 */
val LocalVirtualOffice = staticCompositionLocalOf { false }

/** オフィスの 1 人。エージェントごとに、セッションが 1 つでも動いていれば作業中、未読があれば印。 */
internal data class OfficeAgent(val id: String, val name: String, val working: Boolean, val unread: Boolean)

internal fun officeAgents(agents: List<Agent>, sessionsByAgent: Map<String, SessionListState>): List<OfficeAgent> =
    agents.map { agent ->
        val list = sessionsByAgent[agent.id]
        val all = listOfNotNull(list?.home) + list?.sessions.orEmpty()
        OfficeAgent(agent.id, agent.name, working = all.any { it.running }, unread = all.any { it.unread })
    }

/**
 * サイドパネルの頭の看板に置くバーチャルオフィス。同梱の HTML（assets/office）を WebView で見る専用に描く。
 * - 外へは通信しない。同梱のファイルだけを、決まった https の住所に見せかけて渡す（ES モジュールは file:// では読めない）
 * - タッチは受けない（下のサイドパネルにそのまま渡す。閉じる横のスワイプも邪魔しない）
 * - Gateway にはつながない。アプリが状態だけを `office.setAgents` で流し込む
 * - [active] が false（パネルが閉じている）・アプリが裏に回ったあいだは描かない
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AgentOffice(
    agents: List<OfficeAgent>,
    active: Boolean,
    modifier: Modifier = Modifier,
    /** 枠の上に重なる部品（はみ出し・ステータスバー・閉じるボタン）の高さ。部屋はその下に収める。 */
    topInset: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f),
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val accent = "#%06X".format(colors.primary.toArgb() and 0xFFFFFF)
    val background = colors.surfaceContainerLow.toArgb()
    val still = remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var ready by remember { mutableStateOf(false) }
    // 読み込めたら、オフィスをフェードで出す（読み込むまでの白い面を見せない）
    val opacity = remember { Animatable(0f) }
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(ready) { if (ready) opacity.animateTo(1f, fadeSpec) }

    val webView = remember {
        OfficeWebView(context) { ready = true }
    }
    DisposableEffect(webView) { onDispose { webView.web.destroy() } }
    LifecycleResumeEffect(webView) {
        webView.web.onResume()
        onPauseOrDispose { webView.web.onPause() }
    }

    val payload = remember(agents) {
        JsonArray(
            agents.map { a ->
                buildJsonObject {
                    put("id", a.id)
                    put("name", a.name)
                    put("working", a.working)
                    put("unread", a.unread)
                }
            },
        ).toString()
    }
    LaunchedEffect(ready, payload) { if (ready) webView.call("office.setAgents($payload)") }
    LaunchedEffect(ready, dark, accent) {
        if (ready) webView.call("office.setTheme(${buildJsonObject { put("dark", dark); put("accent", accent) }})")
    }
    // WebView の CSS の 1px は 1dp（端末の密度で拡大される）なので、dp の値をそのまま渡す
    LaunchedEffect(ready, topInset) { if (ready) webView.call("office.setInsets({ top: ${topInset.value} })") }
    LaunchedEffect(ready, still) { if (ready) webView.call("office.setReducedMotion($still)") }
    LaunchedEffect(ready, active) {
        if (active) webView.web.onResume() else webView.web.onPause()
        if (ready) webView.call("office.setPaused(${!active})")
    }

    AndroidView(
        factory = { webView.also { it.web.setBackgroundColor(background) } },
        update = { it.web.setBackgroundColor(background) },
        modifier = modifier
            .semantics { hideFromAccessibility() }
            .graphicsLayer { alpha = opacity.value },
    )
}

/** オフィスを入れる枠。タッチを受けない（[dispatchTouchEvent] が false なので、下の部品へ渡る）。 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
private class OfficeWebView(context: Context, onReady: () -> Unit) : FrameLayout(context) {
    val web = WebView(context).apply {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isFocusable = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            setSupportZoom(false)
            mediaPlaybackRequiresUserGesture = true
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                assetResponse(view.context, request)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun onPageFinished(view: WebView, url: String) = onReady()
        }
        loadUrl("$OFFICE_ORIGIN/office/index.html?embed")
    }

    init { addView(web, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false

    fun call(script: String) = web.evaluateJavascript("window.office && $script", null)
}

/** 同梱のファイルだけを返す。それ以外（外への通信）はすべて断る。 */
private fun assetResponse(context: Context, request: WebResourceRequest): WebResourceResponse {
    val url = request.url
    val path = url.path.orEmpty().removePrefix("/")
    if ("${url.scheme}://${url.host}" != OFFICE_ORIGIN || !path.startsWith("office/") || ".." in path) return denied()
    val mime = when (path.substringAfterLast('.')) {
        "html" -> "text/html"
        "js" -> "text/javascript"
        else -> return denied()
    }
    return runCatching {
        WebResourceResponse(mime, "utf-8", context.assets.open(path))
    }.getOrElse { denied() }
}

private fun denied() = WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))

/** 同梱のファイルを見せる住所（Android の WebViewAssetLoader と同じ、どこにも届かない名前）。 */
private const val OFFICE_ORIGIN = "https://appassets.androidplatform.net"
