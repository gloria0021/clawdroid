package jp.miyavi.android.clawdroid.core.gateway

import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import jp.miyavi.android.clawdroid.core.security.GatewayConnection
import jp.miyavi.android.clawdroid.core.security.SecretStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** 接続の状態（data-model 1 の状態遷移）。 */
sealed interface ConnectionState {
    /** 接続情報がまだ無い（接続画面を出す）。 */
    data object NotConfigured : ConnectionState
    data object Connecting : ConnectionState
    /** 端末の承認待ち（FR-004）。 */
    data class AwaitingApproval(val requestId: String?, val deviceId: String) : ConnectionState
    data class Connected(
        val serverVersion: String,
        val methods: Set<String>,
        val scopes: List<String>,
        val tickIntervalMs: Long,
    ) : ConnectionState
    data class Reconnecting(val attempt: Int, val reason: FailureReason) : ConnectionState
    /** 再起動を指示したあと、戻るまで（FR-072）。 */
    data object Restarting : ConnectionState
    /** 自動の再接続を止めた失敗。 */
    data class Failed(val reason: FailureReason, val message: String?, val code: String? = null) : ConnectionState
    /** 利用者が切断した。 */
    data object Disconnected : ConnectionState
    /** アプリが裏にあるあいだ、電池のために接続を休んでいる（常時接続がオフのとき。前に戻るとつなぎ直す）。 */
    data object Paused : ConnectionState
}

/** Gateway から届いたイベント。 */
data class GatewayEvent(val name: String, val payload: JsonElement?)

/** connect の client 情報（`ref-schema/frames.ts` の ConnectParams.client）。 */
data class ClientInfo(
    val version: String,
    val modelIdentifier: String?,
    val displayName: String?,
) {
    val id: String = "openclaw-android"
    val mode: String = "ui"
    val platform: String = "android"
    val deviceFamily: String = "Android"
}

/**
 * Gateway の WebSocket（プロトコル v4）の接続を 1 本保ち、要求と応答の対応付けとイベントの配信をする。
 * 公式 Android アプリの GatewaySession を参考にした最小の実装（research R1・R2・R6）。
 */
