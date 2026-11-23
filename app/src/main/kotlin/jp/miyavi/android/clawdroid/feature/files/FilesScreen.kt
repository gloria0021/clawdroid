package jp.miyavi.android.clawdroid.feature.files

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import jp.miyavi.android.clawdroid.navigation.popTransition
import jp.miyavi.android.clawdroid.navigation.pushTransition
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.DownloadSource
import jp.miyavi.android.clawdroid.core.data.FileRoot
import jp.miyavi.android.clawdroid.core.data.MessagePart
import jp.miyavi.android.clawdroid.core.designsystem.AppTitleBar
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.feature.chat.message.ImageOverlay

/** ファイルビューアの ViewModel をつなぐ部分（spec 002）。 */
@Composable
fun FilesScreen(viewModel: FilesViewModel, onSwitchToTerminal: (() -> Unit)? = null, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val saved = stringResource(R.string.files_saved)
    val saveFailed = stringResource(R.string.files_save_failed)
    val gone = stringResource(R.string.files_gone)
    val openLabel = stringResource(R.string.files_open_download)
    val noApp = stringResource(R.string.files_no_app)
    val downloadedFormat = stringResource(R.string.files_downloaded)
    val downloadReasons = mapOf(
        DownloadSource.Reason.TOO_LARGE to stringResource(R.string.files_download_too_large),
        DownloadSource.Reason.NO_HTTP to stringResource(R.string.files_download_no_http),
        DownloadSource.Reason.MISSING to stringResource(R.string.files_download_missing),
        DownloadSource.Reason.FAILED to stringResource(R.string.files_download_failed),
    )
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                FilesEvent.Saved -> snackbar.showSnackbar(saved)
                is FilesEvent.SaveFailed -> snackbar.showSnackbar(event.message?.let { "$saveFailed: $it" } ?: saveFailed)
                FilesEvent.FileGone -> snackbar.showSnackbar(gone, duration = SnackbarDuration.Long)
                is FilesEvent.DownloadFailed -> snackbar.showSnackbar(downloadReasons.getValue(event.reason))
                is FilesEvent.Downloaded -> {
                    val result = snackbar.showSnackbar(downloadedFormat.format(event.result.displayName), openLabel, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) {
                        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(event.result.uri, event.result.mimeType)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            snackbar.showSnackbar(noApp)
                        }
                    }
                }
            }
        }
    }
    FilesContent(
        state = state,
        snackbar = snackbar,
        onBack = { if (!viewModel.back()) onBack() },
        onSelectRoot = viewModel::selectRoot,
        onOpen = viewModel::openEntry,
        onDownloadEntry = { viewModel.download(it.path) },
        onRetry = viewModel::retry,
        onStartEditing = viewModel::startEditing,
        onEdit = viewModel::edit,
        onSave = { viewModel.save() },
        onDownload = { viewModel.download() },
        onDiscard = viewModel::discardAndClose,
        onDismissDiscard = viewModel::dismissDiscard,
        onOverwrite = { viewModel.save(overwrite = true) },
        onReload = viewModel::reloadDiscarding,
        onDismissConflict = viewModel::dismissConflict,
        onToggleCode = viewModel::toggleCode,
        onOpenSearch = viewModel::openSearch,
        onSearchQuery = viewModel::setSearchQuery,
        onMoveMatch = viewModel::moveMatch,
        onCloseSearch = viewModel::closeSearch,
        onSwitchToTerminal = onSwitchToTerminal,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilesContent(
    state: FilesUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSelectRoot: (FileRoot) -> Unit,
    onOpen: (jp.miyavi.android.clawdroid.core.data.FileEntry) -> Unit,
    onDownloadEntry: (jp.miyavi.android.clawdroid.core.data.FileEntry) -> Unit,
    onRetry: () -> Unit,
    onStartEditing: () -> Unit,
    onEdit: (String, Int) -> Unit,
    onSave: () -> Unit,
    onDownload: () -> Unit,
    onDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onOverwrite: () -> Unit,
    onReload: () -> Unit,
    onDismissConflict: () -> Unit,
    onToggleCode: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onSearchQuery: (String) -> Unit = {},
    onMoveMatch: (Int) -> Unit = {},
    onCloseSearch: () -> Unit = {},
    /** パネルの中ではターミナルへの切り替え（編集中は出さない。TopAppBar のアイコンは 2 つまで）。 */
    onSwitchToTerminal: (() -> Unit)? = null,
) {
    val hazeState = rememberHazeState()
    val htmlFind = remember { HtmlFindState() }
    val opened = state.opened
    val ready = opened as? OpenedState.Ready
    // 確認・検索・編集はページの中で戻す。ファイル → 一覧 → 上のフォルダは下の重なり（予測型「戻る」）で 1 段ずつ。
    // 根の一覧で戻ると画面ごと閉じる（外側の予測型「戻る」）
    BackHandler(enabled = state.handlesBackInPage, onBack = onBack)
    var overlayImage by remember { mutableStateOf<MessagePart.Image?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AppTitleBar(
                hazeState = hazeState,
                title = {
                    Column {
                        val rootLabel = stringResource(if (state.root is FileRoot.Session) R.string.files_root_session else R.string.files_root_workspace)
                        Text(
                            opened?.path?.substringAfterLast('/') ?: state.path.substringAfterLast('/').ifEmpty { stringResource(R.string.files_title) },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOf(rootLabel, (opened?.path ?: state.path).substringBeforeLast('/', "").ifEmpty { null }).filterNotNull().joinToString(" / "),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), onBack,
                        modifier = Modifier.padding(start = Spacing.s))
                },
                actions = {
                    // TopAppBar のアイコンは 2 つまで（AGENTS.md 2.6）: 保存（編集中）とダウンロード。編集は下のピル
                    if (ready != null) {
                        if (ready.editing) {
                            IconButton(onClick = onSave, enabled = ready.dirty && !ready.saving) {
                                Icon(painterResource(R.drawable.ic_huge_check), stringResource(R.string.files_save_cd))
                            }
                        }
                        IconButton(onClick = onDownload) { Icon(painterResource(R.drawable.ic_huge_download), stringResource(R.string.files_download_cd)) }
                    }
                    if (onSwitchToTerminal != null && ready?.editing != true) {
                        IconButton(onClick = onSwitchToTerminal) { Icon(painterResource(R.drawable.ic_huge_terminal), stringResource(R.string.menu_terminal)) }
                    }
                },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
    ) { padding ->
        // 文字のファイルを読んでいるあいだだけ、下に操作を浮かせる（編集中は出さない）
        val showControls = ready != null && !ready.editing && ready.file.text != null
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                val controlsSpace = if (showControls) Dimens.floatingButtonSize + Spacing.l else 0.dp
                val listPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.l + controlsSpace)
                // フォルダとファイルはページとして重ねる（Gerbil の板 → スレと同じ）。予測型「戻る」で下のページが見える
                val pages = remember(state.folderStack, opened?.path) {
                    state.folderStack.map { FilesPage.Folder(it) } + listOfNotNull(opened?.path?.let(FilesPage::File))
                }
                // NavDisplay はページの中身を作ったときの値で持ち続けるので、毎回いまの状態を読む中身を渡す
                // （渡さないと一覧が読み込み中のまま更新されない）
                val pageContent by rememberUpdatedState<@Composable (FilesPage) -> Unit> { page ->
                    when (page) {
                        is FilesPage.File -> FilePage(
                            state = state,
                            contentPadding = listPadding,
                            htmlFind = htmlFind,
                            onRetry = onRetry,
                            onEdit = onEdit,
                            onOpenImage = { image -> overlayImage = image },
                            onDownload = onDownload,
                        )
                        is FilesPage.Folder -> Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                            if (state.sessionRoot != null) RootSwitcher(state, onSelectRoot)
                            when (val listing = state.listings[page.path] ?: ListingState.Loading) {
                                ListingState.Loading -> LoadingOrFailed(failed = false, message = R.string.files_load_failed, onRetry = onRetry)
                                ListingState.Failed -> LoadingOrFailed(failed = true, message = R.string.files_load_failed, onRetry = onRetry)
                                is ListingState.Loaded -> FileList(
                                    listing = listing.listing,
                                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + Spacing.l),
                                    onOpen = onOpen,
                                    onDownload = onDownloadEntry,
                                )
                            }
                        }
                    }
                }
                NavDisplay(
                    backStack = pages,
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.surface),
                    transitionSpec = { pushTransition() },
                    popTransitionSpec = { popTransition() },
                    predictivePopTransitionSpec = { _ -> popTransition() },
                    entryProvider = { page -> NavEntry(page) { pageContent(page) } },
                )
            }
            if (showControls && ready != null) {
                val htmlPreview = ready.preview == PreviewFormat.HTML && !ready.showCode
                FileControls(
                    hazeState = hazeState,
                    state = FileControlsState(
                        canToggleCode = ready.preview != null,
                        showCode = ready.showCode,
                        editable = ready.file.editable,
                        search = ready.search,
                        htmlCounts = if (htmlPreview) htmlFind.active to htmlFind.count else null,
                    ),
                    actions = remember(htmlPreview, onToggleCode, onStartEditing, onOpenSearch, onSearchQuery, onMoveMatch, onCloseSearch) {
                        FileControlsActions(
                            onToggleCode = onToggleCode,
                            onEdit = onStartEditing,
                            onOpenSearch = onOpenSearch,
                            onQuery = onSearchQuery,
                            onPrevious = { if (htmlPreview) htmlFind.next(forward = false) else onMoveMatch(-1) },
                            onNext = { if (htmlPreview) htmlFind.next(forward = true) else onMoveMatch(1) },
                            onCloseSearch = onCloseSearch,
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(Spacing.l),
                )
            }
        }
    }
    ImageOverlay(overlayImage) { overlayImage = null }

    if (state.confirmDiscard) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            title = { Text(stringResource(R.string.files_discard_title)) },
            text = { Text(stringResource(R.string.files_discard_body)) },
            confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.files_discard_confirm)) } },
            dismissButton = { TextButton(onClick = onDismissDiscard) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (ready?.conflict == true) {
        AlertDialog(
            onDismissRequest = onDismissConflict,
            title = { Text(stringResource(R.string.files_conflict_title)) },
            text = { Text(stringResource(R.string.files_conflict_body)) },
            confirmButton = { TextButton(onClick = onOverwrite) { Text(stringResource(R.string.files_conflict_overwrite)) } },
            dismissButton = { TextButton(onClick = onReload) { Text(stringResource(R.string.files_conflict_reload)) } },
        )
    }
}

