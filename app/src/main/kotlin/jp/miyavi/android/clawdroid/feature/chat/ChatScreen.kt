package jp.miyavi.android.clawdroid.feature.chat

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.ChatMessage
import jp.miyavi.android.clawdroid.core.data.MessagePart
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.clawdroid.core.designsystem.GlassPill
import jp.miyavi.android.clawdroid.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.clawdroid.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.feature.chat.composer.ComposerPill
import jp.miyavi.android.clawdroid.feature.chat.composer.rememberVoiceInput
import jp.miyavi.android.clawdroid.feature.chat.message.AssistantMessage
import jp.miyavi.android.clawdroid.feature.chat.message.ImageOverlay
import jp.miyavi.android.clawdroid.feature.chat.message.LiveAssistantMessage
import jp.miyavi.android.clawdroid.feature.chat.message.ToolResultMessage
import jp.miyavi.android.clawdroid.feature.chat.message.UserMessage
import jp.miyavi.android.clawdroid.feature.chat.sidebar.SidebarContent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** 会話の画面の外への操作。 */
data class ChatNavigation(
    val openSettings: () -> Unit,
    val openAutomations: () -> Unit,
    val openTerminal: (sessionKey: String?) -> Unit,
    val openFiles: (sessionKey: String, agentId: String) -> Unit,
    val openUsage: () -> Unit,
    val restartGateway: () -> Unit,
)

@Composable
fun ChatScreen(viewModel: ChatViewModel, navigation: ChatNavigation, timeLabel: (Long) -> String) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val composer by viewModel.composer.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val sendFailed = stringResource(R.string.chat_send_failed)
    val retry = stringResource(R.string.action_retry)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ChatEvent.SendFailed -> if (snackbar.showSnackbar(sendFailed, retry, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) viewModel.send()
                is ChatEvent.Error -> snackbar.showSnackbar(event.message ?: sendFailed)
            }
        }
    }
    ChatContent(
        state = state,
        composer = composer,
        snackbar = snackbar,
        timeLabel = timeLabel,
        navigation = navigation,
        actions = ChatActions(
            selectAgent = viewModel::selectAgent,
            preloadAgent = viewModel::preloadAgent,
            selectSession = viewModel::selectSession,
            newChat = viewModel::newChat,
            rename = viewModel::rename,
            archive = viewModel::setArchived,
            textChange = viewModel::onTextChange,
            addImage = viewModel::addImage,
            removeImage = viewModel::removeImage,
            voiceText = viewModel::appendVoiceText,
            send = viewModel::send,
            stop = viewModel::abort,
            retryHistory = viewModel::retryHistory,
            reconnect = viewModel::reconnect,
        ),
    )
}

