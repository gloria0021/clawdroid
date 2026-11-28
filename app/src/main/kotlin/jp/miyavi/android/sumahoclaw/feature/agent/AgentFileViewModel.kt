package jp.miyavi.android.sumahoclaw.feature.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.sumahoclaw.core.data.FileRoot
import jp.miyavi.android.sumahoclaw.core.data.FileSource
import jp.miyavi.android.sumahoclaw.feature.files.FilesEvent
import jp.miyavi.android.sumahoclaw.feature.files.OpenedFileController
import jp.miyavi.android.sumahoclaw.feature.files.OpenedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgentFileUiState(
    val name: String,
    val opened: OpenedState? = null,
) {
    /** 画面の中で済む「戻る」（検索）があるか。 */
    val handlesBackInPage: Boolean
        get() = (opened as? OpenedState.Ready)?.search != null
}

/**
 * エージェントのファイル 1 つ（spec 004 US1）。読むのは 002 と同じ [OpenedFileController]。
 * 編集・保存・衝突・下書きはエディターの画面（spec 008。下書きの鍵は `agent:<agentId>:<name>`）。
 * 未作成のファイルは、開けたらすぐエディターを開く。
 */
class AgentFileViewModel(
    agentId: String,
    name: String,
    files: FileSource,
    /** エディターで保存した結果（開いているファイルを差し替える）。 */
    editorResults: kotlinx.coroutines.flow.Flow<jp.miyavi.android.sumahoclaw.feature.editor.EditorResult>? = null,
) : ViewModel() {
    private val root = FileRoot.AgentFile(agentId)
    private val _state = MutableStateFlow(AgentFileUiState(name))
    val state: StateFlow<AgentFileUiState> = _state.asStateFlow()

    // 画面が知らせを受け始める前に開き終わっても取りこぼさないよう、受け取られるまで溜めておく
    // （未作成のファイルは開けたらすぐエディターを開く。spec 008）
    private val _events = kotlinx.coroutines.channels.Channel<FilesEvent>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val events: kotlinx.coroutines.flow.Flow<FilesEvent> = _events.receiveAsFlow()

    private val opened = OpenedFileController(
        scope = viewModelScope,
        files = files,
        read = { _state.value.opened },
        write = { block -> _state.update { it.copy(opened = block(it.opened)) } },
        emit = { _events.trySend(it) },
    )

    init {
        opened.open(root, name)
        editorResults?.let { flow ->
            viewModelScope.launch {
                flow.collect { r -> if (r is jp.miyavi.android.sumahoclaw.feature.editor.EditorResult.Saved) opened.applySaved(r.file) }
            }
        }
    }

    fun retry() = opened.open(root, _state.value.name)

    /** 戻る操作。true なら処理した（false なら画面を閉じてよい）。 */
    fun back(): Boolean {
        val current = _state.value.opened as? OpenedState.Ready
        return when {
            current?.search != null -> { opened.closeSearch(); true }
            else -> false
        }
    }

    fun startEditing() = opened.startEditing()
    fun toggleCode() = opened.toggleCode()
    fun openSearch() = opened.openSearch()
    fun closeSearch() = opened.closeSearch()
    fun setSearchQuery(query: String) = opened.setSearchQuery(query)
    fun moveMatch(step: Int) = opened.moveMatch(step)
}
