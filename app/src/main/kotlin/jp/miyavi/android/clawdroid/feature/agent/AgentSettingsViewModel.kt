package jp.miyavi.android.clawdroid.feature.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.AgentConfig
import jp.miyavi.android.clawdroid.core.data.AgentConfigSource
import jp.miyavi.android.clawdroid.core.data.AgentFileEntry
import jp.miyavi.android.clawdroid.core.data.SkillSource
import jp.miyavi.android.clawdroid.core.data.SkillState
import jp.miyavi.android.clawdroid.core.data.SkillStatus
import jp.miyavi.android.clawdroid.core.gateway.GatewayRpcException
import jp.miyavi.android.clawdroid.feature.files.MarkdownBlock
import jp.miyavi.android.clawdroid.feature.files.markdownBlocks
import jp.miyavi.android.clawdroid.feature.files.runCatchingNonCancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 読み込みの状態。[unsupported] は Gateway がその手段を持たない（spec 004 FR-041）。 */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Loaded<T>(val value: T) : Load<T>
    data class Failed(val unsupported: Boolean) : Load<Nothing>
}

/** スキルの詳細のシート（SKILL.md の解析は一度だけ）。 */
sealed interface SkillDetail {
    val skill: SkillStatus
    data class Loading(override val skill: SkillStatus) : SkillDetail
    data class Ready(override val skill: SkillStatus, val blocks: List<MarkdownBlock>) : SkillDetail
    data class Failed(override val skill: SkillStatus) : SkillDetail
}

data class AgentSettingsUiState(
    val agentId: String,
    /** エージェントの一覧に見つからなければ null（読み込み中も null）。 */
    val agent: Agent? = null,
    val agentsLoading: Boolean = true,
    /** openclaw.json のエージェントの定義（概要。FR-010）。見つからなければ Loaded(null)。 */
    val config: Load<AgentConfig?> = Load.Loading,
    val files: Load<List<AgentFileEntry>> = Load.Loading,
    val skills: Load<List<SkillStatus>> = Load.Loading,
    /** 切り替え中のスキルの、先に見せている値（FR-033）。 */
    val pendingToggles: Map<String, Boolean> = emptyMap(),
    val skillDetail: SkillDetail? = null,
    /** 名前と絵文字を保存中（FR-013）。 */
    val identitySaving: Boolean = false,
    /** 選べるモデル（シートを開くときに読む）。 */
    val modelCatalog: Load<jp.miyavi.android.clawdroid.core.data.ModelCatalog?>? = null,
    /** 既定のモデル・思考を保存中。 */
    val configSaving: Boolean = false,
) {
    /** 見せる有効・無効（切り替え中は先に見せる値）。 */
    fun enabled(skill: SkillStatus): Boolean = pendingToggles[skill.skillKey] ?: skill.enabled

    val fileCount: Int? get() = (files as? Load.Loaded)?.value?.count { !it.missing }
    val skillCounts: Pair<Int, Int>? get() = (skills as? Load.Loaded)?.value?.let { list -> list.count { it.state == SkillState.ENABLED } to list.size }
}

sealed interface AgentSettingsEvent {
    data class Toggled(val skill: SkillStatus, val enabled: Boolean) : AgentSettingsEvent
    data class ToggleFailed(val message: String?) : AgentSettingsEvent
    data object IdentitySaved : AgentSettingsEvent
    data class IdentityFailed(val message: String?) : AgentSettingsEvent
    data object ConfigSaved : AgentSettingsEvent
    data class ConfigFailed(val message: String?) : AgentSettingsEvent
}

/** エージェントのファイルの一覧（ViewModel のテストで偽物に差し替える）。 */
fun interface AgentFilesLoader {
    suspend fun load(agentId: String): List<AgentFileEntry>
}

/**
 * エージェントの設定（spec 004 US1〜US3）。概要・ファイルの一覧・スキル。
 * エージェントは既存の `agents.list` の流れから取り、新しい呼び出しを増やさない（research R7）。
 */