/** ファイルビューアの中のページ（フォルダの一覧と開いたファイル）。 */
private sealed interface FilesPage {
    data class Folder(val path: String) : FilesPage
    data class File(val path: String) : FilesPage
}

/** 開いたファイルのページ。プレビュー ⇄ コードは新しい側だけをフェードで出す（AGENTS.md 2.8）。 */
@Composable
private fun FilePage(
    state: FilesUiState,
    contentPadding: PaddingValues,
    htmlFind: HtmlFindState,
    onRetry: () -> Unit,
    onEdit: (String, Int) -> Unit,
    onOpenImage: (MessagePart.Image) -> Unit,
    onDownload: () -> Unit,
) {
    val opened = state.opened ?: return
    val ready = opened as? OpenedState.Ready
    FadeInOnChange(key = "${opened.path}:${ready?.showCode}") {
        when {
            opened is OpenedState.Loading -> LoadingOrFailed(failed = false, message = R.string.files_open_failed, onRetry = onRetry)
            opened is OpenedState.Failed -> LoadingOrFailed(
                failed = true,
                message = if (opened.missing) R.string.files_missing else R.string.files_open_failed,
                onRetry = onRetry,
            )
            ready != null -> FileViewer(
                state = ready,
                contentPadding = contentPadding,
                htmlFind = htmlFind,
                onEdit = onEdit,
                onOpenImage = { onOpenImage(MessagePart.Image(url = null, base64 = ready.file.imageBase64, mimeType = ready.file.mimeType)) },
                onDownload = onDownload,
            )
        }
    }
}

/** 作業フォルダ / ワークスペースの切り替え（FR-002）。 */
@Composable
private fun RootSwitcher(state: FilesUiState, onSelect: (FileRoot) -> Unit) {
    val roots = listOfNotNull(state.sessionRoot, state.workspaceRoot)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        roots.forEachIndexed { index, root ->
            SegmentedButton(
                selected = state.root == root,
                onClick = { onSelect(root) },
                shape = SegmentedButtonDefaults.itemShape(index, roots.size),
            ) {
                Text(stringResource(if (root is FileRoot.Session) R.string.files_root_session else R.string.files_root_workspace))
            }
        }
    }
}

/** [key] が変わったら、新しい中身だけを不透明度 0 から出す（古い中身と同時に組み立てない）。 */
@Composable
private fun FadeInOnChange(key: Any, content: @Composable () -> Unit) {
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    var first by remember { mutableStateOf(true) }
    androidx.compose.runtime.key(key) {
        // 最初の表示では動かさない（AGENTS.md 2.8）
        val alpha = remember { Animatable(if (first) 1f else 0f) }
        LaunchedEffect(Unit) {
            first = false
            alpha.animateTo(1f, spec)
        }
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) { content() }
    }
}
