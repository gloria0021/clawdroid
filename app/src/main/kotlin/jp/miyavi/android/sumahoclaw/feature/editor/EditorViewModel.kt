package jp.miyavi.android.sumahoclaw.feature.editor

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rosemoe.sora.text.Content
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.database.FileDraftDao
import jp.miyavi.android.sumahoclaw.core.database.FileDraftEntity
import jp.miyavi.android.sumahoclaw.core.text.DocumentFormat
import jp.miyavi.android.sumahoclaw.core.text.LineSeparator
import jp.miyavi.android.sumahoclaw.core.text.StatusCounter
import jp.miyavi.android.sumahoclaw.core.text.TextCharset
import jp.miyavi.android.sumahoclaw.core.text.TextDecoding
import jp.miyavi.android.sumahoclaw.core.text.TextStats
import jp.miyavi.android.sumahoclaw.feature.files.CodeHighlighter
import jp.miyavi.android.sumahoclaw.feature.files.CodeLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** エディターの好み（`SettingsStore` の口。テストで差し替える）。 */
interface EditorPrefs {
    fun wrap(prose: Boolean): Flow<Boolean>
    suspend fun setWrap(prose: Boolean, wrap: Boolean)
    val languages: Flow<Map<String, String>>
    suspend fun setLanguage(key: String, language: String?)
}

enum class EditorPhase { LOADING, READY, FAILED }

enum class EditorFailure(@StringRes val message: Int) {
    NOT_TEXT(R.string.editor_not_text),
    TOO_LARGE(R.string.editor_open_too_large),
    MISSING(R.string.files_missing),
    NO_PERMISSION(R.string.editor_permission_lost),
    FAILED(R.string.editor_open_failed),
}

sealed interface EditorDialog {
    data object ConfirmLeave : EditorDialog
    data object Conflict : EditorDialog
    data object ConfirmSeparators : EditorDialog
    data class ConfirmReload(val charset: TextCharset) : EditorDialog
}

/** 画面への一度きりの知らせ。 */
sealed interface EditorEvent {
    data class Message(@StringRes val text: Int, val args: List<Any> = emptyList(), val retry: Boolean = false) : EditorEvent
    data class MoveTo(val offset: Int) : EditorEvent
    /** 保存先の上限を超えた（大きさの表示は画面で端末の書き方にする）。 */
    data class TooLarge(val limitBytes: Long) : EditorEvent
    data class CopyAll(val text: String) : EditorEvent
    /** 「別の名前で保存」の場所を選ばせる。 */
    data class SaveAs(val name: String) : EditorEvent
    data object Close : EditorEvent
}

data class EditorUiState(
    val title: String,
    val phase: EditorPhase = EditorPhase.LOADING,
    val failure: EditorFailure? = null,
    val format: DocumentFormat = DocumentFormat(),
    val language: CodeLanguage? = null,
    val wrap: Boolean = false,
    val writable: Boolean = true,
    val canChangeCharset: Boolean = false,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** 保存できた回数（保存のアイコンの合図に使う）。 */
    val savedTick: Int = 0,
    val stats: TextStats = TextStats(0, 1),
    /** 行・列（1 始まり。列は見た目の文字で数える）。 */
    val line: Int = 1,
    val column: Int = 1,
    /** 選んでいる文字数（選んでいなければ null）。 */
    val selectedChars: Int? = null,
    val dialog: EditorDialog? = null,
    val searching: Boolean = false,
    val restoredDraft: Boolean = false,
    /** 本文を入れ替えた回数（文字コードの選び直し・読み直しで部品を作り直す）。 */
    val contentVersion: Int = 0,
)

/**
 * エディター（spec 008）。本文は Sora の [Content] で持ち、UI の状態には写さない（2 MB を毎打鍵で作り直さない）。
 * 下書きは打鍵が止まって 1 秒・裏に回ったとき・閉じるときに書く（research R7）。
 */
