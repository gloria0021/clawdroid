package jp.miyavi.android.sumahoclaw.feature.chat

import jp.miyavi.android.sumahoclaw.core.gateway.string
import jp.miyavi.android.sumahoclaw.core.gateway.obj
import jp.miyavi.android.sumahoclaw.core.gateway.array
import jp.miyavi.android.sumahoclaw.core.data.ContextBreakdownParser
import jp.miyavi.android.sumahoclaw.core.data.ContextBreakdown
import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.sumahoclaw.core.data.Agent
import jp.miyavi.android.sumahoclaw.core.data.AgentsState
import jp.miyavi.android.sumahoclaw.core.data.ChatMessage
import jp.miyavi.android.sumahoclaw.core.data.ClaudeCliUsageRepository
import jp.miyavi.android.sumahoclaw.core.data.ChatRepository
import jp.miyavi.android.sumahoclaw.core.data.ChatSession
import jp.miyavi.android.sumahoclaw.core.data.ChatState
import jp.miyavi.android.sumahoclaw.core.data.HostStats
import jp.miyavi.android.sumahoclaw.core.data.ModelCatalog
import jp.miyavi.android.sumahoclaw.core.data.ModelChange
import jp.miyavi.android.sumahoclaw.core.data.OutgoingImage
import jp.miyavi.android.sumahoclaw.core.data.Parse
import jp.miyavi.android.sumahoclaw.core.data.ProviderUsage
import jp.miyavi.android.sumahoclaw.core.data.usesClaudeCli
import jp.miyavi.android.sumahoclaw.core.data.SessionListState
import jp.miyavi.android.sumahoclaw.core.data.SessionSetting
import jp.miyavi.android.sumahoclaw.core.data.SessionSummary
import jp.miyavi.android.sumahoclaw.core.data.SlashCommand
import jp.miyavi.android.sumahoclaw.core.data.SessionsRepository
import jp.miyavi.android.sumahoclaw.core.database.DraftDao
import jp.miyavi.android.sumahoclaw.core.database.DraftEntity
import jp.miyavi.android.sumahoclaw.core.database.ReadingPositionDao
import jp.miyavi.android.sumahoclaw.core.database.ReadingPositionEntity
import jp.miyavi.android.sumahoclaw.core.gateway.ConnectionState
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayNotConnectedException
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayRpcException
import jp.miyavi.android.sumahoclaw.core.gateway.asObject
import jp.miyavi.android.sumahoclaw.core.gateway.bool
import jp.miyavi.android.sumahoclaw.core.settings.SettingsStore
import jp.miyavi.android.sumahoclaw.core.settings.visible
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
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 会話の画面に出す 1 行（表示の準備を済ませたもの。AGENTS.md 1: 項目の中で整形しない）。 */
@androidx.compose.runtime.Immutable
data class MessageUi(
    val message: ChatMessage,
    val timeLabel: String?,
    val elapsedMs: Long? = null,
    /** この行の上に出すモデルの切り替え。 */
    val modelChange: ModelChange? = null,
    /** 本文（Text の部分）を解析した結果。部分と同じ順。スクロール中に解析しないよう、ViewModel で先に作る。 */
    val markdown: List<com.mikepenz.markdown.model.State> = emptyList(),
)

/** セッションのシートの前回の値（プロバイダの使用枠・ホストの数字・セッションごとの内訳）。 */
class SheetUsageCache {
    private val providerValues = MutableStateFlow<List<ProviderUsage>?>(null)
    val providerUpdates = providerValues.asStateFlow()
    var providers: List<ProviderUsage>?
        get() = providerValues.value
        set(value) { providerValues.value = value }
    @Volatile var host: HostStats? = null
    @Volatile private var breakdown: Pair<String, ContextBreakdown>? = null

    fun breakdown(sessionKey: String?): ContextBreakdown? = breakdown?.takeIf { it.first == sessionKey }?.second
    fun putBreakdown(sessionKey: String, value: ContextBreakdown) { breakdown = sessionKey to value }
}

