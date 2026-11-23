package jp.miyavi.android.clawdroid.feature.chat

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.LocalInboundImages
import jp.miyavi.android.clawdroid.core.data.InboundImageSource
import jp.miyavi.android.clawdroid.core.data.InboundImageLoader
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import jp.miyavi.android.clawdroid.feature.chat.message.ModelChangeDivider
import jp.miyavi.android.clawdroid.feature.chat.message.ToolGroup
import jp.miyavi.android.clawdroid.feature.chat.message.UserMessage
import jp.miyavi.android.clawdroid.feature.chat.message.VideoPreviewSheet
import jp.miyavi.android.clawdroid.feature.chat.message.rememberChatLinkOpener
import jp.miyavi.android.clawdroid.feature.chat.message.webUri
import jp.miyavi.android.clawdroid.core.designsystem.FastScroller
import jp.miyavi.android.clawdroid.feature.chat.sidebar.SidePanel
import kotlinx.coroutines.launch

/** 会話の画面の外への操作。 */
data class ChatNavigation(
    val openSettings: () -> Unit,
    val openAutomations: (Agent) -> Unit,
    val openTerminal: (sessionKey: String?, agentId: String?, cwd: String?) -> Unit,
    val openFiles: (sessionKey: String, agentId: String) -> Unit,
    val restartGateway: () -> Unit,
)

@Composable
internal fun ChatScreen(
    viewModel: ChatViewModel,
    navigation: ChatNavigation,
    questions: jp.miyavi.android.clawdroid.core.data.QuestionsRepository? = null,
    timeLabel: (Long) -> String,
    inboundImages: InboundImageLoader? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val composer by viewModel.composer.collectAsStateWithLifecycle()
    val slashCommands by viewModel.slashCommands.collectAsStateWithLifecycle()
    // あとで答えることにした、この会話への質問（入力欄の上から開き直す）
    val questionRequests = questions?.requests?.collectAsStateWithLifecycle()?.value.orEmpty()
    val snoozedQuestions = questions?.snoozed?.collectAsStateWithLifecycle()?.value.orEmpty()
    val snoozedQuestion = questionRequests.firstOrNull { it.id in snoozedQuestions && it.belongsTo(state.currentSessionKey) }
    val snackbar = remember { SnackbarHostState() }
    val sendFailed = stringResource(R.string.chat_send_failed)
    val pendingAlreadyTaken = stringResource(R.string.chat_pending_already_taken)
    val retry = stringResource(R.string.action_retry)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ChatEvent.SendFailed -> if (snackbar.showSnackbar(sendFailed, retry, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) viewModel.send()
                ChatEvent.PendingAlreadyTaken -> snackbar.showSnackbar(pendingAlreadyTaken)
                is ChatEvent.Error -> snackbar.showSnackbar(event.message ?: sendFailed)
            }
        }
    }
    ChatContent(
        inboundImages = inboundImages,
        state = state,
        composer = composer,
        slashCommands = slashCommands,
        hasQuestion = snoozedQuestion != null,
        onOpenQuestion = { snoozedQuestion?.let { questions?.reopen(it.id) } },
        snackbar = snackbar,
        timeLabel = timeLabel,
        navigation = navigation,
        actions = ChatActions(
            selectAgent = viewModel::selectAgent,
            selectSession = viewModel::selectSession,
            newChat = viewModel::newChat,
            rename = viewModel::rename,
            archive = viewModel::setArchived,
            delete = viewModel::delete,
            textChange = viewModel::onTextChange,
            reply = viewModel::replyTo,
            addImage = viewModel::addImage,
            removeImage = viewModel::removeImage,
            voiceText = viewModel::appendVoiceText,
            send = viewModel::send,
            stop = viewModel::abort,
            retryHistory = viewModel::retryHistory,
            cancelPending = viewModel::cancelPending,
            restorePending = viewModel::restorePending,
            loadUsage = viewModel::loadProviderUsage,
            loadContext = viewModel::loadContextBreakdown,
            loadModels = viewModel::loadModelCatalog,
            updateSetting = viewModel::updateSessionSetting,
            reconnect = viewModel::reconnect,
            preloadAgents = viewModel::preloadAgentSessions,
        ),
    )
}

