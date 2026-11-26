package jp.miyavi.android.clawdroid.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.FileEntry
import jp.miyavi.android.clawdroid.core.data.FileOp
import jp.miyavi.android.clawdroid.core.data.FileOpResult
import jp.miyavi.android.clawdroid.core.data.FileRoot
import jp.miyavi.android.clawdroid.core.data.MessagePart
import jp.miyavi.android.clawdroid.core.designsystem.AppTitleBar
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.feature.chat.message.ImageOverlay
import kotlinx.coroutines.launch

/** ファイルビューアの ViewModel をつなぐ部分（spec 002・005）。 */
@Composable
fun FilesScreen(viewModel: FilesViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wrap by viewModel.wrap.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val saved = stringResource(R.string.files_saved)
    val saveFailed = stringResource(R.string.files_save_failed)
    val gone = stringResource(R.string.files_gone)
    val refreshFailed = stringResource(R.string.files_refresh_failed)
    val refreshed = stringResource(R.string.files_refreshed)
    val unchanged = stringResource(R.string.files_unchanged)
    val retry = stringResource(R.string.action_retry)
    val context = androidx.compose.ui.platform.LocalContext.current
    val eventScope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                FilesEvent.Saved -> snackbar.showSnackbar(saved)
                is FilesEvent.SaveFailed -> snackbar.showSnackbar(event.message?.let { "$saveFailed: $it" } ?: saveFailed)
                FilesEvent.FileGone -> snackbar.showSnackbar(gone, duration = SnackbarDuration.Long)
                is FilesEvent.Operated -> snackbar.showSnackbar(context.operationMessage(event.op, event.result))
                // 読み直した結果（中身が同じだと表示が変わらず、効いたか分からないため）
                is FilesEvent.Refreshed -> snackbar.showSnackbar(if (event.changed) refreshed else unchanged)
                // 次のお知らせを止めないよう、再試行の返事は別に待つ
                is FilesEvent.RefreshFailed -> eventScope.launch {
                    if (snackbar.showSnackbar(refreshFailed, actionLabel = retry, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                        if (event.tree) viewModel.refreshTree() else viewModel.refreshOpened()
                    }
                }
            }
        }
    }
    FilesContent(
        state = state,
        snackbar = snackbar,
        wrap = wrap,
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
        onToggleWrap = viewModel::toggleWrap,
        onOperate = viewModel::operate,
        onRefreshOpened = viewModel::refreshOpened,
        onRefreshTree = viewModel::refreshTree,
    )
}

/** ファイルの操作の結果の文言（spec 005 FR-067）。 */
private fun android.content.Context.operationMessage(op: FileOp, result: FileOpResult): String = when (result) {
    FileOpResult.Done -> when (op) {
        is FileOp.CreateFile -> getString(R.string.files_op_created, op.name)
        is FileOp.CreateFolder -> getString(R.string.files_op_created, op.name)
        is FileOp.Rename -> getString(R.string.files_op_renamed, op.newName)
        is FileOp.Delete -> getString(R.string.files_op_deleted, op.path.substringAfterLast('/'))
    }
    FileOpResult.AlreadyExists -> getString(R.string.files_name_error_exists)
    FileOpResult.OutsideRoot -> getString(R.string.files_op_outside)
    FileOpResult.RootUnavailable -> getString(R.string.files_op_unavailable)
    FileOpResult.NotFound -> getString(R.string.files_op_not_found)
    is FileOpResult.Failed -> getString(R.string.files_op_failed, result.message.orEmpty())
    FileOpResult.Unknown -> getString(R.string.files_op_unknown)
}

