package jp.miyavi.android.clawdroid.feature.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.clawdroid.core.data.FileDownloads
import jp.miyavi.android.clawdroid.core.data.FileEntry
import jp.miyavi.android.clawdroid.core.data.FileListing
import jp.miyavi.android.clawdroid.core.data.FileRoot
import jp.miyavi.android.clawdroid.core.data.FileSource
import jp.miyavi.android.clawdroid.core.data.OpenedFile
import jp.miyavi.android.clawdroid.core.database.FileDraftDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        /** Markdown / HTML ならプレビューできる。 */
        val preview: PreviewFormat? = null,
        /** Markdown の塗（ViewModel で一度だけ解析する）。 */
        val blocks: List<MarkdownBlock> = emptyList(),
        /** 元の文字を見ている（プレビューできるファイルは、ふだんはプレビュー）。 */
        val showCode: Boolean = preview == null,
        /** ファイル内の検索（null なら閉じている）。 */
        val search: FileSearch? = null,
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
    /** 開いたフォルダごとの一覧（上のフォルダも残し、予測型「戻る」で後ろに見せる）。 */
    val listings: Map<String, ListingState> = emptyMap(),
    val opened: OpenedState? = null,
    /** 戻るで変更を捨てるかの確認を出している。 */
    val confirmDiscard: Boolean = false,
) {
    val atRoot: Boolean get() = path.isEmpty()
    val listing: ListingState get() = listings[path] ?: ListingState.Loading

    /** 画面の中だけで済む「戻る」（確認・検索・編集）があるか。ページを戻す「戻る」は画面の重なり（予測型「戻る」）に任せる。 */
    val handlesBackInPage: Boolean
        get() = confirmDiscard || (opened as? OpenedState.Ready)?.let { it.search != null || it.editing } == true

    /** 根から今のフォルダまでのパス（画面の重なり。根は ""）。 */
    val folderStack: List<String>
        get() = if (path.isEmpty()) listOf("") else listOf("") + path.split('/').runningReduce { a, b -> "$a/$b" }
}

sealed interface FilesEvent {
    data object Saved : FilesEvent
    data class SaveFailed(val message: String?) : FilesEvent
    data object FileGone : FilesEvent
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

    /** 開いたファイルの状態の移り変わり（004 と共有）。状態は [_state] の `opened` に同期して書く。 */
    private val opened = OpenedFileController(
        scope = viewModelScope,
        files = files,
        drafts = drafts,
        read = { _state.value.opened },
        write = { block -> _state.update { it.copy(opened = block(it.opened)) } },
        emit = { _events.tryEmit(it) },
    )

    init {
        load()
    }

    fun selectRoot(root: FileRoot) {
        if (root == _state.value.root) return
        opened.close()
        _state.update { it.copy(root = root, path = "", listings = emptyMap(), opened = null, confirmDiscard = false) }
        load()
    }

    fun openEntry(entry: FileEntry) {
        if (entry.isDirectory) {
            _state.update { it.copy(path = entry.path) }
            load()
        } else {
            opened.open(_state.value.root, entry.path)
        }
    }

    fun retry() {
        val current = _state.value.opened
        if (current is OpenedState.Failed) opened.open(_state.value.root, current.path) else load()
    }

    /** 戻る操作。true なら処理した（false なら画面を閉じてよい）。 */
    fun back(): Boolean {
        val s = _state.value
        val current = s.opened
        return when {
            s.confirmDiscard -> { _state.update { it.copy(confirmDiscard = false) }; true }
            current is OpenedState.Ready && current.search != null -> { closeSearch(); true }
            current is OpenedState.Ready && current.dirty -> { _state.update { it.copy(confirmDiscard = true) }; true }
            // 変更していない編集は、そのまま読む表示に戻る
            current is OpenedState.Ready && current.editing -> { opened.stopEditing(); true }
            current != null -> { closeFile(); true }
            !s.atRoot -> { goUp(); true }
            else -> false
        }
    }

    fun dismissDiscard() = _state.update { it.copy(confirmDiscard = false) }

    /** 変更を捨てて、読む表示（プレビュー / コード）に戻る（下書きも消す）。 */
    fun discardAndClose() {
        _state.update { it.copy(confirmDiscard = false) }
        opened.stopEditing()
    }

    fun closeFile() = opened.close()

    /** 1 つ上のフォルダへ。残っている一覧をそのまま見せ、下のフォルダの一覧は捨てる。 */
    fun goUp() {
        val s = _state.value
        val parent = s.path.substringBeforeLast('/', "")
        _state.update { it.copy(path = parent, listings = it.listings.filterKeys { key -> key == parent || parent.startsWith("$key/") || key.isEmpty() }) }
        if (_state.value.listings[parent] !is ListingState.Loaded) load()
    }

    private fun load() {
        listJob?.cancel()
        val s = _state.value
        _state.update { it.copy(listings = it.listings + (s.path to ListingState.Loading)) }
        listJob = viewModelScope.launch {
            val result = runCatchingNonCancel { files.list(s.root, s.path) }
            _state.update { current ->
                if (current.root != s.root || s.path !in current.listings) current
                else current.copy(listings = current.listings + (s.path to (result?.let(ListingState::Loaded) ?: ListingState.Failed)))
            }
        }
    }

    fun startEditing() = opened.startEditing()
    fun toggleCode() = opened.toggleCode()
    fun openSearch() = opened.openSearch()
    fun closeSearch() = opened.closeSearch()
    fun setSearchQuery(query: String) = opened.setSearchQuery(query)
    fun moveMatch(step: Int) = opened.moveMatch(step)
    fun edit(text: String, cursor: Int) = opened.edit(text, cursor)

    /** 保存（FR-008）。`overwrite` は衝突のあと「上書き」を選んだとき。 */
    fun save(overwrite: Boolean = false) = opened.save(overwrite)

    /** 衝突のあと「読み直し」: 変更を捨てて最新を開く。 */
    fun reloadDiscarding() = opened.reloadDiscarding()

    fun dismissConflict() = opened.dismissConflict()

    /** ダウンロード（FR-013）。`path` を渡さなければ開いているファイル。進み具合はアプリ全体（上部と通知）に出る。 */
    fun download(path: String? = null) {
        val target = path ?: _state.value.opened?.path ?: return
        downloader.start(_state.value.root, target)
    }
}
