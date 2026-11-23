package jp.miyavi.android.clawdroid.feature.chat

import jp.miyavi.android.clawdroid.core.gateway.string
import jp.miyavi.android.clawdroid.core.gateway.obj
import jp.miyavi.android.clawdroid.core.gateway.array
import jp.miyavi.android.clawdroid.core.data.ContextBreakdownParser
import jp.miyavi.android.clawdroid.core.data.ContextBreakdown
import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.AgentsState
import jp.miyavi.android.clawdroid.core.data.ChatMessage
import jp.miyavi.android.clawdroid.core.data.ChatRepository
import jp.miyavi.android.clawdroid.core.data.ChatSession
import jp.miyavi.android.clawdroid.core.data.ChatState
import jp.miyavi.android.clawdroid.core.data.ModelCatalog
import jp.miyavi.android.clawdroid.core.data.ModelChange
import jp.miyavi.android.clawdroid.core.data.OutgoingImage
import jp.miyavi.android.clawdroid.core.data.Parse
import jp.miyavi.android.clawdroid.core.data.ProviderUsage
import jp.miyavi.android.clawdroid.core.data.SessionListState
import jp.miyavi.android.clawdroid.core.data.SessionSetting
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.data.SlashCommand
import jp.miyavi.android.clawdroid.core.data.SessionsRepository
import jp.miyavi.android.clawdroid.core.database.DraftDao
import jp.miyavi.android.clawdroid.core.database.DraftEntity
import jp.miyavi.android.clawdroid.core.database.ReadingPositionDao
import jp.miyavi.android.clawdroid.core.database.ReadingPositionEntity
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.gateway.GatewayNotConnectedException
import jp.miyavi.android.clawdroid.core.gateway.GatewayRpcException
import jp.miyavi.android.clawdroid.core.gateway.asObject
import jp.miyavi.android.clawdroid.core.gateway.bool
import jp.miyavi.android.clawdroid.core.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 会話の画面に出す 1 行（表示の準備を済ませたもの。AGENTS.md 1: 項目の中で整形しない）。 */
data class MessageUi(
    val message: ChatMessage,
    val timeLabel: String?,
    val elapsedMs: Long? = null,
    /** この行の上に出すモデルの切り替え。 */
    val modelChange: ModelChange? = null,
    /** 本文（Text の部分）を解析した結果。部分と同じ順。スクロール中に解析しないよう、ViewModel で先に作る。 */
    val markdown: List<com.mikepenz.markdown.model.State> = emptyList(),
)

/** 入力欄の状態。 */
data class ComposerState(
    val text: String = "",
    val images: List<PendingImage> = emptyList(),
    val sending: Boolean = false,
    val sessionOptions: Map<SessionSetting, String> = emptyMap(),
    val updatingSettings: Boolean = false,
)

data class PendingImage(val uri: Uri, val image: OutgoingImage)

/** サイドバーと会話の画面の状態。 */
data class ChatScreenState(
    val connection: ConnectionState = ConnectionState.Connecting,
    val agents: AgentsState = AgentsState(),
    val selectedAgentId: String? = null,
    val sessionsByAgent: Map<String, SessionListState> = emptyMap(),
    /** 開いているセッション。null なら新規チャット。 */
    val currentSession: SessionSummary? = null,
    val currentSessionKey: String? = null,
    val chat: ChatState? = null,
    val messages: List<MessageUi> = emptyList(),
) {
    val selectedAgent: Agent? get() = agents.agents.firstOrNull { it.id == selectedAgentId }
}

/** 一度だけ伝える出来事。 */
sealed interface ChatEvent {
    data class Error(val message: String?) : ChatEvent
    data object SendFailed : ChatEvent
    data object PendingAlreadyTaken : ChatEvent
}

