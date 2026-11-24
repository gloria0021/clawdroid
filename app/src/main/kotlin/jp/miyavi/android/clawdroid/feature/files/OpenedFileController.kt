package jp.miyavi.android.clawdroid.feature.files

import jp.miyavi.android.clawdroid.core.data.FileRoot
import jp.miyavi.android.clawdroid.core.data.isSecretName
import jp.miyavi.android.clawdroid.core.data.FileSource
import jp.miyavi.android.clawdroid.core.data.OpenResult
import jp.miyavi.android.clawdroid.core.data.OpenedFile
import jp.miyavi.android.clawdroid.core.data.PreviewKind
import jp.miyavi.android.clawdroid.core.data.SaveResult
import jp.miyavi.android.clawdroid.core.database.FileDraftDao
import jp.miyavi.android.clawdroid.core.database.FileDraftEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 開いた 1 つのファイルの状態の移り変わり（読む・プレビューとコード・検索・編集・保存・衝突・下書き）。
 * 002 のファイルビューアと 004 のエージェントのファイルで共有する（004 research R2）。
 * 状態は持ち主（ViewModel）の UI 状態の中にあり、[read] / [write] で同期して読み書きする（見た目の遅れを作らない）。
 */
class OpenedFileController(
    private val scope: CoroutineScope,
    private val files: FileSource,
    private val drafts: FileDraftDao,
    private val read: () -> OpenedState?,
    private val write: ((OpenedState?) -> OpenedState?) -> Unit,
    private val emit: (FilesEvent) -> Unit,
) {
    private var openJob: Job? = null
    private var draftJob: Job? = null
    private var searchJob: Job? = null

    /** 開く。下書きがあれば戻す。未作成のエージェントのファイルは編集から始める。 */
    fun open(root: FileRoot, path: String, edit: Boolean = false) {
        openJob?.cancel()
        write { OpenedState.Loading(path) }
        openJob = scope.launch {
            val result = runCatchingNonCancel { files.open(root, path) }
            val next = when (result) {
                // 作ったばかりのファイルは編集から始める（spec 005 US6）
                is OpenResult.Opened -> readyState(result.file).let { ready ->
                    if (edit && ready is OpenedState.Ready && ready.file.editable && !ready.editing) {
                        ready.copy(editText = ready.file.text.orEmpty(), cursor = 0, baseHash = ready.file.hash)
                    } else ready
                }
                OpenResult.Missing -> OpenedState.Failed(path, missing = true)
                null -> OpenedState.Failed(path, missing = false)
            }
            write { if (it?.path == path) next else it }
        }
    }

    /** 閉じる（進行中の取得・下書きの保存・検索を止める）。 */
    fun close() {
        openJob?.cancel()
        draftJob?.cancel()
        searchJob?.cancel()
        write { null }
    }

    /** 編集をやめて読む表示に戻る（変更と下書きは捨てる）。 */
    fun stopEditing() {
        val ready = read() as? OpenedState.Ready ?: return
        draftJob?.cancel()
        scope.launch { drafts.delete(draftKey(ready.file)) }
        updateReady { it.copy(editText = null, cursor = 0, baseHash = it.file.hash, restoredDraft = false, conflict = false) }
    }

    fun startEditing() = updateReady { ready ->
        if (!ready.file.editable || ready.editing) ready
        else ready.copy(editText = ready.file.text.orEmpty(), cursor = 0, baseHash = ready.file.hash, search = null)
    }

    /** プレビュー ⇄ 元の文字。検索の言葉と位置はそのまま引き継ぐ。 */
    fun toggleCode() = updateReady { if (it.preview == null || it.editing) it else it.copy(showCode = !it.showCode) }

    fun openSearch() = updateReady { if (it.editing || it.file.text == null) it else it.copy(search = it.search ?: FileSearch()) }

    fun closeSearch() {
        searchJob?.cancel()
        updateReady { it.copy(search = null) }
    }

    /** 入力はすぐ反映し、一致の検索は間引いて Default のスレッドで行う。 */
    fun setSearchQuery(query: String) {
        val ready = read() as? OpenedState.Ready ?: return
        val text = ready.file.text ?: return
        updateReady { it.copy(search = (it.search ?: FileSearch()).copy(query = query)) }
        searchJob?.cancel()
        searchJob = scope.launch {
            if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)
            val matches = withContext(Dispatchers.Default) { findMatches(text, query) }
            updateReady { r ->
                val search = r.search
                if (search == null || search.query != query || r.path != ready.path) r
                else r.copy(search = search.copy(matchedQuery = query, matches = matches, current = 0))
            }
        }
    }

    /** 次（[step] = 1）/ 前（-1）の一致へ。端では反対側に回る。 */
    fun moveMatch(step: Int) = updateReady { r ->
        val search = r.search
        if (search == null || search.matches.isEmpty()) r
        else r.copy(search = search.copy(current = Math.floorMod(search.current + step, search.matches.size)))
    }

    fun edit(text: String, cursor: Int) {
        updateReady { it.copy(editText = text, cursor = cursor) }
        val ready = read() as? OpenedState.Ready ?: return
        // 入力を間引いて下書きに残す（プロセスが終わっても失わない。002 FR-011）
        draftJob?.cancel()
        draftJob = scope.launch {
            delay(DRAFT_SAVE_DEBOUNCE_MS)
            // 秘密の値を含みそうなファイルは、端末に下書きを残さない（spec 005 FR-068・research R8。002 FR-011 の例外）
            if (ready.dirty && !isSecretName(ready.file.path)) {
                drafts.upsert(FileDraftEntity(draftKey(ready.file), text, ready.baseHash.orEmpty(), cursor, System.currentTimeMillis()))
            } else {
                drafts.delete(draftKey(ready.file))
            }
        }
    }

    /**
     * 保存（002 FR-008、004 FR-022）。`overwrite` は衝突のあと「上書き」を選んだとき（最新のハッシュを取り直して書く）。
     * 未作成のファイルは、空のハッシュ = まだ無いことを条件に作る。
     */
    fun save(overwrite: Boolean = false) {
        val ready = read() as? OpenedState.Ready ?: return
        val content = ready.editText ?: return
        if (ready.saving) return
        updateReady { it.copy(saving = true, conflict = false) }
        scope.launch {
            val expected = when {
                overwrite -> when (val latest = runCatchingNonCancel { files.open(ready.file.root, ready.file.path) }) {
                    is OpenResult.Opened -> if (latest.file.creating) "" else latest.file.hash
                    else -> null
                }
                ready.file.creating -> ""
                else -> ready.baseHash
            }
            val result = if (expected == null) SaveResult.Missing
            else runCatchingNonCancel { files.save(ready.file, content, expected) } ?: SaveResult.Failed(null)
            when (result) {
                is SaveResult.Saved -> {
                    draftJob?.cancel()
                    drafts.delete(draftKey(ready.file))
                    val saved = result.file.copy(text = result.file.text ?: content)
                    val reopened = analyze(saved, saved.text).let { it.copy(showCode = it.preview == null || ready.showCode) }
                    write { if (it?.path == ready.path) reopened else it }
                    emit(FilesEvent.Saved)
                }
                SaveResult.Conflict -> updateReady { it.copy(saving = false, conflict = true) }
                SaveResult.Missing -> {
                    updateReady { it.copy(saving = false) }
                    emit(FilesEvent.FileGone)
                }
                is SaveResult.Failed -> {
                    updateReady { it.copy(saving = false) }
                    emit(FilesEvent.SaveFailed(result.message))
                }
            }
        }
    }

    /** 衝突のあと「読み直し」: 変更を捨てて最新を開く。 */
    fun reloadDiscarding() {
        val ready = read() as? OpenedState.Ready ?: return
        scope.launch {
            drafts.delete(draftKey(ready.file))
            open(ready.file.root, ready.file.path)
        }
    }

    fun dismissConflict() = updateReady { it.copy(conflict = false) }

    /** 色分けと下書きの復元をまとめて行い、開いた状態を作る。 */
    private suspend fun readyState(file: OpenedFile): OpenedState.Ready {
        val analyzed = analyze(file, file.text)
        val draft = if (file.editable) drafts.get(draftKey(file)) else null
        return when {
            draft != null && draft.content != file.text ->
                analyzed.copy(editText = draft.content, cursor = draft.cursor, baseHash = draft.baseHash, restoredDraft = true)
            // まだ無いファイルは、書くために開いたので編集から始める
            file.creating && file.editable -> analyzed.copy(editText = file.text.orEmpty(), cursor = 0, baseHash = null)
            else -> analyzed
        }
    }

    /** 色分けと Markdown の解析を Default のスレッドで一度だけ行う。 */
    private suspend fun analyze(file: OpenedFile, text: String?): OpenedState.Ready {
        if (file.kind != PreviewKind.TEXT || text == null) return OpenedState.Ready(file, emptyList())
        val preview = previewFormatOf(file.name)
        // HTML は相対の CSS・JS・画像を Gateway から取って埋め込む（プレビューはネットワークに出ないため）
        val inline = if (preview == PreviewFormat.HTML) {
            val refs = htmlAssetRefs(text)
            runCatchingNonCancel { files.htmlAssets(file.root, file.path, refs) }?.takeIf { it.isNotEmpty() }?.let { inlineHtmlAssets(text, it) }
        } else null
        return withContext(Dispatchers.Default) {
            OpenedState.Ready(
                file = file,
                spans = CodeHighlighter.spans(file.name, text),
                preview = preview,
                htmlInline = inline,
                blocks = if (preview == PreviewFormat.MARKDOWN) markdownBlocks(text) else emptyList(),
            )
        }
    }

    private inline fun updateReady(crossinline block: (OpenedState.Ready) -> OpenedState.Ready) =
        write { (it as? OpenedState.Ready)?.let(block) ?: it }

    companion object {
        const val DRAFT_SAVE_DEBOUNCE_MS = 400L
        const val SEARCH_DEBOUNCE_MS = 150L

        /** 下書きの鍵。作業フォルダ・ワークスペース・エージェントのファイルで分ける。 */
        fun draftKey(file: OpenedFile): String = when (val root = file.root) {
            is FileRoot.Session -> "session:${root.sessionKey}:${file.path}"
            is FileRoot.Workspace -> "workspace:${root.agentId}:${file.path}"
            is FileRoot.AgentFile -> "agent:${root.agentId}:${file.path}"
        }
    }
}

internal inline fun <T> runCatchingNonCancel(block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}
