package jp.miyavi.android.sumahoclaw.feature.editor

import android.graphics.Typeface
import android.view.KeyEvent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.subscribeAlways
import jp.miyavi.android.sumahoclaw.core.designsystem.CodeColors
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.bodyContainerColor
import jp.miyavi.android.sumahoclaw.core.designsystem.codeColors
import jp.miyavi.android.sumahoclaw.core.designsystem.codeTextStyle
import jp.miyavi.android.sumahoclaw.feature.files.CodeLanguage
import jp.miyavi.android.sumahoclaw.feature.files.TokenKind
import jp.miyavi.android.sumahoclaw.feature.files.of

/** カーソルと選択（情報の帯と下書きに使う）。位置は Sora の本文の文字の位置。 */
data class EditorSelection(val line: Int, val column: Int, val start: Int, val end: Int)

/**
 * Compose から Sora の部品を操作する口（取り消し・記号・検索・位置）。画面ごとに 1 つ。
 * Sora に触れるのは、この口と [SoraEditor]・[HighlightLanguage] だけ（plan の Structure Decision）。
 */
class EditorHandle {
    internal var view: CodeEditor? = null

    var canUndo by mutableStateOf(false)
        internal set
    var canRedo by mutableStateOf(false)
        internal set

    /** 検索の一致の数と、今の一致（0 始まり。無ければ -1）。 */
    var matchCount by mutableStateOf(0)
        private set
    var matchIndex by mutableStateOf(-1)
        private set

    fun undo() {
        view?.undo()
        refreshUndo()
    }

    fun redo() {
        view?.redo()
        refreshUndo()
    }

    /** カーソルの位置に文字を入れる（記号の行から。選んでいれば置き換える）。 */
    fun insert(text: String) {
        val v = view ?: return
        v.commitText(text, false)
    }

    fun search(query: String) {
        val v = view ?: return
        if (query.isEmpty()) {
            v.searcher.stopSearch()
            matchCount = 0
            matchIndex = -1
            return
        }
        v.searcher.search(query, EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true))
    }

    /** 検索の結果が出たとき・移ったときに数え直す（Sora は裏で数えるので、選択の変化のたびに読む）。 */
    internal fun refreshSearch() {
        val searcher = view?.searcher ?: return
        if (!searcher.hasQuery()) { matchCount = 0; matchIndex = -1; return }
        matchCount = runCatching { searcher.matchedPositionCount }.getOrDefault(0)
        matchIndex = runCatching { searcher.currentMatchedPositionIndex }.getOrDefault(-1)
    }

    fun next() {
        val searcher = view?.searcher?.takeIf { it.hasQuery() } ?: return
        runCatching { searcher.gotoNext() }
        refreshSearch()
    }

    fun previous() {
        val searcher = view?.searcher?.takeIf { it.hasQuery() } ?: return
        runCatching { searcher.gotoPrevious() }
        refreshSearch()
    }

    fun stopSearch() {
        view?.searcher?.stopSearch()
        matchCount = 0
        matchIndex = -1
    }

    /** 本文の文字の位置へカーソルを移して見せる（書けない文字の位置など）。 */
    fun moveTo(offset: Int) {
        val v = view ?: return
        val pos = v.text.indexer.getCharPosition(offset.coerceIn(0, v.text.length))
        v.setSelection(pos.line, pos.column, true)
    }

    fun showKeyboard() {
        view?.showSoftInput()
    }

    internal fun refreshUndo() {
        val v = view ?: return
        canUndo = v.canUndo()
        canRedo = v.canRedo()
    }
}

/** 一度だけ設定すればよいものを、変わったときだけ当て直すための覚え。 */
private class Applied {
    var colors: ColorScheme? = null
    var textSize = 0f
    var language: Any? = Unit
    var wrap: Boolean? = null
    var editable: Boolean? = null
}

/**
 * Sora の CodeEditor（spec 008 research R1・R6）。本文は [content]（ViewModel が持つ。構成の変更で取り消しの履歴を失わない）。
 * 色は `MaterialTheme.colorScheme` から写し、区切り線・ブロックの線・拡大縮小・補完・自動の括弧は使わない。
 */