/**
 * サイドバー（エージェントのタブとセッション一覧）と、開いている会話をまとめて扱う（US1）。
 * エージェントを切り替えると、サイドバーの一覧も同時に切り替わる（FR-011）。
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ChatViewModel(
    private val gateway: GatewayClient,
    private val sessions: SessionsRepository,
    private val chats: ChatRepository,
    private val settings: SettingsStore,
    private val drafts: DraftDao,
    private val positions: ReadingPositionDao,
    private val contentResolver: ContentResolver,
    private val timeFormatter: (Long) -> String,
) : ViewModel() {
    private val selectedAgentId = MutableStateFlow<String?>(null)
    private val selectedSessionKey = MutableStateFlow<String?>(null)
    private val currentChat = MutableStateFlow<ChatSession?>(null)

    private val _composer = MutableStateFlow(ComposerState())
    val composer: StateFlow<ComposerState> = _composer.asStateFlow()

    /** エージェントごとのスラッシュコマンドの一覧（一度読んだら使い回す）。 */
    private val commandsByAgent = MutableStateFlow<Map<String, List<SlashCommand>>>(emptyMap())
    val slashCommands: StateFlow<List<SlashCommand>> = combine(commandsByAgent, selectedAgentId) { commands, agentId ->
        agentId?.let(commands::get).orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private val chatState = currentChat.flatMapLatest { it?.state ?: flowOf(null) }

    /** 解析済みの本文（同じ文はもう一度解析しない）。 */
    private val markdownCache = MarkdownCache()

    // 整形と Markdown の解析はメインスレッドの外で行い、スクロール中の項目の組み立てを軽くする（AGENTS.md 1）
    private val formattedMessages = chatState
        .map { it?.messages.orEmpty() }
        .distinctUntilChanged()
        .map { messages -> formatMessages(messages, timeFormatter).map { it.copy(markdown = markdownCache.parse(it.message)) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<ChatScreenState> = combine(
        combine(gateway.state, sessions.agents, sessions.sessions) { c, a, s -> Triple(c, a, s) },
        selectedAgentId,
        selectedSessionKey,
        chatState,
        formattedMessages,
    ) { (connection, agents, lists), agentId, sessionKey, chat, messages ->
        ChatScreenState(
            connection = connection,
            agents = agents,
            selectedAgentId = agentId,
            sessionsByAgent = lists,
            currentSession = sessionKey?.let { key -> lists[agentId]?.sessions?.firstOrNull { it.key == key } ?: sessions.find(key) },
            currentSessionKey = sessionKey,
            chat = chat,
            messages = messages,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatScreenState())

    private var draftJob: Job? = null
    /** セッションを開くたびに増える。後から届いた古い切り替えで上書きしないため。 */
    private var sessionGeneration = 0
    private var settingsJob: Job? = null

    init {
        // 前回の選択を戻す（SC-008）。無ければ Gateway の既定のエージェント
        viewModelScope.launch {
            val saved = settings.selection.first()
            val agents = sessions.agents.first { !it.loading && it.agents.isNotEmpty() }
            val agentId = saved.selectedAgentId?.takeIf { id -> agents.agents.any { it.id == id } }
                ?: agents.defaultId?.takeIf { id -> agents.agents.any { it.id == id } }
                ?: agents.agents.first().id
            applyAgent(agentId, saved.selectedSessionKeyByAgent[agentId])
        }
        // 「/」か「$」を打ち始めたら、そのエージェントのコマンド（スキルを含む）の一覧を読む（読めなかったら、次に打ったときに読み直す）
        viewModelScope.launch {
            combine(_composer.map { it.text.startsWith("/") || '$' in it.text }.distinctUntilChanged(), selectedAgentId) { slash, agentId ->
                agentId.takeIf { slash }
            }.distinctUntilChanged().collect { agentId ->
                if (agentId != null && agentId !in commandsByAgent.value) loadSlashCommands(agentId)
            }
        }
        // 下書きは入力を間引いて保存する（FR-028）
        viewModelScope.launch {
            // key は入力した時点のもの（保存までのあいだにセッションを替えても混ざらない）
            _composer.map { draftKey() to it.text }.distinctUntilChanged().debounce(DRAFT_SAVE_DEBOUNCE_MS).collect { (key, text) ->
                if (text.isBlank()) drafts.delete(key) else drafts.upsert(DraftEntity(key, text, System.currentTimeMillis()))
            }
        }
    }

    fun selectAgent(agentId: String) {
        if (agentId == selectedAgentId.value) return
        // 選択は先に確定する（続けて「新しいセッション」を押しても、このエージェントで開く）
        selectedAgentId.value = agentId
        sessions.ensureLoaded(agentId)
        val generation = sessionGeneration
        viewModelScope.launch {
            val saved = settings.selection.first().selectedSessionKeyByAgent[agentId]
            // 読むあいだに別のセッション・新しいチャットが開かれていたら、そちらを優先する
            if (generation == sessionGeneration && selectedAgentId.value == agentId) openSession(saved, agentId)
            settings.selectAgent(agentId)
        }
    }

    /** まだ読んでいないエージェントのセッション履歴を先に読む（サイドパネルで切り替えたときに中身がそろっているように）。 */
    fun preloadAgentSessions() {
        val loaded = sessions.sessions.value
        state.value.agents.agents.forEach { agent -> if (agent.id !in loaded) sessions.ensureLoaded(agent.id) }
    }

    private suspend fun loadSlashCommands(agentId: String) {
        if (!gateway.supports("commands.list")) return
        val commands = try {
            gateway.request("commands.list", buildJsonObject {
                put("agentId", agentId)
                selectedSessionKey.value?.let { put("sessionKey", it) }
                put("scope", "text")
                put("includeArgs", true)
            }).asObject()?.let(Parse::slashCommands)
        } catch (_: TimeoutCancellationException) {
            null
        } catch (_: GatewayNotConnectedException) {
            null
        } catch (_: GatewayRpcException) {
            null
        } ?: return
        commandsByAgent.update { it + (agentId to commands) }
    }

    suspend fun loadModelCatalog(agentId: String, sessionKey: String?): ModelCatalog? {
        if (!gateway.supports("models.list")) return null
        return try {
            val data = gateway.request("models.list", buildJsonObject {
                put("agentId", agentId)
                sessionKey?.let { put("sessionKey", it) }
                put("includeDetails", true)
            }).asObject()
            data?.let(Parse::modelCatalog)
        } catch (_: TimeoutCancellationException) {
            null
        } catch (_: GatewayNotConnectedException) {
            null
        } catch (_: GatewayRpcException) {
            null
        }
    }

    fun updateSessionSetting(setting: SessionSetting, value: String?) {
        val connection = gateway.state.value as? ConnectionState.Connected ?: return
        if (setting == SessionSetting.PERMISSIONS && value == "full" && "operator.admin" !in connection.scopes) return
        val chat = currentChat.value
        if (chat == null) {
            _composer.update { composer ->
                val options = if (value == null) composer.sessionOptions - setting else composer.sessionOptions + (setting to value)
                composer.copy(sessionOptions = if (setting == SessionSetting.MODEL) options - SessionSetting.EFFORT else options)
            }
            return
        }
        if (settingsJob?.isActive == true) return
        _composer.update { it.copy(updatingSettings = true) }
        settingsJob = viewModelScope.launch {
            try {
                sessions.setOption(chat.sessionKey, chat.agentId, setting, value)
            } catch (_: TimeoutCancellationException) {
                _events.tryEmit(ChatEvent.Error(null))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.tryEmit(ChatEvent.Error(error.message))
            } finally {
                if (currentChat.value === chat) _composer.update { it.copy(updatingSettings = false) }
            }
        }
    }

    /**
     * プロバイダの使用枠。Control UI と同じく `models.authStatus` から取る（Claude Code CLI の枠もここに出る）。
     * 使えない Gateway では `usage.status` に戻す。今のセッションのプロバイダを先頭にする。
     */
    suspend fun loadProviderUsage(): List<ProviderUsage> {
        val usage = try {
            when {
                gateway.supports("models.authStatus") -> Parse.authStatusUsage(
                    gateway.request("models.authStatus", timeoutMs = 10_000).asObject() ?: return emptyList(),
                )
                gateway.supports("usage.status") -> loadUsageStatus()
                else -> emptyList()
            }
        } catch (_: TimeoutCancellationException) {
            emptyList()
        } catch (_: GatewayNotConnectedException) {
            emptyList()
        } catch (_: GatewayRpcException) {
            if (gateway.supports("usage.status")) runCatching { loadUsageStatus() }.getOrDefault(emptyList()) else emptyList()
        }
        val provider = state.value.currentSession?.model?.substringBefore('/', "")?.lowercase()
            ?: state.value.selectedAgent?.defaultModel?.substringBefore('/', "")?.lowercase()
        return usage.sortedByDescending { p -> provider != null && provider.isNotEmpty() && p.ids.any { it.equals(provider, ignoreCase = true) } }
    }

    /** 今のセッションのコンテキストの内訳（Control UI と同じ sessions.usage の contextWeight）。取れなければ null。 */
    suspend fun loadContextBreakdown(): ContextBreakdown? {
        val session = state.value.currentSession ?: return null
        val used = session.totalTokens ?: return null
        if (!gateway.supports("sessions.usage")) return null
        return runCatching {
            val root = gateway.request("sessions.usage", buildJsonObject {
                put("key", session.key)
                session.agentId?.let { put("agentId", it) }
                put("limit", 1)
                put("includeContextWeight", true)
            }, timeoutMs = 10_000).asObject()
            val row = root?.array("sessions")?.mapNotNull { it.asObject() }?.firstOrNull { it.string("key") == session.key }
            row?.obj("contextWeight")?.let { ContextBreakdownParser.parse(it, used) }
        }.getOrNull()
    }

    private suspend fun loadUsageStatus(): List<ProviderUsage> {
        repeat(4) { attempt ->
            if (attempt > 0) delay(1_000)
            val usage = gateway.request("usage.status", timeoutMs = 5_000).asObject() ?: return emptyList()
            if (usage.bool("refreshing") != true || attempt == 3) return Parse.providerUsage(usage)
        }
        return emptyList()
    }

    fun selectSession(session: SessionSummary) {
        val agentId = session.agentId ?: selectedAgentId.value ?: return
        selectedAgentId.value = agentId
        openSession(session.key, agentId)
        viewModelScope.launch {
            settings.selectSession(agentId, session.key)
            if (session.unread) runCatching { sessions.markRead(session) }
        }
    }

    fun newChat() {
        val agentId = selectedAgentId.value ?: return
        openSession(null, agentId)
        viewModelScope.launch { settings.selectSession(agentId, null) }
    }

    private suspend fun applyAgent(agentId: String, sessionKey: String?) {
        selectedAgentId.value = agentId
        sessions.ensureLoaded(agentId)
        openSession(sessionKey, agentId)
    }

    private fun openSession(sessionKey: String?, agentId: String) {
        sessionGeneration++
        if (sessionKey == selectedSessionKey.value && currentChat.value?.agentId == agentId && sessionKey != null) return
        currentChat.value?.close()
        selectedSessionKey.value = sessionKey
        currentChat.value = sessionKey?.let { chats.session(it, agentId) }?.also { it.open() }
        loadDraft()
    }

    private fun draftKey(): String = selectedSessionKey.value ?: "new:${selectedAgentId.value}"

    private fun loadDraft() {
        draftJob?.cancel()
        val key = draftKey()
        draftJob = viewModelScope.launch {
            val text = drafts.get(key)?.text.orEmpty()
            _composer.value = ComposerState(text = text)
        }
    }

    fun onTextChange(text: String) = _composer.update { it.copy(text = text) }

    fun replyTo(text: String) {
        val quote = text.lineSequence().joinToString("\n") { if (it.isEmpty()) ">" else "> $it" }
        _composer.update { it.copy(text = "$quote\n\n${it.text}") }
    }

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            val image = withContext(Dispatchers.IO) { readImage(uri) } ?: return@launch
            // 同じ画像は二重に入れない（一覧の key が重なるため）
            _composer.update { c -> if (c.images.any { it.uri == uri }) c else c.copy(images = c.images + PendingImage(uri, image)) }
        }
    }

    fun removeImage(uri: Uri) = _composer.update { c -> c.copy(images = c.images.filterNot { it.uri == uri }) }

    fun appendVoiceText(text: String) = _composer.update { c ->
        c.copy(text = if (c.text.isBlank()) text else c.text.trimEnd() + " " + text)
    }

    /**
     * 送信。前の送信の応答を待たずに次々送れる（実行中は steer で差し込む）。入力欄はすぐ空にする。
     */
    fun send() {
        val composer = _composer.value
        val text = composer.text.trim()
        if (composer.updatingSettings || (text.isEmpty() && composer.images.isEmpty())) return
        val agentId = selectedAgentId.value ?: return
        val idempotencyKey = GatewayClient.newIdempotencyKey()
        val draft = draftKey()
        val chat = currentChat.value
        // 新規チャットは最初の 1 通でセッションを作るので、作り終わるまでは次を送らない
        if (chat == null && composer.sending) return
        _composer.value = ComposerState(sending = chat == null, sessionOptions = if (chat == null) composer.sessionOptions else emptyMap())
        viewModelScope.launch {
            try {
                if (chat == null) {
                    val key = sessions.create(
                        agentId, cwd = null, message = text, idempotencyKey = idempotencyKey,
                        options = composer.sessionOptions,
                    )
                    if (key != null) {
                        openSession(key, agentId)
                        settings.selectSession(agentId, key)
                    }
                } else {
                    chat.send(text, composer.images.map { it.image }, idempotencyKey)
                }
                drafts.delete(draft)
                _composer.update { it.copy(sending = false) }
            } catch (e: Exception) {
                // 失敗したら、入力欄が空のときだけ戻す（あとから書いた文を上書きしない）
                _composer.update { current ->
                    if (current.text.isBlank() && current.images.isEmpty()) composer.copy(sending = false) else current.copy(sending = false)
                }
                _events.tryEmit(ChatEvent.SendFailed)
            }
        }
    }

    /** 受け付け待ちの送信を取り消す。 */
    fun cancelPending(runId: String) {
        val chat = currentChat.value ?: return
        viewModelScope.launch { runCatching { chat.cancelPending(runId) }.onFailure { _events.tryEmit(ChatEvent.Error(it.message)) } }
    }

    fun restorePending(runId: String) {
        val chat = currentChat.value ?: return
        val input = chat.state.value.pending.firstOrNull { it.runId == runId } ?: return
        if (input.text.isBlank() || input.hasAttachments || input.local || !restoringPending.add(runId)) return
        mutate {
            try {
                if (!chat.cancelPending(runId)) {
                    _events.tryEmit(ChatEvent.PendingAlreadyTaken)
                } else if (currentChat.value === chat) {
                    _composer.update { it.copy(text = mergeRestoredDraft(input.text, it.text)) }
                } else {
                    val key = chat.sessionKey
                    drafts.upsert(DraftEntity(key, mergeRestoredDraft(input.text, drafts.get(key)?.text.orEmpty()), System.currentTimeMillis()))
                }
            } finally {
                restoringPending.remove(runId)
            }
        }
    }

    private val restoringPending = mutableSetOf<String>()

    fun abort() {
        val chat = currentChat.value ?: return
        viewModelScope.launch { runCatching { chat.abort() }.onFailure { _events.tryEmit(ChatEvent.Error(it.message)) } }
    }

    fun rename(session: SessionSummary, label: String) = mutate { sessions.rename(session, label) }
    fun setArchived(session: SessionSummary, archived: Boolean) = mutate { sessions.setArchived(session, archived) }

    /** セッションを削除する。開いていたセッションなら新規チャットに戻す。 */
    fun delete(session: SessionSummary) = mutate {
        sessions.delete(session)
        if (selectedSessionKey.value == session.key) newChat()
    }
    fun refreshSessions(agentId: String) = mutate { sessions.refresh(agentId) }
    fun retryHistory() = mutate { currentChat.value?.refreshHistory() }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() }.onFailure { _events.tryEmit(ChatEvent.Error(it.message)) } }
    }

    suspend fun readingPosition(sessionKey: String): ReadingPositionEntity? = positions.get(sessionKey)

    fun saveReadingPosition(sessionKey: String, anchorId: String, offset: Int, atBottom: Boolean) {
        viewModelScope.launch { positions.upsert(ReadingPositionEntity(sessionKey, anchorId, offset, atBottom)) }
    }

    fun reconnect() = gateway.reconnectNow()

    private fun readImage(uri: Uri): OutgoingImage? = runCatching {
        val mime = contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > MAX_IMAGE_BYTES) return null
        OutgoingImage(mime, Base64.encodeToString(bytes, Base64.NO_WRAP), uri.lastPathSegment)
    }.getOrNull()

    override fun onCleared() {
        currentChat.value?.close()
    }

    private companion object {
        const val DRAFT_SAVE_DEBOUNCE_MS = 400L
        /** Gateway の既定の画像の上限（hello-ok の policy.attachments.maxImageBytes）。 */
        const val MAX_IMAGE_BYTES = 6 * 1024 * 1024
    }
}


