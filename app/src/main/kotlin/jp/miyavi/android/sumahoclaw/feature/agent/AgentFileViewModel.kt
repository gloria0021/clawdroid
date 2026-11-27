package jp.miyavi.android.sumahoclaw.feature.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.sumahoclaw.core.data.FileRoot
import jp.miyavi.android.sumahoclaw.core.data.FileSource
import jp.miyavi.android.sumahoclaw.core.database.FileDraftDao
import jp.miyavi.android.sumahoclaw.feature.files.FilesEvent
import jp.miyavi.android.sumahoclaw.feature.files.OpenedFileController
import jp.miyavi.android.sumahoclaw.feature.files.OpenedState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AgentFileUiState(
    val name: String,
    val opened: OpenedState? = null,
    val confirmDiscard: Boolean = false,
) {
    /** 画面の中で済む「戻る」（確認・検索・編集）があるか。 */
    val handlesBackInPage: Boolean
        get() = confirmDiscard || (opened as? OpenedState.Ready)?.let { it.search != null || it.editing } == true
}

/**
 * エージェントのファイル 1 つ（spec 004 US1）。読む・編集・保存・衝突・作成・下書きは 002 と同じ [OpenedFileController]。
 * 下書きの鍵は `agent:<agentId>:<name>`。
 */
class AgentFileViewModel(
    agentId: String,
    name: String,
    files: FileSource,
    drafts: FileDraftDao,
) : ViewModel() {
    private val root = FileRoot.AgentFile(agentId)
    private val _state = MutableStateFlow(AgentFileUiState(name))
    val state: StateFlow<AgentFileUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<FilesEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<FilesEvent> = _events.asSharedFlow()

    private val opened = OpenedFileController(
        scope = viewModelScope,
        files = files,
        drafts = drafts,
        read = { _state.value.opened },
        write = { block -> _state.update { it.copy(opened = block(it.opened)) } },
        emit = { _events.tryEmit(it) },
    )

    init {
        opened.open(root, name)
    }

    fun retry() = opened.open(root, _state.value.name)

    /** 戻る操作。true なら処理した（false なら画面を閉じてよい）。 */
    fun back(): Boolean {
        val s = _state.value
        val current = s.opened as? OpenedState.Ready
        return when {
            s.confirmDiscard -> { _state.update { it.copy(confirmDiscard = false) }; true }
            current?.search != null -> { opened.closeSearch(); true }
            current != null && current.dirty -> { _state.update { it.copy(confirmDiscard = true) }; true }
            // 未作成のファイルは編集から始めるので、変更が無ければそのまま閉じる
            current != null && current.editing && !current.file.creating -> { opened.stopEditing(); true }
            else -> false
        }
    }

    fun dismissDiscard() = _state.update { it.copy(confirmDiscard = false) }

    /** 変更を捨てる。未作成のファイルは閉じる（false を返す）。 */
    fun discard(): Boolean {
        _state.update { it.copy(confirmDiscard = false) }
        val creating = (_state.value.opened as? OpenedState.Ready)?.file?.creating == true
        opened.stopEditing()
        return !creating
    }

    fun startEditing() = opened.startEditing()
    fun toggleCode() = opened.toggleCode()
    fun openSearch() = opened.openSearch()
    fun closeSearch() = opened.closeSearch()
    fun setSearchQuery(query: String) = opened.setSearchQuery(query)
    fun moveMatch(step: Int) = opened.moveMatch(step)
    fun edit(text: String, cursor: Int) = opened.edit(text, cursor)
    fun save(overwrite: Boolean = false) = opened.save(overwrite)
    fun reloadDiscarding() = opened.reloadDiscarding()
    fun dismissConflict() = opened.dismissConflict()
}