@Composable
fun SoraEditor(
    content: Content,
    language: CodeLanguage?,
    wrap: Boolean,
    editable: Boolean,
    handle: EditorHandle,
    initialLine: Int,
    onChanged: () -> Unit,
    onSelection: (EditorSelection) -> Unit,
    onSaveShortcut: () -> Unit,
    onSearchShortcut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val currentLine = bodyContainerColor()
    val textSizePx = with(LocalDensity.current) { codeTextStyle().fontSize.toPx() }
    val changed by rememberUpdatedState(onChanged)
    val selection by rememberUpdatedState(onSelection)
    val save by rememberUpdatedState(onSaveShortcut)
    val find by rememberUpdatedState(onSearchShortcut)
    val applied = remember { Applied() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).apply {
                setTypefaceText(Typeface.MONOSPACE)
                setTypefaceLineNumber(Typeface.MONOSPACE)
                setLineNumberEnabled(true)
                setPinLineNumber(true)
                setHighlightCurrentLine(true)
                setHighlightCurrentBlock(false)
                setBlockLineEnabled(false)
                setDividerWidth(0f)
                setDividerMargin(Spacing.s.value * resources.displayMetrics.density)
                setScalable(false)
                setCursorAnimationEnabled(false)
                setDisplayLnPanel(false)
                setHighlightBracketPair(false)
                props.symbolPairAutoCompletion = false
                props.drawSideBlockLine = false
                props.highlightMatchingDelimiters = false
                props.stickyScroll = false
                props.overScrollEnabled = false
                setText(content, true, null)
                // 開いたときに、呼び出し元で読んでいた行を見せる（キーボードは出さない。FR-003）
                post { if (initialLine > 0) setSelection(initialLine.coerceAtMost(lineCount - 1), 0, true) }
                subscribeAlways<ContentChangeEvent> { event ->
                    handle.refreshUndo()
                    if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) changed()
                }
                subscribeAlways<SelectionChangeEvent> {
                    val c = cursor
                    handle.refreshSearch()
                    selection(EditorSelection(c.leftLine, c.leftColumn, c.left, c.right))
                }
                subscribeAlways<KeyBindingEvent> { event ->
                    if (event.action != KeyEvent.ACTION_DOWN || !event.isCtrlPressed) return@subscribeAlways
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_S -> { save(); event.markAsConsumed() }
                        KeyEvent.KEYCODE_F -> { find(); event.markAsConsumed() }
                    }
                }
                handle.view = this
                handle.refreshUndo()
            }
        },
        update = { view ->
            if (applied.colors != colors) {
                applied.colors = colors
                view.setColorScheme(editorColorScheme(colors, codeColors(colors), currentLine))
            }
            if (applied.textSize != textSizePx) {
                applied.textSize = textSizePx
                view.setTextSizePx(textSizePx)
            }
            if (applied.language != language) {
                applied.language = language
                view.setEditorLanguage(HighlightLanguage(language))
            }
            if (applied.wrap != wrap) {
                applied.wrap = wrap
                view.setWordwrap(wrap)
            }
            if (applied.editable != editable) {
                applied.editable = editable
                view.setEditable(editable)
            }
        },
        onRelease = { view ->
            if (handle.view === view) handle.view = null
            view.release()
        },
    )
}

/**
 * Sora の配色を、アプリのテーマから作る（research R6）。Sora は 1 つの配色を 1 つの部品にしか渡せないので、毎回新しく作る。
 */
internal fun editorColorScheme(colors: ColorScheme, code: CodeColors, currentLine: Color): EditorColorScheme =
    (object : EditorColorScheme(colors.surface.luminance() < 0.5f) {}).apply {
        fun set(id: Int, color: Color) = setColor(id, color.toArgb())
        set(EditorColorScheme.WHOLE_BACKGROUND, colors.surface)
        set(EditorColorScheme.TEXT_NORMAL, colors.onSurface)
        set(EditorColorScheme.LINE_NUMBER_BACKGROUND, colors.surface)
        set(EditorColorScheme.LINE_NUMBER, colors.onSurfaceVariant)
        set(EditorColorScheme.LINE_NUMBER_CURRENT, colors.onSurface)
        set(EditorColorScheme.LINE_DIVIDER, Color.Transparent)
        set(EditorColorScheme.CURRENT_LINE, currentLine)
        set(EditorColorScheme.CURRENT_ROW_BORDER, Color.Transparent)
        set(EditorColorScheme.SELECTED_TEXT_BACKGROUND, colors.primary.copy(alpha = SELECTION_ALPHA).compositeOver(colors.surface))
        set(EditorColorScheme.SELECTED_TEXT_BORDER, Color.Transparent)
        set(EditorColorScheme.SELECTION_INSERT, colors.primary)
        set(EditorColorScheme.SELECTION_HANDLE, colors.primary)
        set(EditorColorScheme.MATCHED_TEXT_BACKGROUND, colors.tertiaryContainer)
        set(EditorColorScheme.MATCHED_TEXT_BORDER, Color.Transparent)
        set(EditorColorScheme.SCROLL_BAR_THUMB, colors.onSurfaceVariant.copy(alpha = SCROLL_BAR_ALPHA))
        set(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, colors.onSurfaceVariant)
        set(EditorColorScheme.SCROLL_BAR_TRACK, Color.Transparent)
        set(EditorColorScheme.BLOCK_LINE, Color.Transparent)
        set(EditorColorScheme.BLOCK_LINE_CURRENT, Color.Transparent)
        set(EditorColorScheme.SIDE_BLOCK_LINE, Color.Transparent)
        set(EditorColorScheme.UNDERLINE, colors.primary)
        set(EditorColorScheme.NON_PRINTABLE_CHAR, colors.onSurfaceVariant)
        set(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, colors.surfaceContainerHigh)
        set(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, colors.onSurface)
        set(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, colors.onSurface)
        set(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, Color.Transparent)
        set(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, Color.Transparent)
        TokenKind.entries.forEach { set(colorIdOf(it), code.of(it)) }
    }

/** 選択の背景に重ねる primary の量（M3 の文字選択と同じくらい）。 */
private const val SELECTION_ALPHA = 0.3f

/** スクロールバーのつまみの不透明度（ふだんは控えめ）。 */
private const val SCROLL_BAR_ALPHA = 0.5f