class EditorViewModel(
    private val target: EditTarget,
    private val source: DocumentSource,
    private val drafts: FileDraftDao,
    private val prefs: EditorPrefs,
    private val results: EditorResults,
    /** 「別の名前で保存」で選ばれた場所に書く。 */
    private val writeAs: suspend (Uri, String, DocumentFormat) -> SaveOutcome,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState(title = target.name, canChangeCharset = source.canChangeCharset, writable = source.writable))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _content = MutableStateFlow<Content?>(null)
    val content: StateFlow<Content?> = _content.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    /** 最後に保存した（開いた）本文。変更があるかを確かめる。null なら比べる元が無い（下書きから戻した）。 */
    private var baseline: String? = null
    private var baseHash: String? = null
    private var bytes: ByteArray? = null
    private var cursor = 0
    private var closeAfterSave = false
    private var draftJob: Job? = null
    private var statsJob: Job? = null
    private var selectionJob: Job? = null
    private var wrapJob: Job? = null

    init {
        viewModelScope.launch { load(reloadWith = null, useDraft = true) }
    }

    private suspend fun load(reloadWith: TextCharset?, useDraft: Boolean) {
        val draft = if (useDraft) drafts.get(target.draftKey) else null
        val result = source.load()
        var restored = false
        val (text, format) = when (result) {
            is LoadResult.Loaded -> {
                bytes = result.bytes
                val base = if (reloadWith != null && result.bytes != null) TextDecoding.decodeAs(result.bytes, reloadWith) else result.decoded
                baseline = base.text
                if (draft != null && draft.content != base.text) {
                    restored = true
                    baseHash = draft.baseHash.ifEmpty { result.baseHash }
                    draft.content to draft.format(base.format)
                } else {
                    baseHash = result.baseHash
                    base.text to base.format
                }
            }
            else -> {
                if (draft == null) {
                    _state.update { it.copy(phase = EditorPhase.FAILED, failure = result.failure()) }
                    return
                }
                // 読めなくても下書きは見せる（許可が切れた・未接続。FR-074）
                restored = true
                baseline = null
                baseHash = draft.baseHash.ifEmpty { null }
                draft.content to draft.format(DocumentFormat())
            }
        }
        cursor = draft?.cursor ?: 0
        val content = withContext(Dispatchers.Default) { Content(text) }
        val languages = prefs.languages.first()
        val language = languages[target.draftKey]?.let { name -> CodeLanguage.entries.firstOrNull { it.name == name } }
            ?: if (languages.containsKey(target.draftKey)) null else CodeHighlighter.languageFor(target.name)
        _content.value = content
        _state.update {
            it.copy(
                phase = EditorPhase.READY,
                failure = null,
                format = format,
                language = language,
                dirty = restored,
                restoredDraft = restored,
                writable = source.writable && (result is LoadResult.Loaded || source !is LocalDocumentSource),
                contentVersion = it.contentVersion + 1,
                dialog = null,
            )
        }
        followWrap(language)
        recount()
        if (restored) _events.tryEmit(EditorEvent.Message(R.string.editor_restored_draft))
    }

    /** 折り返しの好みは、種類（コード / 文章）ごと。 */
    private fun followWrap(language: CodeLanguage?) {
        wrapJob?.cancel()
        wrapJob = viewModelScope.launch {
            prefs.wrap(isProse(language)).collect { wrap -> _state.update { it.copy(wrap = wrap) } }
        }
    }

    /** 本文が変わった（打鍵・貼り付け・取り消し）。 */
    fun onChanged() {
        if (!_state.value.dirty) _state.update { it.copy(dirty = true) }
        scheduleDraft()
        recount()
    }

    fun onSelection(selection: EditorSelection) {
        cursor = selection.start
        val content = _content.value ?: return
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            delay(SELECTION_DEBOUNCE_MS)
            val lineText = runCatching { content.getLine(selection.line).toString() }.getOrDefault("")
            val selected = if (selection.end > selection.start) runCatching { content.subSequence(selection.start, selection.end).toString() }.getOrNull() else null
            val column = withContext(Dispatchers.Default) { StatusCounter.graphemes(lineText, 0, selection.column.coerceAtMost(lineText.length)) + 1 }
            val chars = selected?.let { withContext(Dispatchers.Default) { StatusCounter.graphemes(it, 0, it.length) } }
            _state.update { it.copy(line = selection.line + 1, column = column, selectedChars = chars) }
        }
    }

    private fun recount() {
        val content = _content.value ?: return
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            delay(STATS_DEBOUNCE_MS)
            val text = content.toString()
            val stats = withContext(Dispatchers.Default) { StatusCounter.stats(TextDecoding.normalize(text)) }
            _state.update { it.copy(stats = stats) }
        }
    }

    private fun scheduleDraft() {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE_MS)
            writeDraft()
        }
    }

    /** 下書きを書く（秘密の値を含みそうなファイルは残さない。spec 005 FR-068）。変更が無ければ消す。 */
    private suspend fun writeDraft() {
        val content = _content.value ?: return
        val text = TextDecoding.normalize(content.toString())
        val s = _state.value
        withContext(NonCancellable) {
            if (source.secret || text == baseline) {
                drafts.delete(target.draftKey)
            } else {
                drafts.upsert(
                    FileDraftEntity(
                        key = target.draftKey,
                        content = text,
                        baseHash = baseHash.orEmpty(),
                        cursor = cursor,
                        updatedAt = System.currentTimeMillis(),
                        charset = s.format.charset.name,
                        bom = s.format.bom,
                        lineSeparator = s.format.lineSeparator.name,
                    ),
                )
            }
        }
    }

    /** 裏に回った・閉じる前（打鍵の間引きを待たずに書く）。 */
    fun flushDraft() {
        if (!_state.value.dirty) return
        draftJob?.cancel()
        viewModelScope.launch { writeDraft() }
    }

    /** 同じ対象が別の画面で開かれた（FR-007）。下書きを書いてから閉じる。 */
    suspend fun closeKeepingDraft() {
        draftJob?.cancel()
        if (_state.value.dirty) writeDraft()
        _events.emit(EditorEvent.Close)
    }

    fun save(alignSeparators: Boolean = false, overwrite: Boolean = false) {
        val s = _state.value
        val content = _content.value ?: return
        if (s.saving || s.phase != EditorPhase.READY) return
        if (!s.writable) {
            _events.tryEmit(EditorEvent.SaveAs(target.name))
            return
        }
        if (s.format.lineSeparator == LineSeparator.MIXED && !alignSeparators) {
            _state.update { it.copy(dialog = EditorDialog.ConfirmSeparators) }
            return
        }
        val text = TextDecoding.normalize(content.toString())
        _state.update { it.copy(saving = true, dialog = null) }
        viewModelScope.launch {
            val outcome = source.save(text, s.format, baseHash, overwrite)
            _state.update { it.copy(saving = false) }
            handle(outcome, text)
        }
    }

    private suspend fun handle(outcome: SaveOutcome, text: String) {
        when (outcome) {
            is SaveOutcome.Saved -> {
                draftJob?.cancel()
                drafts.delete(target.draftKey)
                baseline = text
                outcome.baseHash?.let { baseHash = it }
                val f = _state.value.format
                _state.update {
                    it.copy(
                        dirty = _content.value?.let { c -> TextDecoding.normalize(c.toString()) != text } == true,
                        savedTick = it.savedTick + 1,
                        restoredDraft = false,
                        format = if (f.lineSeparator == LineSeparator.MIXED) f.copy(lineSeparator = f.dominant) else f,
                    )
                }
                if (target is EditTarget.GatewayFile && outcome.file != null) results.emit(EditorResult.Saved(target, outcome.file))
                _events.emit(EditorEvent.Message(R.string.files_saved))
                if (closeAfterSave) _events.emit(EditorEvent.Close)
            }
            SaveOutcome.Conflict -> _state.update { it.copy(dialog = EditorDialog.Conflict) }
            SaveOutcome.Missing -> _events.emit(EditorEvent.Message(R.string.files_gone))
            is SaveOutcome.TooLarge -> _events.emit(EditorEvent.TooLarge(outcome.limitBytes))
            is SaveOutcome.Unmappable -> {
                _events.emit(EditorEvent.MoveTo(outcome.offset))
                _events.emit(EditorEvent.Message(R.string.editor_unmappable, listOf(_state.value.format.charset.label)))
            }
            SaveOutcome.ReadOnly -> {
                _state.update { it.copy(writable = false) }
                _events.emit(EditorEvent.Message(if (target is EditTarget.PastedText) R.string.editor_paste_gone else R.string.editor_read_only))
            }
            is SaveOutcome.Failed -> _events.emit(EditorEvent.Message(R.string.files_save_failed, retry = true))
        }
        if (outcome !is SaveOutcome.Saved) closeAfterSave = false
    }

    /** 「別の名前で保存」で選ばれた場所に書く。 */
    fun saveAs(uri: Uri) {
        val content = _content.value ?: return
        val text = TextDecoding.normalize(content.toString())
        val format = _state.value.format
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val outcome = writeAs(uri, text, format)
            _state.update { it.copy(saving = false) }
            when (outcome) {
                is SaveOutcome.Saved -> {
                    draftJob?.cancel()
                    drafts.delete(target.draftKey)
                    baseline = text
                    _state.update { it.copy(dirty = false, savedTick = it.savedTick + 1) }
                    _events.emit(EditorEvent.Message(R.string.files_saved))
                    if (closeAfterSave) _events.emit(EditorEvent.Close)
                }
                else -> handle(outcome, text)
            }
        }
    }

    /** 戻る。閉じてよければ true（変更があれば確認を出して false）。 */
    fun requestClose(): Boolean {
        val s = _state.value
        if (s.dialog != null) {
            _state.update { it.copy(dialog = null) }
            return false
        }
        val content = _content.value
        val changed = s.dirty && content != null && (baseline == null || TextDecoding.normalize(content.toString()) != baseline)
        if (changed) {
            _state.update { it.copy(dialog = EditorDialog.ConfirmLeave) }
            return false
        }
        if (s.dirty) viewModelScope.launch { drafts.delete(target.draftKey) }
        return true
    }

    fun saveAndClose() {
        closeAfterSave = true
        _state.update { it.copy(dialog = null) }
        save()
    }

    fun discardAndClose() {
        draftJob?.cancel()
        _state.update { it.copy(dialog = null) }
        viewModelScope.launch {
            withContext(NonCancellable) { drafts.delete(target.draftKey) }
            _events.emit(EditorEvent.Close)
        }
    }

    fun dismissDialog() = _state.update { it.copy(dialog = null) }

    /** 衝突のあと「変更を捨てて読み直す」。 */
    fun reloadDiscarding() {
        draftJob?.cancel()
        _state.update { it.copy(dialog = null, phase = EditorPhase.LOADING) }
        viewModelScope.launch {
            drafts.delete(target.draftKey)
            load(reloadWith = null, useDraft = false)
        }
    }

    fun overwrite() = save(alignSeparators = true, overwrite = true)

    fun setLanguage(language: CodeLanguage?) {
        _state.update { it.copy(language = language) }
        followWrap(language)
        viewModelScope.launch { prefs.setLanguage(target.draftKey, language?.name ?: "") }
    }

    fun toggleWrap() {
        val s = _state.value
        viewModelScope.launch { prefs.setWrap(isProse(s.language), !s.wrap) }
    }

    /** 文字コードを選び直す（変更があれば先に確かめる。FR-041）。 */
    fun selectCharset(charset: TextCharset, confirmed: Boolean = false) {
        val s = _state.value
        if (!s.canChangeCharset || charset == s.format.charset && !confirmed) return
        if (s.dirty && !confirmed) {
            _state.update { it.copy(dialog = EditorDialog.ConfirmReload(charset)) }
            return
        }
        val data = bytes ?: return
        draftJob?.cancel()
        _state.update { it.copy(dialog = null) }
        viewModelScope.launch {
            drafts.delete(target.draftKey)
            val decoded = withContext(Dispatchers.Default) { TextDecoding.decodeAs(data, charset) }
            baseline = decoded.text
            _content.value = withContext(Dispatchers.Default) { Content(decoded.text) }
            _state.update { it.copy(format = decoded.format, dirty = false, restoredDraft = false, contentVersion = it.contentVersion + 1) }
            recount()
        }
    }

    fun setSearching(searching: Boolean) = _state.update { it.copy(searching = searching) }

    fun copyAll() {
        val content = _content.value ?: return
        _events.tryEmit(EditorEvent.CopyAll(TextDecoding.normalize(content.toString())))
    }

    /** 開いた時点の行（呼び出し元で読んでいた位置）。下書きから戻したときはカーソルの位置を優先する。 */
    val restoredCursor: Int get() = cursor

    private fun LoadResult.failure(): EditorFailure = when (this) {
        LoadResult.NotText -> EditorFailure.NOT_TEXT
        LoadResult.TooLarge -> EditorFailure.TOO_LARGE
        LoadResult.Missing -> EditorFailure.MISSING
        LoadResult.NoPermission -> EditorFailure.NO_PERMISSION
        else -> EditorFailure.FAILED
    }

    private fun FileDraftEntity.format(fallback: DocumentFormat): DocumentFormat {
        val charset = TextCharset.entries.firstOrNull { it.name == this.charset } ?: fallback.charset
        val separator = LineSeparator.entries.firstOrNull { it.name == lineSeparator } ?: fallback.lineSeparator
        return DocumentFormat(charset, bom, separator, if (separator == LineSeparator.MIXED) fallback.dominant else separator)
    }

    companion object {
        const val DRAFT_DEBOUNCE_MS = 1_000L
        const val STATS_DEBOUNCE_MS = 150L
        const val SELECTION_DEBOUNCE_MS = 80L

        /** 文章として扱う（既定で折り返す）か。色分けの言語が無い・Markdown なら文章。 */
        fun isProse(language: CodeLanguage?): Boolean = language == null || language == CodeLanguage.MARKDOWN
    }
}
