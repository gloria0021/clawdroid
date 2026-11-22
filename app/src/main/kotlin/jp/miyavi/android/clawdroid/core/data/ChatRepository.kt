package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.gateway.GatewayEvent
import jp.miyavi.android.clawdroid.core.gateway.array
import jp.miyavi.android.clawdroid.core.gateway.asObject
import jp.miyavi.android.clawdroid.core.gateway.bool
import jp.miyavi.android.clawdroid.core.gateway.obj
import jp.miyavi.android.clawdroid.core.gateway.string
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 1 つの会話の状態。 */
data class ChatState(
    val sessionKey: String,
    val agentId: String,
    val sessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    /** 生成中の応答（delta を組み立てたもの）。 */
    val live: LiveRun? = null,
    val running: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    /** まだ会話に取り込まれていない送信（steer で受け付け待ち）。キャンセルできる。 */
    val pending: List<PendingInput> = emptyList(),
    /** 今の実行が始まった時刻（考え中の経過時間に使う）。実行していなければ null。 */
    val runStartedAt: Long? = null,
    /** 今の実行の段階（status の phase、または実行中のツール名）。 */
    val activity: RunActivity? = null,
)

/** 考え中の表示に出す、今していること。 */
sealed interface RunActivity {
    data class Phase(val phase: String) : RunActivity
    data class Tool(val name: String) : RunActivity
}

/**
 * 受け付け待ちの送信。`local` は応答（ack）を待っている手元の送信、そうでなければ Gateway の pendingInputs（state = queued）。
 * `runId` は chat.send の idempotencyKey と同じで、キャンセル（chat.abort + discardPendingInput）に使う。
 */
data class PendingInput(val runId: String, val text: String, val local: Boolean)

/** 送る画像（Base64 にしたもの）。 */
data class OutgoingImage(val mimeType: String, val base64: String, val fileName: String?)

/**
 * 1 つの会話を受け持つ。履歴を取り、開いているあいだは `sessions.messages.subscribe` で更新を受ける（research R4）。
 * 表示する応答の文字は `chat` の流れだけから組み立てる（`agent` と両方を足すと二重になる）。
 */