/**
 * 記録にある利用者の送信時刻と応答時刻から、回答までのおおよその時間を求める。
 * あわせて、モデルが切り替わった行を見つける（Control UI と同じ判定）。
 */
internal fun formatMessages(messages: List<ChatMessage>, timeFormatter: (Long) -> String): List<MessageUi> {
    val promptsByRun = mutableMapOf<String, ChatMessage>()
    var latestPrompt: ChatMessage? = null
    var lastModel: String? = null
    var seenTurn = false
    return messages.map { message ->
        val modelChange = when {
            message.modelChange != null -> message.modelChange.also { lastModel = it.to }
            message.role == ChatMessage.Role.USER -> {
                val selection = message.modelSelection
                // 切り替え前のモデルは、手元で最後に見た選択。無ければ（履歴の途中からなら）記録の直前の選択
                val before = selection?.let { lastModel ?: it.previous.takeIf { seenTurn } }
                if (selection != null) lastModel = selection.current
                seenTurn = true
                if (selection == null || before == null || before == selection.current) null else ModelChange(before, selection.current)
            }
            else -> {
                if (message.role == ChatMessage.Role.ASSISTANT) seenTurn = true
                null
            }
        }
        if (message.role == ChatMessage.Role.USER) {
            latestPrompt = message
            message.runId?.let { promptsByRun[it] = message }
        }
        val elapsed = if (message.role == ChatMessage.Role.ASSISTANT && !message.isError) {
            val runId = message.runId
            val prompt = if (runId == null) latestPrompt else {
                promptsByRun[runId] ?: latestPrompt?.takeIf { it.runId == null }
            }
            val start = prompt?.timestamp
            val end = message.timestamp
            if (start != null && end != null && end >= start) end - start else null
        } else {
            null
        }
        MessageUi(message, message.timestamp?.let(timeFormatter), elapsed, modelChange)
    }
}

/** 本文の Markdown の解析結果を文ごとに覚えておく（解析し直さない）。会話を切り替えても、古い分は数を限って捨てる。 */
internal class MarkdownCache(private val limit: Int = 2_000) {
    private val cache = object : LinkedHashMap<String, com.mikepenz.markdown.model.State>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, com.mikepenz.markdown.model.State>?) = size > limit
    }

    @Synchronized
    fun parse(message: ChatMessage): List<com.mikepenz.markdown.model.State> =
        message.parts.filterIsInstance<jp.miyavi.android.clawdroid.core.data.MessagePart.Text>().map { part ->
            cache.getOrPut(part.markdown) { com.mikepenz.markdown.model.parseMarkdown(part.markdown) }
        }
}
