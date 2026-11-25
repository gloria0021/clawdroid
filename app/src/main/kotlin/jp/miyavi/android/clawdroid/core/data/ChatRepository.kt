package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.gateway.GatewayEvent
import jp.miyavi.android.clawdroid.core.gateway.array
import jp.miyavi.android.clawdroid.core.gateway.asObject
import jp.miyavi.android.clawdroid.core.gateway.bool
import jp.miyavi.android.clawdroid.core.gateway.obj
import jp.miyavi.android.clawdroid.core.gateway.string
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    /** まだ会話に取り込まれていない送信。取り消せるもの（[PendingInput.cancellable]）と、送信済みで読まれ待ちのものがある。 */
    val pending: List<PendingInput> = emptyList(),
    /** 今の実行が始まった時刻（考え中の経過時間に使う）。実行していなければ null。 */
    val runStartedAt: Long? = null,
    /** 今の実行の段階（status の phase、または実行中のツール名）。 */
    val activity: RunActivity? = null,
    val activeRunId: String? = null,
    val usageByRun: Map<String, RunOutputUsage> = emptyMap(),
    internal val completedRunIds: List<String> = emptyList(),
) {
    val reportedOutputTokens: Long? get() = usageByRun[activeRunId ?: live?.runId]?.tokens

    /** OpenClaw の手元のキューにあり、取り消せる送信（入力欄の上のピル）。 */
    val queuedInputs: List<PendingInput> get() = pending.filter { it.cancellable }

    /** 送信済みで、まだ会話に取り込まれていない送信（会話の末尾に「未読」の吹き出しで出す）。 */
    val sentInputs: List<PendingInput> get() = pending.filterNot { it.cancellable }
}

/** 考え中の表示に出す、今していること。 */
sealed interface RunActivity {
    data class Phase(val phase: String) : RunActivity
    data class Tool(val name: String) : RunActivity
    /** コンテキストを圧縮している（agent の compaction の流れ）。 */
    data object Compacting : RunActivity
}

/**
 * 会話に取り込まれていない送信。`local` は応答（ack）を待っている手元の送信、そうでなければ Gateway の pendingInputs（state = queued）。
 * `runId` は chat.send の idempotencyKey と同じで、キャンセル（chat.abort + discardPendingInput）に使う。
 *
 * [cancellable] は Gateway が「OpenClaw のキューにある」と示した送信（`queued: true`）だけ。実行中の会話へ差し込み済み・差し込みの
 * 順番待ちの送信は、Gateway からも取り下げられないので送信済みとして扱う（ピルに取り消しを出さない）。
 */
data class PendingInput(
    val runId: String,
    val text: String,
    val local: Boolean,
    val hasAttachments: Boolean = false,
    val cancellable: Boolean = false,
    /** 吹き出しに出す中身（文字と画像）。 */
    val parts: List<MessagePart> = emptyList(),
)