class ChatSession(
    private val gateway: GatewayClient,
    private val scope: CoroutineScope,
    val sessionKey: String,
    val agentId: String,
) {
    private val _state = MutableStateFlow(ChatState(sessionKey, agentId))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var jobs = mutableListOf<Job>()
    private var refreshJob: Job? = null
    private var subscribed = false

    /** 画面に出たら呼ぶ。履歴の取得と購読を始める。 */
    fun open() {
        if (jobs.isNotEmpty()) return
        jobs += scope.launch {
            var wasConnected = false
            gateway.state.collect { state ->
                val connected = state is ConnectionState.Connected
                if (connected && !wasConnected) {
                    subscribed = false
                    subscribe()
                    refreshHistory()
                }
                wasConnected = connected
            }
        }
        jobs += scope.launch { gateway.events.collect(::onEvent) }
    }

    /** 画面から外れたら呼ぶ。購読を解除する（裏ではイベントを絞る。FR-122）。 */
    fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        if (subscribed) {
            subscribed = false
            scope.launch { runCatching { gateway.request("sessions.messages.unsubscribe", keyParams()) } }
        }
    }

    private suspend fun subscribe() {
        runCatching {
            gateway.request("sessions.messages.subscribe", buildJsonObject {
                put("key", sessionKey)
                put("agentId", agentId)
                put("includeApprovals", true)
            })
            subscribed = true
        }
    }

    suspend fun refreshHistory() {
        runCatching {
            gateway.request("chat.history", buildJsonObject {
                put("sessionKey", sessionKey)
                put("agentId", agentId)
                put("limit", HISTORY_LIMIT)
            }).asObject()
        }.onSuccess { root ->
            val messages = root?.array("messages").orEmpty()
                .mapIndexedNotNull { i, el -> Parse.message(el as? JsonObject ?: return@mapIndexedNotNull null, i) }
                .filter { it.parts.isNotEmpty() }
            val inFlight = root?.obj("inFlightRun")
            val sessionInfo = Parse.session(root?.obj("sessionInfo"))
            val queued = root?.obj("pendingInputs")?.array("items").orEmpty().mapNotNull { el ->
                val item = el as? JsonObject ?: return@mapNotNull null
                val runId = item.string("runId")?.removeSuffix(":user") ?: return@mapNotNull null
                if (item.string("state") != "queued") return@mapNotNull null
                val text = (item["message"] as? JsonObject)?.let { Parse.contentParts(it["content"]) }
                    ?.filterIsInstance<MessagePart.Text>()?.joinToString("\n") { it.markdown }
                    ?: (item["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                PendingInput(runId, text.orEmpty(), local = false)
            }
            _state.update { current ->
                // 生成中の応答は、手元の delta の方が新しければそれを残す
                val live = inFlight?.string("runId")?.let { runId ->
                    val fetched = inFlight.string("text").orEmpty()
                    val local = current.live?.takeIf { it.runId == runId }?.text.orEmpty()
                    LiveRun(runId, if (local.length > fetched.length) local else fetched)
                }
                // 履歴に取り込まれた送信と、Gateway が受け付け待ちにしている送信は手元の一覧から外す
                val known = messages.mapNotNull { it.runId }.toSet() + queued.map { it.runId }
                val localPending = current.pending.filter { it.local && it.runId !in known }
                val running = inFlight != null || sessionInfo?.running == true
                // 実行の始まりは、最後の利用者のメッセージの時刻（無ければ今）
                val startedAt = when {
                    !running -> null
                    else -> {
                        // 実行中の応答は最後の利用者のメッセージから始まっている。途中のツールの通知などで手元に入った「今」より前ならそちらを使う
                        val lastUser = messages.lastOrNull { it.role == ChatMessage.Role.USER }?.timestamp
                        listOfNotNull(lastUser, current.runStartedAt).minOrNull() ?: System.currentTimeMillis()
                    }
                }
                current.copy(
                    sessionId = root?.string("sessionId") ?: current.sessionId,
                    messages = messages,
                    pending = localPending + queued,
                    runStartedAt = startedAt,
                    activity = if (running) current.activity else null,
                    live = live,
                    running = inFlight != null || sessionInfo?.running == true,
                    loading = false,
                    error = null,
                )
            }
        }.onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }

    /** 連続する通知をまとめて取り直す。 */
    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(REFRESH_DEBOUNCE_MS)
            refreshHistory()
        }
    }

    private fun onEvent(event: GatewayEvent) {
        val payload = event.payload.asObject() ?: return
        val key = payload.string("sessionKey") ?: payload.string("key") ?: payload.obj("session")?.string("key")
        if (key != sessionKey) return
        when (event.name) {
            "chat" -> onChatEvent(payload)
            "session.message" -> scheduleRefresh()
            // ツールの実行の途中経過。今のツール名を出し、確定した行は履歴から取り直す
            "session.tool" -> {
                val data = payload.obj("data")
                val name = listOf("name", "toolName", "tool").firstNotNullOfOrNull { data?.string(it) ?: payload.string(it) }
                _state.update {
                    it.copy(
                        running = true,
                        runStartedAt = it.runStartedAt ?: System.currentTimeMillis(),
                        activity = name?.let(RunActivity::Tool) ?: it.activity,
                    )
                }
                scheduleRefresh()
            }
            "sessions.changed" -> {
                val row = Parse.session(payload.obj("session")) ?: return
                _state.update { it.copy(running = row.running || it.live != null) }
            }
        }
    }

    /** chat の流れ（`ref-schema/logs-chat.ts` の ChatEvent）。 */
    private fun onChatEvent(payload: JsonObject) {
        val runId = payload.string("runId") ?: return
        when (payload.string("state")) {
            "status" -> _state.update {
                it.copy(
                    running = true,
                    runStartedAt = it.runStartedAt ?: System.currentTimeMillis(),
                    activity = payload.string("phase")?.let(RunActivity::Phase) ?: it.activity,
                )
            }
            "delta" -> _state.update { current ->
                val snapshot = (payload["message"] as? JsonObject)?.let { Parse.contentParts(it["content"]) }
                    ?.filterIsInstance<MessagePart.Text>()?.joinToString("\n\n") { it.markdown }
                val deltaText = payload.string("deltaText").orEmpty()
                val base = current.live?.takeIf { it.runId == runId }?.text.orEmpty()
                val text = when {
                    snapshot != null -> snapshot
                    payload.bool("replace") == true -> deltaText
                    else -> base + deltaText
                }
                current.copy(
                    live = LiveRun(runId, text),
                    running = true,
                    runStartedAt = current.runStartedAt ?: System.currentTimeMillis(),
                    activity = null,
                )
            }
            "final", "aborted", "error" -> {
                _state.update {
                    it.copy(
                        running = false,
                        runStartedAt = null,
                        activity = null,
                        error = if (payload.string("state") == "error") payload.string("errorMessage") else it.error,
                    )
                }
                scope.launch {
                    refreshHistory()
                    _state.update { if (it.live?.runId == runId) it.copy(live = null) else it }
                }
            }
        }
    }

    /**
     * 送信。待たずに次々送れる（steer: 実行中なら今の実行に差し込み、止まっていれば新しく始める）。
     * 実行中に送ったものは「受け付け待ち」に出し、会話に取り込まれるまでキャンセルできる。
     * 止まっているときは利用者のメッセージをすぐ会話に加える（FR-021）。確定は履歴の取り直しで行う。
     */
    suspend fun send(text: String, images: List<OutgoingImage>, idempotencyKey: String) {
        val queuedBehindRun = _state.value.running
        val optimistic = ChatMessage(
            id = "local-$idempotencyKey",
            role = ChatMessage.Role.USER,
            parts = buildList {
                if (text.isNotBlank()) add(MessagePart.Text(text))
                images.forEach { add(MessagePart.Image(null, it.base64, it.mimeType)) }
            },
            timestamp = System.currentTimeMillis(),
            runId = idempotencyKey,
            isError = false,
            truncated = false,
        )
        _state.update {
            if (queuedBehindRun) it.copy(pending = it.pending + PendingInput(idempotencyKey, text, local = true), error = null)
            else it.copy(messages = it.messages + optimistic, running = true, runStartedAt = System.currentTimeMillis(), activity = null, error = null)
        }
        try {
            gateway.request("chat.send", buildJsonObject {
                put("sessionKey", sessionKey)
                put("agentId", agentId)
                put("message", text)
                put("idempotencyKey", idempotencyKey)
                put("queueMode", "steer")
                if (images.isNotEmpty()) {
                    put("attachments", buildJsonArray {
                        images.forEach { img ->
                            add(buildJsonObject {
                                put("type", "image")
                                put("mimeType", img.mimeType)
                                img.fileName?.let { put("fileName", it) }
                                put("content", img.base64)
                            })
                        }
                    })
                }
            })
            if (queuedBehindRun) scheduleRefresh()
        } catch (e: Exception) {
            _state.update { s ->
                s.copy(
                    messages = s.messages.filterNot { it.id == optimistic.id },
                    pending = s.pending.filterNot { it.runId == idempotencyKey },
                    running = if (queuedBehindRun) s.running else false,
                    error = e.message,
                )
            }
            throw e
        }
    }

    /** 受け付け待ちの送信を取り消す（Control UI と同じく chat.abort に discardPendingInput を付ける）。 */
    suspend fun cancelPending(runId: String) {
        _state.update { s -> s.copy(pending = s.pending.filterNot { it.runId == runId }) }
        try {
            gateway.request("chat.abort", buildJsonObject {
                put("sessionKey", sessionKey)
                put("agentId", agentId)
                put("runId", runId)
                put("discardPendingInput", true)
            })
        } finally {
            refreshHistory()
        }
    }

    suspend fun abort() {
        gateway.request("chat.abort", buildJsonObject {
            put("sessionKey", sessionKey)
            put("agentId", agentId)
        })
    }

    /** 省略された長い応答の全文（`chat.message.get`）。 */
    suspend fun fullMessage(messageId: String): ChatMessage? =
        gateway.request("chat.message.get", buildJsonObject {
            put("sessionKey", sessionKey)
            put("agentId", agentId)
            put("messageId", messageId)
        }).asObject()?.obj("message")?.let { Parse.message(it, 0) }

    private fun keyParams() = buildJsonObject {
        put("key", sessionKey)
        put("agentId", agentId)
    }

    private companion object {
        const val HISTORY_LIMIT = 200
        const val REFRESH_DEBOUNCE_MS = 250L
    }
}

/** 会話を key ごとに 1 つだけ持つ（画面の再生成で作り直さない）。 */
class ChatRepository(private val gateway: GatewayClient, private val scope: CoroutineScope) {
    private val sessions = mutableMapOf<String, ChatSession>()

    @Synchronized
    fun session(sessionKey: String, agentId: String): ChatSession =
        sessions.getOrPut(sessionKey) { ChatSession(gateway, scope, sessionKey, agentId) }
}