/** 入力欄の状態。 */
data class ComposerState(
    val text: String = "",
    val images: List<PendingImage> = emptyList(),
    /** 引用とコピーしたテキスト（添付の列にタイルで並べる。マスター指定 2026-11-26）。 */
    val texts: List<PendingText> = emptyList(),
    val sending: Boolean = false,
    val sessionOptions: Map<SessionSetting, String> = emptyMap(),
    val updatingSettings: Boolean = false,
    /**
     * 保存中のセッションの設定（先に見せる。value が null なら既定に戻す。マスター指定 2026-11-26: 反映が遅い回線でも、
     * 切り替わったかが分かるように）。Gateway の値を読み直したら外す。
     */
    val pendingSetting: Pair<SessionSetting, String?>? = null,
    /** 保存できた設定と、その回数（その場所の印を一瞬チェックにする合図）。 */
    val settingSaved: Pair<SessionSetting, Int>? = null,
    /** 新しいセッションの作業フォルダ（null ならエージェントの既定）。 */
    val newSessionCwd: String? = null,
)

data class PendingImage(val uri: Uri, val image: OutgoingImage)

/**
 * 文字の添付。引用（吹き出しの文字を選んで「プロンプトに入力」）は本文の前に Markdown の引用で、
 * コピーしたテキスト（長い貼り付け）は .txt の添付で送る（マスター指定 2026-11-26）。
 */
data class PendingText(val id: String, val kind: Kind, val text: String) {
    enum class Kind { QUOTE, PASTE }
}

/** 長い貼り付けを添付にする境目（この文字数以上で、かつ改行を含むとき。1 行の URL やコマンドは本文に入れる）。 */
internal const val LONG_PASTE_CHARS = 100

/** 貼り付けた文字を添付にするか。 */
internal fun isLongPaste(text: CharSequence): Boolean = text.length >= LONG_PASTE_CHARS && '\n' in text

/** 送る本文。引用を Markdown の引用にして先頭に置く（複数なら空行で分ける）。 */
internal fun withQuotes(text: String, quotes: List<String>): String {
    if (quotes.isEmpty()) return text
    val block = quotes.joinToString("\n\n") { q -> q.trim().lines().joinToString("\n") { "> $it" } }
    return if (text.isBlank()) block else "$block\n\n$text"
}

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
    /** 会話の一覧の行（古い順）。 */
    val rows: List<ChatRow> = emptyList(),
    /** サイドパネルに出すエージェント（設定の並びと表示。1 体ならタブを出さない）。 */
    val sidebarAgents: List<Agent> = agents.agents,
) {
    val selectedAgent: Agent? get() = agents.agents.firstOrNull { it.id == selectedAgentId }

    /**
     * 新しい会話を始められるか。Gateway につながっていて、エージェントが読み込めている（サイドパネルの守りと同じ条件）。
     * つながった直後のエージェントの読み込み中も、まだ始められない。
     */
    val readyForNewChat: Boolean get() = connection is ConnectionState.Connected && agents.agents.isNotEmpty()
}

