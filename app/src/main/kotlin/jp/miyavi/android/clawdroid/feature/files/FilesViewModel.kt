package jp.miyavi.android.clawdroid.feature.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.clawdroid.core.data.DownloadResult
import jp.miyavi.android.clawdroid.core.data.DownloadSource
import jp.miyavi.android.clawdroid.core.data.FileDownloads
import jp.miyavi.android.clawdroid.core.data.FileEntry
import jp.miyavi.android.clawdroid.core.data.FileListing
import jp.miyavi.android.clawdroid.core.data.FileRoot
import jp.miyavi.android.clawdroid.core.data.FileSource
import jp.miyavi.android.clawdroid.core.data.OpenResult
import jp.miyavi.android.clawdroid.core.data.OpenedFile
import jp.miyavi.android.clawdroid.core.data.PreviewKind
import jp.miyavi.android.clawdroid.core.data.SaveResult
import jp.miyavi.android.clawdroid.core.database.FileDraftDao
import jp.miyavi.android.clawdroid.core.database.FileDraftEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

/** 一覧の状態。 */
sealed interface ListingState {
    data object Loading : ListingState
    data class Loaded(val listing: FileListing) : ListingState
    data object Failed : ListingState
}

/** 開いたファイルの状態（data-model の移り変わり）。 */
sealed interface OpenedState {
    val path: String
    data class Loading(override val path: String) : OpenedState
    data class Failed(override val path: String, val missing: Boolean) : OpenedState
    data class Ready(
        val file: OpenedFile,
        /** 読むときの色分け（ViewModel で一度だけ作る）。 */
        val spans: List<TokenSpan>,
        /** 編集中の内容（null なら読んでいる）。 */
        val editText: String? = null,
        val cursor: Int = 0,
        /** 編集を始めたときのハッシュ（下書きから戻したときは下書きのもの）。 */
        val baseHash: String? = file.hash,
        val saving: Boolean = false,
        /** 保存したときに Gateway 側で書き換えられていた。 */
        val conflict: Boolean = false,
        /** 下書きから戻した。 */
        val restoredDraft: Boolean = false,
    ) : OpenedState {
        override val path: String get() = file.path
        val editing: Boolean get() = editText != null
        val dirty: Boolean get() = editText != null && editText != file.text
    }
}

data class FilesUiState(
    /** 作業フォルダ（会話に作業フォルダが無ければ null）。 */
    val sessionRoot: FileRoot.Session?,
    val workspaceRoot: FileRoot.Workspace,
    val root: FileRoot,
    val path: String = "",
    val listing: ListingState = ListingState.Loading,
    val opened: OpenedState? = null,
    /** 戻るで変更を捨てるかの確認を出している。 */
    val confirmDiscard: Boolean = false,
) {
    val atRoot: Boolean get() = path.isEmpty()
}

sealed interface FilesEvent {
    data object Saved : FilesEvent
    data class SaveFailed(val message: String?) : FilesEvent
    data object FileGone : FilesEvent
    data class Downloaded(val result: DownloadResult.Saved) : FilesEvent
    data class DownloadFailed(val reason: DownloadSource.Reason) : FilesEvent
}

/**
 * ファイルビューア（spec 002 US1〜US3）。一覧・開く・編集・保存・衝突・下書き・ダウンロード。
 * 色分けの解析は Default のスレッドで一度だけ（AGENTS.md 1）。
 */