/**
 * ファイルの画面（spec 005 US4・US5）。2 ペインを置ける幅ではツリーを左に出したまま、置けなければツリーを左から引き出すパネルにする。
 * 出し分けは Material 3 Adaptive の区画数で決める（画面幅の数値で分けない。AGENTS.md 1）。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun FilesContent(
    state: FilesUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSelectRoot: (FileRoot) -> Unit,
    onOpen: (FileEntry) -> Unit,
    onDownloadEntry: (FileEntry) -> Unit,
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
    wrap: Boolean = false,
    onToggleWrap: () -> Unit = {},
    onOperate: (FileOp) -> Unit = {},
    /** タイトルバーのボタンで、開いているファイルを読み直す。 */
    onRefreshOpened: () -> Unit = {},
    /** 引き下げて、ツリーを読み直す。 */
    onRefreshTree: () -> Unit = {},
) {
    val hazeState = rememberHazeState()
    val htmlFind = remember { HtmlFindState() }
    val linkActions = jp.miyavi.android.clawdroid.feature.chat.message.rememberLinkActions(snackbar)
    val opened = state.opened
    val ready = opened as? OpenedState.Ready
    val scope = rememberCoroutineScope()
    BackHandler(enabled = state.handlesBackInPage, onBack = onBack)
    var overlayImage by remember { mutableStateOf<MessagePart.Image?>(null) }
    var treeAction by remember { mutableStateOf<TreeAction?>(null) }
    var textScale by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(1f) }
    val twoPane = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()).maxHorizontalPartitions >= 2
    // ファイルを開いていなければ、パネルを開いた状態で始める
    val drawerState = rememberDrawerState(if (opened == null) DrawerValue.Open else DrawerValue.Closed)
    // 開いたファイルの更新日時（中身を読めない大きなファイルは、ツリーの一覧の値）。「n 分前」は分ごとに進める
    val context = androidx.compose.ui.platform.LocalContext.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val metaFormat = remember(context, locale) { FileMetaFormat(context, locale) }
    val now by rememberMinuteClock()
    val openedUpdatedAt = ready?.file?.updatedAt ?: opened?.path?.let { state.tree.entry(it)?.updatedAt }
    val openedUpdated = openedUpdatedAt?.let { metaFormat.updated(it, now) }
    val treeListState = rememberLazyListState()
    // 編集中の入力欄は画面の上で持つ（記号の行と取り消しが同じ欄を触る）
    val editor = if (ready?.editing == true) rememberEditorState(ready, onEdit) else null

    val tree: @Composable (PaddingValues) -> Unit = { padding ->
        RefreshBox(refreshing = state.treeRefreshing, onRefresh = onRefreshTree, topInset = padding.calculateTopPadding()) {
        FileTree(
            state = state,
            listState = treeListState,
            contentPadding = padding,
            onSelectRoot = onSelectRoot,
            onOpen = { entry ->
                onOpen(entry)
                // ファイルを押したらパネルを閉じてコードを見せる
                if (!entry.isDirectory && !twoPane) scope.launch { drawerState.close() }
            },
            onDownload = onDownloadEntry,
            onAction = { treeAction = it },
            onRetry = onRetry,
        )
        }
    }

    val screen: @Composable () -> Unit = {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                AppTitleBar(
                    hazeState = hazeState,
                    title = {
                        Column {
                            val rootLabel = stringResource(if (state.root is FileRoot.Session) R.string.files_root_session else R.string.files_root_workspace)
                            Text(
                                opened?.path?.substringAfterLast('/') ?: stringResource(R.string.files_title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // ツリーの中でのパス（どのフォルダの中か。FR-043）と更新日時。パスは省略し、日時は切らない
                            Row {
                                Text(
                                    listOfNotNull(rootLabel, opened?.path?.substringBeforeLast('/', "")?.ifEmpty { null }).joinToString(" / "),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                openedUpdated?.let { Text(" · $it", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                            }
                        }
                    },
                    navigationIcon = {
                        Row(Modifier.padding(start = Spacing.s)) {
                            FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), onBack)
                            // ツリーを開く（スワイプだけに頼らない。TalkBack からも開ける。FR-041）
                            if (!twoPane) {
                                FloatingIconButton(
                                    hazeState, painterResource(R.drawable.ic_huge_file_tree), stringResource(R.string.files_tree_open_cd),
                                    { scope.launch { drawerState.open() } },
                                    modifier = Modifier.padding(start = Spacing.s),
                                )
                            }
                        }
                    },
                    actions = {
                        // TopAppBar のアイコンは 2 つまで（AGENTS.md 2.6）: 保存（編集中）とダウンロード、読むときはダウンロードと読み直し。
                        // 読み直しは引き下げではなくボタン（スクロールしない APK などでも効かせる。マスター指定 2026-11-26）
                        if (ready != null) {
                            if (ready.editing) {
                                IconButton(onClick = onSave, enabled = ready.dirty && !ready.saving) {
                                    Icon(painterResource(R.drawable.ic_huge_check), stringResource(R.string.files_save_cd))
                                }
                            }
                            IconButton(onClick = onDownload) { Icon(painterResource(R.drawable.ic_huge_download), stringResource(R.string.files_download_cd)) }
                            if (!ready.editing) {
                                IconButton(onClick = onRefreshOpened, enabled = !ready.refreshing) {
                                    Icon(painterResource(R.drawable.ic_huge_refresh), stringResource(R.string.files_refresh_cd))
                                }
                            }
                        }
                    },
                )
            },
            snackbarHost = { GlassSnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        ) { padding ->
            val codeView = ready != null && ready.showCode && ready.file.text != null
            val showControls = ready != null && !ready.editing && ready.file.text != null
            Box(Modifier.fillMaxSize()) {
                // WebView（HTML のプレビュー）はガラスの透かしに取り込まない（取り込むと描画のスレッドで落ちる）
                val showsWebView = ready != null && ready.preview == PreviewFormat.HTML && !ready.showCode && !ready.editing
                Row(Modifier.fillMaxSize().then(if (showsWebView) Modifier else Modifier.hazeSource(hazeState))) {
                    if (twoPane) {
                        Box(Modifier.width(Dimens.sidebarWidth).fillMaxHeight()) {
                            tree(PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.l))
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        val controlsSpace = if (showControls) Dimens.floatingButtonSize + Spacing.l else 0.dp
                        val contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.l + controlsSpace)
                        if (opened == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (!twoPane) TextButton(onClick = { scope.launch { drawerState.open() } }) { Text(stringResource(R.string.files_tree_open_cd)) }
                            }
                        } else {
                            // ピンチで文字の大きさを 50〜200% に（文字だけを大きくし、折り返しも組み直す。マスター指定 2026-11-24）
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            Box(Modifier.fillMaxSize().pinchToZoom { zoom -> textScale = (textScale * zoom).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE) }) {
                                androidx.compose.runtime.CompositionLocalProvider(
                                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, density.fontScale * textScale),
                                ) {
                                    FilePage(
                                        opened = opened,
                                        contentPadding = contentPadding,
                                        htmlFind = htmlFind,
                                        onRetry = onRetry,
                                        onEdit = onEdit,
                                        onOpenImage = { image -> overlayImage = image },
                                        onDownload = onDownload,
                                        editor = editor,
                                        wrap = wrap,
                                    )
                                }
                            }
                        }
                    }
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
                            codeTools = codeView,
                            wrap = wrap,
                        ),
                        actions = remember(htmlPreview, onToggleCode, onStartEditing, onOpenSearch, onSearchQuery, onMoveMatch, onCloseSearch, onToggleWrap) {
                            FileControlsActions(
                                onToggleCode = onToggleCode,
                                onEdit = onStartEditing,
                                onOpenSearch = onOpenSearch,
                                onQuery = onSearchQuery,
                                onPrevious = { if (htmlPreview) htmlFind.next(forward = false) else onMoveMatch(-1) },
                                onNext = { if (htmlPreview) htmlFind.next(forward = true) else onMoveMatch(1) },
                                onCloseSearch = onCloseSearch,
                                onToggleWrap = onToggleWrap,
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .then(if (twoPane) Modifier.padding(start = Dimens.sidebarWidth) else Modifier)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(Spacing.l),
                    )
                }
                // 編集中は、キーボードの上に取り消し・やり直しと記号の行（FR-055・056）
                if (editor != null) {
                    SymbolRow(
                        hazeState = hazeState,
                        state = editor,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(Spacing.s),
                    )
                }
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(jp.miyavi.android.clawdroid.feature.chat.message.LocalLinkActions provides linkActions) {
        if (twoPane) {
            screen()
        } else {
            // 画面全体のスワイプで開くと、コードのスクロールを横取りする。開くのは左端の細い帯からだけにし、
            // 開いたあとは全体のスワイプで閉じられる（マスター指摘 2026-11-24）
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen || drawerState.isAnimationRunning,
                drawerContent = {
                    ModalDrawerSheet(drawerState = drawerState) {
                        tree(PaddingValues(top = Spacing.s, bottom = Spacing.l))
                    }
                },
            ) {
                Box(Modifier.fillMaxSize().edgeSwipeToOpen { scope.launch { drawerState.open() } }) {
                    screen()
                }
            }
        }
    }
    ImageOverlay(overlayImage) { overlayImage = null }

    treeAction?.let { action ->
        val parent = when (action) {
            is TreeAction.NewFile -> action.parent
            is TreeAction.NewFolder -> action.parent
            is TreeAction.Rename -> action.entry.path.substringBeforeLast('/', "")
            is TreeAction.Delete -> action.entry.path.substringBeforeLast('/', "")
        }
        // 保存していない変更のあるファイル（またはそれを含むフォルダ）の名前の変更・削除は、変更が失われることも示す
        val target = (action as? TreeAction.Rename)?.entry?.path ?: (action as? TreeAction.Delete)?.entry?.path
        val losesEdits = target != null && ready?.dirty == true && (ready.path == target || ready.path.startsWith("$target/"))
        FileOpDialog(action, state.tree.namesIn(parent), losesEdits, onDismiss = { treeAction = null }) { op ->
            treeAction = null
            onOperate(op)
        }
    }

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