/** 一度だけ伝える出来事。 */
sealed interface ChatEvent {
    data object WorktreeCreating : ChatEvent
    data class Error(val message: String?) : ChatEvent
    /** セッションの設定を保存できなかった（元の値に戻した）。同じ値で再試行できる。 */
    data class SettingFailed(val message: String?, val setting: SessionSetting, val value: String?) : ChatEvent
    data object SendFailed : ChatEvent
    /** 長い貼り付けを添付にした（Snackbar の「入力欄に戻す」で戻せる）。 */
    data class PastedAsAttachment(val id: String) : ChatEvent
    data object PendingAlreadyTaken : ChatEvent
    data object AttachmentTooLarge : ChatEvent
    /** 削除したが、ワークツリーは使用中などで残った。 */
    data class WorktreePreserved(val branch: String?) : ChatEvent
    /** マージの依頼が済み、並行開発を終えた。 */
    data object WorktreeMerged : ChatEvent
    /** マージの依頼が済まなかった（並行開発はそのまま）。 */
    data object WorktreeMergeUnfinished : ChatEvent
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
    private val claudeUsage: ClaudeCliUsageRepository,
    private val settings: SettingsStore,
    private val drafts: DraftDao,
    private val positions: ReadingPositionDao,
    private val contentResolver: ContentResolver,
    private val timeFormatter: (Long) -> String,
    /** 開いている会話を知らせ、通知から開く行き先を受ける（spec 006。無ければ使わない）。 */
    private val foreground: jp.miyavi.android.sumahoclaw.core.notify.ForegroundState? = null,
    /** エディターで直した「コピーしたテキスト」（spec 008 US5。タイルの中身を差し替える）。 */
    editorResults: kotlinx.coroutines.flow.Flow<jp.miyavi.android.sumahoclaw.feature.editor.EditorResult>? = null,
) : ViewModel() {
    /** 通知から会話を開いた（起動時の「前回の会話を戻す」で上書きしない）。 */
    @Volatile private var openedExplicitly = false
    private val visible = MutableStateFlow(false)
    /** 作業フォルダごとの今のブランチ（サイドパネルの行に出す）。 */
    val headBranches: kotlinx.coroutines.flow.StateFlow<Map<String, String>> get() = sessions.headBranches

    /** 作業フォルダごとの未コミットの変更量（サイドパネルに出す）。 */
    val folderDiffs: kotlinx.coroutines.flow.StateFlow<Map<String, jp.miyavi.android.sumahoclaw.core.data.DiffTotals>> get() = sessions.folderDiffs

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

    // 整形・Markdown の解析・行のまとめはメインスレッドの外で行う（AGENTS.md 1）。履歴を取り直しても、
    // 変わっていない行は前のインスタンスを使い回し、表示済みの行を組み立て直さない
    private val chatRows = chatState
        .map { it?.messages.orEmpty() }
        .distinctUntilChanged()
        .runningFold(emptyList<ChatRow>()) { previous, messages -> buildChatRows(messages, previous, timeFormatter, markdownCache::parse) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<ChatScreenState> = combine(
        combine(gateway.state, sessions.agents, sessions.sessions, settings.agentLayout) { c, a, s, l -> Quad(c, a, s, l) },
        selectedAgentId,
        selectedSessionKey,
        chatState,
        chatRows,
    ) { (connection, agents, lists, layout), agentId, sessionKey, chat, rows ->
        ChatScreenState(
            connection = connection,
            agents = agents,
            selectedAgentId = agentId,
            sessionsByAgent = lists,
            currentSession = sessionKey?.let { key -> lists[agentId]?.sessions?.firstOrNull { it.key == key } ?: sessions.find(key) },
            currentSessionKey = sessionKey,
            chat = chat,
            rows = rows,
            sidebarAgents = layout.visible(agents.agents),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatScreenState())

    private var draftJob: Job? = null
    /** セッションを開くたびに増える。後から届いた古い切り替えで上書きしないため。 */
    private var sessionGeneration = 0
    private var settingsJob: Job? = null
    val sheetCache = SheetUsageCache()

    init {
        editorResults?.let { flow ->
            viewModelScope.launch {
                flow.collect { r ->
                    if (r is jp.miyavi.android.sumahoclaw.feature.editor.EditorResult.PasteEdited) replacePastedText(r.textId, r.text)
                }
            }
        }
    }

    /** タイルの中身を差し替える（送る前のものだけ。送ったあとのものは変えない）。 */
    fun replacePastedText(id: String, text: String) = _composer.update { c ->
        c.copy(texts = c.texts.map { if (it.id == id && it.kind == PendingText.Kind.PASTE) it.copy(text = text) else it })
    }

    init {
        // 「マージを依頼して終える」の結果を知らせる。終えたセッションを開いていれば新規チャットに戻す
        viewModelScope.launch {
            sessions.worktreeFinishes.collect { result ->
                when (result) {
                    is jp.miyavi.android.sumahoclaw.core.data.WorktreeFinish.Merged -> {
                        if (selectedSessionKey.value == result.session.key) newChat()
                        _events.tryEmit(ChatEvent.WorktreeMerged)
                    }
                    is jp.miyavi.android.sumahoclaw.core.data.WorktreeFinish.Unmerged -> _events.tryEmit(ChatEvent.WorktreeMergeUnfinished)
                }
            }
        }
        // プロバイダの使用枠は、セッションのシートを開いたときだけ取る（アプリを開いただけでホストの claude を動かさない。
        // マスター指定 2026-11-26。CJ レビュー）。Claude Code CLI の行は、取れたところで加わる
        // 前回の選択を戻す（SC-008）。無ければ Gateway の既定のエージェント
        viewModelScope.launch {
            val saved = settings.selection.first()
            val agents = sessions.agents.first { !it.loading && it.agents.isNotEmpty() }
            if (openedExplicitly) return@launch
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
        // 通知から開く会話（spec 006 R7）と、画面で開いている会話を知らせる（その会話の応答は通知しない。FR-011）
        foreground?.let { fg ->
            viewModelScope.launch {
                fg.openRequests.collect { target ->
                    when (target) {
                        is jp.miyavi.android.sumahoclaw.core.notify.OpenTarget.Session -> {
                            fg.consumed()
                            openByKey(target.agentId, target.sessionKey)
                        }
                        // ランチャーのショートカット（spec 007 R3）
                        is jp.miyavi.android.sumahoclaw.core.notify.OpenTarget.NewChat -> {
                            fg.consumed()
                            openNewChat(target.agentId, target.cwd)
                        }
                        else -> Unit
                    }
                }
            }
            viewModelScope.launch {
                combine(visible, selectedSessionKey) { shown, key -> key.takeIf { shown } }.collect { fg.openSessionKey.value = it }
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
        _composer.update { it.copy(updatingSettings = true, pendingSetting = setting to value) }
        settingsJob = viewModelScope.launch {
            // setOption は保存のあと一覧を読み直してから戻るので、外した時点で本物の値がそろっている
            var saved = false
            try {
                sessions.setOption(chat.sessionKey, chat.agentId, setting, value)
                saved = true
            } catch (_: TimeoutCancellationException) {
                _events.tryEmit(ChatEvent.SettingFailed(null, setting, value))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.tryEmit(ChatEvent.SettingFailed(error.message, setting, value))
            } finally {
                if (currentChat.value === chat) {
                    _composer.update {
                        it.copy(
                            updatingSettings = false,
                            pendingSetting = null,
                            settingSaved = if (saved) setting to ((it.settingSaved?.second ?: 0) + 1) else it.settingSaved,
                        )
                    }
                }
            }
        }
    }

    /**
     * プロバイダの使用枠。全エージェントのプロバイダを 1 つにまとめて出し、今のモデルのプロバイダを先頭にする
     * （別のプロバイダのエージェントに渡す前に、そちらの残りも確かめられるように。マスター指定 2026-11-26）。
     * Gateway の分（`models.authStatus` / `usage.status`）を先に出し、Claude Code CLI の分はホストの CLI から取れ次第加える。
     * Claude Code CLI は Gateway の値を使わない（取れなければ行を出さない）。
     */
    suspend fun loadProviderUsage(): List<ProviderUsage> = withContext(Dispatchers.Default) {
        val gatewayUsage = gatewayProviderUsage()
        val current = state.value
        val draftModel = _composer.value.sessionOptions[SessionSetting.MODEL]
        val cliAgent = claudeCliAgent(current, draftModel, gatewayUsage)
        val others = gatewayUsage.filterNot { "claude-cli" in it.ids }
        if (cliAgent == null) return@withContext sortedForCurrent(others).also { sheetCache.providers = it }
        // CLI を待つあいだも、ほかのプロバイダを先に出す（前回の Claude の行があれば残して、高さを変えない）
        val previousClaude = sheetCache.providers?.firstOrNull { "claude-cli" in it.ids }
        sheetCache.providers = sortedForCurrent(others + listOfNotNull(previousClaude))
        sortedForCurrent(others + listOfNotNull(claudeUsage.load(cliAgent))).also { sheetCache.providers = it }
    }

    /** 今のモデルのプロバイダを先頭にする（ほかは Gateway の並びのまま）。 */
    private fun sortedForCurrent(usage: List<ProviderUsage>): List<ProviderUsage> {
        val current = state.value
        val draftModel = _composer.value.sessionOptions[SessionSetting.MODEL]
        val provider = if (usesClaudeCli(current.currentSession, current.selectedAgent, draftModel)) "claude-cli"
        else usageModel(current, draftModel)?.substringBefore('/')?.lowercase()
        if (provider.isNullOrEmpty()) return usage
        return usage.sortedByDescending { p -> p.ids.any { it.equals(provider, ignoreCase = true) } }
    }

    /**
     * Claude Code CLI の使用枠を取りに行くエージェント（そのエージェントのターミナルで CLI を動かす）。
     * 今のエージェントが CLI ならそれ、でなければ CLI を使う最初のエージェント。
     * どのエージェントも使わず Gateway にも CLI の認証が無ければ null（CLI を起動しない）。
     */
    private fun claudeCliAgent(current: ChatScreenState, draftModel: String?, gatewayUsage: List<ProviderUsage>): String? {
        if (usesClaudeCli(current.currentSession, current.selectedAgent, draftModel)) current.selectedAgentId?.let { return it }
        current.agents.agents.firstOrNull { usesClaudeCli(null, it, null) }?.let { return it.id }
        return current.selectedAgentId.takeIf { gatewayUsage.any { "claude-cli" in it.ids } }
    }

    // 解析はメインスレッドの外で（シートを開く動きと重ねない）
    private suspend fun gatewayProviderUsage(): List<ProviderUsage> {
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
        return usage
    }

    private fun usageModel(current: ChatScreenState, override: String?): String? =
        override ?: current.currentSession?.modelReference ?: current.currentSession?.model ?: current.selectedAgent?.defaultModel

    /** 今のセッションのコンテキストの内訳（Control UI と同じ sessions.usage の contextWeight）。取れなければ null。 */
    suspend fun loadContextBreakdown(): ContextBreakdown? {
        val key = state.value.currentSession?.key
        return withContext(Dispatchers.Default) { contextBreakdown() }.also { if (key != null && it != null) sheetCache.putBreakdown(key, it) }
    }

    private suspend fun contextBreakdown(): ContextBreakdown? {
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

    /** Gateway のホストのメモリとディスク（`system.info`）。使えない Gateway や失敗なら null。 */
    suspend fun loadHostStats(): HostStats? {
        if (!gateway.supports("system.info")) return null
        return withContext(Dispatchers.Default) {
            runCatching { Parse.hostStats(gateway.request("system.info", timeoutMs = 5_000).asObject()) }.getOrNull()
        }?.also { sheetCache.host = it }
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

    /** 会話の画面が見えている・隠れた（ほかの画面を重ねた・アプリが裏に回った）。 */
    fun setVisible(shown: Boolean) { visible.value = shown }

    /** key で会話を開く（通知から）。 */
    fun openByKey(agentId: String, sessionKey: String) {
        openedExplicitly = true
        selectedAgentId.value = agentId
        sessions.ensureLoaded(agentId)
        openSession(sessionKey, agentId)
        viewModelScope.launch { settings.selectSession(agentId, sessionKey) }
    }

    fun newChat() {
        val agentId = selectedAgentId.value ?: return
        openSession(null, agentId)
        viewModelScope.launch { settings.selectSession(agentId, null) }
    }

    /** そのエージェントの新しい会話を、作業フォルダを入れて開く（ランチャーのショートカット。spec 007 R3）。 */
    fun openNewChat(agentId: String, cwd: String?) {
        openedExplicitly = true
        selectedAgentId.value = agentId
        sessions.ensureLoaded(agentId)
        openSession(null, agentId, newSessionCwd = cwd)
        viewModelScope.launch { settings.selectSession(agentId, null) }
    }

    /** セッション履歴の並べ方（更新順 / 作業フォルダ別）。 */
    val sessionGrouping: StateFlow<Map<String, jp.miyavi.android.sumahoclaw.core.settings.SessionGrouping>> = settings.sessionGrouping
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** エージェントごとに覚える（チャット用は更新順、開発用はフォルダ別、のように分けられる）。 */
    fun setSessionGrouping(agentId: String, v: jp.miyavi.android.sumahoclaw.core.settings.SessionGrouping) {
        viewModelScope.launch { settings.setSessionGrouping(agentId, v) }
    }

    /** 新しいセッションの作業フォルダを選ぶ（null で既定）。 */
    fun setNewSessionCwd(cwd: String?) = _composer.update { it.copy(newSessionCwd = cwd?.trim()?.takeIf(String::isNotEmpty)) }

    /** フォルダ選択の中身（[path] が null なら Gateway の既定の場所）。 */
    suspend fun listDir(path: String?): jp.miyavi.android.sumahoclaw.core.data.DirListing? =
        runCatching { sessions.listDir(path) }.getOrNull()

    /** 新しいセッションで選べる作業フォルダの候補。 */
    suspend fun folderCandidates(): List<String> = selectedAgentId.value?.let { runCatching { sessions.folderCandidates(it) }.getOrNull() }.orEmpty()

    fun setPinned(session: SessionSummary, pinned: Boolean) = mutate { sessions.setPinned(session, pinned) }

    suspend fun diff(session: SessionSummary): jp.miyavi.android.sumahoclaw.core.data.SessionDiff? =
        runCatching { sessions.diff(session) }.getOrNull()

    /** ⋮ のメニューに出す合計の +/−（中身は取らない）。 */
    /** 並行開発のシートの、元にするブランチの候補。一覧が取れなければ、変更量から今のブランチだけ返す。 */
    suspend fun branches(session: SessionSummary): jp.miyavi.android.sumahoclaw.core.data.RepoBranches? {
        val cwd = session.cwd ?: return null
        runCatching { sessions.branches(cwd) }.getOrNull()?.takeIf { it.branches.isNotEmpty() }?.let { return it }
        val head = diffSummary(session)?.branch ?: return null
        return jp.miyavi.android.sumahoclaw.core.data.RepoBranches(emptyList(), head = head, default = null)
    }

    suspend fun diffSummary(session: SessionSummary): jp.miyavi.android.sumahoclaw.core.data.SessionDiff? =
        runCatching { sessions.diff(session, summaryOnly = true) }.getOrNull()

    /** 既存のセッションからワークツリーのセッションを分けて作り、開く。 */
    fun createWorktree(parent: SessionSummary, name: String?, baseRef: String?, fork: Boolean) {
        val agentId = parent.agentId ?: selectedAgentId.value ?: return
        viewModelScope.launch {
            _events.tryEmit(ChatEvent.WorktreeCreating)
            val key = runCatching { sessions.createWorktree(parent, agentId, name, baseRef, fork) }
                .onFailure { _events.tryEmit(ChatEvent.Error(it.message)) }
                .getOrNull() ?: return@launch
            selectedAgentId.value = agentId
            openSession(key, agentId)
            settings.selectSession(agentId, key)
        }
    }

    /** 並行開発のシートで、同じ名前と重ならない名前を出すための、使えない名前。 */
    suspend fun takenWorktreeNames(parent: SessionSummary): Set<String>? =
        runCatching { sessions.takenWorktreeNames(parent) }.getOrNull()

    /** アーカイブ済みのワークツリーのセッションを戻して開く（Gateway がワークツリーも復元する）。 */
    fun restoreWorktree(session: SessionSummary) {
        val agentId = session.agentId ?: selectedAgentId.value ?: return
        viewModelScope.launch {
            runCatching { sessions.setArchived(session, false) }
                .onFailure { _events.tryEmit(ChatEvent.Error(it.message)); return@launch }
            selectedAgentId.value = agentId
            openSession(session.key, agentId)
            settings.selectSession(agentId, session.key)
        }
    }

    private suspend fun applyAgent(agentId: String, sessionKey: String?) {
        selectedAgentId.value = agentId
        sessions.ensureLoaded(agentId)
        openSession(sessionKey, agentId)
    }

    /** [newSessionCwd] は新しい会話（[sessionKey] が null）で使う作業フォルダ。下書きを読み終えてから入れる（読み込みが入力欄を作り直すため）。 */
    private fun openSession(sessionKey: String?, agentId: String, newSessionCwd: String? = null) {
        sessionGeneration++
        if (sessionKey == selectedSessionKey.value && currentChat.value?.agentId == agentId && sessionKey != null) return
        currentChat.value?.close()
        selectedSessionKey.value = sessionKey
        currentChat.value = sessionKey?.let { chats.session(it, agentId) }?.also { it.open() }
        loadDraft(newSessionCwd)
    }

    private fun draftKey(): String = selectedSessionKey.value ?: "new:${selectedAgentId.value}"

    private fun loadDraft(newSessionCwd: String? = null) {
        draftJob?.cancel()
        val key = draftKey()
        draftJob = viewModelScope.launch {
            val text = drafts.get(key)?.text.orEmpty()
            _composer.value = ComposerState(text = text, newSessionCwd = newSessionCwd?.trim()?.takeIf(String::isNotEmpty))
        }
    }

    fun onTextChange(text: String) = _composer.update { it.copy(text = text) }

    /** 送ったメッセージの文を入力欄へ写す。書きかけがあれば消さずに後ろへ足す。 */
    fun reuse(text: String) = _composer.update { it.copy(text = if (it.text.isBlank()) text else "${it.text.trimEnd()}\n\n$text") }

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            val image = withContext(Dispatchers.IO) { readAttachment(uri) }
                ?: return@launch run { _events.tryEmit(ChatEvent.AttachmentTooLarge) }
            // 同じ画像は二重に入れない（一覧の key が重なるため）
            _composer.update { c -> if (c.images.any { it.uri == uri }) c else c.copy(images = c.images + PendingImage(uri, image)) }
        }
    }

    fun removeImage(uri: Uri) = _composer.update { c -> c.copy(images = c.images.filterNot { it.uri == uri }) }

    /** 吹き出しの文字を選んで「プロンプトに入力」。引用のタイルにする（長さに関係なく）。 */
    fun addQuote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _composer.update { it.copy(texts = it.texts + PendingText(java.util.UUID.randomUUID().toString(), PendingText.Kind.QUOTE, trimmed)) }
    }

    /** 長い貼り付けを、コピーしたテキストのタイルにする。 */
    fun addPastedText(text: String) {
        val id = java.util.UUID.randomUUID().toString()
        _composer.update { it.copy(texts = it.texts + PendingText(id, PendingText.Kind.PASTE, text)) }
        _events.tryEmit(ChatEvent.PastedAsAttachment(id))
    }

    fun removeText(id: String) = _composer.update { c -> c.copy(texts = c.texts.filterNot { it.id == id }) }

    /** タイルをやめて、文字として入力欄に展開する（書きかけがあれば後ろへ足す）。 */
    fun expandText(id: String) = _composer.update { c ->
        val item = c.texts.firstOrNull { it.id == id } ?: return@update c
        val body = if (item.kind == PendingText.Kind.QUOTE) withQuotes("", listOf(item.text)) else item.text
        c.copy(texts = c.texts - item, text = if (c.text.isBlank()) body else "${c.text.trimEnd()}\n\n$body")
    }

    fun appendVoiceText(text: String) = _composer.update { c ->
        c.copy(text = if (c.text.isBlank()) text else c.text.trimEnd() + " " + text)
    }

    /**
     * 送信。前の送信の応答を待たずに次々送れる（実行中は steer で差し込む）。入力欄はすぐ空にする。
     */
    fun send() {
        val composer = _composer.value
        val typed = composer.text.trim()
        if (composer.updatingSettings || (typed.isEmpty() && composer.images.isEmpty() && composer.texts.isEmpty())) return
        val pastes = composer.texts.filter { it.kind == PendingText.Kind.PASTE }
        val quoted = withQuotes(typed, composer.texts.filter { it.kind == PendingText.Kind.QUOTE }.map { it.text })
        // コピーしたテキストは .txt の添付で送る。新しいチャットの最初の 1 通は添付を運べないので本文の後ろに入れる
        val text = if (currentChat.value == null && pastes.isNotEmpty()) {
            (listOf(quoted) + pastes.map { it.text }).filter(String::isNotBlank).joinToString("\n\n")
        } else quoted
        val agentId = selectedAgentId.value ?: return
        val idempotencyKey = GatewayClient.newIdempotencyKey()
        val draft = draftKey()
        val chat = currentChat.value
        // 新規チャットは最初の 1 通でセッションを作るので、作り終わるまでは次を送らない
        if (chat == null && composer.sending) return
        _composer.value = ComposerState(
            sending = chat == null,
            sessionOptions = if (chat == null) composer.sessionOptions else emptyMap(),
            newSessionCwd = if (chat == null) composer.newSessionCwd else null,
        )
        viewModelScope.launch {
            try {
                if (chat == null) {
                    val key = sessions.create(
                        agentId, cwd = composer.newSessionCwd, message = text, idempotencyKey = idempotencyKey,
                        options = composer.sessionOptions,
                    )
                    if (key != null) {
                        openSession(key, agentId)
                        settings.selectSession(agentId, key)
                    }
                } else {
                    // 長い貼り付けの変換（UTF-8・Base64）は、入力欄を空にしてから裏のスレッドで（CJ レビュー 2026-11-26）。
                    // 新しいチャットの最初の 1 通は本文に入れるので変換しない
                    // 押した順に Gateway へ渡す（長いログの変換を待つあいだに、あとの短い補足が先に届かないように。CJ レビュー
                    // 2026-11-26）。Mutex は待った順に渡し、launch は押した時点で lock まで進むので、押した順がそのまま守られる
                    sendOrder.withLock {
                        val textFiles = withContext(Dispatchers.Default) {
                            pastes.mapIndexed { i, p ->
                                OutgoingImage("text/plain", Base64.encodeToString(p.text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP), "pasted-text-${i + 1}.txt")
                            }
                        }
                        chat.send(text, composer.images.map { it.image } + textFiles, idempotencyKey)
                    }
                }
                drafts.delete(draft)
                _composer.update { it.copy(sending = false) }
            } catch (e: Exception) {
                // 失敗したら、入力欄が空のときだけ戻す（あとから書いた文を上書きしない）
                _composer.update { current ->
                    if (current.text.isBlank() && current.images.isEmpty() && current.texts.isEmpty()) composer.copy(sending = false) else current.copy(sending = false)
                }
                _events.tryEmit(ChatEvent.SendFailed)
            }
        }
    }

    /** 受け付け待ちの送信を取り消す。 */
    fun cancelPending(runId: String) {
        val chat = currentChat.value ?: return
        viewModelScope.launch {
            runCatching { chat.cancelPending(runId) }
                // 押した直後に送られた（キューから出た）ときは取り消せない。ピルは送信済みの吹き出しに移る
                .onSuccess { withdrawn -> if (!withdrawn) _events.tryEmit(ChatEvent.PendingAlreadyTaken) }
                .onFailure { _events.tryEmit(ChatEvent.Error(it.message)) }
        }
    }

    fun restorePending(runId: String) {
        val chat = currentChat.value ?: return
        val input = chat.state.value.pending.firstOrNull { it.runId == runId } ?: return
        if (input.text.isBlank() || input.hasAttachments || !input.cancellable || !restoringPending.add(runId)) return
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

    /** 既存の会話への送信を、押した順に 1 つずつ渡す。 */
    private val sendOrder = kotlinx.coroutines.sync.Mutex()

    fun abort() {
        val chat = currentChat.value ?: return
        viewModelScope.launch { runCatching { chat.abort() }.onFailure { _events.tryEmit(ChatEvent.Error(it.message)) } }
    }

    fun rename(session: SessionSummary, label: String) = mutate { sessions.rename(session, label) }
    fun setArchived(session: SessionSummary, archived: Boolean) = mutate { sessions.setArchived(session, archived) }

    /** 並行開発を終える。中身はアーカイブで、ワークツリーは Gateway がスナップショットを取ってから片付ける（解除すれば戻る）。 */
    fun finishWorktree(session: SessionSummary) = mutate {
        sessions.setArchived(session, true)
        if (selectedSessionKey.value == session.key) newChat()
    }

    /**
     * 並行開発を採用する: エージェントにマージと push を頼み、済んだら終える（結果は [ChatEvent.WorktreeMerged] /
     * [ChatEvent.WorktreeMergeUnfinished]）。[request] は依頼の文（画面側で文言から作る）。
     */
    fun mergeAndFinishWorktree(session: SessionSummary, request: String) = mutate {
        val agentId = session.agentId ?: return@mutate
        chats.session(session.key, agentId).send(request, emptyList(), java.util.UUID.randomUUID().toString())
        sessions.finishAfterMerge(session)
    }

    /** セッションを削除する。開いていたセッションなら新規チャットに戻す。 */
    fun delete(session: SessionSummary) = mutate {
        val preserved = sessions.delete(session)
        if (selectedSessionKey.value == session.key) newChat()
        if (preserved != null) _events.tryEmit(ChatEvent.WorktreePreserved(preserved.branch ?: session.worktreeBranch))
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

    /** 画像かファイルを読む。上限を超える・読めないときは null。 */
    private fun readAttachment(uri: Uri): OutgoingImage? = runCatching {
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        val limit = if (mime.startsWith("image/")) MAX_IMAGE_BYTES else MAX_FILE_BYTES
        val bytes = contentResolver.openInputStream(uri)?.use { it.readNBytes(limit + 1) } ?: return null
        if (bytes.size > limit) return null
        OutgoingImage(mime, Base64.encodeToString(bytes, Base64.NO_WRAP), displayName(uri))
    }.getOrNull()

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    override fun onCleared() {
        currentChat.value?.close()
    }

    private companion object {
        const val DRAFT_SAVE_DEBOUNCE_MS = 400L
        /** Gateway の既定の画像の上限（hello-ok の policy.attachments.maxImageBytes）。 */
        const val MAX_IMAGE_BYTES = 6 * 1024 * 1024
        // shortcut: 既定の 1 フレーム 25 MiB に base64（4/3 倍）で収まる 15 MB に固定。hello-ok の policy を読むようにしたら置き換える
        const val MAX_FILE_BYTES = 15 * 1024 * 1024
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
        message.parts.filterIsInstance<jp.miyavi.android.sumahoclaw.core.data.MessagePart.Text>().map { part ->
            cache.getOrPut(part.markdown) { com.mikepenz.markdown.model.parseMarkdown(part.markdown) }
        }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