data class ChatActions(
    val selectAgent: (String) -> Unit,
    val preloadAgent: (String) -> Unit,
    val selectSession: (jp.miyavi.android.clawdroid.core.data.SessionSummary) -> Unit,
    val newChat: () -> Unit,
    val rename: (jp.miyavi.android.clawdroid.core.data.SessionSummary, String) -> Unit,
    val archive: (jp.miyavi.android.clawdroid.core.data.SessionSummary, Boolean) -> Unit,
    val textChange: (String) -> Unit,
    val addImage: (android.net.Uri) -> Unit,
    val removeImage: (android.net.Uri) -> Unit,
    val voiceText: (String) -> Unit,
    val send: () -> Unit,
    val stop: () -> Unit,
    val retryHistory: () -> Unit,
    val reconnect: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatContent(
    state: ChatScreenState,
    composer: ComposerState,
    snackbar: SnackbarHostState,
    timeLabel: (Long) -> String,
    navigation: ChatNavigation,
    actions: ChatActions,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val hazeState = rememberHazeState()
    val agents = state.agents.agents
    val selectedIndex = agents.indexOfFirst { it.id == state.selectedAgentId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { agents.size }

    // タブとページが止まった位置のエージェントを選ぶ（サイドバーの一覧と会話を同時に切り替える。FR-011）
    LaunchedEffect(pagerState, agents) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().drop(1).collect { page ->
            agents.getOrNull(page)?.let { actions.selectAgent(it.id) }
        }
    }
    LaunchedEffect(pagerState, agents) {
        snapshotFlow { pagerState.targetPage }.distinctUntilChanged().collect { page ->
            agents.getOrNull(page)?.let { actions.preloadAgent(it.id) }
        }
    }
    // 外から選択が変わったら（復元・通知から開くなど）ページを合わせる。位置の復元は動かさない
    LaunchedEffect(selectedIndex, agents.size) {
        if (agents.isNotEmpty() && pagerState.currentPage != selectedIndex && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(actions.addImage) }
    var openedImage by remember { mutableStateOf<MessagePart.Image?>(null) }
    val voiceDenied = stringResource(R.string.voice_permission_denied)
    val voiceError = stringResource(R.string.voice_error)
    val voice = rememberVoiceInput(
        onResult = actions.voiceText,
        onPermissionDenied = { scope.launch { snackbar.showSnackbar(voiceDenied) } },
        onError = { scope.launch { snackbar.showSnackbar(voiceError) } },
    )
    val copied = stringResource(R.string.copied)

    ProvideScreenHaze(hazeState) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    windowInsets = WindowInsets(0.dp),
                    modifier = Modifier.widthIn(max = Dimens.sidebarWidth),
                ) {
                    SidebarContent(
                        agents = agents,
                        pagerState = pagerState,
                        sessionsByAgent = state.sessionsByAgent,
                        selectedSessionKey = state.currentSessionKey,
                        timeLabel = timeLabel,
                        onSelectSession = { session ->
                            actions.selectSession(session)
                            scope.launch { drawerState.close() }
                        },
                        onRename = actions.rename,
                        onArchive = actions.archive,
                        onOpenAutomations = { scope.launch { drawerState.close() }; navigation.openAutomations() },
                        onOpenTerminal = { scope.launch { drawerState.close() }; navigation.openTerminal(state.currentSessionKey) },
                        onOpenSettings = { scope.launch { drawerState.close() }; navigation.openSettings() },
                    )
                }
            },
        ) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    val listState = rememberLazyListState()
                    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val bottomInset = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding()
                    MessageList(
                        state = state,
                        listState = listState,
                        contentPadding = PaddingValues(
                            start = Spacing.l,
                            end = Spacing.l,
                            top = topInset + Dimens.floatingButtonSize + Spacing.xl,
                            bottom = bottomInset + Dimens.minTouchTarget * 2 + Spacing.xl,
                        ),
                        onCopy = { text ->
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
                                snackbar.showSnackbar(copied)
                            }
                        },
                        onShare = { text ->
                            context.startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null),
                            )
                        },
                        onOpenImage = { openedImage = it },
                        onRetry = actions.retryHistory,
                        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    )

                    TopBar(
                        hazeState = hazeState,
                        state = state,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNewChat = actions.newChat,
                        navigation = navigation,
                        onReconnect = actions.reconnect,
                        modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars),
                    )

                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .imePadding()
                            .widthIn(max = Dimens.composerMaxWidth)
                            .padding(horizontal = Spacing.l, vertical = Spacing.s),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ScrollToBottomButton(listState = listState, hazeState = hazeState)
                        ComposerPill(
                            hazeState = hazeState,
                            state = composer,
                            running = state.chat?.running == true,
                            enabled = state.connection is ConnectionState.Connected && state.currentSession?.archived != true,
                            voice = voice,
                            onTextChange = actions.textChange,
                            onAttach = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onRemoveImage = actions.removeImage,
                            onSend = actions.send,
                            onStop = actions.stop,
                        )
                    }

                    GlassSnackbarHost(
                        snackbar,
                        Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = Dimens.minTouchTarget * 2),
                    )
                }
            }
        }
        ImageOverlay(image = openedImage, onDismiss = { openedImage = null })
    }
}

@Composable
private fun TopBar(
    hazeState: dev.chrisbanes.haze.HazeState,
    state: ChatScreenState,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    navigation: ChatNavigation,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            FloatingIconButton(
                hazeState = hazeState,
                icon = painterResource(R.drawable.ic_huge_menu),
                contentDescription = stringResource(R.string.sidebar_open_cd),
                onClick = onOpenDrawer,
            )
            // ヘッダの中央: セッション名と作業フォルダ名（FR-032）
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val title = state.currentSession?.title
                    ?: if (state.currentSessionKey == null) stringResource(R.string.session_untitled) else null
                val folder = state.currentSession?.cwd?.substringAfterLast('/')
                if (title != null) {
                    GlassPill(hazeState = hazeState, contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.xs)) {
                        Column(Modifier.heightIn(min = Dimens.compactTouchTarget), verticalArrangement = Arrangement.Center) {
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (folder != null) {
                                Text(folder, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
            FloatingIconButton(
                hazeState = hazeState,
                icon = painterResource(R.drawable.ic_huge_new_chat),
                contentDescription = stringResource(R.string.chat_new_cd),
                onClick = onNewChat,
            )
            Box {
                FloatingIconButton(
                    hazeState = hazeState,
                    icon = painterResource(R.drawable.ic_huge_more),
                    contentDescription = stringResource(R.string.chat_more_cd),
                    onClick = { menuOpen = true },
                )
                GlassDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val key = state.currentSessionKey
                    val agentId = state.selectedAgentId
                    if (key != null && agentId != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_files)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_huge_folder), null) },
                            onClick = { menuOpen = false; navigation.openFiles(key, agentId) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_terminal)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_huge_terminal), null) },
                        onClick = { menuOpen = false; navigation.openTerminal(key) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_usage)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_huge_usage), null) },
                        onClick = { menuOpen = false; navigation.openUsage() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_restart_gateway)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_huge_restart), null) },
                        onClick = { menuOpen = false; navigation.restartGateway() },
                    )
                }
            }
        }
        ConnectionBanner(state.connection, onReconnect, Modifier.padding(top = Spacing.s).align(Alignment.CenterHorizontally))
    }
}

