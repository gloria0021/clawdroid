package jp.miyavi.android.clawdroid.feature.files

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.DownloadSource
import jp.miyavi.android.clawdroid.core.data.FileRoot
import jp.miyavi.android.clawdroid.core.data.MessagePart
import jp.miyavi.android.clawdroid.core.designsystem.AppTitleBar
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.feature.chat.message.ImageOverlay

/** ファイルビューアの ViewModel をつなぐ部分（spec 002）。 */
@Composable
fun FilesScreen(viewModel: FilesViewModel, onBack: () -> Unit) {
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
) {
    val hazeState = rememberHazeState()
    val opened = state.opened
    val ready = opened as? OpenedState.Ready
    // 戻るは 1 段ずつ（ファイル → 一覧 → 上のフォルダ）。根の一覧で戻ると画面を閉じる（予測型「戻る」に任せる）
    BackHandler(enabled = opened != null || !state.atRoot || state.confirmDiscard, onBack = onBack)
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
                    // TopAppBar のアイコンは 2 つまで（AGENTS.md 2.6）: 編集 / 保存 と ダウンロード
                    if (ready != null) {
                        if (ready.editing) {
                            IconButton(onClick = onSave, enabled = ready.dirty && !ready.saving) {
                                Icon(painterResource(R.drawable.ic_huge_check), stringResource(R.string.files_save_cd))
                            }
                        } else if (ready.file.editable) {
                            IconButton(onClick = onStartEditing) { Icon(painterResource(R.drawable.ic_huge_edit), stringResource(R.string.files_edit_cd)) }
                        }
                        IconButton(onClick = onDownload) { Icon(painterResource(R.drawable.ic_huge_download), stringResource(R.string.files_download_cd)) }
                    }
                },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
    ) { padding ->
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            val listPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.l)
            // 一覧 ⇄ 開いたファイル、フォルダの移動は、新しい側だけをフェードで出す（AGENTS.md 2.8）
            FadeInOnChange(key = opened?.path ?: "list:${state.root}:${state.path}") {
                when {
                    opened is OpenedState.Loading -> LoadingOrFailed(failed = false, message = R.string.files_open_failed, onRetry = onRetry)
                    opened is OpenedState.Failed -> LoadingOrFailed(
                        failed = true,
                        message = if (opened.missing) R.string.files_missing else R.string.files_open_failed,
                        onRetry = onRetry,
                    )
                    ready != null -> FileViewer(
                        state = ready,
                        contentPadding = listPadding,
                        onEdit = onEdit,
                        onOpenImage = { overlayImage = MessagePart.Image(url = null, base64 = ready.file.imageBase64, mimeType = ready.file.mimeType) },
                        onDownload = onDownload,
                    )
                    else -> Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                        if (state.sessionRoot != null) RootSwitcher(state, onSelectRoot)
                        when (val listing = state.listing) {
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