/** 変更を捨てる確認と、保存の衝突（上書き / 読み直し）。004 のエージェントのファイルでも使う。 */
@Composable
internal fun FileDialogs(
    confirmDiscard: Boolean,
    conflict: Boolean,
    onDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onOverwrite: () -> Unit,
    onReload: () -> Unit,
    onDismissConflict: () -> Unit,
) {
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            title = { Text(stringResource(R.string.files_discard_title)) },
            text = { Text(stringResource(R.string.files_discard_body)) },
            confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.files_discard_confirm)) } },
            dismissButton = { TextButton(onClick = onDismissDiscard) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (conflict) {
        AlertDialog(
            onDismissRequest = onDismissConflict,
            title = { Text(stringResource(R.string.files_conflict_title)) },
            text = { Text(stringResource(R.string.files_conflict_body)) },
            confirmButton = { TextButton(onClick = onOverwrite) { Text(stringResource(R.string.files_conflict_overwrite)) } },
            dismissButton = { TextButton(onClick = onReload) { Text(stringResource(R.string.files_conflict_reload)) } },
        )
    }
}

/** 開いたファイルのページ。プレビュー ⇄ コードは新しい側だけをフェードで出す（AGENTS.md 2.8）。 */
@Composable
internal fun FilePage(
    opened: OpenedState?,
    contentPadding: PaddingValues,
    htmlFind: HtmlFindState,
    onRetry: () -> Unit,
    onEdit: (String, Int) -> Unit,
    onOpenImage: (MessagePart.Image) -> Unit,
    onDownload: () -> Unit,
    editor: androidx.compose.foundation.text.input.TextFieldState? = null,
    wrap: Boolean = true,
) {
    opened ?: return
    val ready = opened as? OpenedState.Ready
    FadeInOnChange(key = "${opened.path}:${ready?.showCode}:${ready?.editing}") {
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
                editor = editor,
                wrap = wrap,
            )
        }
    }
}