/** 再接続中などの控えめな表示（Edge Cases）。接続済みのときは出さない。 */
@Composable
private fun ConnectionBanner(connection: ConnectionState, onReconnect: () -> Unit, modifier: Modifier = Modifier) {
    val text = when (connection) {
        is ConnectionState.Reconnecting, ConnectionState.Connecting -> stringResource(R.string.connection_reconnecting)
        ConnectionState.Restarting -> stringResource(R.string.connection_restarting)
        ConnectionState.Disconnected -> stringResource(R.string.connection_offline)
        is ConnectionState.Failed -> stringResource(R.string.connection_auth_expired)
        else -> null
    }
    AnimatedVisibility(visible = text != null, enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()), exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()), modifier = modifier) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
            Row(Modifier.padding(start = Spacing.l, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(text.orEmpty(), style = MaterialTheme.typography.labelMedium)
                if (connection !is ConnectionState.Connecting) {
                    TextButton(onClick = onReconnect) { Text(stringResource(R.string.connection_reconnect)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageList(
    state: ChatScreenState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onOpenImage: (MessagePart.Image) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chat = state.chat
    val messages = state.messages
    // 新しい行が増えたとき、一番下にいたなら一番下に付いていく（上を読んでいる最中は動かさない）
    val atBottom by remember { androidx.compose.runtime.derivedStateOf { !listState.canScrollForward } }
    val live = chat?.live
    LaunchedEffect(messages.size, live?.text?.length, state.currentSessionKey) {
        if (atBottom || listState.layoutInfo.totalItemsCount == 0) {
            val last = listState.layoutInfo.totalItemsCount - 1
            if (last >= 0) listState.scrollToItem(last, Int.MAX_VALUE / 2)
        }
    }
    when {
        state.currentSessionKey == null || (chat != null && !chat.loading && messages.isEmpty() && live == null) -> {
            Box(modifier, contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.chat_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
        chat == null || (chat.loading && messages.isEmpty()) -> Box(modifier, contentAlignment = Alignment.Center) { LoadingIndicator() }
        chat.error != null && messages.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.chat_load_failed), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
        else -> LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(messages, key = { it.message.id }, contentType = { it.message.role }) { item ->
                val message = item.message
                val itemModifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()
                when (message.role) {
                    ChatMessage.Role.USER -> UserMessage(message, onOpenImage, itemModifier)
                    ChatMessage.Role.ASSISTANT, ChatMessage.Role.SYSTEM -> AssistantMessage(
                        message = message,
                        onCopy = onCopy,
                        onShare = onShare,
                        onOpenImage = onOpenImage,
                        onShowFull = null,
                        modifier = itemModifier,
                    )
                    ChatMessage.Role.TOOL_RESULT -> ToolResultMessage(message, itemModifier)
                }
            }
            if (live != null && live.text.isNotBlank()) {
                item(key = "live-${live.runId}") {
                    LiveAssistantMessage(live.text, Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth())
                }
            } else if (chat.running) {
                item(key = "running") {
                    Box(Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()) { LoadingIndicator(Modifier.size(Dimens.minTouchTarget)) }
                }
            }
        }
    }
}

/** 上へスクロールしているときだけ出る「最新へ」のボタン。 */
@Composable
private fun ScrollToBottomButton(listState: LazyListState, hazeState: dev.chrisbanes.haze.HazeState) {
    val scope = rememberCoroutineScope()
    val show by remember { androidx.compose.runtime.derivedStateOf { listState.canScrollForward && listState.layoutInfo.totalItemsCount > 0 } }
    AnimatedVisibility(
        visible = show,
        enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        FloatingIconButton(
            hazeState = hazeState,
            icon = painterResource(R.drawable.ic_huge_arrow_down),
            contentDescription = stringResource(R.string.chat_scroll_bottom_cd),
            onClick = { scope.launch { listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0), Int.MAX_VALUE / 2) } },
            modifier = Modifier.padding(bottom = Spacing.s),
        )
    }
}