class FilesViewModel(
    private val files: FileSource,
    private val downloader: FileDownloads,
    private val drafts: FileDraftDao,
    sessionKey: String,
    agentId: String,
    /** 会話に作業フォルダがあるか（無ければワークスペースから始める）。 */
    hasWorkingFolder: Boolean,
) : ViewModel() {
    private val sessionRoot = FileRoot.Session(sessionKey, agentId).takeIf { hasWorkingFolder }
    private val workspaceRoot = FileRoot.Workspace(agentId)

    private val _state = MutableStateFlow(FilesUiState(sessionRoot, workspaceRoot, sessionRoot ?: workspaceRoot))
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<FilesEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<FilesEvent> = _events.asSharedFlow()

    private var listJob: Job? = null
    private var openJob: Job? = null
    private var draftJob: Job? = null

    init {
        load()
    }

    fun selectRoot(root: FileRoot) {
        if (root == _state.value.root) return
        openJob?.cancel()
        _state.update { it.copy(root = root, path = "", opened = null, confirmDiscard = false) }
        load()
    }

    fun openEntry(entry: FileEntry) {
        if (entry.isDirectory) {
            _state.update { it.copy(path = entry.path) }
            load()
        } else {
            open(entry.path)
        }
    }

    fun retry() {
        val opened = _state.value.opened
        if (opened is OpenedState.Failed) open(opened.path) else load()
    }

    /** 戻る操作。true なら処理した（false なら画面を閉じてよい）。 */
    fun back(): Boolean {
        val s = _state.value
        val opened = s.opened
        return when {
            s.confirmDiscard -> { _state.update { it.copy(confirmDiscard = false) }; true }
            opened is OpenedState.Ready && opened.dirty -> { _state.update { it.copy(confirmDiscard = true) }; true }
            opened != null -> { closeFile(); true }
            !s.atRoot -> { goUp(); true }
            else -> false
        }
    }

    fun dismissDiscard() = _state.update { it.copy(confirmDiscard = false) }

    /** 変更を捨てて閉じる（下書きも消す）。 */
    fun discardAndClose() {
        val ready = _state.value.opened as? OpenedState.Ready
        _state.update { it.copy(confirmDiscard = false) }
        ready?.let { file -> viewModelScope.launch { drafts.delete(draftKey(file.file)) } }
        closeFile()
    }

    fun closeFile() {
        openJob?.cancel()
        draftJob?.cancel()
        _state.update { it.copy(opened = null) }
    }

    fun goUp() {
        val s = _state.value
        val parent = (s.listing as? ListingState.Loaded)?.listing?.parentPath ?: s.path.substringBeforeLast('/', "")
        _state.update { it.copy(path = parent) }
        load()
    }

    private fun load() {
        listJob?.cancel()
        val s = _state.value
        _state.update { it.copy(listing = ListingState.Loading) }
        listJob = viewModelScope.launch {
            val result = runCatchingNonCancel { files.list(s.root, s.path) }
            _state.update { current ->
                if (current.root != s.root || current.path != s.path) current
                else current.copy(listing = result?.let(ListingState::Loaded) ?: ListingState.Failed)
            }
        }
    }

    private fun open(path: String) {
        openJob?.cancel()
        val root = _state.value.root
        _state.update { it.copy(opened = OpenedState.Loading(path)) }
        openJob = viewModelScope.launch {
            val result = runCatchingNonCancel { files.open(root, path) }
            val ready = when (result) {
                is OpenResult.Opened -> readyState(result.file)
                OpenResult.Missing -> OpenedState.Failed(path, missing = true)
                null -> OpenedState.Failed(path, missing = false)
            }
            _state.update { if (it.opened?.path == path) it.copy(opened = ready) else it }
        }
    }

    /** 色分けと下書きの復元をまとめて行い、開いた状態を作る。 */
    private suspend fun readyState(file: OpenedFile): OpenedState.Ready {
        val text = file.text
        val spans = if (file.kind == PreviewKind.TEXT && text != null) {
            withContext(Dispatchers.Default) { CodeHighlighter.spans(file.name, text) }
        } else {
            emptyList()
        }
        val draft = if (file.editable) drafts.get(draftKey(file)) else null
        return if (draft != null && draft.content != text) {
            OpenedState.Ready(file, spans, editText = draft.content, cursor = draft.cursor, baseHash = draft.baseHash, restoredDraft = true)
        } else {
            OpenedState.Ready(file, spans)
        }
    }

    fun startEditing() = updateReady { ready ->
        if (!ready.file.editable || ready.editing) ready
        else ready.copy(editText = ready.file.text.orEmpty(), cursor = 0, baseHash = ready.file.hash)
    }

    fun edit(text: String, cursor: Int) {
        updateReady { it.copy(editText = text, cursor = cursor) }
        val ready = _state.value.opened as? OpenedState.Ready ?: return
        // 入力を間引いて下書きに残す（プロセスが終わっても失わない。FR-011）
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(DRAFT_SAVE_DEBOUNCE_MS)
            if (ready.dirty) {
                drafts.upsert(FileDraftEntity(draftKey(ready.file), text, ready.baseHash.orEmpty(), cursor, System.currentTimeMillis()))
            } else {
                drafts.delete(draftKey(ready.file))
            }
        }
    }

    /** 保存（FR-008）。`overwrite` は衝突のあと「上書き」を選んだとき。 */
    fun save(overwrite: Boolean = false) {
        val ready = _state.value.opened as? OpenedState.Ready ?: return
        val content = ready.editText ?: return
        if (ready.saving) return
        updateReady { it.copy(saving = true, conflict = false) }
        viewModelScope.launch {
            val expected = if (overwrite) {
                // 最新のハッシュを取り直して、それに対して書く
                (runCatchingNonCancel { files.open(ready.file.root, ready.file.path) } as? OpenResult.Opened)?.file?.hash
            } else {
                ready.baseHash
            }
            val result = if (expected == null) SaveResult.Missing else runCatchingNonCancel { files.save(ready.file, content, expected) } ?: SaveResult.Failed(null)
            when (result) {
                is SaveResult.Saved -> {
                    draftJob?.cancel()
                    drafts.delete(draftKey(ready.file))
                    val spans = withContext(Dispatchers.Default) { CodeHighlighter.spans(result.file.name, content) }
                    val saved = result.file.copy(text = result.file.text ?: content)
                    _state.update { s -> if (s.opened?.path == ready.path) s.copy(opened = OpenedState.Ready(saved, spans)) else s }
                    _events.tryEmit(FilesEvent.Saved)
                }
                SaveResult.Conflict -> updateReady { it.copy(saving = false, conflict = true) }
                SaveResult.Missing -> {
                    updateReady { it.copy(saving = false) }
                    _events.tryEmit(FilesEvent.FileGone)
                }
                is SaveResult.Failed -> {
                    updateReady { it.copy(saving = false) }
                    _events.tryEmit(FilesEvent.SaveFailed(result.message))
                }
            }
        }
    }

    /** 衝突のあと「読み直し」: 変更を捨てて最新を開く。 */
    fun reloadDiscarding() {
        val ready = _state.value.opened as? OpenedState.Ready ?: return
        viewModelScope.launch {
            drafts.delete(draftKey(ready.file))
            open(ready.file.path)
        }
    }

    fun dismissConflict() = updateReady { it.copy(conflict = false) }

    /** ダウンロード（FR-013）。`path` を渡さなければ開いているファイル。 */
    fun download(path: String? = null) {
        val target = path ?: _state.value.opened?.path ?: return
        val root = _state.value.root
        viewModelScope.launch {
            val result = runCatchingNonCancel { downloader.download(root, target) } ?: DownloadResult.Failed(DownloadSource.Reason.FAILED)
            _events.tryEmit(
                when (result) {
                    is DownloadResult.Saved -> FilesEvent.Downloaded(result)
                    is DownloadResult.Failed -> FilesEvent.DownloadFailed(result.reason)
                },
            )
        }
    }

    private inline fun updateReady(crossinline block: (OpenedState.Ready) -> OpenedState.Ready) =
        _state.update { s -> (s.opened as? OpenedState.Ready)?.let { s.copy(opened = block(it)) } ?: s }

    private fun draftKey(file: OpenedFile): String = when (val root = file.root) {
        is FileRoot.Session -> "session:${root.sessionKey}:${file.path}"
        is FileRoot.Workspace -> "workspace:${root.agentId}:${file.path}"
    }

    private inline fun <T> runCatchingNonCancel(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val DRAFT_SAVE_DEBOUNCE_MS = 400L
    }
}
