package jp.miyavi.android.sumahoclaw.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.rosemoe.sora.text.Content
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.designsystem.AppTitleBar
import jp.miyavi.android.sumahoclaw.core.designsystem.FloatingIconButton
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.sumahoclaw.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.sumahoclaw.core.designsystem.SaveStateIcon
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.text.TextCharset
import jp.miyavi.android.sumahoclaw.feature.files.SymbolRow
import jp.miyavi.android.sumahoclaw.feature.files.indentUnit
import jp.miyavi.android.sumahoclaw.feature.files.CodeLanguage

/** 画面の操作（ViewModel を直接渡さない。AGENTS.md 1）。 */
class EditorActions(
    val onBack: () -> Unit,
    val onSave: () -> Unit,
    val onSaveAs: () -> Unit,
    val onChanged: () -> Unit,
    val onSelection: (EditorSelection) -> Unit,
    val onToggleWrap: () -> Unit,
    val onSelectLanguage: (CodeLanguage?) -> Unit,
    val onSelectCharset: (TextCharset) -> Unit,
    val onSearching: (Boolean) -> Unit,
    val onCopyAll: () -> Unit,
    val onDownload: (() -> Unit)?,
    val onSaveAndClose: () -> Unit,
    val onDiscardAndClose: () -> Unit,
    val onDismissDialog: () -> Unit,
    val onOverwrite: () -> Unit,
    val onReloadDiscarding: () -> Unit,
    val onAlignAndSave: () -> Unit,
    val onConfirmReload: (TextCharset) -> Unit,
)

