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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.DismissibleNavigationDrawer
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
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val attachmentTooLarge = stringResource(R.string.chat_attach_too_large)
    val retry = stringResource(R.string.action_retry)
    val pastedAsAttachment = stringResource(R.string.chat_text_pasted)
    val expandLabel = stringResource(R.string.chat_text_expand)
    val worktreeCreating = stringResource(R.string.worktree_creating)
    val worktreePreserved = stringResource(R.string.worktree_preserved)
    val worktreeMerged = stringResource(R.string.worktree_merged)
    val worktreeMergeUnfinished = stringResource(R.string.worktree_merge_unfinished)
    val grouping by viewModel.sessionGrouping.collectAsStateWithLifecycle()
    // 会話の画面が見えているあいだだけ「開いている」とする（ほかの画面を重ねた・裏に回ったら外す。spec 006 FR-011）
    androidx.lifecycle.compose.LifecycleResumeEffect(viewModel) {
        viewModel.setVisible(true)
        onPauseOrDispose { viewModel.setVisible(false) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ChatEvent.SendFailed -> if (snackbar.showSnackbar(sendFailed, retry, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) viewModel.send()
                // 長い貼り付けを添付にした。すぐ文字に戻せるようにする（マスター指定 2026-11-26）
                is ChatEvent.PastedAsAttachment -> if (
                    snackbar.showSnackbar(pastedAsAttachment, expandLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed
                ) viewModel.expandText(event.id)
                ChatEvent.PendingAlreadyTaken -> snackbar.showSnackbar(pendingAlreadyTaken)
                ChatEvent.AttachmentTooLarge -> snackbar.showSnackbar(attachmentTooLarge)
                ChatEvent.WorktreeCreating -> snackbar.showSnackbar(worktreeCreating)
                is ChatEvent.WorktreePreserved -> snackbar.showSnackbar(
                    String.format(worktreePreserved, event.branch.orEmpty()), duration = SnackbarDuration.Long,
                )
                ChatEvent.WorktreeMerged -> snackbar.showSnackbar(worktreeMerged)
                ChatEvent.WorktreeMergeUnfinished -> snackbar.showSnackbar(worktreeMergeUnfinished, duration = SnackbarDuration.Long)
                is ChatEvent.Error -> snackbar.showSnackbar(event.message ?: sendFailed)
                // 設定は元の値に戻したうえで知らせ、同じ値で再試行できるようにする（マスター指定 2026-11-26）
                is ChatEvent.SettingFailed -> if (
                    snackbar.showSnackbar(event.message ?: sendFailed, retry, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed
                ) viewModel.updateSessionSetting(event.setting, event.value)
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
    // サイドパネルの存在を初回だけ知らせる（パネルを少しのぞかせる）。見せたか・自分で開いたかを端末に残す（マスター指定 2026-11-26）
    val context = androidx.compose.ui.platform.LocalContext.current
    val hintPrefs = remember(context) { context.getSharedPreferences(HINT_PREFS, android.content.Context.MODE_PRIVATE) }
    var panelHintPending by remember { mutableStateOf(!hintPrefs.getBoolean(KEY_PANEL_HINT_SHOWN, false)) }
    ChatContent(
        panelHintPending = panelHintPending,
        onPanelHintDone = {
            panelHintPending = false
            hintPrefs.edit().putBoolean(KEY_PANEL_HINT_SHOWN, true).apply()
        },
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
            finishWorktree = viewModel::finishWorktree,
            mergeAndFinishWorktree = viewModel::mergeAndFinishWorktree,
            delete = viewModel::delete,
            textChange = viewModel::onTextChange,
            reuse = viewModel::reuse,
            addQuote = viewModel::addQuote,
            addPastedText = viewModel::addPastedText,
            removeText = viewModel::removeText,
            expandText = viewModel::expandText,
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
            loadHostStats = viewModel::loadHostStats,
            sheetCache = viewModel.sheetCache,
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
    val finishWorktree: (jp.miyavi.android.clawdroid.core.data.SessionSummary) -> Unit = {},
    /** 採用: エージェントにマージを頼み、済んだら終える（2 つ目は依頼の文）。 */
    val mergeAndFinishWorktree: (jp.miyavi.android.clawdroid.core.data.SessionSummary, String) -> Unit = { _, _ -> },
    val delete: (jp.miyavi.android.clawdroid.core.data.SessionSummary) -> Unit,
    val textChange: (String) -> Unit,
    val reuse: (String) -> Unit = {},
    val addQuote: (String) -> Unit = {},
    val addPastedText: (String) -> Unit = {},
    val removeText: (String) -> Unit = {},
    val expandText: (String) -> Unit = {},
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
    val loadHostStats: suspend () -> jp.miyavi.android.clawdroid.core.data.HostStats?,
    val sheetCache: SheetUsageCache,
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
    /** サイドパネルを初回だけのぞかせるか。 */
    panelHintPending: Boolean = false,
    /** のぞかせた、または自分でパネルを開いた（もう知らせない）。 */
    onPanelHintDone: () -> Unit = {},
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
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // サイドパネルを開き始めたら（ボタン・左端からのスワイプとも）、キーボードを閉じる。
    // 閉じた位置はパネルの位置の最小値（開く途中は必ずそれより右）なので、そこからタッチの遊び（touch slop）より動いた時点で閉じる
    val touchSlop = androidx.compose.ui.platform.LocalViewConfiguration.current.touchSlop
    LaunchedEffect(drawerState, touchSlop) {
        var closedOffset = Float.POSITIVE_INFINITY
        snapshotFlow { drawerState.currentOffset }
            .map { offset ->
                if (offset.isNaN()) return@map false
                closedOffset = minOf(closedOffset, offset)
                drawerState.targetValue == DrawerValue.Open || offset > closedOffset + touchSlop
            }
            .distinctUntilChanged()
            .collect { opening ->
                if (opening) {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
            }
    }
    val connected = state.connection as? ConnectionState.Connected
    // 新しい会話の画面で、まだ始められない（つながっていない・エージェントを読み込み中）: 真ん中に状態を出し、
    // 入力欄も使えなくする。上のお知らせは重ねない（マスター指定 2026-11-25）
    val offlinePlaceholder = state.currentSessionKey == null && !state.readyForNewChat
    val canReply = connected != null && state.currentSession?.archived != true && !offlinePlaceholder
    // 画像は一度に複数選べる（上限は MAX_ATTACH_IMAGES）
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACH_IMAGES)) { uris ->
        uris.forEach(actions.addImage)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.take(MAX_ATTACH_IMAGES).forEach(actions.addImage)
    }
    var openedImage by remember { mutableStateOf<jp.miyavi.android.clawdroid.feature.chat.message.ImageGallery?>(null) }
    // プレビュー中の引用・コピーしたテキスト（id。外したり送ったりして無くなったら閉じる）
    var openedText by rememberSaveable { mutableStateOf<String?>(null) }
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
    // 保存中は選んだ値を先に見せる（null は既定に戻す。マスター指定 2026-11-26）
    val pendingSetting = composer.pendingSetting
    fun pendingOf(setting: SessionSetting): Pair<SessionSetting, String?>? = pendingSetting?.takeIf { it.first == setting }
    fun savedTick(setting: SessionSetting): Int = composer.settingSaved?.takeIf { it.first == setting }?.second ?: 0
    val model = pendingOf(SessionSetting.MODEL)?.let { it.second ?: agent?.defaultModel }
        ?: composer.sessionOptions[SessionSetting.MODEL] ?: state.currentSession?.model ?: agent?.defaultModel
    val effort = pendingOf(SessionSetting.EFFORT)?.let { it.second ?: agent?.thinkingDefault }
        ?: composer.sessionOptions[SessionSetting.EFFORT] ?: state.currentSession?.thinkingLevel ?: agent?.thinkingDefault
    val permissionPending = pendingOf(SessionSetting.PERMISSIONS)
    val permission = if (permissionPending != null) permissionPending.second
    else composer.sessionOptions[SessionSetting.PERMISSIONS] ?: state.currentSession?.permissionMode
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

    // 会話の画像（添付の media://inbound/...、エージェントの MEDIA: のパス）は開いている会話の権限で取る
    val imageSessionKey = state.currentSessionKey
    val imageAgentId = state.currentSession?.agentId ?: state.selectedAgentId
    val imageSource = remember(inboundImages, imageSessionKey, imageAgentId) {
        if (inboundImages == null || imageSessionKey == null) null
        else object : InboundImageSource {
            override suspend fun load(source: String, messageScope: String) = inboundImages.load(source, imageSessionKey, imageAgentId, messageScope)
            override suspend fun bytes(source: String) = inboundImages.loadBytes(source, imageSessionKey, imageAgentId)
            override val progress get() = inboundImages.progress
            override suspend fun storageKey(source: String, messageId: String) =
                listOf(inboundImages.endpoint().orEmpty(), imageSessionKey, messageId, source).joinToString("|")
            override suspend fun saveFile(resolver: android.content.ContentResolver, file: jp.miyavi.android.clawdroid.core.data.MessagePart.File, onProgress: (Long, Long) -> Unit) =
                inboundImages.saveFile(resolver, file, imageSessionKey, imageAgentId, onProgress)
        }
    }
    CompositionLocalProvider(LocalInboundImages provides imageSource) {
    ProvideScreenHaze(hazeState) {
        // 会話の上に重ねず、会話を右へ押し出して横から出す（Gemini 型。暗くしない）。M3 の標準部品のまま
        // （マスター指定 2026-11-26。以前は ModalNavigationDrawer で上に重ねていた）
        val panelVeil = MaterialTheme.colorScheme.surface
        // 初回だけ、パネルを 1/3 まで引き出して止め、説明を出す。「了解」を押すまで引き出したまま保ち、押したら閉じる
        // （どこにあるかを伝える動き。一度きり。AGENTS.md 2.8。マスター指定 2026-11-26）。デバッグ版はメニューから何度でも流せる。
        // 本物の引き出しを途中まで動かす。M3 の部品は閉じ切っているあいだパネルを置かない（会話の位置が 0 のときは描かない）ため、
        // 会話だけをずらすとパネルが出なかった
        val closeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val hintDone by rememberUpdatedState(onPanelHintDone)
        val hintText = stringResource(R.string.panel_hint_message)
        val hintAck = stringResource(R.string.action_got_it)
        // 説明は引き出しの外に重ねる（会話と一緒に動かない）。会話側だけを scrim で沈め、のぞいたパネルを明るく残す
        // （スポットライト型。マスター指定 2026-11-26）。「了解」か戻るで閉じる
        var hintOverlay by remember { mutableStateOf(false) }
        var hintAckSignal by remember { mutableStateOf<kotlinx.coroutines.CompletableDeferred<Unit>?>(null) }
        var hintRunning by remember { mutableStateOf(false) }
        var hintJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        val windowWidthPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toFloat()
        val playPanelHint: () -> Unit = {
            hintJob?.cancel()
            hintJob = scope.launch {
                val closed = -windowWidthPx
                val peek = closed * (1f - PANEL_HINT_FRACTION)
                // 1/3 まで減速しながら出して止める。M3 の引き出しは「開く・閉じる」の 2 か所にしか止まれず、動きは必ず
                // 目標（開き切り）で終わるので、1/3 に着いたところで動きを打ち切る。打ち切った引き出しはその位置に留まり、
                // フレームを要求しない（CJ レビュー 2026-11-26: 終わりの値を 1/3 にしても開き切りに飛んでいた。以前は 1 時間動かしていた）。
                // shortcut: 途中の位置で止める動きはトークンのばねでは作れないため、キーフレームの時間を使う
                val peekSpec = androidx.compose.animation.core.keyframes {
                    durationMillis = PANEL_HINT_OPEN_MS + PANEL_HINT_GUARD_MS
                    closed at 0 using androidx.compose.animation.core.EaseOutCubic
                    peek at PANEL_HINT_OPEN_MS
                    peek at PANEL_HINT_OPEN_MS + PANEL_HINT_GUARD_MS - 1
                }
                hintRunning = true
                try {
                    val ack = kotlinx.coroutines.CompletableDeferred<Unit>()
                    hintAckSignal = ack
                    val opening = launch { drawerState.animateTo(DrawerValue.Open, peekSpec) }
                    val reaching = async { snapshotFlow { kotlin.math.abs(drawerState.currentOffset - peek) < 1f }.first { it } }
                    // 1/3 に着いた（true）・先に動きが終わった（false）・時間切れ（null）を分ける（CJ レビュー 2026-11-26）
                    val outcome = withTimeoutOrNull((PANEL_HINT_OPEN_MS + PANEL_HINT_GUARD_MS / 2).toLong()) {
                        kotlinx.coroutines.selects.select<Boolean> {
                            reaching.onAwait { true }
                            opening.onJoin { false }
                        }
                    }
                    // 先に終わったのが自分の引き出しで打ち切られたからなら、そのまま引き出しに任せる
                    val userTook = outcome == false && opening.isCancelled
                    reaching.cancel()
                    opening.cancel()
                    opening.join()
                    if (outcome != true) {
                        // 1/3 で止められなかった（アニメーションがオフで開き切った・時間切れ）なら、説明は出さずに閉じて終える。
                        // 自分で引き出し始めていたら、閉じずに任せる
                        if (!userTook) drawerState.snapTo(DrawerValue.Closed)
                        return@launch
                    }
                    val stopped = drawerState.currentOffset
                    hintOverlay = true
                    // 自分でパネルを動かしたら（止めた位置から離れたら）、了解とみなして説明を下げ、あとは引き出しに任せる
                    val dragged = launch {
                        snapshotFlow { kotlin.math.abs(drawerState.currentOffset - stopped) > 1f }.first { it }
                        ack.complete(Unit)
                    }
                    // 「了解」を押すまで消えない。押すまでパネルも引き出したまま
                    ack.await()
                    val userMoved = dragged.isCompleted
                    dragged.cancel()
                    hintOverlay = false
                    if (!userMoved) drawerState.animateTo(DrawerValue.Closed, closeSpec)
                } finally {
                    hintOverlay = false
                    hintAckSignal = null
                    hintRunning = false
                }
            }
        }
        // 会話を開いてエージェントがそろい、画面が落ち着いてから（動きと重い処理を重ねない）。自分で開いたら知らせない
        val panelReady = state.sidebarAgents.isNotEmpty() && state.connection is ConnectionState.Connected
        LaunchedEffect(panelHintPending, panelReady) {
            if (!panelHintPending || !panelReady) return@LaunchedEffect
            delay(PANEL_HINT_DELAY_MS)
            if (drawerState.isOpen || drawerState.targetValue == DrawerValue.Open) { hintDone(); return@LaunchedEffect }
            // 見せたことを先に残す（途中で画面を離れても二度は出さない）。動きはこの効果と切り離して最後まで流す
            hintDone()
            playPanelHint()
        }
        LaunchedEffect(drawerState) {
            snapshotFlow { drawerState.targetValue }.collect { target ->
                // 自分で開き始めたら（案内の動きは除く）、もう案内しない
                if (target == DrawerValue.Open && !hintRunning && panelHintPending) hintDone()
            }
        }
        fun revealOffset(): Float = drawerState.currentOffset
        val density = androidx.compose.ui.platform.LocalDensity.current
        val panelFloatPx = with(density) { PANEL_FLOAT.toPx() }
        // パネルの幅（全画面のパネルなので窓の幅）。中身を浮かせる割合に使う
        val drawerWidthPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toFloat()
        DismissibleNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                // 全画面のサイドパネル（Gemini 型）。開くと画面いっぱいまで広がる
                SidePanel(
                    connection = state.connection,
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
                    // 看板も中身も、少し下から浮き上がる
                    liftRange = PANEL_FLOAT,
                    contentLift = {
                        val offset = revealOffset()
                        if (offset.isNaN() || drawerWidthPx <= 0f) 0f else (-offset / drawerWidthPx).coerceIn(0f, 1f) * panelFloatPx
                    },
                    // 引き出しの奥行き（Gemini 型。マスター指定 2026-11-26）: パネルは横に動かさず、会話の下に最初から置いてあるように
                    // その場に留める（部品が左へずらす分を打ち消す）。引き出すにつれて少し下から浮き上がり、背景色（surface）のもやが晴れる。
                    // 開き具合は描画の中だけで読む（再構成もレイアウトも起こさない。AGENTS.md 2.8）
                    modifier = Modifier
                        .graphicsLayer {
                            val offset = drawerState.currentOffset
                            if (offset.isNaN() || size.width <= 0f) return@graphicsLayer
                            translationX = -offset.coerceAtMost(0f)
                            // 部品はパネルを会話より上に描くので、その場に留めると会話を覆ってしまう（会話が右へずれる動きが
                            // 見えず、いきなり全面に出ていた）。会話が退いた左側の幅だけを描き、そこだけ押せるようにする。
                            // 閉じているときは幅 0 で、会話への操作を邪魔しない
                            clip = true
                            shape = RevealedWidthShape((size.width + offset).coerceIn(0f, size.width))
                        }
                        .drawWithContent {
                            drawContent()
                            val offset = revealOffset()
                            val shown = if (offset.isNaN() || size.width <= 0f) 0f else (1f + offset / size.width).coerceIn(0f, 1f)
                            val veil = (1f - shown) * PANEL_VEIL_MAX
                            if (veil > 0f) drawRect(panelVeil, alpha = veil)
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
                            // デバッグ版だけ: 初回の案内をもう一度流す（リリース版からは外した。マスター指定 2026-11-26）
                            onDebugPanelHint = if (jp.miyavi.android.clawdroid.BuildConfig.DEBUG) playPanelHint else null,
                        )
                        ConnectionBanner(
                            if (offlinePlaceholder) connected ?: ConnectionState.NotConfigured else state.connection,
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
                            // 使えないあいだは入力欄ごと淡くする（押せそうに見せない）
                            val composerAlpha by androidx.compose.animation.core.animateFloatAsState(
                                if (canReply) 1f else jp.miyavi.android.clawdroid.core.designsystem.DisabledAlpha,
                                MaterialTheme.motionScheme.defaultEffectsSpec(),
                                label = "composerAlpha",
                            )
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
                                permission = agent?.let { a ->
                                    jp.miyavi.android.clawdroid.feature.chat.composer.PermissionControl(
                                        value = permission,
                                        saving = pendingOf(SessionSetting.PERMISSIONS) != null,
                                        savedTick = savedTick(SessionSetting.PERMISSIONS),
                                        enabled = canEdit && state.currentSession?.permissionModePending != true,
                                        options = jp.miyavi.android.clawdroid.feature.chat.composer.settingOptions(
                                            SessionSetting.PERMISSIONS, a, state.currentSession, composer.sessionOptions, canSelectFull, catalog = null,
                                        ).orEmpty(),
                                        onSelect = { value -> actions.updateSetting(SessionSetting.PERMISSIONS, value) },
                                    )
                                },
                                pending = state.chat?.queuedInputs.orEmpty(),
                                onCancelPending = actions.cancelPending,
                                onRestorePending = actions.restorePending,
                                onTextChange = actions.textChange,
                                onPickPhotos = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                // 写真は別の項目なので、ファイルは文書の種類に絞る（*/* だとピッカーの「最近」が写真で埋まる。マスター指定 2026-11-24）
                                onPickFiles = { pickFile.launch(DOCUMENT_MIME_TYPES) },
                                onRemoveImage = actions.removeImage,
                                onOpenText = { openedText = it.id },
                                onRemoveText = actions.removeText,
                                onPasteLongText = actions.addPastedText,
                                onPasteImage = actions.addImage,
                                onOpenImage = { attachments, index ->
                                    // ファイルは並びから外し、押した画像の位置を数え直す
                                    val images = attachments.filter { it.image.isImage }
                                    openedImage = jp.miyavi.android.clawdroid.feature.chat.message.ImageGallery(
                                        images.map { MessagePart.Image(url = null, base64 = it.image.base64, mimeType = it.image.mimeType) },
                                        images.indexOf(attachments[index]).coerceAtLeast(0),
                                    )
                                },
                                // 送ったらキーボードを閉じて、応答を読めるようにする（マスター指定 2026-11-26）
                                onSend = {
                                    keyboard?.hide()
                                    focusManager.clearFocus()
                                    actions.send()
                                },
                                onStop = actions.stop,
                                modifier = Modifier.widthIn(max = Dimens.composerMaxWidth).padding(horizontal = Spacing.l, vertical = Spacing.s)
                                    .graphicsLayer { alpha = composerAlpha },
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
                        // 文字を選んだときのメニューの「プロンプトに入力」（このアプリの中だけ）
                        jp.miyavi.android.clawdroid.feature.chat.message.PromptFromSelection(
                            onInsert = { text ->
                                // 引用のタイルにする（本文には混ぜない。マスター指定 2026-11-26）
                                if (canReply) {
                                    actions.addQuote(text)
                                    if (composerFocus.requestFocus()) keyboard?.show()
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                        MessageList(
                            newSessionCwd = composer.newSessionCwd,
                            onPickFolder = { pickingFolder = true },
                            onReconnect = actions.reconnect,
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
                            onReuse = if (canReply) { text ->
                                actions.reuse(text)
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
                    }
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding())) {
                        ScrollToBottomButton(listState, hazeState)
                    }
                }
            }
        }
        // 案内の重ね（スポットライト型）。会話側（右の 2/3）だけを沈めて操作を受け止め、のぞいたパネル（左の 1/3）は
        // 明るいまま触れる（引き出せば了解とみなす）。説明はパネルとの境目の近くに浮かせる
        BackHandler(enabled = hintOverlay) { hintAckSignal?.complete(Unit) }
        AnimatedVisibility(
            visible = hintOverlay,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(1f - PANEL_HINT_FRACTION)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = PANEL_HINT_SCRIM))
                        // 下の会話には触れさせない（押しても消さない。読まずに消えるのを防ぐ）
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    jp.miyavi.android.clawdroid.core.designsystem.GlassPanel(
                        hazeState = hazeState,
                        fixedOpacity = jp.miyavi.android.clawdroid.core.designsystem.MENU_OPACITY,
                        modifier = Modifier.padding(horizontal = Spacing.l),
                    ) {
                        Column(
                            Modifier.padding(start = Spacing.xl, end = Spacing.m, top = Spacing.l, bottom = Spacing.s),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Text(
                                hintText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(end = Spacing.s).semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            TextButton(
                                onClick = { hintAckSignal?.complete(Unit) },
                                modifier = Modifier.align(Alignment.End),
                            ) { Text(hintAck) }
                        }
                    }
                }
            }
        }
        ImageOverlay(gallery = openedImage, onDismiss = { openedImage = null })
        composer.texts.firstOrNull { it.id == openedText }?.let { item ->
            TextAttachmentSheet(
                item = item,
                hazeState = hazeState,
                onExpand = { openedText = null; actions.expandText(item.id) },
                onRemove = { openedText = null; actions.removeText(item.id) },
                onDismiss = { openedText = null },
            )
        }
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
            SessionAction.FINISH -> if (current != null) {
                FinishWorktreeSheet(
                    session = current,
                    hazeState = hazeState,
                    loadDiffSummary = actions.loadDiffSummary,
                    onDismiss = { sessionAction = null },
                    onMerge = { request -> sessionAction = null; actions.mergeAndFinishWorktree(current, request) },
                    onDiscard = { sessionAction = null; actions.finishWorktree(current) },
                )
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
                loadHostStats = actions.loadHostStats,
                cache = actions.sheetCache,
                saving = pendingSetting?.first,
                savedTick = ::savedTick,
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
    onDebugPanelHint: (() -> Unit)? = null,
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
                onDebugPanelHint = onDebugPanelHint,
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



/**
 * Gateway につながっていないときの新しい会話の画面。淡いマスコットと状態の一言。つなぎ直している間は読み込みの表示、
 * 止まっていれば「接続し直す」（マスター指定 2026-11-25）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OfflinePlaceholder(
    connection: ConnectionState,
    agents: jp.miyavi.android.clawdroid.core.data.AgentsState,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = connection is ConnectionState.Connected
    // つながったがエージェントを読めなかった: つなぎ直して読み直す
    val agentsFailed = connected && !agents.loading && agents.agents.isEmpty()
    val trying = !agentsFailed && (
        connected || connection is ConnectionState.Connecting || connection is ConnectionState.Reconnecting ||
            connection is ConnectionState.Restarting || connection is ConnectionState.AwaitingApproval ||
            // 休んでいた接続は、前に戻るとすぐつなぎ直す
            connection is ConnectionState.Paused
        )
    Column(
        modifier.padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Image(
            painter = painterResource(R.drawable.launcher_mascot_foreground),
            contentDescription = null,
            modifier = Modifier.size(Dimens.offlineMascot).graphicsLayer { alpha = jp.miyavi.android.clawdroid.core.designsystem.DisabledAlpha },
        )
        Text(
            stringResource(
                when {
                    agentsFailed -> R.string.offline_agents_failed
                    connected -> R.string.panel_loading_agents
                    trying -> R.string.offline_connecting
                    else -> R.string.offline_unreachable
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        // つながっていないときだけ、使えない理由を添える（読み込み中は待てば使える）
        if (!connected) {
            Text(
                stringResource(R.string.offline_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (trying) LoadingIndicator() else TextButton(onClick = onReconnect) { Text(stringResource(R.string.connection_reconnect)) }
    }
}

/** 再接続中などの控えめな表示（Edge Cases）。接続済みのときは出さない。 */
@Composable
private fun ConnectionBanner(connection: ConnectionState, onReconnect: () -> Unit, modifier: Modifier = Modifier) {
    val text = when (connection) {
        is ConnectionState.Reconnecting, ConnectionState.Connecting, ConnectionState.Paused -> stringResource(R.string.connection_reconnecting)
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
    onReuse: ((String) -> Unit)? = null,
    onOpenImage: (jp.miyavi.android.clawdroid.feature.chat.message.ImageGallery) -> Unit,
    onOpenVideo: (String) -> Unit,
    onRetry: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState,
    newSessionCwd: String? = null,
    onPickFolder: () -> Unit = {},
    onReconnect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 透かす元は会話の中身だけ（高速スクロールの吹き出しは透かす側なので、元に含めない）
    val sourceModifier = modifier.hazeSource(hazeState)
    val chat = state.chat
    val agent = state.currentSession?.agentId?.let { id -> state.agents.agents.firstOrNull { it.id == id } }
        ?: state.selectedAgent
    // 行のまとめは ViewModel でメインスレッドの外で作る（変わっていない行は前と同じインスタンス）
    val rows = state.rows
    // 新しい順に並べる。作り直すと項目の中身の関数が毎回変わり、表示済みの行まで組み直すので覚えておく
    val latestFirst = remember(rows) { rows.asReversed() }
    // 一番古い行だけ上の余白を付けない。位置（index）で比べると、1 件増えるたびに全部の行の index がずれて組み直しになる
    val oldestKey = rows.firstOrNull()?.key
    val live = chat?.live
    val liveText = live?.text.orEmpty()
    when {
        // 新しい会話で Gateway につながっていない: ローカルで何かできそうに見せない（作業フォルダの選択も出さない）
        state.currentSessionKey == null && !state.readyForNewChat -> Box(sourceModifier, contentAlignment = Alignment.Center) {
            OfflinePlaceholder(state.connection, state.agents, onReconnect, Modifier.padding(contentPadding))
        }
        state.currentSessionKey == null || (chat != null && !chat.loading && !chat.running && rows.isEmpty() && live == null) -> {
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
        chat == null || (chat.loading && rows.isEmpty()) -> Box(sourceModifier, contentAlignment = Alignment.Center) { LoadingIndicator() }
        chat.error != null && rows.isEmpty() -> Box(sourceModifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.chat_load_failed), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
        // 右端の高速スクロールバー。古い方へ一気に戻れる。吹き出しはその位置の日時
        else -> FastScroller(
            listState = listState,
            itemCount = latestFirst.size + 1,
            label = { index -> if (index == 0) null else latestFirst.getOrNull(index - 1)?.last?.timeLabel },
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
                Column(Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()) {
                    if (chat.running || liveText.isNotBlank()) {
                        LiveAssistantMessage(
                            text = liveText,
                            agent = agent,
                            running = chat.running,
                            startedAt = chat.runStartedAt,
                            reportedOutputTokens = chat.reportedOutputTokens,
                            compacting = chat.activity == jp.miyavi.android.clawdroid.core.data.RunActivity.Compacting,
                            modifier = Modifier.fillMaxWidth().padding(top = if (rows.isEmpty()) 0.dp else Spacing.l),
                        )
                    }
                    // 送信済みで、まだ読まれていない送信。流れてくる応答より常に下に置き、読まれたらその位置で会話に入る
                    // （送った時点の位置に入れると、読む前の応答がその下に続いて答えたように見えるため）
                    SentInputs(chat.sentInputs, onOpenImage, onOpenVideo, onCopy, top = chat.running || liveText.isNotBlank() || rows.isNotEmpty())
                }
            }
            items(latestFirst, key = { it.key }, contentType = { it.contentType }) { row ->
                val item = row.last
                val message = item.message
                val itemModifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()
                    .padding(top = if (row.key == oldestKey) 0.dp else Spacing.l)
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
                        parts = row.tools,
                        modifier = itemModifier,
                    )
                } else when (message.role) {
                    ChatMessage.Role.USER -> Column(itemModifier) {
                        // この送信から別のモデルが答える
                        if (modelChange != null) ModelChangeDivider(modelChange, Modifier.padding(bottom = Spacing.l))
                        UserMessage(message, item.timeLabel, onOpenImage, onOpenVideo, onCopy = onCopy, onReuse = onReuse)
                    }
                    ChatMessage.Role.ASSISTANT, ChatMessage.Role.SYSTEM -> AssistantMessage(
                        message = message,
                        markdown = item.markdown,
                        agent = agent,
                        precedingTools = row.tools,
                        timeLabel = item.timeLabel,
                        elapsedMs = item.elapsedMs,
                        onCopy = onCopy,
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

/** 送信済みで読まれ待ちの送信（未読の吹き出し）。淡く出し、時刻の代わりに「未読」と示す。取り消しの操作は出さない。 */
@Composable
private fun SentInputs(
    inputs: List<jp.miyavi.android.clawdroid.core.data.PendingInput>,
    onOpenImage: (jp.miyavi.android.clawdroid.feature.chat.message.ImageGallery) -> Unit,
    onOpenVideo: (String) -> Unit,
    onCopy: (String) -> Unit,
    top: Boolean,
) {
    val unread = stringResource(R.string.chat_sent_unread)
    inputs.forEachIndexed { index, input ->
        key(input.runId) {
            val message = remember(input) {
                ChatMessage(
                    id = "sent-${input.runId}",
                    role = ChatMessage.Role.USER,
                    parts = input.parts.ifEmpty { listOf(MessagePart.Text(input.text)) },
                    timestamp = null,
                    runId = input.runId,
                    isError = false,
                    truncated = false,
                )
            }
            UserMessage(
                message, unread, onOpenImage, onOpenVideo,
                onCopy = onCopy,
                modifier = Modifier
                    .padding(top = if (index > 0 || top) Spacing.l else 0.dp)
                    .graphicsLayer { alpha = SENT_UNREAD_ALPHA },
            )
        }
    }
}

/** 未読の吹き出しの不透明度（AGENTS.md 2.2 の無効・折りたたみと同じ 0.6）。 */
private const val SENT_UNREAD_ALPHA = 0.6f

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
private val DOCUMENT_MIME_TYPES = arrayOf("application/*", "text/*")

private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp")
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "avif")

/** サイドパネルを閉じかけのときに重ねるもやの、いちばん濃い不透明度（開き切ると 0）。 */
private const val PANEL_VEIL_MAX = 0.9f

/** 左から [width] だけを残す形（サイドパネルのうち、会話が退いて見えている所）。描画と押せる範囲の両方を切る。 */
private data class RevealedWidthShape(val width: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ) = androidx.compose.ui.graphics.Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, width, size.height))
}

/** 初回の案内で会話を右へずらす割合（画面の幅に対して。パネルの 1/3 が見える。マスター指定 2026-11-26）。 */
private const val PANEL_HINT_FRACTION = 1f / 3f

/** 1/3 に着いてから打ち切るまでの保険の時間（この間はキーフレームが 1/3 に留まる）。 */
private const val PANEL_HINT_GUARD_MS = 2_000

/** 初回の案内で、パネルを 1/3 まで出すのにかける時間（shortcut: 途中で止めるためキーフレームの時間で動かす）。 */
private const val PANEL_HINT_OPEN_MS = 450

/** 案内で会話側に重ねる scrim の不透明度（M3 のモーダルの後ろと同じくらい）。 */
private const val PANEL_HINT_SCRIM = 0.32f


/** 会話を開いてから案内を始めるまでの間（画面が落ち着くのを待つ。shortcut: トークンに無い待ち時間）。 */
private const val PANEL_HINT_DELAY_MS = 800L

private const val HINT_PREFS = "hints"
private const val KEY_PANEL_HINT_SHOWN = "panel_hint_shown"

/** サイドパネルが閉じた位置で沈んでいる深さ（引き出すにつれて浮き上がる）。 */
private val PANEL_FLOAT = Spacing.xl

/**
 * 引用・コピーしたテキストの全文（共通のボトムシート。AGENTS.md 2.5）。長ければ中の面だけをスクロールする。
 * 引用は本文と同じ文字、コピーしたテキストはログやコードが多いので等幅（マスター指定 2026-11-26）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextAttachmentSheet(
    item: jp.miyavi.android.clawdroid.feature.chat.PendingText,
    hazeState: dev.chrisbanes.haze.HazeState,
    onExpand: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val quote = item.kind == jp.miyavi.android.clawdroid.feature.chat.PendingText.Kind.QUOTE
    jp.miyavi.android.clawdroid.core.designsystem.AppBottomSheet(onDismissRequest = onDismiss, hazeState = hazeState) {
        Text(
            stringResource(if (quote) R.string.chat_text_quote else R.string.chat_text_paste) + "・" + stringResource(R.string.chat_text_chars, item.text.length),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs),
        )
        androidx.compose.material3.Surface(
            color = jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    item.text,
                    style = if (quote) jp.miyavi.android.clawdroid.core.designsystem.bodyTextStyle()
                    else MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
        ) {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.chat_text_remove)) }
            androidx.compose.material3.FilledTonalButton(onClick = onExpand) { Text(stringResource(R.string.chat_text_expand)) }
        }
    }
}