class AgentSettingsViewModel(
    private val agentId: String,
    agents: kotlinx.coroutines.flow.Flow<Pair<List<Agent>, Boolean>>,
    private val files: AgentFilesLoader,
    private val skills: SkillSource,
    private val configs: AgentConfigSource,
    /** Gateway がその手段を持っているか（hello の methods。空なら持っているとみなす）。 */
    private val supports: (String) -> Boolean,
    /** 名前を変えたあとに、エージェントの一覧（サイドパネル・会話の表示）を読み直す。 */
    private val refreshAgents: suspend () -> Unit = {},
) : ViewModel() {
    /** 名前と絵文字を変えられるか（Gateway に `agents.update` がある）。 */
    val canEditIdentity: Boolean get() = supports("agents.update")

    /** 既定のモデルを変えられるか（`agents.update` と `models.list`）。 */
    val canEditModel: Boolean get() = supports("agents.update") && supports("models.list")

    /** 思考の既定を変えられるか（`config.patch`）。 */
    val canEditThinking: Boolean get() = supports("config.patch")

    private val _state = MutableStateFlow(AgentSettingsUiState(agentId))
    val state: StateFlow<AgentSettingsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AgentSettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AgentSettingsEvent> = _events.asSharedFlow()

    private var configJob: Job? = null
    private var filesJob: Job? = null
    private var skillsJob: Job? = null
    private var detailJob: Job? = null

    init {
        viewModelScope.launch {
            agents.collectLatest { (list, loading) ->
                _state.update { it.copy(agent = list.firstOrNull { a -> a.id == agentId }, agentsLoading = loading) }
            }
        }
        loadConfig(quiet = false)
        loadFiles(quiet = false)
        loadSkills(quiet = false)
    }

    /** 画面に戻ってきたとき（ファイルを直したあと）に、表示を崩さず読み直す。 */
    fun refresh() {
        loadConfig(quiet = true)
        loadFiles(quiet = true)
        loadSkills(quiet = true)
    }

    fun retryConfig() = loadConfig(quiet = false)
    fun retryFiles() = loadFiles(quiet = false)

    private fun loadConfig(quiet: Boolean) {
        if (!supports("config.get")) {
            _state.update { it.copy(config = Load.Failed(unsupported = true)) }
            return
        }
        configJob?.cancel()
        if (!quiet || _state.value.config !is Load.Loaded) _state.update { it.copy(config = Load.Loading) }
        configJob = viewModelScope.launch {
            val result = load { configs.load(agentId) }
            _state.update { s -> if (quiet && result is Load.Failed && s.config is Load.Loaded) s else s.copy(config = result) }
        }
    }
    fun retrySkills() = loadSkills(quiet = false)

    private fun loadFiles(quiet: Boolean) {
        if (!supports("agents.files.list")) {
            _state.update { it.copy(files = Load.Failed(unsupported = true)) }
            return
        }
        filesJob?.cancel()
        if (!quiet || _state.value.files !is Load.Loaded) _state.update { it.copy(files = Load.Loading) }
        filesJob = viewModelScope.launch {
            val result = load { files.load(agentId) }
            _state.update { s -> if (quiet && result is Load.Failed && s.files is Load.Loaded) s else s.copy(files = result) }
        }
    }

    private fun loadSkills(quiet: Boolean) {
        if (!supports("skills.status")) {
            _state.update { it.copy(skills = Load.Failed(unsupported = true)) }
            return
        }
        skillsJob?.cancel()
        if (!quiet || _state.value.skills !is Load.Loaded) _state.update { it.copy(skills = Load.Loading) }
        skillsJob = viewModelScope.launch {
            val result = load { skills.status(agentId) }
            _state.update { s -> if (quiet && result is Load.Failed && s.skills is Load.Loaded) s else s.copy(skills = result) }
        }
    }

    /**
     * 有効・無効の切り替え（FR-033）。見た目を先に変え、失敗したら戻して理由を出す。成功したら読み直して状態をそろえる。
     * [announce] が false なら（「元に戻す」から）Snackbar を出さない。
     */
    fun setEnabled(skill: SkillStatus, enabled: Boolean, announce: Boolean = true) {
        if (!supports("skills.update")) {
            _events.tryEmit(AgentSettingsEvent.ToggleFailed(null))
            return
        }
        _state.update { it.copy(pendingToggles = it.pendingToggles + (skill.skillKey to enabled)) }
        viewModelScope.launch {
            val error = try {
                skills.setEnabled(skill.skillKey, enabled)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }
            if (error == null) {
                val refreshed = runCatchingNonCancel { skills.status(agentId) }
                _state.update { s ->
                    s.copy(
                        skills = refreshed?.let { Load.Loaded(it) } ?: s.skills.withEnabled(skill.skillKey, enabled),
                        pendingToggles = s.pendingToggles - skill.skillKey,
                    )
                }
                if (announce) _events.tryEmit(AgentSettingsEvent.Toggled(skill, enabled))
            } else {
                _state.update { it.copy(pendingToggles = it.pendingToggles - skill.skillKey) }
                _events.tryEmit(AgentSettingsEvent.ToggleFailed((error as? GatewayRpcException)?.error?.message))
            }
        }
    }

    /** 名前と絵文字を保存する。成功したら一覧と定義・ファイル（IDENTITY.md も書き直される）を読み直す。 */
    fun saveIdentity(name: String, emoji: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _state.value.identitySaving) return
        _state.update { it.copy(identitySaving = true) }
        viewModelScope.launch {
            val error = try {
                configs.updateIdentity(agentId, trimmed, emoji.trim())
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }
            _state.update { it.copy(identitySaving = false) }
            if (error == null) {
                runCatchingNonCancel { refreshAgents() }
                loadConfig(quiet = true)
                loadFiles(quiet = true)
                _events.tryEmit(AgentSettingsEvent.IdentitySaved)
            } else {
                _events.tryEmit(AgentSettingsEvent.IdentityFailed((error as? GatewayRpcException)?.error?.message))
            }
        }
    }

    /** 選べるモデルを読む（まだ読んでいなければ）。 */
    fun loadModelCatalog() {
        if (_state.value.modelCatalog is Load.Loaded || _state.value.modelCatalog == Load.Loading) return
        _state.update { it.copy(modelCatalog = Load.Loading) }
        viewModelScope.launch {
            val result = load { configs.modelCatalog(agentId) }
            _state.update { it.copy(modelCatalog = result) }
        }
    }

    /** 既定のモデルを変える（null なら全体の既定に戻す）。 */
    fun saveModel(model: String?) = saveConfig { configs.setModel(agentId, model) }

    /** 思考の既定を変える（null なら全体の既定に戻す）。読んだときの設定の版を添える。 */
    fun saveThinking(level: String?) {
        val baseHash = (_state.value.config as? Load.Loaded)?.value?.baseHash
        saveConfig { configs.setThinking(agentId, level, baseHash) }
    }

    /** 保存して、定義とエージェントの一覧を読み直す。失敗も読み直して、他で変わった内容をそろえる。 */
    private fun saveConfig(block: suspend () -> Unit) {
        if (_state.value.configSaving) return
        _state.update { it.copy(configSaving = true) }
        viewModelScope.launch {
            val error = try {
                block()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }
            _state.update { it.copy(configSaving = false) }
            loadConfig(quiet = true)
            runCatchingNonCancel { refreshAgents() }
            _events.tryEmit(
                if (error == null) AgentSettingsEvent.ConfigSaved
                else AgentSettingsEvent.ConfigFailed((error as? GatewayRpcException)?.error?.message),
            )
        }
    }

    /** 「元に戻す」。 */
    fun undo(skill: SkillStatus, enabled: Boolean) = setEnabled(skill, !enabled, announce = false)

    /** スキルの詳細を開く（SKILL.md を読み、Markdown を一度だけ解析する）。 */
    fun openSkill(skill: SkillStatus) {
        detailJob?.cancel()
        val path = skill.filePath
        if (path == null || !supports("skills.files.get")) {
            _state.update { it.copy(skillDetail = SkillDetail.Failed(skill)) }
            return
        }
        _state.update { it.copy(skillDetail = SkillDetail.Loading(skill)) }
        detailJob = viewModelScope.launch {
            val text = runCatchingNonCancel { skills.readSkillFile(agentId, path) }
            val detail = if (text == null) SkillDetail.Failed(skill)
            else SkillDetail.Ready(skill, withContext(Dispatchers.Default) { markdownBlocks(text) })
            _state.update { if (it.skillDetail?.skill?.skillKey == skill.skillKey) it.copy(skillDetail = detail) else it }
        }
    }

    fun closeSkill() {
        detailJob?.cancel()
        _state.update { it.copy(skillDetail = null) }
    }

    private suspend fun <T> load(block: suspend () -> T): Load<T> = try {
        Load.Loaded(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: GatewayRpcException) {
        Load.Failed(unsupported = e.error.message.contains("unknown method", ignoreCase = true))
    } catch (_: Exception) {
        Load.Failed(unsupported = false)
    }

    private fun Load<List<SkillStatus>>.withEnabled(key: String, enabled: Boolean): Load<List<SkillStatus>> =
        (this as? Load.Loaded)?.let { loaded ->
            Load.Loaded(loaded.value.map { s -> if (s.skillKey == key) s.copy(state = if (enabled) SkillState.ENABLED else SkillState.DISABLED) else s })
        } ?: this
}
