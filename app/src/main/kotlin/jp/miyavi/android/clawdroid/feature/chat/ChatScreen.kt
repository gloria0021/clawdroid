package jp.miyavi.android.clawdroid.feature.chat

import jp.miyavi.android.clawdroid.core.designsystem.GlassPill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import jp.miyavi.android.clawdroid.core.designsystem.statusBarFade
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import jp.miyavi.android.clawdroid.core.designsystem.hazeSurface
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
import jp.miyavi.android.clawdroid.feature.chat.message.SystemNoticeDivider
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
    /** エージェントの設定（spec 004）。 */
    val openAgentSettings: (agentId: String) -> Unit = {},
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
    val worktreeCreating = stringResource(R.string.worktree_creating)
    val worktreePreserved = stringResource(R.string.worktree_preserved)
    val grouping by viewModel.sessionGrouping.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ChatEvent.SendFailed -> if (snackbar.showSnackbar(sendFailed, retry, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) viewModel.send()
                ChatEvent.PendingAlreadyTaken -> snackbar.showSnackbar(pendingAlreadyTaken)
                ChatEvent.WorktreeCreating -> snackbar.showSnackbar(worktreeCreating)
                is ChatEvent.WorktreePreserved -> snackbar.showSnackbar(
                    String.format(worktreePreserved, event.branch.orEmpty()), duration = SnackbarDuration.Long,
                )
                is ChatEvent.Error -> snackbar.showSnackbar(event.message ?: sendFailed)
            }
        }
    }
    // 作業フォルダごとの今のブランチ（サイドパネルの行に出す）
    val headBranches by viewModel.headBranches.collectAsStateWithLifecycle()
    val folderDiffs by viewModel.folderDiffs.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        jp.miyavi.android.clawdroid.feature.chat.sidebar.LocalHeadBranches provides headBranches,
        jp.miyavi.android.clawdroid.feature.chat.sidebar.LocalFolderDiffs provides folderDiffs,
        jp.miyavi.android.clawdroid.feature.chat.sidebar.LocalDiffLoader provides viewModel::diffSummary,
        LocalWorktreeNaming provides WorktreeNaming(
            takenNames = viewModel::takenWorktreeNames,
            archived = { agentId -> state.sessionsByAgent[agentId]?.archived.orEmpty() },
            restore = viewModel::restoreWorktree,
        ),
    ) {
    ChatContent(
        grouping = grouping,
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
            setNewSessionCwd = viewModel::setNewSessionCwd,
            loadFolderCandidates = viewModel::folderCandidates,
            listDir = viewModel::listDir,
            setGrouping = viewModel::setSessionGrouping,
            createWorktree = viewModel::createWorktree,
            setPinned = viewModel::setPinned,
            loadDiff = viewModel::diff,
            loadDiffSummary = viewModel::diffSummary,
            loadBranches = viewModel::branches,
        ),
    )
    }
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
    val setNewSessionCwd: (String?) -> Unit = {},
    val loadFolderCandidates: suspend () -> List<String> = { emptyList() },
    val listDir: suspend (String?) -> jp.miyavi.android.clawdroid.core.data.DirListing? = { null },
    val setGrouping: (String, jp.miyavi.android.clawdroid.core.settings.SessionGrouping) -> Unit = { _, _ -> },
    val createWorktree: (jp.miyavi.android.clawdroid.core.data.SessionSummary, String?, String?, Boolean) -> Unit = { _, _, _, _ -> },
    val setPinned: (jp.miyavi.android.clawdroid.core.data.SessionSummary, Boolean) -> Unit = { _, _ -> },
    val loadDiff: suspend (jp.miyavi.android.clawdroid.core.data.SessionSummary) -> jp.miyavi.android.clawdroid.core.data.SessionDiff? = { null },
    val loadDiffSummary: suspend (jp.miyavi.android.clawdroid.core.data.SessionSummary) -> jp.miyavi.android.clawdroid.core.data.SessionDiff? = { null },
    val loadBranches: suspend (jp.miyavi.android.clawdroid.core.data.SessionSummary) -> jp.miyavi.android.clawdroid.core.data.RepoBranches? = { null },
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
    grouping: Map<String, jp.miyavi.android.clawdroid.core.settings.SessionGrouping> = emptyMap(),
) {
    var pickingFolder by rememberSaveable { mutableStateOf(false) }
    // タイトルバーの ⋮ から開いたもの（名前の変更・変更量・別のフォルダ）
    var sessionAction by rememberSaveable { mutableStateOf<SessionAction?>(null) }
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
    var openedImage by remember { mutableStateOf<jp.miyavi.android.clawdroid.feature.chat.message.ImageGallery?>(null) }
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
                    extension in IMAGE_EXTENSIONS -> openedImage = jp.miyavi.android.clawdroid.feature.chat.message.ImageGallery(listOf(MessagePart.Image(uri, null, null)))
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
        else object : InboundImageSource {
            override suspend fun load(source: String) = inboundImages.load(source, imageSessionKey, imageAgentId)
            override suspend fun bytes(source: String) = inboundImages.loadBytes(source, imageSessionKey, imageAgentId)
        }
    }
    CompositionLocalProvider(LocalInboundImages provides imageSource) {
    ProvideScreenHaze(hazeState) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                // 全画面のサイドパネル（Gemini 型）。開くと画面いっぱいまで広がる
                SidePanel(
                    agents = state.sidebarAgents,
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
                    onOpenAgentSettings = { agent -> scope.launch { drawerState.close() }; navigation.openAgentSettings(agent.id) },
                    groupingByAgent = grouping,
                    onGroupingChange = actions.setGrouping,
                    onUnpin = { actions.setPinned(it, false) },
                    loadBranches = actions.loadBranches,
                    onCreateWorktree = { session, name, base, fork ->
                        scope.launch { drawerState.close() }
                        actions.createWorktree(session, name, base, fork)
                    },
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
                            onSessionAction = { sessionAction = it },
                            onTogglePin = { state.currentSession?.let { actions.setPinned(it, !it.pinned) } },
                            loadDiffSummary = actions.loadDiffSummary,
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
                                onPasteImage = actions.addImage,
                                onOpenImage = { images, index ->
                                    openedImage = jp.miyavi.android.clawdroid.feature.chat.message.ImageGallery(
                                        images.map { MessagePart.Image(url = null, base64 = it.image.base64, mimeType = it.image.mimeType) },
                                        index,
                                    )
                                },
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
                    val linkActions = jp.miyavi.android.clawdroid.feature.chat.message.rememberLinkActions(snackbar)
                    CompositionLocalProvider(
                        LocalUriHandler provides uriHandler,
                        jp.miyavi.android.clawdroid.feature.chat.message.LocalLinkActions provides linkActions,
                        jp.miyavi.android.clawdroid.feature.chat.message.LocalOpenAgentSettings provides navigation.openAgentSettings,
                    ) {
                        MessageList(
                            newSessionCwd = composer.newSessionCwd,
                            onPickFolder = { pickingFolder = true },
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
                            // 浮くボタンだけの頭は折りたたまない。TopAppBar の無いスクロールの連携を付けると、
                            // 高さの上限が決まらないままスクロールを全部吸い込んで、一覧が動かなくなるため
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding())) {
                        ScrollToBottomButton(listState, hazeState)
                    }
                }
            }
        }
        ImageOverlay(gallery = openedImage, onDismiss = { openedImage = null })
        val current = state.currentSession
        when (sessionAction) {
            SessionAction.RENAME -> if (current != null) SessionRenameDialog(current.title.orEmpty(), onDismiss = { sessionAction = null }) {
                sessionAction = null
                actions.rename(current, it)
            }
            SessionAction.DIFF -> if (current != null) SessionDiffSheet(current, hazeState, actions.loadDiff) { sessionAction = null }
            SessionAction.WORKTREE -> if (current != null) {
                WorktreeSheet(current, onDismiss = { sessionAction = null }, loadBranches = actions.loadBranches) { name, base, fork ->
                    sessionAction = null
                    actions.createWorktree(current, name, base, fork)
                }
            }
            null -> Unit
        }
        if (pickingFolder) {
            FolderPickerSheet(
                hazeState = hazeState,
                current = composer.newSessionCwd,
                load = actions.loadFolderCandidates,
                listDir = actions.listDir,
                onPick = { pickingFolder = false; actions.setNewSessionCwd(it) },
                onDismiss = { pickingFolder = false },
            )
        }
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
    onSessionAction: (SessionAction) -> Unit = {},
    onTogglePin: () -> Unit = {},
    loadDiffSummary: suspend (jp.miyavi.android.clawdroid.core.data.SessionSummary) -> jp.miyavi.android.clawdroid.core.data.SessionDiff? = { null },
) {
    // 試し（マスター指定 2026-11-23）: 帯のガラスをやめ、サイドパネルと同じ「Clawdroid」のガラスのピルと、
    // ファイル・ターミナル・⋮ の丸いガラスのボタンだけを浮かせる。ピルを押すとサイドパネルが開く
    // 上端のフェード: 会話を背景色へ溶かし、ステータスバーとボタンを読みやすくする
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarFade()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = Spacing.m, end = Spacing.m, top = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
    ) {
        // 右に「Clawdroid」のピルだけ（⋮ の代わり。押すとメニュー。右寄せはマスター指定 2026-11-24）。
        // サイドパネルは左端からのスワイプで開く（≡ は置かない。マスター指定 2026-11-23）
        val key = state.currentSessionKey
        val agentId = state.selectedAgentId
        val menuLabel = stringResource(R.string.session_more_cd)
        GlassPill(hazeState = hazeState, contentPadding = PaddingValues(0.dp)) {
            SessionMoreMenu(
                session = state.currentSession,
                onAction = onSessionAction,
                onTogglePin = onTogglePin,
                loadDiffSummary = loadDiffSummary,
                onOpenFiles = if (key != null && agentId != null) ({ navigation.openFiles(key, agentId) }) else null,
                onOpenTerminal = { navigation.openTerminal(key, agentId, state.currentSession?.cwd) },
                anchor = { onClick ->
                    Row(
                        Modifier
                            .clickable(onClickLabel = menuLabel, onClick = onClick)
                            .heightIn(min = Dimens.floatingButtonSize)
                            .padding(start = Spacing.s, end = Spacing.l),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.launcher_mascot_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.sidebarAppIcon).graphicsLayer {
                                scaleX = jp.miyavi.android.clawdroid.feature.chat.sidebar.LauncherForegroundViewportScale
                                scaleY = jp.miyavi.android.clawdroid.feature.chat.sidebar.LauncherForegroundViewportScale
                            },
                        )
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                },
            )
        }
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
    // Snackbar と同じ透過のピル（Gerbil の GlassSnackbarHost の形）。読みやすさのため不透明度 80%（マスター指定 2026-11-24）
    val hazeState = jp.miyavi.android.clawdroid.core.designsystem.LocalScreenHazeState.current
    // 消える動きのあいだも文字を残す
    var shownText by remember { mutableStateOf(text.orEmpty()) }
    if (text != null) shownText = text
    AnimatedVisibility(visible = text != null, enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()), exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()), modifier = modifier) {
        jp.miyavi.android.clawdroid.core.designsystem.ProvideGlassEnvironment {
            val shape = CircleShape
            Row(
                Modifier
                    .padding(horizontal = Spacing.l)
                    .widthIn(max = Dimens.maxReadingWidth)
                    .then(
                        if (hazeState != null) Modifier.hazeSurface(hazeState, shape, jp.miyavi.android.clawdroid.core.designsystem.NOTICE_OPACITY)
                        else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
                    )
                    .clip(shape)
                    .heightIn(min = Dimens.minTouchTarget)
                    .padding(start = Spacing.xl, end = if (connection is ConnectionState.Connecting) Spacing.xl else Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    shownText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false).padding(vertical = Spacing.m),
                )
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
    onOpenImage: (jp.miyavi.android.clawdroid.feature.chat.message.ImageGallery) -> Unit,
    onOpenVideo: (String) -> Unit,
    onRetry: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState,
    newSessionCwd: String? = null,
    onPickFolder: () -> Unit = {},
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
                Column(Modifier.padding(contentPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.chat_empty_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 新しいセッションでは、作業フォルダを選べる（最初の 1 通で作られる）
                    if (state.currentSessionKey == null) {
                        androidx.compose.material3.AssistChip(
                            onClick = onPickFolder,
                            label = {
                                Text(
                                    newSessionCwd?.substringAfterLast('/')?.ifEmpty { newSessionCwd } ?: stringResource(R.string.new_session_folder_default),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_huge_folder), contentDescription = null, modifier = Modifier.size(Dimens.smallIcon)) },
                            modifier = Modifier.padding(top = Spacing.l).widthIn(max = Dimens.settingMenuMaxWidth)

                        )
                    }
                }
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
                        compacting = chat.activity == jp.miyavi.android.clawdroid.core.data.RunActivity.Compacting,
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
                val notice = message.notice
                if (notice != null) {
                    // コンテキストの圧縮などのお知らせは、吹き出しではなく区切りで出す
                    SystemNoticeDivider(notice, message.text, item.markdown.singleOrNull(), itemModifier)
                } else if (message.parts.isEmpty()) {
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
