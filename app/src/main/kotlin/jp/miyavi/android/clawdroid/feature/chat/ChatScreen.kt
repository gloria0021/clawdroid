package jp.miyavi.android.clawdroid.feature.chat

import jp.miyavi.android.clawdroid.feature.chat.message.ThinkingIndicator
import jp.miyavi.android.clawdroid.core.data.LocalInboundImages
import jp.miyavi.android.clawdroid.core.data.InboundImageSource
import jp.miyavi.android.clawdroid.core.data.InboundImageLoader
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import android.content.ClipData
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.ChatMessage
import jp.miyavi.android.clawdroid.core.data.MessagePart
import jp.miyavi.android.clawdroid.core.data.ModelCatalog
import jp.miyavi.android.clawdroid.core.data.ProviderUsage
import jp.miyavi.android.clawdroid.core.data.SessionSetting
import jp.miyavi.android.clawdroid.core.designsystem.AppTitleBar
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.clawdroid.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.clawdroid.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.feature.chat.composer.ComposerPill
import jp.miyavi.android.clawdroid.feature.chat.composer.ComposerSessionSheet
import jp.miyavi.android.clawdroid.feature.chat.composer.SettingAvailability
import jp.miyavi.android.clawdroid.feature.chat.composer.rememberVoiceInput
import jp.miyavi.android.clawdroid.feature.chat.message.AssistantMessage
import jp.miyavi.android.clawdroid.feature.chat.message.ImageOverlay
import jp.miyavi.android.clawdroid.feature.chat.message.LiveAssistantMessage
import jp.miyavi.android.clawdroid.feature.chat.message.ToolGroup
import jp.miyavi.android.clawdroid.feature.chat.message.UserMessage
import jp.miyavi.android.clawdroid.feature.chat.message.VideoPreviewSheet
import jp.miyavi.android.clawdroid.feature.chat.message.rememberChatLinkOpener
import jp.miyavi.android.clawdroid.feature.chat.message.webUri
import jp.miyavi.android.clawdroid.feature.chat.sidebar.SidePanel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** 会話の画面の外への操作。 */
data class ChatNavigation(
    val openSettings: () -> Unit,
    val openAutomations: () -> Unit,
    val openTerminal: (sessionKey: String?) -> Unit,
    val openFiles: (sessionKey: String, agentId: String) -> Unit,
    val restartGateway: () -> Unit,
)

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    navigation: ChatNavigation,
    timeLabel: (Long) -> String,
    inboundImages: InboundImageLoader? = null,
) {
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
        inboundImages = inboundImages,
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
            reply = viewModel::replyTo,
            addImage = viewModel::addImage,
            removeImage = viewModel::removeImage,
            voiceText = viewModel::appendVoiceText,
            send = viewModel::send,
            stop = viewModel::abort,
            retryHistory = viewModel::retryHistory,
            cancelPending = viewModel::cancelPending,
            loadUsage = viewModel::loadProviderUsage,
            loadModels = viewModel::loadModelCatalog,
            updateSetting = viewModel::updateSessionSetting,
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
    val reply: (String) -> Unit,
    val addImage: (android.net.Uri) -> Unit,
    val removeImage: (android.net.Uri) -> Unit,
    val voiceText: (String) -> Unit,
    val send: () -> Unit,
    val stop: () -> Unit,
    val retryHistory: () -> Unit,
    val cancelPending: (String) -> Unit,
    val loadUsage: suspend () -> List<ProviderUsage>,
    val loadModels: suspend (String, String?) -> ModelCatalog?,
    val updateSetting: (SessionSetting, String?) -> Unit,
    val reconnect: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatContent(
    state: ChatScreenState,
    composer: ComposerState,
    snackbar: SnackbarHostState,
    timeLabel: (Long) -> String,
    navigation: ChatNavigation,
    actions: ChatActions,
    inboundImages: InboundImageLoader? = null,
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

    val clipboard = LocalClipboard.current
    val composerFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val connected = state.connection as? ConnectionState.Connected
    val canReply = connected != null && state.currentSession?.archived != true
    // 画像は一度に複数選べる（上限は MAX_ATTACH_IMAGES）
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACH_IMAGES)) { uris ->
        uris.forEach(actions.addImage)
    }
    var openedImage by remember { mutableStateOf<MessagePart.Image?>(null) }
    var openedVideo by rememberSaveable { mutableStateOf<String?>(null) }
    val voiceDenied = stringResource(R.string.voice_permission_denied)
    val voiceError = stringResource(R.string.voice_error)
    val voice = rememberVoiceInput(
        onResult = actions.voiceText,
        onPermissionDenied = { scope.launch { snackbar.showSnackbar(voiceDenied) } },
        onError = { scope.launch { snackbar.showSnackbar(voiceError) } },
    )
    val copied = stringResource(R.string.copied)
    var showSessionSheet by rememberSaveable { mutableStateOf(false) }
    val agent = state.selectedAgent
    val model = composer.sessionOptions[SessionSetting.MODEL] ?: state.currentSession?.model ?: agent?.defaultModel
    val effort = composer.sessionOptions[SessionSetting.EFFORT] ?: state.currentSession?.thinkingLevel ?: agent?.thinkingDefault
    val permission = composer.sessionOptions[SessionSetting.PERMISSIONS] ?: state.currentSession?.permissionMode
    val canSelectFull = connected?.scopes?.contains("operator.admin") == true
    val writeScope = canSelectFull || connected?.scopes?.contains("operator.write") == true
    val writeMethod = if (state.currentSessionKey == null) "sessions.create" else "sessions.patch"
    val canEdit = canReply && writeScope && agent != null && !composer.sending && !composer.updatingSettings &&
        connected?.methods?.contains(writeMethod) == true
    val canLoadModels = connected?.methods?.contains("models.list") == true
    val linkFailed = stringResource(R.string.chat_link_failed)
    val onLinkError: () -> Unit = { scope.launch { snackbar.showSnackbar(linkFailed) } }
    val openLink by rememberUpdatedState(rememberChatLinkOpener(onLinkError))
    val linkError by rememberUpdatedState(onLinkError)
    val uriHandler = remember {
        object : UriHandler {
            override fun openUri(uri: String) {
                val target = webUri(uri)
                val extension = target?.path?.substringAfterLast('.')?.lowercase()
                when {
                    target == null -> linkError()
                    extension in VIDEO_EXTENSIONS -> openedVideo = uri
                    extension in IMAGE_EXTENSIONS -> openedImage = MessagePart.Image(uri, null, null)
                    else -> openLink(target)
                }
            }
        }
    }

    // 添付画像（media://inbound/...）は開いている会話の権限で取る
    val imageSessionKey = state.currentSessionKey
    val imageAgentId = state.currentSession?.agentId ?: state.selectedAgentId
    val imageSource = remember(inboundImages, imageSessionKey, imageAgentId) {
        if (inboundImages == null || imageSessionKey == null) null
        else InboundImageSource { inboundImages.load(it, imageSessionKey, imageAgentId) }
    }
    CompositionLocalProvider(LocalInboundImages provides imageSource) {
    ProvideScreenHaze(hazeState) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                // 全画面のサイドパネル（Gemini 型）。開くと画面いっぱいまで広がる
                SidePanel(
                    agents = agents,
                    pagerState = pagerState,
                    sessionsByAgent = state.sessionsByAgent,
                    selectedSessionKey = state.currentSessionKey,
                    timeLabel = timeLabel,
                    onClose = { scope.launch { drawerState.close() } },
                    onNewSession = {
                        actions.newChat()
                        scope.launch { drawerState.close() }
                    },
                    onSelectSession = { session ->
                        actions.selectSession(session)
                        scope.launch { drawerState.close() }
                    },
                    onRename = actions.rename,
                    onArchive = actions.archive,
                    onOpenAutomations = { scope.launch { drawerState.close() }; navigation.openAutomations() },
                    onOpenSettings = { scope.launch { drawerState.close() }; navigation.openSettings() },
                )
            },
        ) {
            val listState = rememberLazyListState()
            val titleBarState = key(state.currentSessionKey) { rememberTopAppBarState() }
            val titleBarScroll = TopAppBarDefaults.enterAlwaysScrollBehavior(
                state = titleBarState,
                snapAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            )
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    Column {
                        SessionTitleBar(
                            hazeState = hazeState,
                            scrollBehavior = titleBarScroll,
                            state = state,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            navigation = navigation,
                        )
                        ConnectionBanner(
                            state.connection,
                            actions.reconnect,
                            Modifier.padding(top = Spacing.s).align(Alignment.CenterHorizontally),
                        )
                    }
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Box(
                            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).imePadding(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ComposerPill(
                                hazeState = hazeState,
                                state = composer,
                                running = state.chat?.running == true,
                                enabled = canReply,
                                focusRequester = composerFocus,
                                voice = voice,
                                model = model,
                                contextFraction = state.currentSession?.contextFraction,
                                onOpenSession = { showSessionSheet = true },
                                pending = state.chat?.pending.orEmpty(),
                                onCancelPending = actions.cancelPending,
                                onTextChange = actions.textChange,
                                onAttach = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onRemoveImage = actions.removeImage,
                                onSend = actions.send,
                                onStop = actions.stop,
                                modifier = Modifier.widthIn(max = Dimens.composerMaxWidth).padding(horizontal = Spacing.l, vertical = Spacing.s),
                            )
                        }
                    }
                },
                snackbarHost = { GlassSnackbarHost(snackbar) },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                        MessageList(
                            state = state,
                            listState = listState,
                            contentPadding = PaddingValues(
                                start = Spacing.l,
                                end = Spacing.l,
                                top = padding.calculateTopPadding() + Spacing.s,
                                bottom = Spacing.s,
                            ),
                            onCopy = { text ->
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
                                    snackbar.showSnackbar(copied)
                                }
                            },
                            onReply = if (canReply) { text ->
                                actions.reply(text)
                                if (composerFocus.requestFocus()) keyboard?.show()
                            } else null,
                            onOpenImage = { openedImage = it },
                            onOpenVideo = { openedVideo = it },
                            onRetry = actions.retryHistory,
                            // タイトルバーの折りたたみは会話の一覧のスクロールだけに付いていく（入力欄のスクロールは届かない）
                            modifier = Modifier.fillMaxSize().nestedScroll(titleBarScroll.nestedScrollConnection).hazeSource(hazeState),
                        )
                    }
                    Box(Modifier.align(Alignment.BottomCenter)) { ScrollToBottomButton(listState, hazeState) }
                }
            }
        }
        ImageOverlay(image = openedImage, onDismiss = { openedImage = null })
        openedVideo?.let { VideoPreviewSheet(it, hazeState) { openedVideo = null } }
        if (showSessionSheet) {
            ComposerSessionSheet(
                hazeState = hazeState,
                agent = agent,
                session = state.currentSession,
                model = model,
                effort = effort,
                permission = permission,
                draftOptions = composer.sessionOptions,
                availability = SettingAvailability(
                    model = canEdit && canLoadModels,
                    effort = canEdit && (canLoadModels || agent?.thinkingLevels?.isNotEmpty() == true),
                    permissions = canEdit && state.currentSession?.permissionModePending != true,
                ),
                canSelectFull = canSelectFull,
                loadModels = actions.loadModels,
                loadUsage = actions.loadUsage,
                onSelect = { setting, value -> actions.updateSetting(setting, value) },
                onDismiss = { showSessionSheet = false },
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTitleBar(
    hazeState: dev.chrisbanes.haze.HazeState,
    scrollBehavior: TopAppBarScrollBehavior,
    state: ChatScreenState,
    onOpenDrawer: () -> Unit,
    navigation: ChatNavigation,
) {
    var menuOpen by remember { mutableStateOf(false) }
    AppTitleBar(
        hazeState = hazeState,
        scrollBehavior = scrollBehavior,
        title = {
            Text(
                state.currentSession?.title ?: stringResource(
                    if (state.currentSessionKey == null) R.string.session_untitled else R.string.session_no_title,
                ),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(painterResource(R.drawable.ic_huge_menu), stringResource(R.string.sidebar_open_cd))
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_huge_more), stringResource(R.string.chat_more_cd))
                }
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
                        text = { Text(stringResource(R.string.menu_restart_gateway)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_huge_restart), null) },
                        onClick = { menuOpen = false; navigation.restartGateway() },
                    )
                }
            }
        },
    )
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
    onReply: ((String) -> Unit)?,
    onOpenImage: (MessagePart.Image) -> Unit,
    onOpenVideo: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chat = state.chat
    val agent = state.currentSession?.agentId?.let { id -> state.agents.agents.firstOrNull { it.id == id } }
        ?: state.selectedAgent
    val messages = state.messages
    val rows = remember(messages) {
        buildList {
            var tools = mutableListOf<MessageUi>()
            for (item in messages) {
                if (item.message.parts.isNotEmpty() && item.message.parts.all { it is MessagePart.ToolCall }) {
                    tools += item
                } else {
                    if (tools.isNotEmpty()) {
                        add(tools)
                        tools = mutableListOf()
                    }
                    add(listOf(item))
                }
            }
            if (tools.isNotEmpty()) add(tools)
        }
    }
    val live = chat?.live
    val pending = chat?.pending.orEmpty()
    // 一番下に付いていくか。利用者がスクロールを終えたときに「一番下にいるか」で決め直す（上を読んでいる最中は動かさない）
    var stickToBottom by remember(state.currentSessionKey) { mutableStateOf(true) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(listState, state.currentSessionKey) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) stickToBottom = !listState.canScrollForward
        }
    }
    suspend fun followBottom() {
        if (!stickToBottom || dragged) return
        withFrameNanos { } // 新しい行の配置を待つ
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.scrollToItem(last, Int.MAX_VALUE / 2)
    }
    // 中身が増えた・応答が伸びた・受け付け待ちが変わったとき
    LaunchedEffect(rows.size, live?.text?.length, pending.size, chat?.running, state.currentSessionKey) { followBottom() }
    // 入力欄が伸びる・キーボードが出るなどで表示の高さが変わったとき（会話の表示は縮み、最新の行を見せたままにする）
    LaunchedEffect(listState, state.currentSessionKey) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }.distinctUntilChanged().collect { followBottom() }
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
            items(rows, key = { it.first().message.id }, contentType = { it.first().message.role }) { row ->
                val item = row.first()
                val message = item.message
                val itemModifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()
                if (message.parts.isNotEmpty() && message.parts.all { it is MessagePart.ToolCall }) {
                    ToolGroup(
                        parts = remember(row) { row.flatMap { it.message.parts.filterIsInstance<MessagePart.ToolCall>() } },
                        modifier = itemModifier,
                    )
                } else when (message.role) {
                    ChatMessage.Role.USER -> UserMessage(message, item.timeLabel, onOpenImage, onOpenVideo, itemModifier)
                    ChatMessage.Role.ASSISTANT, ChatMessage.Role.SYSTEM -> AssistantMessage(
                        message = message,
                        agent = agent,
                        timeLabel = item.timeLabel,
                        elapsedMs = item.elapsedMs,
                        onCopy = onCopy,
                        onReply = onReply,
                        onOpenImage = onOpenImage,
                        onOpenVideo = onOpenVideo,
                        onShowFull = null,
                        modifier = itemModifier,
                    )
                    ChatMessage.Role.TOOL_RESULT -> Unit
                }
            }
            if (live != null && live.text.isNotBlank()) {
                item(key = "live-${live.runId}") {
                    LiveAssistantMessage(live.text, agent, Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth())
                }
            }
            // 実行中は、応答が流れていてもいなくても最後に考え中の表示（経過時間と今していること）
            if (chat.running) {
                item(key = "thinking") {
                    ThinkingIndicator(
                        startedAt = chat.runStartedAt,
                        activity = chat.activity,
                        modifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().animateItem(
                            fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        ),
                    )
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

/** 一度に添付できる画像の数。 */
private const val MAX_ATTACH_IMAGES = 10

private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp")
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "avif")
