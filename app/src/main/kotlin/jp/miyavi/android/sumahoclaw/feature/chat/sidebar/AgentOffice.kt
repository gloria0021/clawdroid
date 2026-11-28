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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
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
import jp.miyavi.android.sumahoclaw.core.settings.OfficeSettings
import jp.miyavi.android.sumahoclaw.core.settings.OfficeTheme
import kotlinx.serialization.json.JsonArray
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * バーチャルオフィス（実験機能。spec 009）の設定。設定 → エージェント → バーチャルオフィス。
 * [OfficeSettings.enabled] なら、サイドパネルの頭の看板（エージェントの顔）をオフィスに替える。
 */
val LocalOfficeSettings = staticCompositionLocalOf { OfficeSettings() }

/** キャラクターの種類の数（assets/office/index.html の PRESETS と同じ。人 8・犬 4・猫 4）。 */
const val OFFICE_AVATAR_COUNT = 16

/** エージェントのキャラクター。選んでいなければ id から決める（試作と同じ計算。開くたびに同じ）。 */
fun officeAvatarOf(agentId: String, chosen: Map<String, Int>): Int {
    chosen[agentId]?.takeIf { it in 0 until OFFICE_AVATAR_COUNT }?.let { return it }
    var h = 0
    agentId.codePoints().forEach { h = h * 31 + it }
    return kotlin.math.abs(h % OFFICE_AVATAR_COUNT)
}

/**
 * オフィスの 1 人（Gateway の写し）。[running] はセッションが 1 つでも動いているか、[updatedAt] はセッションのいちばん新しい更新。
 * デスクにいるかは [deskAgents] で、動いていなくても最後の動きから [DESK_LINGER_MS] のあいだはデスクに残す。
 */
internal data class OfficeAgent(val id: String, val name: String, val running: Boolean, val updatedAt: Long?)

internal fun officeAgents(agents: List<Agent>, sessionsByAgent: Map<String, SessionListState>): List<OfficeAgent> =
    agents.map { agent ->
        val list = sessionsByAgent[agent.id]
        val all = listOfNotNull(list?.home) + list?.sessions.orEmpty()
        OfficeAgent(agent.id, agent.name, running = all.any { it.running }, updatedAt = all.mapNotNull { it.updatedAt }.maxOrNull())
    }

/**
 * メッセージを受け取ったら（動き始めた・セッションが更新された）デスクへ向かい、動き終わってからも最低 [DESK_LINGER_MS] はデスクに残る。
 * 短いやり取りで、デスクに着く前にベッドへ戻ってしまわないように（マスター指定 2026-11-28）。
 * 時刻は端末の経過時間で測る（Gateway の時計とずれていても狂わない）。最初に見た更新時刻は基準にするだけで、動きとみなさない。
 */
internal class DeskTracker {
    private val until = HashMap<String, Long>()
    private val lastUpdate = HashMap<String, Long>()
    private val wasRunning = HashMap<String, Boolean>()

    /** 新しい状態を受け取る。[now] は端末の経過時間（ms）。 */
    fun observe(agents: List<OfficeAgent>, now: Long) {
        for (a in agents) {
            val prevUpdate = lastUpdate[a.id]
            val updated = prevUpdate != null && a.updatedAt != null && a.updatedAt > prevUpdate
            if (a.running || wasRunning[a.id] == true || updated) until[a.id] = now + DESK_LINGER_MS
            a.updatedAt?.let { lastUpdate[a.id] = maxOf(prevUpdate ?: it, it) }
            wasRunning[a.id] = a.running
        }
    }

    fun atDesk(a: OfficeAgent, now: Long) = a.running || (until[a.id] ?: 0L) > now

    /** 次にデスクを離れる人が出る時刻（無ければ null）。 */
    fun nextExpiry(now: Long): Long? = until.values.filter { it > now }.minOrNull()
}

