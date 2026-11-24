package jp.miyavi.android.clawdroid.feature.agent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.designsystem.AppTitleBar
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.clawdroid.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.data.MessagePart
import jp.miyavi.android.clawdroid.feature.chat.message.ImageOverlay
import jp.miyavi.android.clawdroid.feature.files.FileControls
import jp.miyavi.android.clawdroid.feature.files.FileControlsActions
import jp.miyavi.android.clawdroid.feature.files.FileControlsState
import jp.miyavi.android.clawdroid.feature.files.FileDialogs
import jp.miyavi.android.clawdroid.feature.files.FilePage
import jp.miyavi.android.clawdroid.feature.files.FilesEvent
import jp.miyavi.android.clawdroid.feature.files.HtmlFindState
import jp.miyavi.android.clawdroid.feature.files.OpenedState

/** エージェントのファイル 1 つ（spec 004 US1、contracts/ui.md）。002 のファイルの画面と同じ部品で描く。 */
@Composable
fun AgentFileScreen(viewModel: AgentFileViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val saved = stringResource(R.string.files_saved)
    val saveFailed = stringResource(R.string.files_save_failed)
    val gone = stringResource(R.string.files_gone)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                FilesEvent.Saved -> snackbar.showSnackbar(saved)
                is FilesEvent.SaveFailed -> snackbar.showSnackbar(event.message?.let { "$saveFailed: $it" } ?: saveFailed)
                FilesEvent.FileGone -> snackbar.showSnackbar(gone, duration = SnackbarDuration.Long)
                // エージェントのファイルでは作成・削除をしない
                is FilesEvent.Operated -> Unit
            }
        }
    }
    AgentFileContent(
        state = state,
        snackbar = snackbar,
        onBack = { if (!viewModel.back()) onBack() },
        onRetry = viewModel::retry,
        onStartEditing = viewModel::startEditing,
        onEdit = viewModel::edit,
        onSave = { viewModel.save() },
        onDiscard = { if (!viewModel.discard()) onBack() },
        onDismissDiscard = viewModel::dismissDiscard,
        onOverwrite = { viewModel.save(overwrite = true) },
        onReload = viewModel::reloadDiscarding,
        onDismissConflict = viewModel::dismissConflict,
        onToggleCode = viewModel::toggleCode,
        onOpenSearch = viewModel::openSearch,
        onSearchQuery = viewModel::setSearchQuery,
        onMoveMatch = viewModel::moveMatch,
        onCloseSearch = viewModel::closeSearch,
    )
}

@Composable
internal fun AgentFileContent(
    state: AgentFileUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStartEditing: () -> Unit,
    onEdit: (String, Int) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onOverwrite: () -> Unit,
    onReload: () -> Unit,
    onDismissConflict: () -> Unit,
    onToggleCode: () -> Unit,
    onOpenSearch: () -> Unit,
    onSearchQuery: (String) -> Unit,
    onMoveMatch: (Int) -> Unit,
    onCloseSearch: () -> Unit,
) {
    val hazeState = rememberHazeState()
    val htmlFind = remember { HtmlFindState() }
    val ready = state.opened as? OpenedState.Ready
    // 確認・検索・編集はページの中で戻す。それ以外は画面の重なり（予測型「戻る」）に任せる
    BackHandler(enabled = state.handlesBackInPage, onBack = onBack)
    var overlayImage by remember { mutableStateOf<MessagePart.Image?>(null) }
    ProvideScreenHaze(hazeState) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                AppTitleBar(
                    hazeState = hazeState,
                    title = { Text(state.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), onBack,
                            modifier = Modifier.padding(start = Spacing.s))
                    },
                    actions = {
                        if (ready?.editing == true) {
                            IconButton(onClick = onSave, enabled = ready.dirty && !ready.saving) {
                                Icon(painterResource(R.drawable.ic_huge_check), stringResource(R.string.files_save_cd))
                            }
                        }
                    },
                )
            },
            snackbarHost = { GlassSnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        ) { padding ->
            val showControls = ready != null && !ready.editing && ready.file.text != null
            Box(Modifier.fillMaxSize()) {
                // WebView（HTML のプレビュー）はガラスの透かしに取り込まない（取り込むと描画のスレッドで落ちる）
                val showsWebView = ready != null && ready.preview == jp.miyavi.android.clawdroid.feature.files.PreviewFormat.HTML && !ready.showCode && !ready.editing
                Column(Modifier.fillMaxSize().then(if (showsWebView) Modifier else Modifier.hazeSource(hazeState))) {
                    val controlsSpace = if (showControls) Dimens.floatingButtonSize + Spacing.l else 0.dp
                    // 大きすぎて編集できないときは、本文の上に理由を 1 行（FR-024）
                    val tooLarge = ready?.file?.tooLargeToEdit == true
                    if (tooLarge) {
                        Text(
                            stringResource(R.string.agent_file_too_large),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = padding.calculateTopPadding()).padding(horizontal = Spacing.l, vertical = Spacing.s),
                        )
                    }
                    FilePage(
                        opened = state.opened,
                        contentPadding = PaddingValues(
                            top = if (tooLarge) 0.dp else padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + Spacing.l + controlsSpace,
                        ),
                        htmlFind = htmlFind,
                        onRetry = onRetry,
                        onEdit = onEdit,
                        onOpenImage = { overlayImage = it },
                        onDownload = {},
                    )
                }
                if (showControls && ready != null) {
                    FileControls(
                        hazeState = hazeState,
                        state = FileControlsState(
                            canToggleCode = ready.preview != null,
                            showCode = ready.showCode,
                            editable = ready.file.editable,
                            search = ready.search,
                            htmlCounts = null,
                        ),
                        actions = remember(onToggleCode, onStartEditing, onOpenSearch, onSearchQuery, onMoveMatch, onCloseSearch) {
                            FileControlsActions(
                                onToggleCode = onToggleCode,
                                onEdit = onStartEditing,
                                onOpenSearch = onOpenSearch,
                                onQuery = onSearchQuery,
                                onPrevious = { onMoveMatch(-1) },
                                onNext = { onMoveMatch(1) },
                                onCloseSearch = onCloseSearch,
                            )
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(Spacing.l),
                    )
                }
            }
        }
    }
    ImageOverlay(overlayImage) { overlayImage = null }
    FileDialogs(
        confirmDiscard = state.confirmDiscard,
        conflict = ready?.conflict == true,
        onDiscard = onDiscard,
        onDismissDiscard = onDismissDiscard,
        onOverwrite = onOverwrite,
        onReload = onReload,
        onDismissConflict = onDismissConflict,
    )
}