/**
 * エディターの画面（spec 008 contracts/editor-ui.md）。上はガラスの TopAppBar、本文は Sora、下は検索・記号の行・情報の帯。
 * 本文は Haze の透かす元に入れない（View の描画を取り込まない。WebView と同じ扱い）。
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorContent(
    state: EditorUiState,
    content: Content?,
    handle: EditorHandle,
    initialLine: Int,
    snackbar: SnackbarHostState,
    actions: EditorActions,
) {
    val hazeState = rememberHazeState()
    var menu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val canSave = state.phase == EditorPhase.READY && !state.saving && (state.dirty || !state.writable)
    val writable by androidx.compose.runtime.rememberUpdatedState(state.writable)
    BackHandler(enabled = state.searching || state.dirty || state.dialog != null) {
        if (state.searching) {
            handle.stopSearch()
            actions.onSearching(false)
        } else actions.onBack()
    }
    ProvideScreenHaze(hazeState) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                AppTitleBar(
                    hazeState = hazeState,
                    title = {
                        Text(state.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        Row(Modifier.padding(start = Spacing.s)) {
                            FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.editor_back_cd), actions.onBack)
                        }
                    },
                    actions = {
                        // 保存・折り返し・言語・検索は下に浮くピル（AGENTS.md 2.7）。TopAppBar には ⋮ だけ（全文をコピー・ダウンロード・別の名前で保存）
                        if (state.phase == EditorPhase.READY) {
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_huge_more), stringResource(R.string.editor_more_cd)) }
                                GlassDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.editor_menu_copy_all)) }, onClick = { menu = false; actions.onCopyAll() })
                                    actions.onDownload?.let { download ->
                                        DropdownMenuItem(text = { Text(stringResource(R.string.files_download_cd)) }, onClick = { menu = false; download() })
                                    }
                                    DropdownMenuItem(text = { Text(stringResource(R.string.editor_menu_save_as)) }, onClick = { menu = false; actions.onSaveAs() })
                                }
                            }
                        }
                    },
                )
            },
            snackbarHost = { GlassSnackbarHost(snackbar, Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                // ガラスが透かす元: 背景（本文の外の帯の後ろ）と、本文（下に浮くピルの後ろ）
                Box(Modifier.matchParentSize().hazeSource(hazeState))
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding())
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            state.phase == EditorPhase.FAILED -> Text(
                                stringResource(state.failure?.message ?: R.string.editor_open_failed),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                            )
                            content == null || state.phase == EditorPhase.LOADING ->
                                LoadingIndicator(Modifier.align(Alignment.Center))
                            else -> {
                                val motion = MaterialTheme.motionScheme
                                // そろったらフェードで出す（AGENTS.md 2.8 のプレースホルダーからの入れ替え）
                                val shown = remember { MutableTransitionState(false).apply { targetState = true } }
                                androidx.compose.animation.AnimatedVisibility(visibleState = shown, enter = fadeIn(motion.defaultEffectsSpec()), exit = fadeOut(motion.fastEffectsSpec())) {
                                    // 本文を入れ替えたら（文字コードの選び直し・読み直し）部品を作り直す
                                    key(state.contentVersion) {
                                        SoraEditor(
                                            content = content,
                                            language = state.language,
                                            wrap = state.wrap,
                                            editable = true,
                                            handle = handle,
                                            initialLine = initialLine,
                                            onChanged = actions.onChanged,
                                            onSelection = actions.onSelection,
                                            onSaveShortcut = if (state.writable) actions.onSave else actions.onSaveAs,
                                            onSearchShortcut = { actions.onSearching(true) },
                                            // 本文もガラスの透かす元にする（下に浮くピルの後ろで文字が透ける）。Sora は普通の Canvas で描く View で、
                                            // WebView（別の描画の仕組み）とは違うので取り込める。ピルは本文の外（兄弟）に置き、自分を透かさない
                                            modifier = Modifier.fillMaxSize().hazeSource(hazeState, zIndex = 1f),
                                        )
                                    }
                                }
                            }
                        }
                        // 下に浮くピル（保存 | 折り返し・言語・検索）。キーボードが出ているあいだは検索中だけ出す
                        val motion = MaterialTheme.motionScheme
                        androidx.compose.animation.AnimatedVisibility(
                            visible = state.phase == EditorPhase.READY && (state.searching || !WindowInsets.isImeVisible),
                            enter = fadeIn(motion.defaultEffectsSpec()),
                            exit = fadeOut(motion.fastEffectsSpec()),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
                        ) {
                            EditorControls(
                                hazeState = hazeState,
                                state = EditorControlsState(
                                    writable = state.writable,
                                    canSave = canSave,
                                    saving = state.saving,
                                    savedTick = state.savedTick,
                                    wrap = state.wrap,
                                    language = state.language,
                                    searching = state.searching,
                                    query = query,
                                    matchIndex = handle.matchIndex,
                                    matchCount = handle.matchCount,
                                ),
                                actions = remember(handle, actions) {
                                    EditorControlsActions(
                                        onSave = { if (writable) actions.onSave() else actions.onSaveAs() },
                                        onToggleWrap = actions.onToggleWrap,
                                        onSelectLanguage = actions.onSelectLanguage,
                                        onOpenSearch = { actions.onSearching(true) },
                                        onQuery = { q -> query = q; handle.search(q) },
                                        onPrevious = handle::previous,
                                        onNext = handle::next,
                                        onCloseSearch = { handle.stopSearch(); actions.onSearching(false) },
                                    )
                                },
                            )
                        }
                    }
                    if (!state.searching && WindowInsets.isImeVisible && content != null && state.phase == EditorPhase.READY) {
                        // キーボードが出ているあいだは、ピルの代わりに記号の行（先頭に保存）
                        val indent = remember(content, state.contentVersion) { indentUnit(content.toString().take(INDENT_SAMPLE_CHARS)) }
                        SymbolRow(
                            hazeState = hazeState,
                            canUndo = handle.canUndo,
                            canRedo = handle.canRedo,
                            indent = indent,
                            onUndo = handle::undo,
                            onRedo = handle::redo,
                            onInsert = handle::insert,
                            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                            leading = {
                                IconButton(onClick = if (state.writable) actions.onSave else actions.onSaveAs, enabled = canSave) {
                                    SaveStateIcon(saving = state.saving, savedTick = state.savedTick) {
                                        Icon(painterResource(R.drawable.ic_huge_check), stringResource(if (state.writable) R.string.files_save_cd else R.string.editor_menu_save_as))
                                    }
                                }
                            },
                        )
                    }
                    if (state.phase == EditorPhase.READY) {
                        EditorStatusBar(
                            hazeState = hazeState,
                            state = StatusBarState(
                                line = state.line,
                                column = state.column,
                                chars = state.stats.chars,
                                selectedChars = state.selectedChars,
                                lines = state.stats.lines,
                                charset = state.format.charset,
                                bom = state.format.bom,
                                lineSeparator = state.format.lineSeparator,
                                language = state.language,
                                canChangeCharset = state.canChangeCharset,
                            ),
                            onSelectCharset = actions.onSelectCharset,
                            onSelectLanguage = actions.onSelectLanguage,
                        )
                    }
                }
            }
        }
    }
    EditorDialogs(state, actions)
}

@Composable
private fun EditorDialogs(state: EditorUiState, actions: EditorActions) {
    when (val dialog = state.dialog) {
        EditorDialog.ConfirmLeave -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text(stringResource(R.string.editor_leave_title)) },
            text = { Text(stringResource(R.string.editor_leave_body)) },
            confirmButton = { TextButton(onClick = if (state.writable) actions.onSaveAndClose else actions.onSaveAs) { Text(stringResource(R.string.editor_leave_save)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = actions.onDismissDialog) { Text(stringResource(R.string.editor_leave_keep)) }
                    TextButton(onClick = actions.onDiscardAndClose) { Text(stringResource(R.string.editor_leave_discard)) }
                }
            },
        )
        EditorDialog.Conflict -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text(stringResource(R.string.files_conflict_title)) },
            text = { Text(stringResource(R.string.files_conflict_body)) },
            confirmButton = { TextButton(onClick = actions.onOverwrite) { Text(stringResource(R.string.files_conflict_overwrite)) } },
            dismissButton = { TextButton(onClick = actions.onReloadDiscarding) { Text(stringResource(R.string.files_conflict_reload)) } },
        )
        EditorDialog.ConfirmSeparators -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text(stringResource(R.string.editor_line_separator_title)) },
            text = { Text(stringResource(R.string.editor_line_separator_body, state.format.dominant.label)) },
            confirmButton = { TextButton(onClick = actions.onAlignAndSave) { Text(stringResource(R.string.editor_line_separator_confirm)) } },
            dismissButton = { TextButton(onClick = actions.onDismissDialog) { Text(stringResource(R.string.editor_leave_keep)) } },
        )
        is EditorDialog.ConfirmReload -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text(stringResource(R.string.editor_reload_title)) },
            text = { Text(stringResource(R.string.editor_reload_body)) },
            confirmButton = { TextButton(onClick = { actions.onConfirmReload(dialog.charset) }) { Text(stringResource(R.string.editor_reload_confirm)) } },
            dismissButton = { TextButton(onClick = actions.onDismissDialog) { Text(stringResource(R.string.editor_leave_keep)) } },
        )
        null -> Unit
    }
}

/** 字下げの単位を決めるために読む先頭の文字数（大きいファイルで全文を見ない）。 */
private const val INDENT_SAMPLE_CHARS = 64 * 1024