/**
 * 引き下げて読み直す枠（ツリーだけ。M3 の PullToRefreshBox。印は Expressive の LoadingIndicator）。
 * 中身はタイトルバーの下まで敷いているので、印はタイトルバーの下から出す。影は付けない（AGENTS.md 2.5）。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    topInset: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    val state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = refreshing,
                elevation = 0.dp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset),
            )
        },
    ) { content() }
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

/**
 * 2 本指のピンチだけを拾う（1 本指のスクロールは下の一覧に任せる）。一覧より先に受け取るため Initial の段で読み、
 * 2 本指のあいだだけ使い切る。
 */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) onZoom(zoom)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

private const val MIN_TEXT_SCALE = 0.5f
private const val MAX_TEXT_SCALE = 2f

/**
 * 左端の細い帯（端末の「戻る」のジェスチャーの帯のすぐ内側）から右へスワイプすると、ツリーを開く。
 * 帯を部品として上に重ねると下のボタン（左下の切り替えなど）のタップを奪うので、画面全体の Initial の段で見張り、
 * 帯から始まった横向きのドラッグだと分かったときだけ使い切る（タップや縦のスクロールは下にそのまま渡る）。
 */
@Composable
private fun Modifier.edgeSwipeToOpen(onOpen: () -> Unit): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gestureInset = androidx.compose.foundation.layout.WindowInsets.systemGestures.getLeft(density, androidx.compose.ui.platform.LocalLayoutDirection.current)
    val band = with(density) { Spacing.xl.toPx() }
    val threshold = band
    val latestOpen by androidx.compose.runtime.rememberUpdatedState(onOpen)
    return pointerInput(gestureInset, band) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.x < gestureInset || down.position.x > gestureInset + band) return@awaitEachGesture
            var claimed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (!claimed) {
                    // 縦の動きが勝ったら手を引く（スクロールに任せる）
                    if (kotlin.math.abs(dy) > viewConfiguration.touchSlop && kotlin.math.abs(dy) > kotlin.math.abs(dx)) break
                    if (dx > viewConfiguration.touchSlop && dx > kotlin.math.abs(dy)) claimed = true
                }
                if (claimed) {
                    change.consume()
                    if (dx > threshold) { latestOpen(); break }
                }
            }
        }
    }
}
