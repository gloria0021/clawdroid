package jp.miyavi.android.clawdroid.feature.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.clawdroid.core.data.Automation
import jp.miyavi.android.clawdroid.core.data.AutomationDraft
import jp.miyavi.android.clawdroid.core.data.AutomationRun
import jp.miyavi.android.clawdroid.core.data.AutomationSource
import jp.miyavi.android.clawdroid.core.data.buildAutomationPatch
import jp.miyavi.android.clawdroid.core.gateway.GatewayRpcException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 失敗の知らせ（Snackbar）。 */
enum class AutomationProblem { LOAD, TOGGLE, SAVE, CONFLICT, RUN, DELETE, NO_PERMISSION }

sealed interface AutomationEvent {
    data class Failed(val problem: AutomationProblem) : AutomationEvent
    data object Saved : AutomationEvent
    data object RunStarted : AutomationEvent
    data object Deleted : AutomationEvent
}

private fun Exception.problem(default: AutomationProblem): AutomationProblem =
    if (this is GatewayRpcException && missingScope) AutomationProblem.NO_PERMISSION else default

// ---- 一覧 ----

data class AutomationListUi(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val jobs: List<Automation> = emptyList(),
)

/** エージェントの自動化の一覧。有効・無効はその場で切り替え、失敗したら戻す。 */
class AutomationsViewModel(private val source: AutomationSource, private val agentId: String) : ViewModel() {
    private val _state = MutableStateFlow(AutomationListUi())
    val state: StateFlow<AutomationListUi> = _state.asStateFlow()
    private val _events = MutableSharedFlow<AutomationEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AutomationEvent> = _events.asSharedFlow()
    private var loadJob: Job? = null

