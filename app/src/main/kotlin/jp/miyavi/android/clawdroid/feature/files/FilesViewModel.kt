package jp.miyavi.android.clawdroid.feature.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.clawdroid.core.data.FileDownloads
import jp.miyavi.android.clawdroid.core.data.FileEntry
import androidx.lifecycle.SavedStateHandle
import jp.miyavi.android.clawdroid.core.data.FileListing
import jp.miyavi.android.clawdroid.core.data.FileOp
import jp.miyavi.android.clawdroid.core.data.FileOpResult
import jp.miyavi.android.clawdroid.core.data.FileOpsRepository
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
import kotlinx.coroutines.flow.stateIn
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
        /** HTML のプレビュー用に、相対の CSS・JS・画像を埋め込んだもの（無ければ元の文字）。 */
        val htmlInline: String? = null,
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
    /** ファイルのツリー（spec 005 US4）。 */
    val tree: FileTreeState = FileTreeState(),
    /** 作業フォルダの絶対パス（ファイルの操作に使う。ワークスペースでは null）。 */
    val rootPath: String? = null,
    val opened: OpenedState? = null,
    /** 戻るで変更を捨てるかの確認を出している。 */
    val confirmDiscard: Boolean = false,
    /** ファイルの作成・削除・名前の変更ができる（作業フォルダで、ターミナルを開ける接続。spec 005 FR-065・066）。 */
    val canModify: Boolean = false,
    /** 操作の途中のパス（その行に進み具合を出す）。 */
    val pendingOp: String? = null,
) {
    /** 画面の中だけで済む「戻る」（確認・検索・編集）があるか。 */
    val handlesBackInPage: Boolean
        get() = confirmDiscard || (opened as? OpenedState.Ready)?.let { it.search != null || it.editing } == true

    val rows: List<TreeRow> get() = flattenTree(tree)
}