/** 動き終わってからデスクに残る時間。 */
internal const val DESK_LINGER_MS = 20_000L

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
    val office = LocalOfficeSettings.current
    // 昼・夜: 自動ならアプリの外観に合わせる
    val dark = when (office.theme) {
        OfficeTheme.AUTO -> colors.surface.luminance() < 0.5f
        OfficeTheme.DAY -> false
        OfficeTheme.NIGHT -> true
    }
    val accent = "#%06X".format(colors.primary.toArgb() and 0xFFFFFF)
    val background = colors.surfaceContainerLow.toArgb()
    // 端末の「アニメーションを削除」は、アプリに戻るたびに読み直す（設定から戻ったときに切り替えを反映する。CJ レビュー 2026-11-28）
    var still by remember(context) { androidx.compose.runtime.mutableStateOf(animationsRemoved(context)) }
    // WebView と「デスクに残る」の記録は、画面の出し入れ・作り直しを越えて 1 つを使い続ける（作り直すと部屋を読み込み直し、
    // 歩いている人が一瞬で席に移り、20 秒の待ちも忘れてしまう。マスター指摘 2026-11-28）
    val webView = remember(context) { OfficeHost.view(context) }
    val ready = webView.ready
    // 読み込めたら、オフィスをフェードで出す（読み込むまでの白い面を見せない）。読み込み済みならそのまま出す
    val opacity = remember { Animatable(if (ready) 1f else 0f) }
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(ready) { if (ready) opacity.animateTo(1f, fadeSpec) }
    // 描くのは「パネルが見えている」かつ「アプリが前にある（RESUMED）」ときだけ。この 1 つの条件で、WebView の再開・止める と
    // HTML の setPaused を決める（読み込みが終わったときも同じ条件。CJ レビュー 2026-11-28）。
    // ウィンドウのフォーカスは見ない（通知の欄を下ろしても止めない。マスター指定）。HTML は止まっているあいだ次のコマを予約せず、
    // setPaused(false) で必ず入れ直す
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val resumed by lifecycle.currentStateFlow.collectAsState()
    val running = active && resumed.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    LifecycleResumeEffect(context) {
        still = animationsRemoved(context)
        onPauseOrDispose { }
    }

    // デスクにいるか: 動いている、または最後の動きから 20 秒以内。残り時間が切れたら数え直す
    val tracker = OfficeHost.tracker
    var tick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val observedAt = remember(agents) { android.os.SystemClock.elapsedRealtime().also { tracker.observe(agents, it) } }
    LaunchedEffect(observedAt, tick) {
        val now = android.os.SystemClock.elapsedRealtime()
        val next = tracker.nextExpiry(now) ?: return@LaunchedEffect
        kotlinx.coroutines.delay(next - now + 1)
        tick++
    }
    val payload = remember(agents, tick, office.avatars) {
        val now = android.os.SystemClock.elapsedRealtime()
        JsonArray(
            agents.map { a ->
                buildJsonObject {
                    put("id", a.id)
                    put("name", a.name)
                    put("preset", officeAvatarOf(a.id, office.avatars))
                    put("working", tracker.atDesk(a, now))
                    // 動いているあいだだけ、頭の上に作業中の記号（終わって 20 秒デスクで待つあいだは出さない）
                    put("busy", a.running)
                }
            },
        ).toString()
    }
    // 読み込み中・つなぎ直しの途中で一時的に空になった一覧は送らない（全員を消して、戻ったときに一瞬で席に置き直してしまう）
    LaunchedEffect(ready, payload) { if (ready && agents.isNotEmpty()) webView.call("office.setAgents($payload)") }
    LaunchedEffect(ready, dark, accent) {
        if (ready) webView.call("office.setTheme(${buildJsonObject { put("dark", dark); put("accent", accent) }})")
    }
    // WebView の CSS の 1px は 1dp（端末の密度で拡大される）なので、dp の値をそのまま渡す
    LaunchedEffect(ready, topInset) { if (ready) webView.call("office.setInsets({ top: ${topInset.value} })") }
    LaunchedEffect(ready, still) { if (ready) webView.call("office.setReducedMotion($still)") }
    LaunchedEffect(ready, running) {
        if (running) webView.web.onResume() else webView.web.onPause()
        if (ready) webView.call("office.setPaused(${!running})")
    }

    AndroidView(
        // 前の画面に付いたままなら外してから付け直す（同じ WebView を使い続けるため）
        factory = { webView.also { (it.parent as? android.view.ViewGroup)?.removeView(it); it.web.setBackgroundColor(background) } },
        update = { it.web.setBackgroundColor(background) },
        modifier = modifier
            .semantics { hideFromAccessibility() }
            .graphicsLayer { alpha = opacity.value },
    )
}

/** 端末の「アニメーションを削除」がオンか（アニメーターの再生時間スケールが 0）。 */
private fun animationsRemoved(context: Context): Boolean =
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * プロセスに 1 つの WebView と記録。設定でオフにしたら [release] で捨てる。
 * WebView はアプリの Context で作る（Activity を作り直しても使い続けるため。ダイアログなどは使わない）。
 */
internal object OfficeHost {
    val tracker = DeskTracker()
    private var instance: OfficeWebView? = null

    fun view(context: Context): OfficeWebView = instance ?: OfficeWebView(context.applicationContext).also { instance = it }

    fun release() {
        instance?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); it.web.destroy() }
        instance = null
    }
}

/** オフィスを入れる枠。タッチを受けない（[dispatchTouchEvent] が false なので、下の部品へ渡る）。 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class OfficeWebView(context: Context, page: String = "embed") : FrameLayout(context) {
    /** 部屋を読み込み終えたか。 */
    var ready by mutableStateOf(false)
        private set

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
            override fun onPageFinished(view: WebView, url: String) { ready = true }
        }
        loadUrl("$OFFICE_ORIGIN/office/index.html?$page")
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

/**
 * キャラクターの見本の絵（設定 → バーチャルオフィス → アバター）。オフィスと同じ HTML で描いて受け取り、プロセスのあいだ覚えておく。
 * 描けなかった（WebGL が使えない端末など）ときは null のまま（画面は名前だけで選ぶ）。
 */
internal object OfficePortraits {
    var images by mutableStateOf<List<androidx.compose.ui.graphics.ImageBitmap>?>(null)
        private set

    /** 見本を作る。WebView は画面に付けたまま（[host] に入れて）渡す。 */
    fun load(view: OfficeWebView, scope: kotlinx.coroutines.CoroutineScope) {
        if (images != null) return
        view.web.evaluateJavascript("window.office && JSON.stringify(office.portraits($PORTRAIT_PX))") { result ->
            scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                val decoded = runCatching {
                    // 返り値は JSON の文字列（中身が配列の JSON）
                    val inner = kotlinx.serialization.json.Json.parseToJsonElement(result).let { (it as kotlinx.serialization.json.JsonPrimitive).content }
                    kotlinx.serialization.json.Json.parseToJsonElement(inner).let { it as JsonArray }.map { url ->
                        val bytes = android.util.Base64.decode((url as kotlinx.serialization.json.JsonPrimitive).content.substringAfter(","), android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
                    }
                }.getOrNull()?.takeIf { it.size == OFFICE_AVATAR_COUNT }
                if (decoded != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { images = decoded }
            }
        }
    }
}

/** 見本の絵の大きさ（px）。 */
private const val PORTRAIT_PX = 160