    /** 画面に出るたびに読み直す（詳細で変えた内容を映す）。読み込み済みなら表示はそのまま。 */
    fun refresh() {
        loadJob?.cancel()
        _state.update { if (it.jobs.isEmpty()) it.copy(loading = true, failed = false) else it }
        loadJob = viewModelScope.launch {
            val jobs = try {
                source.list(agentId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _state.update {
                if (jobs != null) AutomationListUi(loading = false, failed = false, jobs = jobs)
                else it.copy(loading = false, failed = it.jobs.isEmpty())
            }
        }
    }

    fun setEnabled(job: Automation, enabled: Boolean) {
        replace(job.copy(enabled = enabled))
        viewModelScope.launch {
            try {
                val updated = source.setEnabled(job, enabled)
                replace(updated ?: job.copy(enabled = enabled))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                replace(job)
                _events.tryEmit(AutomationEvent.Failed(e.problem(AutomationProblem.TOGGLE)))
            }
        }
    }

    private fun replace(job: Automation) = _state.update { s -> s.copy(jobs = s.jobs.map { if (it.id == job.id) job else it }) }
}

// ---- 詳細 ----

enum class AutomationTab { SETTINGS, HISTORY }

data class RunsUi(
    val runs: List<AutomationRun> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val nextOffset: Int? = null,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
)

data class AutomationDetailUi(
    val job: Automation? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** 読み込んだときの設定（変更があるかの比較元）。 */
    val original: AutomationDraft? = null,
    val draft: AutomationDraft? = null,
    val saving: Boolean = false,
    val tab: AutomationTab = AutomationTab.SETTINGS,
    val runs: RunsUi = RunsUi(),
    val confirmDelete: Boolean = false,
    /** 選べるモデル（モデル・思考のメニューを開いたときに読む。読んでいなければ null）。 */
    val models: jp.miyavi.android.clawdroid.feature.agent.Load<jp.miyavi.android.clawdroid.core.data.ModelCatalog?>? = null,
) {
    val dirty: Boolean get() = draft != null && draft != original
    val canSave: Boolean get() = dirty && draft?.valid == true && !saving
}

/** 自動化の詳細（設定の編集・今すぐ実行・削除・実行履歴）。 */
class AutomationDetailViewModel(
    private val source: AutomationSource,
    private val jobId: String,
    /** エージェントの選べるモデル（`models.list`）。 */
    private val loadModels: suspend (agentId: String) -> jp.miyavi.android.clawdroid.core.data.ModelCatalog? = { null },
) : ViewModel() {
    private val _state = MutableStateFlow(AutomationDetailUi())
    val state: StateFlow<AutomationDetailUi> = _state.asStateFlow()
    private val _events = MutableSharedFlow<AutomationEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AutomationEvent> = _events.asSharedFlow()
    private var runsJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.job == null, failed = false) }
        viewModelScope.launch {
            val job = try {
                source.get(jobId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _state.update { s ->
                when {
                    job == null -> s.copy(loading = false, failed = s.job == null)
                    // 編集中なら、書きかけの設定は残して比較元だけ新しくする
                    s.dirty -> s.copy(job = job, loading = false, original = AutomationDraft.of(job))
                    else -> s.copy(job = job, loading = false, original = AutomationDraft.of(job), draft = AutomationDraft.of(job))
                }
            }
        }
    }

    /** 選べるモデルを読む（まだ読んでいなければ。失敗したら次に開いたときに読み直す）。 */
    fun loadModelCatalog() {
        val s = _state.value
        if (s.models is jp.miyavi.android.clawdroid.feature.agent.Load.Loaded || s.models == jp.miyavi.android.clawdroid.feature.agent.Load.Loading) return
        val agentId = s.job?.agentId ?: run {
            // エージェントが分からなければ選べるモデルも分からない（既定と今の値だけを出す）
            _state.update { it.copy(models = jp.miyavi.android.clawdroid.feature.agent.Load.Failed(unsupported = false)) }
            return
        }
        _state.update { it.copy(models = jp.miyavi.android.clawdroid.feature.agent.Load.Loading) }
        viewModelScope.launch {
            val result = try {
                jp.miyavi.android.clawdroid.feature.agent.Load.Loaded(loadModels(agentId))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                jp.miyavi.android.clawdroid.feature.agent.Load.Failed(unsupported = false)
            }
            _state.update { it.copy(models = result) }
        }
    }

    fun selectTab(tab: AutomationTab) {
        _state.update { it.copy(tab = tab) }
        if (tab == AutomationTab.HISTORY && !_state.value.runs.loaded) loadRuns()
    }

    fun edit(draft: AutomationDraft) = _state.update { it.copy(draft = draft) }

    /** 1 項目の編集を確定する（シートの「保存」）。すぐ Gateway に保存し、失敗したら元に戻す。 */
    fun commit(draft: AutomationDraft) {
        edit(draft)
        save()
    }

    /** 変更を捨てて、読み込んだときの設定に戻す。 */
    fun revert() = _state.update { it.copy(draft = it.original) }

    fun save() {
        val s = _state.value
        val job = s.job ?: return
        val before = s.original ?: return
        val after = s.draft ?: return
        if (!s.canSave) return
        val patch = buildAutomationPatch(job, before, after)
        if (patch.isEmpty()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val updated = source.update(job, patch) ?: source.get(jobId)
                _state.update {
                    val fresh = updated ?: job
                    it.copy(job = fresh, saving = false, original = AutomationDraft.of(fresh), draft = AutomationDraft.of(fresh))
                }
                _events.tryEmit(AutomationEvent.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 保存できなかった変更は画面に残さない（読み込んだときの設定に戻す）
                _state.update { it.copy(saving = false, draft = it.original) }
                val conflict = (e as? GatewayRpcException)?.error?.let { err ->
                    err.code.contains("CONFLICT", ignoreCase = true) || err.message.contains("revision", ignoreCase = true)
                } == true
                _events.tryEmit(AutomationEvent.Failed(if (conflict) AutomationProblem.CONFLICT else e.problem(AutomationProblem.SAVE)))
                if (conflict) load()
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val job = _state.value.job ?: return
        _state.update { it.copy(job = job.copy(enabled = enabled)) }
        viewModelScope.launch {
            try {
                val updated = source.setEnabled(job, enabled)
                // 比較元の版も新しくする（書きかけの設定はそのまま）
                if (updated != null) _state.update { it.copy(job = updated) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(job = job) }
                _events.tryEmit(AutomationEvent.Failed(e.problem(AutomationProblem.TOGGLE)))
            }
        }
    }

    fun runNow() {
        viewModelScope.launch {
            try {
                source.runNow(jobId)
                _events.tryEmit(AutomationEvent.RunStarted)
                // 実行中の表示と、終わったあとの履歴を拾う
                load()
                delay(RUN_REFRESH_DELAY_MS)
                load()
                if (_state.value.runs.loaded) loadRuns()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(AutomationEvent.Failed(e.problem(AutomationProblem.RUN)))
            }
        }
    }

    fun askDelete() = _state.update { it.copy(confirmDelete = true) }
    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    fun delete() {
        val job = _state.value.job ?: return
        _state.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            try {
                source.remove(job)
                _events.tryEmit(AutomationEvent.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(AutomationEvent.Failed(e.problem(AutomationProblem.DELETE)))
            }
        }
    }

    fun loadRuns() {
        runsJob?.cancel()
        _state.update { it.copy(runs = it.runs.copy(loading = it.runs.runs.isEmpty(), failed = false)) }
        runsJob = viewModelScope.launch {
            try {
                val page = source.runs(jobId, 0)
                _state.update { it.copy(runs = RunsUi(runs = page.runs, nextOffset = page.nextOffset, loaded = true)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(runs = it.runs.copy(loading = false, failed = it.runs.runs.isEmpty())) }
            }
        }
    }

    /** 履歴の続き（いちばん下まで読んだとき）。 */
    fun loadMoreRuns() {
        val runs = _state.value.runs
        val offset = runs.nextOffset ?: return
        if (runs.loadingMore) return
        _state.update { it.copy(runs = it.runs.copy(loadingMore = true)) }
        runsJob = viewModelScope.launch {
            try {
                val page = source.runs(jobId, offset)
                _state.update { s ->
                    val known = s.runs.runs.map { it.key }.toSet()
                    s.copy(runs = s.runs.copy(runs = s.runs.runs + page.runs.filter { it.key !in known }, nextOffset = page.nextOffset, loadingMore = false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(runs = it.runs.copy(loadingMore = false)) }
            }
        }
    }

    private companion object {
        const val RUN_REFRESH_DELAY_MS = 5_000L
    }
}