class GatewayClient(
    private val scope: CoroutineScope,
    private val httpClient: OkHttpClient,
    private val identities: DeviceIdentityRepository,
    private val secrets: SecretStore,
    private val clientInfo: ClientInfo,
    private val handshakeTimeoutMs: Long = DEFAULT_HANDSHAKE_TIMEOUT_MS,
    /** 前に戻ったときの応答の確かめの待ち時間（3 秒では遅い回線で生きている接続を捨てていた）。 */
    private val foregroundProbeTimeoutMs: Long = FOREGROUND_PROBE_TIMEOUT_MS,
    /** この時間内に何か届いていれば、前に戻ったときの確かめを飛ばす。 */
    private val probeSkipRecentMs: Long = PROBE_SKIP_RECENT_MS,
) {
    private val tlsClient = httpClient.newBuilder().followRedirects(false).build()
    private val lanClient = tlsClient.newBuilder().proxy(java.net.Proxy.NO_PROXY)
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> = httpClient.dns.lookup(hostname)
                .filter(GatewayUrl::isPrivateAddress).ifEmpty { throw java.net.UnknownHostException("Private address required") }
        }).addInterceptor { chain ->
            val host = chain.request().url.host
            if (!GatewayUrl.isPrivateHost(host)) throw java.io.IOException("Private Gateway required")
            chain.proceed(chain.request())
        }.build()

    private fun transport(tls: Boolean) = if (tls) tlsClient else lanClient

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.NotConfigured)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Frame.Response>>()
    private val requestCounter = AtomicLong()
    private val reconnectSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var socket: WebSocket? = null
    @Volatile private var restartPending = false
    private var loopJob: Job? = null

    /** 保存済みの接続情報があれば接続を始める。 */
    fun start() {
        scope.launch {
            if (secrets.loadConnection() == null) {
                _state.value = ConnectionState.NotConfigured
            } else if (loopJob?.isActive != true) {
                launchLoop()
            }
        }
    }

    /**
     * 保存済みの接続情報があっても、つながずに休んだ状態で始める（アプリの起動時）。つなぐかは呼ぶ側（前にある・常時接続・
     * 承認待ちやダウンロード中）が [resumeFromPause] で決める。端末の起動などで裏からプロセスだけが起きたときに、
     * 常時接続がオフでもつないでしまわないため（CJ レビュー 2026-11-26）。
     */
    fun startPaused() {
        scope.launch {
            _state.value = if (secrets.loadConnection() == null) ConnectionState.NotConfigured else ConnectionState.Paused
        }
    }

    /** 接続画面から。接続情報を保存して接続を始める（以前の端末のトークンは、接続先が同じなら残す）。 */
    suspend fun connect(url: String, gatewayToken: String) {
        val previous = secrets.loadConnection()
        val keepDeviceToken = previous?.url == url
        secrets.saveConnection(
            GatewayConnection(
                url = url,
                gatewayToken = gatewayToken,
                deviceToken = previous?.deviceToken.takeIf { keepDeviceToken },
                deviceTokenScopes = if (keepDeviceToken) previous?.deviceTokenScopes.orEmpty() else emptyList(),
            ),
        )
        launchLoop()
    }

    /** 利用者の切断。接続情報は消さない（[forget] で消す）。 */
    fun disconnect() {
        loopJob?.cancel()
        closeSocket()
        _state.value = ConnectionState.Disconnected
    }

    /**
     * 裏にあるあいだ接続を休む（常時接続がオフのとき。マスター指定 2026-11-26）。ソケットを開けたままだと、
     * アプリが凍結されていても tick を受けるたびに電波が起きるため。接続情報は残し、[resumeFromPause] でつなぎ直す。
     * 未設定・利用者の切断・自動でつなぎ直さない失敗のときは何もしない。
     */
    fun pause() {
        when (_state.value) {
            ConnectionState.NotConfigured, ConnectionState.Disconnected, ConnectionState.Paused, is ConnectionState.Failed -> return
            else -> Unit
        }
        loopJob?.cancel()
        closeSocket()
        _state.value = ConnectionState.Paused
    }

    /** 休んでいた接続をつなぎ直す。休んでいなければ false（何もしない）。 */
    fun resumeFromPause(): Boolean {
        if (_state.value != ConnectionState.Paused) return false
        _state.value = if (restartPending) ConnectionState.Restarting else ConnectionState.Connecting
        launchLoop()
        return true
    }

    /** 接続情報を消して、接続画面に戻す。 */
    suspend fun forget() {
        loopJob?.cancel()
        closeSocket()
        secrets.clearConnection()
        _state.value = ConnectionState.NotConfigured
    }

    /**
     * 利用者の「接続し直す」。待ち時間を飛ばすだけでなく、進行中の試行も捨てて新しいソケットで始め直す。
     * スリープ明けなどで経路が切れていた間に始めた試行は、TCP や TLS の再送の間隔が延びて長く固まることがあり、
     * それが終わるのを待たない（アプリを起動し直すと直るのはこの差）。
     */
    fun reconnectNow() {
        when (_state.value) {
            is ConnectionState.Connected -> probeConnection()
            else -> launchLoop()
        }
    }

    /** 復帰時は、再接続中なら新しいソケットで始め直し、接続中のソケットには応答があるか確かめる。 */
    fun onForeground() {
        when (_state.value) {
            is ConnectionState.Reconnecting, ConnectionState.Restarting -> launchLoop()
            is ConnectionState.Connected -> probeConnection()
            else -> Unit
        }
    }

    /** 最後に Gateway から何か（応答・イベント・tick）が届いた時刻（[monotonicMs]）。 */
    @Volatile private var lastFrameAt = Long.MIN_VALUE / 2

    /**
     * 接続中のソケットに応答があるか確かめ、無ければ閉じて接続し直す。
     * 直前に何か届いていれば生きているので確かめない。遅い回線や Gateway が忙しいときに、生きている接続を
     * 捨ててつなぎ直しを繰り返さないよう、待ち時間は長めにとる（マスター指摘 2026-11-25）。
     */
    private fun probeConnection() {
        val connectionSocket = socket ?: return
        if (monotonicMs() - lastFrameAt < probeSkipRecentMs) return
        scope.launch {
            val responds = withTimeoutOrNull(foregroundProbeTimeoutMs) {
                try {
                    request("health")
                    true
                } catch (_: GatewayRpcException) {
                    true // RPC エラーでも WebSocket の応答は届いている。
                } catch (_: GatewayNotConnectedException) {
                    false
                }
            } == true
            if (!responds && socket === connectionSocket && _state.value is ConnectionState.Connected) {
                logReconnect("reconnect: no health response within ${foregroundProbeTimeoutMs}ms after foreground")
                reconnectSignal.trySend(Unit)
                connectionSocket.cancel()
            }
        }
    }

    /** 再起動を指示した直後に呼ぶ。切断から再接続までを「再起動中」として示す。 */
    fun markRestarting() {
        restartPending = true
        _state.value = ConnectionState.Restarting
    }

    /** 接続済みになるまで待つ。 */
    suspend fun awaitConnected(): ConnectionState.Connected =
        state.first { it is ConnectionState.Connected } as ConnectionState.Connected

    fun supports(method: String): Boolean = (state.value as? ConnectionState.Connected)?.methods?.contains(method) ?: false

    /** RPC を呼ぶ。失敗は [GatewayRpcException]、接続が無ければ [GatewayNotConnectedException]。 */
    suspend fun request(method: String, params: JsonObject? = null, timeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS): JsonElement? {
        val ws = socket.takeIf { state.value is ConnectionState.Connected } ?: throw GatewayNotConnectedException()
        val id = "r${requestCounter.incrementAndGet()}"
        val deferred = CompletableDeferred<Frame.Response>()
        pending[id] = deferred
        try {
            if (!ws.send(Frames.request(id, method, params))) throw GatewayNotConnectedException()
            val response = withTimeout(timeoutMs) { deferred.await() }
            if (!response.ok) throw GatewayRpcException(response.error ?: GatewayError("UNKNOWN", method, null, null, null))
            return response.payload
        } finally {
            pending.remove(id)
        }
    }

    /**
     * 会話に添付された画像（`media://inbound/<id>`）を、Gateway の HTTP の経路から取る（公式 Android アプリと同じ）。
     * `GET {http(s)://host:port}{path}/__openclaw__/assistant-media?source=…&sessionKey=…&agentId=…`、
     * 認証は端末のトークン → ゲートウェイトークンの順に Bearer で試す。
     */
    suspend fun loadInboundMedia(source: String, sessionKey: String, agentId: String?, maxBytes: Long = MAX_INBOUND_MEDIA_BYTES): ByteArray? {
        if (!INBOUND_MEDIA.matches(source.trim())) return null
        val connection = secrets.loadConnection() ?: return null
        val ws = connection.url.wsToHttpUrl() ?: return null
        val url = ws.newBuilder()
            .encodedPath(ws.encodedPath.trimEnd('/') + "/__openclaw__/assistant-media")
            .query(null)
            .addQueryParameter("source", source.trim())
            .addQueryParameter("sessionKey", sessionKey)
            .apply { agentId?.takeIf(String::isNotBlank)?.let { addQueryParameter("agentId", it) } }
            .build()
        val tokens = listOfNotNull(connection.deviceToken, connection.gatewayToken).map(String::trim).filter(String::isNotEmpty).distinct()
        return withContext(Dispatchers.IO) {
            for (token in tokens) {
                val request = Request.Builder().url(url).header("Accept", "image/*").header("Authorization", "Bearer $token").build()
                val result = runCatching {
                    transport(url.isHttps).newCall(request).execute().use { response ->
                        if (response.code == 401 || response.code == 403) return@use null to true
                        val body = response.body
                        if (!response.isSuccessful || body.contentLength() > maxBytes) return@use null to false
                        body.byteStream().readNBytes((maxBytes + 1).toInt()).takeIf { it.size <= maxBytes } to false
                    }
                }.getOrNull() ?: return@withContext null
                val (bytes, unauthorized) = result
                if (!unauthorized) return@withContext bytes
            }
            null
        }
    }

    /**
     * Gateway が返した相対の短命リンク（`sessions.files.download` の `url`）を HTTP で取り、中身を [sink] に渡す。
     * リンクはこの接続に紐付くため認証は要らないが、前段に認証のある構成に備えて Bearer も付ける。届かなければ false。
     */
    /** 1 回分の受け取りの結果。 */
    enum class LinkAttempt {
        /** 最後まで受け取った。 */
        COMPLETE,
        /** 途中で切れた・リンクが失効した（つなぎ直して新しいリンクで続きから受け取れる）。 */
        RETRY,
        /** 受け取れない（権限など）。 */
        FAIL,
    }

    /**
     * Gateway が返した相対の短命リンク（`sessions.files.download` の `url`）を HTTP で取る。[from] バイト目から
     * 続きを頼み（Range）、[sink] には（中身, 続きから受け取れたか, 全体の大きさ）を渡す。
     * リンクはこの接続に紐付くため、接続が切れると失効する（そのときは [LinkAttempt.RETRY]）。
     */
    suspend fun downloadLink(
        relativeUrl: String,
        from: Long = 0,
        sink: (input: java.io.InputStream, resumed: Boolean, total: Long?) -> Unit,
    ): LinkAttempt {
        val connection = secrets.loadConnection() ?: return LinkAttempt.FAIL
        val base = connection.url.wsToHttpUrl() ?: return LinkAttempt.FAIL
        val url = base.resolve(relativeUrl) ?: return LinkAttempt.FAIL
        if (url.scheme != base.scheme || url.host != base.host || url.port != base.port) return LinkAttempt.FAIL
        val token = listOfNotNull(connection.deviceToken, connection.gatewayToken).map(String::trim).firstOrNull(String::isNotEmpty)
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).apply {
                token?.let { header("Authorization", "Bearer $it") }
                if (from > 0) header("Range", "bytes=$from-")
            }.build()
            val call = transport(url.isHttps).newCall(request)
            // 中止（コルーチンの取り消し）で、読み込み中の通信も止める
            val cancelOnStop = currentCoroutineContext()[Job]?.invokeOnCompletion { if (it != null) call.cancel() }
            try {
                call.execute().use { response ->
                    when {
                        response.code == 206 -> {
                            val total = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                            response.body.byteStream().use { sink(it, true, total) }
                            LinkAttempt.COMPLETE
                        }
                        response.isSuccessful -> {
                            val total = response.body.contentLength().takeIf { it >= 0 }
                            response.body.byteStream().use { sink(it, false, total) }
                            LinkAttempt.COMPLETE
                        }
                        // 失効したリンク（接続が替わった）は、新しいリンクでやり直す
                        response.code == 404 || response.code == 410 || response.code >= 500 -> {
                            android.util.Log.w("ClawdroidDownload", "HTTP ${response.code}, retrying with a new link")
                            LinkAttempt.RETRY
                        }
                        else -> {
                            android.util.Log.w("ClawdroidDownload", "HTTP ${response.code}")
                            LinkAttempt.FAIL
                        }
                    }
                }
            } catch (e: java.io.IOException) {
                if (call.isCanceled()) throw kotlinx.coroutines.CancellationException("download cancelled")
                android.util.Log.w("ClawdroidDownload", "connection dropped, will resume", e)
                LinkAttempt.RETRY
            } finally {
                cancelOnStop?.dispose()
            }
        }
    }

    private fun String.wsToHttpUrl(): HttpUrl? {
        val http = when {
            startsWith("wss://", ignoreCase = true) -> "https://" + substring(6)
            startsWith("ws://", ignoreCase = true) -> "http://" + substring(5)
            else -> this
        }
        return http.toHttpUrlOrNull()
    }

    // ---- 接続のループ ----

    private fun launchLoop() {
        val previous = loopJob
        previous?.cancel()
        // 前の試行の後片付け（ソケットを閉じる・待ちの要求を失敗にする）が、新しい接続に重ならないように待つ
        loopJob = scope.launch {
            previous?.join()
            runLoop()
        }
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (scope.isActive) {
            val connection = secrets.loadConnection() ?: run {
                _state.value = ConnectionState.NotConfigured
                return
            }
            if (_state.value !is ConnectionState.AwaitingApproval) {
                _state.value = when {
                    restartPending -> ConnectionState.Restarting
                    attempt == 0 -> ConnectionState.Connecting
                    else -> _state.value
                }
            }
            when (val outcome = runSession(connection)) {
                SessionOutcome.ClosedAfterConnected -> {
                    attempt = 1
                    setReconnecting(attempt, FailureReason.UNREACHABLE)
                    waitBeforeRetry(INITIAL_RETRY_DELAY_MS)
                }
                SessionOutcome.Unreachable -> {
                    attempt++
                    setReconnecting(attempt, FailureReason.UNREACHABLE)
                    waitBeforeRetry(backoffMs(attempt))
                }
                is SessionOutcome.RetryLater -> {
                    attempt++
                    if (_state.value !is ConnectionState.AwaitingApproval) setReconnecting(attempt, FailureReason.OTHER)
                    waitBeforeRetry(outcome.delayMs)
                }
                SessionOutcome.PairingApproved -> {
                    attempt = 0
                    _state.value = ConnectionState.Connecting
                }
                is SessionOutcome.Fatal -> {
                    restartPending = false
                    _state.value = ConnectionState.Failed(outcome.reason, outcome.message, outcome.code)
                    return
                }
            }
        }
    }

    private fun setReconnecting(attempt: Int, reason: FailureReason) {
        _state.value = if (restartPending) ConnectionState.Restarting else ConnectionState.Reconnecting(attempt, reason)
    }

    private suspend fun waitBeforeRetry(delayMs: Long) {
        withTimeoutOrNull(delayMs) { reconnectSignal.receive() }
    }

    private sealed interface SessionOutcome {
        data object ClosedAfterConnected : SessionOutcome
        data object Unreachable : SessionOutcome
        data object PairingApproved : SessionOutcome
        data class RetryLater(val delayMs: Long) : SessionOutcome
        data class Fatal(val reason: FailureReason, val message: String?, val code: String? = null) : SessionOutcome
    }

    private sealed interface Signal {
        data class Text(val text: String) : Signal
        data class Closed(val code: Int, val reason: String) : Signal
        data class Failure(val error: Throwable) : Signal
    }

    private suspend fun runSession(connection: GatewayConnection): SessionOutcome {
        val signals = Channel<Signal>(Channel.UNLIMITED)
        val ws = try {
            transport(connection.url.startsWith("wss:", ignoreCase = true)).newWebSocket(Request.Builder().url(connection.url).build(), object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    lastFrameAt = monotonicMs()
                    signals.trySend(Signal.Text(text))
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                    signals.trySend(Signal.Closed(code, reason))
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { signals.trySend(Signal.Closed(code, reason)) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { signals.trySend(Signal.Failure(t)) }
            })
        } catch (e: IllegalArgumentException) {
            return SessionOutcome.Fatal(FailureReason.UNREACHABLE, e.message)
        }
        try {
            // 1. connect.challenge を待つ
            // ソケットを開くところ（DNS・TCP・TLS・アップグレード）も含めて待つ。読み取りの時間切れは無い設定なので、ここで区切る
            val challenge = withTimeoutOrNull(minOf(handshakeTimeoutMs, OPEN_TIMEOUT_MS)) {
                while (true) {
                    when (val s = signals.receive()) {
                        is Signal.Text -> {
                            val frame = Frames.parse(s.text) as? Frame.Event ?: continue
                            if (frame.event == "connect.challenge") return@withTimeoutOrNull frame.payload.asObject()
                        }
                        is Signal.Closed, is Signal.Failure -> return@withTimeoutOrNull null
                    }
                }
                @Suppress("UNREACHABLE_CODE") null
            } ?: return SessionOutcome.Unreachable
            val nonce = challenge.string("nonce") ?: return SessionOutcome.Unreachable
            val signedAt = challenge.long("ts") ?: return SessionOutcome.Unreachable

            // 2. connect を送る
            val identity = identities.loadOrCreate()
            val connectId = "connect-${requestCounter.incrementAndGet()}"
            ws.send(Frames.request(connectId, "connect", buildConnectParams(connection, identity, nonce, signedAt)))

            // 3. 応答を待つ
            val response = withTimeoutOrNull(handshakeTimeoutMs) {
                while (true) {
                    when (val s = signals.receive()) {
                        is Signal.Text -> {
                            val frame = Frames.parse(s.text) as? Frame.Response ?: continue
                            if (frame.id == connectId) return@withTimeoutOrNull frame
                        }
                        is Signal.Closed, is Signal.Failure -> return@withTimeoutOrNull null
                    }
                }
                @Suppress("UNREACHABLE_CODE") null
            } ?: return SessionOutcome.Unreachable

            if (!response.ok) return handleConnectError(response.error, connection, identity.deviceId, signals)

            // 4. 接続済み
            val hello = response.payload.asObject() ?: return SessionOutcome.Unreachable
            onHello(hello, connection, ws)
            return runConnected(ws, signals, tickIntervalMs(hello))
        } finally {
            if (socket === ws) socket = null
            ws.cancel()
            failPending()
        }
    }

    private suspend fun handleConnectError(
        error: GatewayError?,
        connection: GatewayConnection,
        deviceId: String,
        signals: Channel<Signal>,
    ): SessionOutcome {
        if (error == null) return SessionOutcome.Unreachable
        if (GatewayErrorClassifier.isPairingRequired(error)) {
            _state.value = ConnectionState.AwaitingApproval(error.details?.string("requestId"), deviceId)
            if (!GatewayErrorClassifier.waitsForResolution(error)) return SessionOutcome.RetryLater(PAIRING_POLL_MS)
            // イベントも切断も届かなければ、承認待ちを表示したまま新しい接続で確認する。
            return withTimeoutOrNull(handshakeTimeoutMs) { awaitPairingResolution(signals) }
                ?: SessionOutcome.RetryLater(0)
        }
        // 保存した端末のトークンが古いときは捨ててやり直す
        if (error.detailCode == "AUTH_DEVICE_TOKEN_MISMATCH" && connection.deviceToken != null) {
            secrets.saveConnection(connection.copy(deviceToken = null, deviceTokenScopes = emptyList()))
            return SessionOutcome.RetryLater(0)
        }
        if (GatewayErrorClassifier.retryable(error)) return SessionOutcome.RetryLater(error.retryAfterMs ?: backoffMs(2))
        return SessionOutcome.Fatal(GatewayErrorClassifier.classify(error), error.message, error.detailCode ?: error.code)
    }

    private suspend fun awaitPairingResolution(signals: Channel<Signal>): SessionOutcome {
        while (true) {
            when (val signal = signals.receive()) {
                is Signal.Text -> {
                    val event = Frames.parse(signal.text) as? Frame.Event ?: continue
                    if (event.event != "device.pair.resolved") continue
                    return when (val decision = event.payload.asObject()?.string("decision")) {
                        "approved" -> SessionOutcome.PairingApproved
                        "rejected", "expired" -> SessionOutcome.Fatal(FailureReason.PAIRING_REJECTED, null, "PAIRING_" + decision.uppercase())
                        else -> SessionOutcome.RetryLater(PAIRING_POLL_MS)
                    }
                }
                is Signal.Closed, is Signal.Failure -> return SessionOutcome.RetryLater(PAIRING_POLL_MS)
            }
        }
    }

    private suspend fun onHello(hello: JsonObject, connection: GatewayConnection, ws: WebSocket) {
        val auth = hello.obj("auth")
        val scopes = auth?.array("scopes")?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val deviceToken = auth?.string("deviceToken")
        if (deviceToken != null && deviceToken != connection.deviceToken) {
            secrets.saveConnection(connection.copy(deviceToken = deviceToken, deviceTokenScopes = scopes))
        }
        val methods = hello.obj("features")?.array("methods")?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet().orEmpty()
        restartPending = false
        socket = ws
        _state.value = ConnectionState.Connected(
            serverVersion = hello.obj("server")?.string("version") ?: "",
            methods = methods,
            scopes = scopes,
            tickIntervalMs = tickIntervalMs(hello),
        )
    }

    private fun tickIntervalMs(hello: JsonObject): Long = hello.obj("policy")?.long("tickIntervalMs") ?: DEFAULT_TICK_INTERVAL_MS

    /** 接続中の受信。tick が tickIntervalMs × 2 途絶えたら閉じる（公式クライアントと同じ判定）。 */
    private suspend fun runConnected(ws: WebSocket, signals: Channel<Signal>, tickIntervalMs: Long): SessionOutcome {
        val silenceLimit = tickIntervalMs * 2
        while (true) {
            val signal = withTimeoutOrNull(silenceLimit) { signals.receive() }
                ?: return SessionOutcome.ClosedAfterConnected.also {
                    logReconnect("reconnect: no frame for ${silenceLimit}ms")
                    ws.cancel()
                }
            when (signal) {
                is Signal.Text -> when (val frame = Frames.parse(signal.text)) {
                    is Frame.Response -> pending[frame.id]?.complete(frame)
                    is Frame.Event -> if (frame.event != "tick") _events.tryEmit(GatewayEvent(frame.event, frame.payload))
                    null -> Unit
                }
                is Signal.Closed, is Signal.Failure -> {
                    // つなぎ直しの理由を logcat に残す（遅い・切れる報告の切り分け用）
                    logReconnect("reconnect: socket ${if (signal is Signal.Closed) "closed" else "failed"}: $signal")
                    return SessionOutcome.ClosedAfterConnected
                }
            }
        }
    }

    private fun buildConnectParams(connection: GatewayConnection, identity: DeviceIdentity, nonce: String, signedAt: Long): JsonObject {
        val signature = identity.sign(
            DeviceAuthPayload.buildV3(
                deviceId = identity.deviceId,
                clientId = clientInfo.id,
                clientMode = clientInfo.mode,
                role = ROLE,
                scopes = SCOPES,
                signedAtMs = signedAt,
                token = connection.gatewayToken,
                nonce = nonce,
                platform = clientInfo.platform,
                deviceFamily = clientInfo.deviceFamily,
            ),
        )
        return buildJsonObject {
            put("minProtocol", PROTOCOL_VERSION)
            put("maxProtocol", PROTOCOL_VERSION)
            put("client", buildJsonObject {
                put("id", clientInfo.id)
                clientInfo.displayName?.let { put("displayName", it) }
                put("version", clientInfo.version)
                put("platform", clientInfo.platform)
                put("deviceFamily", clientInfo.deviceFamily)
                clientInfo.modelIdentifier?.let { put("modelIdentifier", it) }
                put("timeZone", TimeZone.getDefault().id)
                put("mode", clientInfo.mode)
                put("instanceId", identity.deviceId.take(16))
            })
            put("caps", JsonArray(CAPS.map(::JsonPrimitive)))
            put("role", ROLE)
            put("scopes", JsonArray(SCOPES.map(::JsonPrimitive)))
            put("auth", buildJsonObject {
                put("token", connection.gatewayToken)
                connection.deviceToken?.let { put("deviceToken", it) }
            })
            put("device", buildJsonObject {
                put("id", identity.deviceId)
                put("publicKey", identity.publicKeyBase64Url)
                put("signature", signature)
                put("signedAt", signedAt)
                put("nonce", nonce)
            })
            put("locale", Locale.getDefault().toLanguageTag())
            put("userAgent", "clawdroid/${clientInfo.version} (android)")
        }
    }

    private fun closeSocket() {
        socket?.close(NORMAL_CLOSURE, null)
        socket = null
        failPending()
    }

    private fun failPending() {
        val error = GatewayError("NOT_CONNECTED", "connection closed", null, null, null)
        pending.values.forEach { it.complete(Frame.Response("", false, null, error)) }
        pending.clear()
    }

    companion object {
        const val PROTOCOL_VERSION = 4
        const val ROLE = "operator"
        /** ターミナルと再起動に operator.admin が要る（research R1）。 */
        val SCOPES = listOf("operator.admin", "operator.approvals", "operator.read", "operator.write")
        val CAPS = listOf("agent-kind", "usage-refreshing", "health-summary", "hello-health-on-demand", "presence-deltas", "model-selection-policy",
            // 応答の文字は chat の流れだけから描くので、agent の文字の重複を受け取らない（spec 006 R3）
            "chat-only-assistant-text")
        const val DEFAULT_HANDSHAKE_TIMEOUT_MS = 30_000L
        const val DEFAULT_REQUEST_TIMEOUT_MS = 30_000L
        const val DEFAULT_TICK_INTERVAL_MS = 30_000L
        const val INITIAL_RETRY_DELAY_MS = 1_000L
        const val MAX_RETRY_DELAY_MS = 30_000L
        const val PAIRING_POLL_MS = 3_000L
        const val MAX_INBOUND_MEDIA_BYTES = 20L * 1024 * 1024
        private val INBOUND_MEDIA = Regex("^media://inbound/[^/?#\\\\]+$", RegexOption.IGNORE_CASE)
        const val FOREGROUND_PROBE_TIMEOUT_MS = 10_000L
        const val PROBE_SKIP_RECENT_MS = 10_000L
        /** ソケットを開いて connect.challenge が届くまでの上限。 */
        private const val OPEN_TIMEOUT_MS = 15_000L
        private const val NORMAL_CLOSURE = 1000

        fun backoffMs(attempt: Int): Long =
            (INITIAL_RETRY_DELAY_MS shl (attempt - 1).coerceIn(0, 5)).coerceAtMost(MAX_RETRY_DELAY_MS)

        /** 一意の冪等キー。 */
        fun newIdempotencyKey(): String = UUID.randomUUID().toString()
    }
}

/** 単調に増える時刻（ミリ秒）。端末の時計の変更に影響されず、JVM の単体テストでも動く。 */
private fun monotonicMs(): Long = System.nanoTime() / 1_000_000

/** つなぎ直しの理由を logcat に残す（JVM の単体テストでは Log が使えないので黙って飛ばす）。 */
private fun logReconnect(message: String) {
    runCatching { android.util.Log.i("ClawdroidGateway", message) }
}