data class ChatActions(
    val selectAgent: (String) -> Unit,
    val selectSession: (jp.miyavi.android.clawdroid.core.data.SessionSummary) -> Unit,
    val newChat: () -> Unit,
    val rename: (jp.miyavi.android.clawdroid.core.data.SessionSummary, String) -> Unit,
    val archive: (jp.miyavi.android.clawdroid.core.data.SessionSummary, Boolean) -> Unit,
    val delete: (jp.miyavi.android.clawdroid.core.data.SessionSummary) -> Unit,
    val textChange: (String) -> Unit,
    val reply: (String) -> Unit,
    val addImage: (android.net.Uri) -> Unit,
    val removeImage: (android.net.Uri) -> Unit,
    val voiceText: (String) -> Unit,
    val send: () -> Unit,
    val stop: () -> Unit,
    val retryHistory: () -> Unit,
    val cancelPending: (String) -> Unit,
    val restorePending: (String) -> Unit,
    val loadUsage: suspend () -> List<ProviderUsage>,
    val loadContext: suspend () -> jp.miyavi.android.clawdroid.core.data.ContextBreakdown?,
    val loadModels: suspend (String, String?) -> ModelCatalog?,
    val updateSetting: (SessionSetting, String?) -> Unit,
    val reconnect: () -> Unit,
    val preloadAgents: () -> Unit = {},
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
    slashCommands: List<jp.miyavi.android.clawdroid.core.data.SlashCommand> = emptyList(),
    hasQuestion: Boolean = false,
    onOpenQuestion: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val hazeState = rememberHazeState()
    val agents = state.agents.agents
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    // サイドパネルを開けたら、ほかのエージェントの履歴も読んでおく（切り替えた瞬間に中身がそろうように）
    val drawerOpening = drawerState.targetValue == DrawerValue.Open
    LaunchedEffect(drawerOpening) { if (drawerOpening) actions.preloadAgents() }

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
                    selectedAgentId = state.selectedAgentId,
                    onSelectAgent = actions.selectAgent,
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
                    onDelete = actions.delete,
                    onOpenAutomations = { agent -> scope.launch { drawerState.close() }; navigation.openAutomations(agent) },
                    onOpenSettings = { scope.launch { drawerState.close() }; navigation.openSettings() },
                )
            },
        ) {
            val listState = key(state.currentSessionKey) { rememberLazyListState() }
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
                    // 入力欄の後ろの帯は透明（会話がそのまま見える）。入力欄は影で少し浮かせる
                    Box(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).imePadding(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ComposerPill(
                                hazeState = hazeState,
                                state = composer,
                                slashCommands = slashCommands,
                                hasQuestion = hasQuestion,
                                onOpenQuestion = onOpenQuestion,
                                running = state.chat?.running == true,
                                enabled = canReply,
                                focusRequester = composerFocus,
                                voice = voice,
                                model = model,
                                contextFraction = state.currentSession?.contextFraction,
                                onOpenSession = { showSessionSheet = true },
                                pending = state.chat?.pending.orEmpty(),
                                onCancelPending = actions.cancelPending,
                                onRestorePending = actions.restorePending,
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
                // 会話は入力欄の裏まで描き、最後の行は入力欄の上に来るよう下の余白で押し上げる
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                        MessageList(
                            state = state,
                            listState = listState,
                            contentPadding = PaddingValues(
                                start = Spacing.l,
                                end = Spacing.l,
                                top = padding.calculateTopPadding() + Spacing.s,
                                bottom = padding.calculateBottomPadding() + Spacing.s,
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
                            hazeState = hazeState,
                            // タイトルバーの折りたたみは会話の一覧のスクロールだけに付いていく（入力欄のスクロールは届かない）
                            modifier = Modifier.fillMaxSize().nestedScroll(titleBarScroll.nestedScrollConnection),
                        )
                    }
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding())) {
                        ScrollToBottomButton(listState, hazeState)
                    }
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
                loadContext = actions.loadContext,
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
            // ファイルとターミナルはアイコンで（タイトルバーのアイコンは 2 つまで。AGENTS.md 2.6）。再起動は設定から
            val key = state.currentSessionKey
            val agentId = state.selectedAgentId
            if (key != null && agentId != null) {
                IconButton(onClick = { navigation.openFiles(key, agentId) }) {
                    Icon(painterResource(R.drawable.ic_huge_folder), stringResource(R.string.menu_files))
                }
            }
            IconButton(onClick = { navigation.openTerminal(key, agentId, state.currentSession?.cwd) }) {
                Icon(painterResource(R.drawable.ic_huge_terminal), stringResource(R.string.menu_terminal))
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
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier,
) {
    // 透かす元は会話の中身だけ（高速スクロールの吹き出しは透かす側なので、元に含めない）
    val sourceModifier = modifier.hazeSource(hazeState)
    val chat = state.chat
    val agent = state.currentSession?.agentId?.let { id -> state.agents.agents.firstOrNull { it.id == id } }
        ?: state.selectedAgent
    val messages = state.messages
    // 続いたツールの実行は 1 行にまとめ、次にエージェントの応答が来たらその応答の見出しに入れる
    val rows = remember(messages) {
        buildList {
            var tools = mutableListOf<MessageUi>()
            for (item in messages) {
                val toolOnly = item.message.parts.isNotEmpty() && item.message.parts.all { it is MessagePart.ToolCall }
                when {
                    toolOnly -> tools += item
                    item.message.role == ChatMessage.Role.ASSISTANT && tools.isNotEmpty() -> {
                        add(tools + item)
                        tools = mutableListOf()
                    }
                    else -> {
                        if (tools.isNotEmpty()) {
                            add(tools)
                            tools = mutableListOf()
                        }
                        add(listOf(item))
                    }
                }
            }
            if (tools.isNotEmpty()) add(tools)
        }
    }
    val rowKeys = remember(rows) { chatRowKeys(rows.map { it.first().message.id }) }
    val live = chat?.live
    val liveText = live?.text.orEmpty()
    val latestFirst = rows.asReversed()
    when {
        state.currentSessionKey == null || (chat != null && !chat.loading && !chat.running && messages.isEmpty() && live == null) -> {
            Box(sourceModifier, contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.chat_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
        chat == null || (chat.loading && messages.isEmpty()) -> Box(sourceModifier, contentAlignment = Alignment.Center) { LoadingIndicator() }
        chat.error != null && messages.isEmpty() -> Box(sourceModifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.chat_load_failed), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
        // 右端の高速スクロールバー。古い方へ一気に戻れる。吹き出しはその位置の日時
        else -> FastScroller(
            listState = listState,
            itemCount = latestFirst.size + 1,
            label = { index -> if (index == 0) null else latestFirst.getOrNull(index - 1)?.last()?.timeLabel },
            hazeState = hazeState,
            reverseLayout = true,
            topInset = contentPadding.calculateTopPadding(),
            bottomInset = contentPadding.calculateBottomPadding(),
            modifier = modifier,
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            contentPadding = contentPadding,
            reverseLayout = true,
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 末尾のキーを固定し、生成中の行が伸びても同じレイアウトで下端に留める。
            // 履歴へスクロールしていれば LazyColumn がその行のキーと位置を維持する。
            item(key = "chat-tail") {
                if (chat.running || liveText.isNotBlank()) {
                    LiveAssistantMessage(
                        text = liveText,
                        agent = agent,
                        running = chat.running,
                        startedAt = chat.runStartedAt,
                        reportedOutputTokens = chat.reportedOutputTokens,
                        modifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()
                            .padding(top = if (rows.isEmpty()) 0.dp else Spacing.l),
                    )
                }
            }
            itemsIndexed(latestFirst, key = { index, _ -> rowKeys[rowKeys.lastIndex - index] }, contentType = { _, row -> row.last().message.role }) { index, row ->
                val item = row.last()
                val message = item.message
                val itemModifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()
                    .padding(top = if (index == latestFirst.lastIndex) 0.dp else Spacing.l)
                val precedingTools = remember(row) { row.dropLast(1).flatMap { it.message.parts.filterIsInstance<MessagePart.ToolCall>() } }
                val modelChange = item.modelChange
                if (message.parts.isEmpty()) {
                    // モデルの切り替えの記録だけの行
                    if (modelChange != null) ModelChangeDivider(modelChange, itemModifier)
                } else if (message.parts.all { it is MessagePart.ToolCall }) {
                    ToolGroup(
                        parts = remember(row) { row.flatMap { it.message.parts.filterIsInstance<MessagePart.ToolCall>() } },
                        modifier = itemModifier,
                    )
                } else when (message.role) {
                    ChatMessage.Role.USER -> Column(itemModifier) {
                        // この送信から別のモデルが答える
                        if (modelChange != null) ModelChangeDivider(modelChange, Modifier.padding(bottom = Spacing.l))
                        UserMessage(message, item.timeLabel, onOpenImage, onOpenVideo)
                    }
                    ChatMessage.Role.ASSISTANT, ChatMessage.Role.SYSTEM -> AssistantMessage(
                        message = message,
                        markdown = item.markdown,
                        agent = agent,
                        precedingTools = precedingTools,
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

        }
        }
    }
}

/** 上へスクロールしているときだけ出る「最新へ」のボタン。 */
@Composable
private fun ScrollToBottomButton(listState: LazyListState, hazeState: dev.chrisbanes.haze.HazeState) {
    val scope = rememberCoroutineScope()
    val show by remember { androidx.compose.runtime.derivedStateOf { listState.canScrollBackward } }
    AnimatedVisibility(
        visible = show,
        enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        FloatingIconButton(
            hazeState = hazeState,
            icon = painterResource(R.drawable.ic_huge_arrow_down),
            contentDescription = stringResource(R.string.chat_scroll_bottom_cd),
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.padding(bottom = Spacing.s),
        )
    }
}

/** 一度に添付できる画像の数。 */
private const val MAX_ATTACH_IMAGES = 10

private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp")
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "avif")
