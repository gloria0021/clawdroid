package jp.miyavi.android.clawdroid.feature.chat

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
import jp.miyavi.android.clawdroid.core.data.OutgoingImage
import jp.miyavi.android.clawdroid.core.data.SessionListState
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.data.SessionsRepository
import jp.miyavi.android.clawdroid.core.database.DraftDao
import jp.miyavi.android.clawdroid.core.database.DraftEntity
import jp.miyavi.android.clawdroid.core.database.ReadingPositionDao
import jp.miyavi.android.clawdroid.core.database.ReadingPositionEntity
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 会話の画面に出す 1 行（表示の準備を済ませたもの。AGENTS.md 1: 項目の中で整形しない）。 */
data class MessageUi(
    val message: ChatMessage,
    val timeLabel: String?,
)

/** 入力欄の状態。 */
data class ComposerState(
    val text: String = "",
    val images: List<PendingImage> = emptyList(),
    val sending: Boolean = false,
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

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private val chatState = currentChat.flatMapLatest { it?.state ?: flowOf(null) }

    private val formattedMessages = chatState
        .map { chat -> chat?.messages.orEmpty().map { MessageUi(it, it.timestamp?.let(timeFormatter)) } }
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
        viewModelScope.launch {
            val saved = settings.selection.first().selectedSessionKeyByAgent[agentId]
            applyAgent(agentId, saved)
            settings.selectAgent(agentId)
        }
    }

    /** サイドバーのページが変わったとき、一覧だけ先に読み込む（会話はまだ替えない）。 */
    fun preloadAgent(agentId: String) = sessions.ensureLoaded(agentId)

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

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            val image = withContext(Dispatchers.IO) { readImage(uri) } ?: return@launch
            _composer.update { it.copy(images = it.images + PendingImage(uri, image)) }
        }
    }

    fun removeImage(uri: Uri) = _composer.update { c -> c.copy(images = c.images.filterNot { it.uri == uri }) }

    fun appendVoiceText(text: String) = _composer.update { c ->
        c.copy(text = if (c.text.isBlank()) text else c.text.trimEnd() + " " + text)
    }

    fun send() {
        val composer = _composer.value
        val text = composer.text.trim()
        if (composer.sending || (text.isEmpty() && composer.images.isEmpty())) return
        val agentId = selectedAgentId.value ?: return
        val idempotencyKey = GatewayClient.newIdempotencyKey()
        val draft = draftKey()
        _composer.value = ComposerState(sending = true)
        viewModelScope.launch {
            try {
                val chat = currentChat.value
                if (chat == null) {
                    // 新規チャット: 最初のメッセージと一緒にセッションを作る（US3）
                    val key = sessions.create(agentId, cwd = null, message = text, idempotencyKey = idempotencyKey)
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
                // 失敗したら入力を戻す
                _composer.value = composer.copy(sending = false)
                _events.tryEmit(ChatEvent.SendFailed)
            }
        }
    }

    fun abort() {
        val chat = currentChat.value ?: return
        viewModelScope.launch { runCatching { chat.abort() }.onFailure { _events.tryEmit(ChatEvent.Error(it.message)) } }
    }

    fun rename(session: SessionSummary, label: String) = mutate { sessions.rename(session, label) }
    fun setArchived(session: SessionSummary, archived: Boolean) = mutate { sessions.setArchived(session, archived) }
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