/** 送る画像（Base64 にしたもの）。 */
/** 送る添付（画像かファイル）。画像以外は Gateway が保存して、記録に添付のリンクとして出す。 */
data class OutgoingImage(val mimeType: String, val base64: String, val fileName: String?) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

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
    /** 履歴の取り直しを 1 本ずつ流し、待っているあいだに重なった頼みは 1 回にまとめる。 */
    private val refreshLock = Mutex()
    private val refreshRequests = AtomicLong()
    @Volatile private var refreshCovered = 0L
    private var subscribed = false
    /** アプリが裏にあるあいだ（文字ごとの流れを止めている。spec 006 FR-004）。 */
    @Volatile private var paused = false

    /** アプリが裏に回った。開いていれば購読を止める（電池のため。全会話の状態の変化は別で届く）。 */
    fun pause() {
        if (paused) return
        paused = true
        if (subscribed) {
            subscribed = false
            scope.launch { runCatching { gateway.request("sessions.messages.unsubscribe", keyParams()) } }
        }
    }

    /** アプリが前に戻った。開いていれば購読し直し、裏にいたあいだの分を取り直す。 */
    fun resume() {
        if (!paused) return
        paused = false
        if (jobs.isEmpty()) return
        scope.launch {
            if (gateway.state.value is ConnectionState.Connected) {
                subscribe()
                refreshHistory()
            }
        }
    }

    /** 画面に出たら呼ぶ。履歴の取得と購読を始める。 */
    fun open() {
        if (jobs.isNotEmpty()) return
        jobs += scope.launch {
            var wasConnected = false
            gateway.state.collect { state ->
                val connected = state is ConnectionState.Connected
                if (connected && !wasConnected && !paused) {
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

    /**
     * 履歴を取り直す。取り直しの最中に頼まれた分は、終わってから 1 回だけ取り直す（通知・実行の終わり・再接続が重なっても
     * 同じ履歴を何度も解析して一覧を差し替えない）。解析は呼び元によらずメインスレッドの外で行う。
     */
    suspend fun refreshHistory() {
        val request = refreshRequests.incrementAndGet()
        refreshLock.withLock {
            // 頼んだあとに始まった取り直しが済んでいれば、その結果に含まれている
            if (refreshCovered >= request) return
            val covers = refreshRequests.get()
            withContext(Dispatchers.Default) { fetchHistory() }
            refreshCovered = covers
        }
    }

    private suspend fun fetchHistory() {
        val requestedState = _state.value
        runCatching {
            gateway.request("chat.history", buildJsonObject {
                put("sessionKey", sessionKey)
                put("agentId", agentId)
                put("limit", HISTORY_LIMIT)
            }).asObject()
        }.onSuccess { root ->
            val messages = root?.array("messages").orEmpty()
                .mapIndexedNotNull { i, el -> Parse.message(el as? JsonObject ?: return@mapIndexedNotNull null, i) }
                .filter { it.parts.isNotEmpty() || it.modelChange != null }
            val reportedRun = root?.obj("inFlightRun")
            val reportedRunId = reportedRun?.string("runId")
            val replayUsage = reportedRun?.array("events").orEmpty().mapNotNull { it.asObject()?.let(::parseRunOutputUsage) }
            val sessionInfo = Parse.session(root?.obj("sessionInfo"))
            val queued = root?.obj("pendingInputs")?.array("items").orEmpty().mapNotNull { el ->
                val item = el as? JsonObject ?: return@mapNotNull null
                val runId = item.string("runId")?.removeSuffix(":user") ?: return@mapNotNull null
                if (item.string("state") != "queued") return@mapNotNull null
                val text = (item["message"] as? JsonObject)?.let { Parse.contentParts(it["content"]) }
                    ?.filterIsInstance<MessagePart.Text>()?.joinToString("\n") { it.markdown }
                    ?: (item["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val message = item.obj("message")
                val hasAttachments = message?.let { Parse.contentParts(it["content"]).any { part -> part is MessagePart.Image } ||
                    it.obj("__openclaw")?.array("media").orEmpty().isNotEmpty() } == true
                val parts = message?.let { m ->
                    val media = m.obj("__openclaw")?.array("media").orEmpty().mapNotNull { el ->
                        val obj = el.asObject() ?: return@mapNotNull null
                        val url = obj.string("url") ?: return@mapNotNull null
                        if (obj.string("kind") == "image" || obj.string("contentType")?.startsWith("image/") == true) MessagePart.Image(url = url, base64 = null, mimeType = obj.string("contentType")) else null
                    }
                    media + Parse.contentParts(m["content"])
                } ?: listOfNotNull(text?.takeIf(String::isNotBlank)?.let { MessagePart.Text(it) })
                PendingInput(runId, text.orEmpty(), local = false, hasAttachments = hasAttachments, cancellable = item.bool("queued") == true, parts = parts)
            }
            _state.update { current ->
                val reportedRunEnded = reportedRunId != null && reportedRunId in current.completedRunIds
                val inFlight = reportedRun.takeUnless { reportedRunEnded }
                val runChanged = current.activeRunId != requestedState.activeRunId ||
                    current.completedRunIds != requestedState.completedRunIds
                // 先に届いたライブ通知を古い履歴で上書きしない。完了後は実行 ID のない
                // セッション概要だけで推論中に戻さない（概要は完了直後に古いことがある）。
                val running = if (runChanged) current.running else inFlight != null ||
                    (!reportedRunEnded && sessionInfo?.running == true &&
                        (current.running || current.completedRunIds.isEmpty()))
                // 生成中の応答は、手元の delta の方が新しければそれを残す
                val live = inFlight?.string("runId")?.let { runId ->
                    val fetched = inFlight.string("text").orEmpty()
                    val local = current.live?.takeIf { it.runId == runId }?.text.orEmpty()
                    LiveRun(runId, if (local.length > fetched.length) local else fetched)
                }
                // 途中の文（ツールの前の一言など）が会話の記録に入ったら、生成中の表示からは外す（Control UI と同じ。二重に出さない）
                fun LiveRun.withoutCommitted(): LiveRun = copy(text = withoutCommitted(text, runId, messages))
                // 履歴に取り込まれた送信と、Gateway が受け付け待ちにしている送信は手元の一覧から外す
                val known = messages.mapNotNull { it.runId }.toSet() + queued.map { it.runId }
                val localPending = current.pending.filter { it.local && it.runId !in known }
                // 実行の始まりは、最後の利用者のメッセージの時刻（無ければ今）
                val activeRunId = if (!running) null else if (runChanged) current.activeRunId
                    else inFlight?.string("runId") ?: current.activeRunId
                val startedAt = when {
                    !running -> null
                    else -> {
                        // 実行中の応答はその実行の利用者のメッセージから始まっている。途中のツールの通知などで手元に入った「今」より前ならそちらを使う。
                        // 別の実行のメッセージ（履歴に残らない /compact などで、前の送信が最後の利用者のメッセージになる）は使わない
                        val runUser = messages.lastOrNull { it.role == ChatMessage.Role.USER }
                            ?.takeIf { activeRunId != null && it.runId == activeRunId }?.timestamp
                        listOfNotNull(runUser, current.runStartedAt).minOrNull() ?: System.currentTimeMillis()
                    }
                }
                current.copy(
                    sessionId = root?.string("sessionId") ?: current.sessionId,
                    messages = withoutTranscriptEchoes(withoutDuplicateResets(messages)),
                    pending = localPending + queued.map { item ->
                        val mine = current.pending.firstOrNull { it.runId == item.runId }
                        item.copy(
                            hasAttachments = item.hasAttachments || mine?.hasAttachments == true,
                            // 手元で送った画像は、Gateway の一覧より先に中身があるので、それを使う
                            parts = if (mine != null && mine.parts.any { it is MessagePart.Image && it.base64 != null }) mine.parts else item.parts,
                        )
                    }.distinctBy { it.runId },
                    runStartedAt = startedAt,
                    activity = if (running) current.activity else null,
                    activeRunId = activeRunId,
                    usageByRun = replayUsage.fold(current.usageByRun) { acc, usage -> acc.withUsage(usage) },
                    live = (if (runChanged) current.live else live)?.withoutCommitted(),
                    running = running,
                    loading = false,
                    error = null,
                )
            }
        }.onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }

    /**
     * 生成中の文から、もう会話の記録に入った部分を除く。生成中の文は実行全体の文をつないだもの（途中の一言 + 続き）なので、
     * この実行で記録されたエージェントの文を、先頭から順に取り除く（Control UI と同じく二重に出さない）。
     */
    private fun withoutCommitted(text: String, runId: String, messages: List<ChatMessage>): String {
        val byRun = messages.filter { it.role == ChatMessage.Role.ASSISTANT && it.runId == runId }
        // 記録に runId が無いときは、最後の利用者のメッセージより後の文
        val replies = byRun.ifEmpty { messages.takeLastWhile { it.role != ChatMessage.Role.USER }.filter { it.role == ChatMessage.Role.ASSISTANT } }
            .map { it.text.trim() }
            .filter(String::isNotEmpty)
        var rest = text.trimStart()
        for (reply in replies) {
            if (rest.startsWith(reply)) rest = rest.removePrefix(reply).trimStart()
        }
        return if (rest.length == text.trimStart().length) text else rest
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
        if (event.name == "agent") {
            if (key != null && key != sessionKey) return
            if (payload.string("agentId")?.let { it != agentId } == true) return
            if (payload.string("stream") == "compaction") {
                onCompactionEvent(payload, keyed = key != null)
                return
            }
            val usage = parseRunOutputUsage(payload) ?: return
            _state.update { current ->
                if (key == null && usage.runId != current.activeRunId) current
                else current.copy(
                    activeRunId = current.activeRunId ?: usage.runId.takeIf { current.running },
                    usageByRun = current.usageByRun.withUsage(usage),
                )
            }
            return
        }
        if (key != sessionKey) return
        when (event.name) {
            "chat" -> onChatEvent(payload)
            "session.message" -> scheduleRefresh()
            // ツールの実行の途中経過。今のツール名を出し、確定した行は履歴から取り直す
            "session.tool" -> {
                val data = payload.obj("data")
                val name = listOf("name", "toolName", "tool").firstNotNullOfOrNull { data?.string(it) ?: payload.string(it) }
                val runId = payload.string("runId") ?: data?.string("runId")
                _state.update {
                    if (!it.running || (runId != null &&
                            (runId in it.completedRunIds || (it.activeRunId != null && it.activeRunId != runId)))) return@update it
                    it.copy(
                        running = true,
                        runStartedAt = it.runStartedAt ?: System.currentTimeMillis(),
                        activity = name?.let(RunActivity::Tool) ?: it.activity,
                    )
                }
                scheduleRefresh()
            }
            "sessions.changed" -> scheduleRefresh()
        }
    }

    /**
     * コンテキストの圧縮の始まりと終わり（`stream = compaction`、data.phase = start / end）。
     * 実行中の考え中の表示を「圧縮しています」に替え、終われば戻す。要約は履歴の取り直しで区切りとして入る。
     */
    private fun onCompactionEvent(payload: JsonObject, keyed: Boolean) {
        val runId = payload.string("runId")
        val phase = payload.obj("data")?.string("phase")
        _state.update {
            if (!it.running || (runId != null && runId in it.completedRunIds)) return@update it
            if (!keyed && runId != it.activeRunId) return@update it
            when (phase) {
                "start" -> it.copy(activity = RunActivity.Compacting)
                "end" -> if (it.activity == RunActivity.Compacting) it.copy(activity = null) else it
                else -> it
            }
        }
        if (phase == "end") scheduleRefresh()
    }

    /** chat の流れ（`ref-schema/logs-chat.ts` の ChatEvent）。 */
    private fun onChatEvent(payload: JsonObject) {
        val runId = payload.string("runId") ?: return
        when (payload.string("state")) {
            "status" -> _state.update {
                if (runId in it.completedRunIds) return@update it
                it.copy(
                    running = true,
                    runStartedAt = it.runStartedAt ?: System.currentTimeMillis(),
                    activity = if (it.activity == RunActivity.Compacting) it.activity
                        else payload.string("phase")?.let(RunActivity::Phase) ?: it.activity,
                    activeRunId = runId,
                )
            }
            "delta" -> _state.update { current ->
                if (runId in current.completedRunIds) return@update current
                val snapshot = (payload["message"] as? JsonObject)?.let { Parse.contentParts(it["content"]) }
                    ?.filterIsInstance<MessagePart.Text>()?.joinToString("\n\n") { it.markdown }
                val deltaText = payload.string("deltaText").orEmpty()
                val base = current.live?.takeIf { it.runId == runId }?.text.orEmpty()
                val text = when {
                    snapshot != null -> snapshot
                    payload.bool("replace") == true -> deltaText
                    else -> base + deltaText
                }.let { text -> withoutCommitted(text, runId, current.messages) }
                current.copy(
                    live = LiveRun(runId, text),
                    activeRunId = runId,
                    running = true,
                    runStartedAt = current.runStartedAt ?: System.currentTimeMillis(),
                    activity = null,
                )
            }
            "final", "aborted", "error" -> {
                _state.update {
                    if (runId in it.completedRunIds) return@update it
                    val completed = it.copy(completedRunIds = (it.completedRunIds + runId).takeLast(COMPLETED_RUN_LIMIT))
                    if (it.activeRunId != null && it.activeRunId != runId) return@update completed
                    completed.copy(
                        running = false,
                        runStartedAt = null,
                        activeRunId = null,
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
                images.filter { it.isImage }.forEach { add(MessagePart.Image(null, it.base64, it.mimeType)) }
            },
            timestamp = System.currentTimeMillis(),
            runId = idempotencyKey,
            isError = false,
            truncated = false,
        )
        _state.update {
            if (queuedBehindRun) it.copy(pending = it.pending + PendingInput(idempotencyKey, text, local = true, hasAttachments = images.isNotEmpty(), parts = optimistic.parts), error = null)
            else it.copy(
                messages = it.messages + optimistic, activeRunId = idempotencyKey, running = true, runStartedAt = System.currentTimeMillis(),
                // CLI の圧縮は進みの通知が来ないので、`/compact` を送った時点で圧縮中にする
                activity = if (isCompactCommand(text)) RunActivity.Compacting else null,
                error = null,
            )
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
                                put("type", if (img.isImage) "image" else "file")
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
    suspend fun cancelPending(runId: String): Boolean = try {
            val result = gateway.request("chat.abort", buildJsonObject {
                put("sessionKey", sessionKey)
                put("agentId", agentId)
                put("runId", runId)
                put("discardPendingInput", true)
            }).asObject()
            val withdrawn = result?.bool("aborted") == true && result.array("runIds")?.contains(kotlinx.serialization.json.JsonPrimitive(runId)) == true
            if (withdrawn) _state.update { s -> s.copy(pending = s.pending.filterNot { it.runId == runId }) }
            withdrawn
        } finally {
            refreshHistory()
        }

    /**
     * 中断。今動いている実行だけを止め、待っている送信は残す（止めたあとに順に送られる。Claude Code と同じ）。
     * runId を付けない chat.abort は待っている送信まで取り消すので、実行の ID が分かるときは必ず付ける
     * （docs/concepts/queue.md）。分からないとき（ほかの端末で始まった実行を拾えていない）だけ会話ごと止める。
     */
    suspend fun abort() {
        val runId = _state.value.activeRunId
        gateway.request("chat.abort", buildJsonObject {
            put("sessionKey", sessionKey)
            put("agentId", agentId)
            runId?.let { put("runId", it) }
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
        const val COMPLETED_RUN_LIMIT = 32
    }
}

/** 会話を key ごとに 1 つだけ持つ（画面の再生成で作り直さない）。 */
class ChatRepository(private val gateway: GatewayClient, private val scope: CoroutineScope) {
    private val sessions = mutableMapOf<String, ChatSession>()

    @Synchronized
    fun session(sessionKey: String, agentId: String): ChatSession =
        sessions.getOrPut(sessionKey) { ChatSession(gateway, scope, sessionKey, agentId) }.also { if (background) it.pause() }

    private var background = false

    /** アプリの前後の切り替え（spec 006 FR-004）。 */
    @Synchronized
    fun setBackground(background: Boolean) {
        this.background = background
        sessions.values.forEach { if (background) it.pause() else it.resume() }
    }
}

/** `/compact`（指示付きも）を送ったか。 */
internal fun isCompactCommand(text: String): Boolean = text.trim().let { it == "/compact" || it.startsWith("/compact ") }