sealed interface FilesEvent {
    data object Saved : FilesEvent
    data class SaveFailed(val message: String?) : FilesEvent
    data object FileGone : FilesEvent
    /** ファイルの操作の結果（spec 005 FR-067）。 */
    data class Operated(val op: FileOp, val result: FileOpResult) : FilesEvent
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
    /** ファイルの作成・削除・名前の変更（無ければ出さない）。 */
    private val ops: FileOpsRepository? = null,
    /** 開いていたフォルダとファイル（プロセスの終了を越えて残す。spec 005 FR-042）。 */
    private val saved: SavedStateHandle = SavedStateHandle(),
    /** 折り返しの選択を覚える所（無ければ画面の中だけ）。 */
    private val settings: jp.miyavi.android.clawdroid.core.settings.SettingsStore? = null,
    /** 最後に開いていたファイルと展開を、アプリを閉じても覚えておく所（会話ごと）。 */
    private val memory: FileViewMemory? = null,
) : ViewModel() {
    init {
        // 前回の続き（最後に開いたファイル・展開していたフォルダ）から始める
        if (memory != null && saved.keys().isEmpty()) memory.load().forEach { (key, value) -> saved[key] = value }
    }

    /** コードを折り返すか（spec 005 FR-053）。 */
    val wrap: StateFlow<Boolean> = settings?.codeWrap?.let { flow ->
        flow.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)
    } ?: MutableStateFlow(false)

    fun toggleWrap() {
        val next = !wrap.value
        settings?.let { viewModelScope.launch { it.setCodeWrap(next) } } ?: (wrap as MutableStateFlow).let { it.value = next }
    }

    private val agentId = agentId
    private val sessionRoot = FileRoot.Session(sessionKey, agentId).takeIf { hasWorkingFolder }
    private val workspaceRoot = FileRoot.Workspace(agentId)

    private val _state = MutableStateFlow(
        FilesUiState(
            sessionRoot, workspaceRoot,
            root = if (saved.get<Boolean>(KEY_WORKSPACE) == true) workspaceRoot else sessionRoot ?: workspaceRoot,
            tree = FileTreeState(expanded = saved.get<ArrayList<String>>(KEY_EXPANDED).orEmpty().toSet()),
        ),
    )
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<FilesEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<FilesEvent> = _events.asSharedFlow()

    private val listJobs = mutableMapOf<String, Job>()

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
        _state.value.tree.needsLoad().forEach(::load)
        saved.get<String>(KEY_OPENED)?.let { opened.open(_state.value.root, it) }
        updateCanModify()
    }

    fun selectRoot(root: FileRoot) {
        if (root == _state.value.root) return
        opened.close()
        _state.update { it.copy(root = root, tree = FileTreeState(), rootPath = null, opened = null, confirmDiscard = false) }
        store(KEY_WORKSPACE, root is FileRoot.Workspace)
        persistTree()
        store(KEY_OPENED, null)
        listJobs.values.forEach { it.cancel() }
        listJobs.clear()
        load("")
        updateCanModify()
    }

    /** フォルダはその場で開閉、ファイルは開く。 */
    fun openEntry(entry: FileEntry) {
        if (entry.isDirectory) toggleFolder(entry.path) else openFile(entry.path)
    }

    fun toggleFolder(path: String) {
        _state.update { it.copy(tree = it.tree.toggle(path)) }
        persistTree()
        if (path in _state.value.tree.expanded && _state.value.tree.children[path].let { it == null || it == ListingState.Failed }) load(path)
    }

    private fun openFile(path: String, edit: Boolean = false) {
        opened.open(_state.value.root, path, edit)
        store(KEY_OPENED, path)
    }

    fun retry() {
        val current = _state.value.opened
        if (current is OpenedState.Failed) opened.open(_state.value.root, current.path)
        _state.value.tree.needsLoad().forEach(::load)
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
            else -> false
        }
    }

    fun dismissDiscard() = _state.update { it.copy(confirmDiscard = false) }

    /** 変更を捨てて、読む表示（プレビュー / コード）に戻る（下書きも消す）。 */
    fun discardAndClose() {
        _state.update { it.copy(confirmDiscard = false) }
        opened.stopEditing()
    }

    fun closeFile() {
        opened.close()
        store(KEY_OPENED, null)
    }

    /** フォルダの中身を読む（読み込み中に同じフォルダを頼まれたら、前のものをやめて読み直す）。 */
    private fun load(path: String) {
        listJobs.remove(path)?.cancel()
        val root = _state.value.root
        _state.update { it.copy(tree = it.tree.loading(path)) }
        listJobs[path] = viewModelScope.launch {
            val result = runCatchingNonCancel { files.list(root, path) }
            _state.update { current ->
                if (current.root != root) current
                else current.copy(
                    tree = current.tree.loaded(path, result?.let(ListingState::Loaded) ?: ListingState.Failed),
                    rootPath = if (path.isEmpty()) result?.root ?: current.rootPath else current.rootPath,
                )
            }
            if (path.isEmpty()) updateCanModify()
        }
    }

    private fun store(key: String, value: Any?) {
        saved[key] = value
        memory?.save(key, value)
    }

    private fun persistTree() {
        store(KEY_EXPANDED, ArrayList(_state.value.tree.expanded))
    }

    private fun updateCanModify() = _state.update {
        it.copy(canModify = ops?.available() == true && it.root is FileRoot.Session && it.rootPath != null)
    }

    /**
     * ファイルの作成・削除・名前の変更（spec 005 US6）。終わったら親のフォルダを取り直して結果を確かめる（FR-067）。
     * 作ったファイルは編集できる状態で開き、名前を変えた・消したファイルが開いていれば開き直す・閉じる。
     */
    fun operate(op: FileOp) {
        val repo = ops ?: return
        val s = _state.value
        val root = s.rootPath ?: return
        if (!s.canModify || s.pendingOp != null) return
        val target = when (op) {
            is FileOp.CreateFile -> childPath(op.parent, op.name)
            is FileOp.CreateFolder -> childPath(op.parent, op.name)
            is FileOp.Rename -> op.path
            is FileOp.Delete -> op.path
        }
        val parent = when (op) {
            is FileOp.CreateFile -> op.parent
            is FileOp.CreateFolder -> op.parent
            is FileOp.Rename -> op.path.substringBeforeLast('/', "")
            is FileOp.Delete -> op.path.substringBeforeLast('/', "")
        }
        _state.update { it.copy(pendingOp = target) }
        viewModelScope.launch {
            val result = runCatchingNonCancel { repo.run(agentId, root, op) } ?: FileOpResult.Unknown
            val openedPath = _state.value.opened?.path
            _state.update { current ->
                var tree = current.tree.invalidate(parent)
                if (result == FileOpResult.Done || result == FileOpResult.Unknown) {
                    if (op is FileOp.Rename || op is FileOp.Delete) tree = tree.forget(target)
                    if (op is FileOp.CreateFolder && parent.isNotEmpty() && parent !in tree.expanded) tree = tree.toggle(parent)
                    if (op is FileOp.CreateFile && parent.isNotEmpty() && parent !in tree.expanded) tree = tree.toggle(parent)
                }
                current.copy(tree = tree, pendingOp = null)
            }
            persistTree()
            load(parent)
            if (result == FileOpResult.Done) {
                when (op) {
                    is FileOp.CreateFile -> openFile(target, edit = true)
                    is FileOp.Rename -> if (openedPath == target || openedPath?.startsWith("$target/") == true) {
                        closeFile()
                        if (openedPath == target) openFile(childPath(parent, op.newName))
                    }
                    is FileOp.Delete -> if (openedPath == target || openedPath?.startsWith("$target/") == true) closeFile()
                    is FileOp.CreateFolder -> Unit
                }
            }
            _events.tryEmit(FilesEvent.Operated(op, result))
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

    private fun childPath(parent: String, name: String) = if (parent.isEmpty()) name else "$parent/$name"

    /** ダウンロード（FR-013）。`path` を渡さなければ開いているファイル。進み具合はアプリ全体（上部と通知）に出る。 */
    fun download(path: String? = null) {
        val target = path ?: _state.value.opened?.path ?: return
        downloader.start(_state.value.root, target)
    }

    private companion object {
        const val KEY_EXPANDED = "files.expanded"
        const val KEY_OPENED = "files.opened"
        const val KEY_WORKSPACE = "files.workspace"
    }
}

/** 会話ごとに、最後に開いていたファイル・展開・ルートを覚えておく（spec 005 FR-042。マスター指定 2026-11-24）。 */
class FileViewMemory(private val prefs: android.content.SharedPreferences, private val key: String) {
    fun load(): Map<String, Any?> = buildMap {
        prefs.getString("$key|opened", null)?.let { put("files.opened", it) }
        prefs.getStringSet("$key|expanded", null)?.let { put("files.expanded", ArrayList(it)) }
        if (prefs.getBoolean("$key|workspace", false)) put("files.workspace", true)
    }

    fun save(name: String, value: Any?) {
        prefs.edit().apply {
            when (name) {
                "files.opened" -> if (value == null) remove("$key|opened") else putString("$key|opened", value as String)
                "files.expanded" -> putStringSet("$key|expanded", (value as? Collection<*>)?.map { it.toString() }?.toSet().orEmpty())
                "files.workspace" -> putBoolean("$key|workspace", value == true)
            }
        }.apply()
    }
}
