package jp.miyavi.android.sumahoclaw.navigation

import android.text.format.DateUtils
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import jp.miyavi.android.sumahoclaw.core.settings.AgentLayout
import jp.miyavi.android.sumahoclaw.core.shortcut.removed
import jp.miyavi.android.sumahoclaw.core.shortcut.reordered
import jp.miyavi.android.sumahoclaw.core.shortcut.upsert
import jp.miyavi.android.sumahoclaw.core.settings.visible
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.sumahoclaw.AppContainer
import jp.miyavi.android.sumahoclaw.BuildConfig
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.RestartResult
import jp.miyavi.android.sumahoclaw.core.designsystem.FloatingIconButton
import jp.miyavi.android.sumahoclaw.core.designsystem.ScreenMotionScheme
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.gateway.ConnectionState
import kotlinx.coroutines.flow.map
import jp.miyavi.android.sumahoclaw.core.settings.ThemeSettings
import jp.miyavi.android.sumahoclaw.feature.automation.AutomationsScreen
import jp.miyavi.android.sumahoclaw.feature.chat.ChatNavigation
import jp.miyavi.android.sumahoclaw.feature.chat.ChatScreen
import jp.miyavi.android.sumahoclaw.feature.chat.ChatViewModel
import jp.miyavi.android.sumahoclaw.feature.connect.ConnectScreen
import jp.miyavi.android.sumahoclaw.feature.connect.ConnectViewModel
import jp.miyavi.android.sumahoclaw.feature.settings.SettingsActions
import jp.miyavi.android.sumahoclaw.feature.settings.SettingsContent
import jp.miyavi.android.sumahoclaw.feature.settings.SettingsState
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object ChatRoute : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data class AutomationsRoute(val agentId: String, val agentName: String) : NavKey
@Serializable data class AutomationDetailRoute(val jobId: String) : NavKey
@Serializable data object AgentLayoutRoute : NavKey
@Serializable data object NotificationSettingsRoute : NavKey
@Serializable data object ShortcutSettingsRoute : NavKey
@Serializable data object LicensesRoute : NavKey
@Serializable data class TerminalRoute(val sessionKey: String? = null, val agentId: String? = null, val cwd: String? = null) : NavKey
@Serializable data class FilesRoute(val sessionKey: String, val agentId: String) : NavKey
@Serializable data object UsageRoute : NavKey
@Serializable data class AgentSettingsRoute(val agentId: String) : NavKey
@Serializable data class AgentFileRoute(val agentId: String, val name: String) : NavKey
/** エージェントの設定から開くワークスペース（会話に紐付かない。spec 004 FR-012）。 */
@Serializable data class WorkspaceFilesRoute(val agentId: String) : NavKey

/**
 * 画面の構成。接続情報が無い・認証が切れたときは接続画面、それ以外は会話を起点にした Navigation 3 の画面の積み重ね。
 */
@Composable
fun AppNavigation(container: AppContainer) {
    val connection by container.gateway.state.collectAsStateWithLifecycle()
    // 一度でも接続できたら、切断や再接続のあいだも会話の画面を出し続ける（表示済みの内容を残す）。
    // 前回の起動でつながった接続先なら、起動直後のつなぎ直しのあいだに接続画面を挟まない（マスター指摘 2026-11-25）
    val context = LocalContext.current
    val launchPrefs = remember { context.getSharedPreferences(LAUNCH_PREFS, android.content.Context.MODE_PRIVATE) }
    var everConnected by rememberSaveable { mutableStateOf(launchPrefs.getBoolean(KEY_CONNECTED_BEFORE, false)) }
    // 初めての接続で承認を経たときは、つながったことを確かめてもらってから会話へ進む（本家に倣う。マスター指定 2026-11-24）
    var pairedPending by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(container.gateway) {
        container.gateway.state.collect { state ->
            when (state) {
                is ConnectionState.Connected -> {
                    everConnected = true
                    launchPrefs.edit().putBoolean(KEY_CONNECTED_BEFORE, true).apply()
                }
                is ConnectionState.AwaitingApproval -> if (!everConnected) pairedPending = true
                ConnectionState.NotConfigured, ConnectionState.Disconnected -> {
                    if (state == ConnectionState.NotConfigured) {
                        everConnected = false
                        launchPrefs.edit().remove(KEY_CONNECTED_BEFORE).apply()
                    }
                    if (!everConnected) pairedPending = false
                }
                else -> Unit
            }
        }
    }
    val needsConnect = connection is ConnectionState.NotConfigured || pairedPending ||
        (!everConnected && connection !is ConnectionState.Connected)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (needsConnect) {
            val vm: ConnectViewModel = viewModel(factory = viewModelFactory { initializer { ConnectViewModel(container.gateway, container.secrets) } })
            ConnectScreen(vm, onContinue = { pairedPending = false })
        } else {
            val approvalHaze = rememberHazeState()
            Box(Modifier.fillMaxSize().hazeSource(approvalHaze)) { MainNavigation(container) }
            // ダウンロードの進み具合は、どの画面でも上部に出す
            jp.miyavi.android.sumahoclaw.core.designsystem.DownloadBanner(
                container.downloads, approvalHaze, Modifier.align(Alignment.TopCenter),
            )
            ApprovalPrompt(container.approvals, container.sessions, approvalHaze)
            // 承認を先に出す。承認が無いときだけ質問のシートを出す（シートを重ねない）
            val approvals by container.approvals.requests.collectAsStateWithLifecycle()
            if (approvals.isEmpty()) QuestionPrompt(container.questions, container.sessions, approvalHaze)
        }
    }
}

@Composable
private fun MainNavigation(container: AppContainer) {
    val backStack = rememberNavBackStack(ChatRoute)
    // ファイルとターミナルの状態は会話ごとに残す（閉じて開き直すと続きから）。画面より長く生きるようアクティビティに置く
    val workStores: WorkStores = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    val restartDone = stringResource(R.string.restart_done)
    val restartTimeout = stringResource(R.string.restart_timeout)
    val restartFailed = stringResource(R.string.restart_failed)
    // 通知から開く: 会話は最初の画面まで戻して（会話を開くのは ChatViewModel）、自動化は詳細を重ねる（spec 006 R7）
    LaunchedEffect(Unit) {
        container.foreground.openRequests.collect { target ->
            when (target) {
                is jp.miyavi.android.sumahoclaw.core.notify.OpenTarget.Session,
                is jp.miyavi.android.sumahoclaw.core.notify.OpenTarget.NewChat,
                -> while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                is jp.miyavi.android.sumahoclaw.core.notify.OpenTarget.Automation -> {
                    container.foreground.consumed()
                    backStack.push(AutomationDetailRoute(target.jobId))
                }
                jp.miyavi.android.sumahoclaw.core.notify.OpenTarget.Approvals -> container.foreground.consumed()
            }
        }
    }
    val timeLabel: (Long) -> String = remember(context) {
        { ms -> DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString() }
    }

    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.surface),
        onBack = { backStack.pop() },
        transitionSpec = { pushTransition() },
        popTransitionSpec = { popTransition() },
        predictivePopTransitionSpec = { _ -> popTransition() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<ChatRoute> {
                val gate = rememberNavGate()
                val vm: ChatViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            ChatViewModel(
                                gateway = container.gateway,
                                sessions = container.sessions,
                                chats = container.chats,
                                claudeUsage = container.claudeUsage,
                                settings = container.settings,
                                drafts = container.database.drafts(),
                                positions = container.database.readingPositions(),
                                contentResolver = context.contentResolver,
                                timeFormatter = { ms ->
                                    DateUtils.formatDateTime(context, ms, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL)
                                },
                                foreground = container.foreground,
                            )
                        }
                    },
                )
                ChatScreen(
                    viewModel = vm,
                    questions = container.questions,
                    inboundImages = container.inboundImages,
                    timeLabel = timeLabel,
                    navigation = ChatNavigation(
                        openSettings = { gate.run { backStack.push(SettingsRoute) } },
                        openAutomations = { agent -> gate.run { backStack.push(AutomationsRoute(agent.id, agent.name)) } },
                        openTerminal = { key, agentId, cwd -> gate.run { backStack.push(TerminalRoute(key, agentId, cwd)) } },
                        openFiles = { key, agentId -> gate.run { backStack.push(FilesRoute(key, agentId)) } },
                        restartGateway = { confirmRestart = true },
                        openAgentSettings = { agentId -> gate.run { backStack.push(AgentSettingsRoute(agentId)) } },
                    ),
                )
            }
            entry<SettingsRoute> {
                val gate = rememberNavGate(onBack = { backStack.pop() })
                val theme by container.settings.theme.collectAsStateWithLifecycle(ThemeSettings())
                val connection by container.gateway.state.collectAsStateWithLifecycle()
                var url by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) { url = container.secrets.loadConnection()?.url }
                val agentsState by container.sessions.agents.collectAsStateWithLifecycle()
                val agentLayout by container.settings.agentLayout.collectAsStateWithLifecycle(AgentLayout())
                val shown = remember(agentsState.agents, agentLayout) { agentLayout.visible(agentsState.agents).size }
                val notifications by container.settings.notifications.collectAsStateWithLifecycle(null)
                val shortcuts by container.settings.shortcuts.collectAsStateWithLifecycle(jp.miyavi.android.sumahoclaw.core.shortcut.ShortcutSettings())
                // 言語を切り替えると端末が画面を作り直すので、開いたときに読めばよい
                val languageContext = androidx.compose.ui.platform.LocalContext.current
                val languages = remember { jp.miyavi.android.sumahoclaw.core.settings.AppLanguage.supported(languageContext) }
                val language = remember { jp.miyavi.android.sumahoclaw.core.settings.AppLanguage.current(languageContext) }
                SettingsContent(
                    state = SettingsState(
                        theme, connection, url, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, shown, agentsState.agents.size, notifications, shortcuts.slots.size,
                        languages = languages, language = language,
                    ),
                    snackbar = snackbar,
                    actions = SettingsActions(
                        onBack = { gate.run { backStack.pop() } },
                        setAppearance = { scope.launch { container.settings.setAppearance(it) } },
                        setTheme = { scope.launch { container.settings.setTheme(it) } },
                        setFont = { scope.launch { container.settings.setFont(it) } },
                        setFontSize = { scope.launch { container.settings.setFontSize(it) } },
                        setCustomSeed = { scope.launch { container.settings.setCustomSeed(it) } },
                        setSurfaceStyle = { scope.launch { container.settings.setSurfaceStyle(it) } },
                        setGlassRefraction = { scope.launch { container.settings.setGlassRefraction(it) } },
                        setBlurStrength = { scope.launch { container.settings.setBlurStrength(it) } },
                        reconnect = container.gateway::reconnectNow,
                        disconnect = container.gateway::disconnect,
                        forget = { scope.launch { container.gateway.forget() } },
                        restart = { confirmRestart = true },
                        openAgentLayout = { gate.run { backStack.push(AgentLayoutRoute) } },
                        setNotifications = { next -> scope.launch { container.settings.setNotifications(next) } },
                        openNotificationSettings = { gate.run { backStack.push(NotificationSettingsRoute) } },
                        openShortcuts = { gate.run { backStack.push(ShortcutSettingsRoute) } },
                        openLicenses = { gate.run { backStack.push(LicensesRoute) } },
                        setLanguage = { jp.miyavi.android.sumahoclaw.core.settings.AppLanguage.set(languageContext, it) },
                    ),
                )
            }
            entry<LicensesRoute> {
                val gate = rememberNavGate(onBack = { backStack.pop() })
                jp.miyavi.android.sumahoclaw.feature.settings.LicensesScreen(onBack = { gate.run { backStack.pop() } })
            }
            entry<ShortcutSettingsRoute> {
                val gate = rememberNavGate(onBack = { backStack.pop() })
                val shortcuts by container.settings.shortcuts.collectAsStateWithLifecycle(jp.miyavi.android.sumahoclaw.core.shortcut.ShortcutSettings())
                val agentsState by container.sessions.agents.collectAsStateWithLifecycle()
                val lists by container.sessions.sessions.collectAsStateWithLifecycle()
                // 枠の名前（会話の名前）を出すため、枠のエージェントの一覧を読んでおく
                LaunchedEffect(shortcuts.slots) { shortcuts.slots.map { it.agentId }.distinct().forEach(container.sessions::ensureLoaded) }
                val shortcutActions = remember(container) {
                    jp.miyavi.android.sumahoclaw.feature.settings.ShortcutSettingsActions(
                        onBack = { gate.run { backStack.pop() } },
                        save = { slot ->
                            scope.launch {
                                val url = container.secrets.loadConnection()?.url
                                container.settings.updateShortcuts { it.upsert(slot, url) }
                            }
                        },
                        remove = { id -> scope.launch { container.settings.updateShortcuts { it.removed(id) } } },
                        reorder = { ids -> scope.launch { container.settings.updateShortcuts { it.reordered(ids) } } },
                        ensureLoaded = container.sessions::ensureLoaded,
                        loadFolderCandidates = { agentId -> runCatching { container.sessions.folderCandidates(agentId) }.getOrDefault(emptyList()) },
                        listDir = container.sessions::listDir,
                    )
                }
                jp.miyavi.android.sumahoclaw.feature.settings.ShortcutSettingsScreen(
                    state = jp.miyavi.android.sumahoclaw.feature.settings.ShortcutSettingsUi(shortcuts.slots, agentsState.agents, lists),
                    actions = shortcutActions,
                )
            }
            entry<NotificationSettingsRoute> {
                val gate = rememberNavGate(onBack = { backStack.pop() })
                val notifications by container.settings.notifications.collectAsStateWithLifecycle(null)
                notifications?.let { current ->
                    jp.miyavi.android.sumahoclaw.feature.settings.NotificationSettingsScreen(
                        settings = current,
                        onChange = { next -> scope.launch { container.settings.setNotifications(next) } },
                        onTest = { container.notifier.showTest() },
                        onBack = { gate.run { backStack.pop() } },
                    )
                }
            }
            entry<AgentLayoutRoute> {
                val gate = rememberNavGate(onBack = { backStack.pop() })
                val agentsState by container.sessions.agents.collectAsStateWithLifecycle()
                val agentLayout by container.settings.agentLayout.collectAsStateWithLifecycle(AgentLayout())
                jp.miyavi.android.sumahoclaw.feature.settings.AgentLayoutScreen(
                    agents = agentsState.agents,
                    layout = agentLayout,
                    onChange = { scope.launch { container.settings.setAgentLayout(it) } },
                    onBack = { gate.run { backStack.pop() } },
                )
            }
            entry<AutomationsRoute> { route ->
                val gate = rememberNavGate(onBack = { backStack.pop() })
                val automations = remember { jp.miyavi.android.sumahoclaw.core.data.AutomationsRepository(container.gateway) }
                val vm: jp.miyavi.android.sumahoclaw.feature.automation.AutomationsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { jp.miyavi.android.sumahoclaw.feature.automation.AutomationsViewModel(automations, route.agentId) }
                    },
                )
                AutomationsScreen(
                    viewModel = vm,
                    agentName = route.agentName,
                    onOpen = { job -> gate.run { backStack.push(AutomationDetailRoute(job.id)) } },
                    onBack = { gate.run { backStack.pop() } },
                )
            }
            entry<AutomationDetailRoute> { route ->
                val gate = rememberNavGate(onBack = { backStack.pop() })
                val automations = remember { jp.miyavi.android.sumahoclaw.core.data.AutomationsRepository(container.gateway) }
                val vm: jp.miyavi.android.sumahoclaw.feature.automation.AutomationDetailViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            val configs = jp.miyavi.android.sumahoclaw.core.data.AgentConfigRepository(container.gateway)
                            jp.miyavi.android.sumahoclaw.feature.automation.AutomationDetailViewModel(automations, route.jobId) { configs.modelCatalog(it) }
                        }
                    },
                )
                jp.miyavi.android.sumahoclaw.feature.automation.AutomationDetailScreen(vm) { gate.run { backStack.pop() } }
            }
            entry<TerminalRoute> { route ->
                val gate = rememberNavGate(onBack = { backStack.pop() })
                WithWorkStore(workStores, route.sessionKey, route.agentId) {
                    val vm: jp.miyavi.android.sumahoclaw.feature.terminal.TerminalViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                jp.miyavi.android.sumahoclaw.feature.terminal.TerminalViewModel(
                                    repository = jp.miyavi.android.sumahoclaw.core.data.TerminalRepository(
                                        container.gateway,
                                        jp.miyavi.android.sumahoclaw.core.data.SharedPreferencesTerminalIdStore(
                                            context.getSharedPreferences("terminal_ids", android.content.Context.MODE_PRIVATE),
                                        ),
                                    ),
                                    gateway = container.gateway,
                                    // 会話ごとに 1 つ。会話の無いときはエージェントごと
                                    conversationKey = route.sessionKey ?: "agent:${route.agentId.orEmpty()}",
                                    agentId = route.agentId,
                                    cwd = route.cwd,
                                )
                            }
                        },
                    )
                    val sessionKey = route.sessionKey
                    val agentId = route.agentId
                    jp.miyavi.android.sumahoclaw.feature.terminal.TerminalScreen(
                        viewModel = vm,
                        cwd = route.cwd,
                        onBack = { gate.run { backStack.pop() } },
                        onEnd = { vm.end { gate.run { backStack.pop() } } },
                        // 会話に戻らずにファイルへ（画面ごと入れ替える。戻ると会話）
                        onSwitchToFiles = if (sessionKey != null && agentId != null) {
                            { gate.run { backStack.replaceTop(FilesRoute(sessionKey, agentId)) } }
                        } else null,
                    )
                }
            }
            entry<FilesRoute> { route ->
                val gate = rememberNavGate(onBack = { backStack.pop() })
                WithWorkStore(workStores, route.sessionKey, route.agentId) {
                    val files = remember { jp.miyavi.android.sumahoclaw.core.data.FilesRepository(container.gateway) }
                    val vm: jp.miyavi.android.sumahoclaw.feature.files.FilesViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                jp.miyavi.android.sumahoclaw.feature.files.FilesViewModel(
                                    files = files,
                                    downloader = container.downloads,
                                    drafts = container.database.fileDrafts(),
                                    sessionKey = route.sessionKey,
                                    agentId = route.agentId,
                                    // 会話には必ず Gateway の作業フォルダ（無ければエージェントのワークスペース）がある
                                    hasWorkingFolder = true,
                                    // ファイルの作成・削除・名前の変更は、使い捨てのシェルで（spec 005 R7）
                                    ops = jp.miyavi.android.sumahoclaw.core.data.FileOpsRepository(
                                        jp.miyavi.android.sumahoclaw.core.data.TerminalRepository(
                                            container.gateway,
                                            jp.miyavi.android.sumahoclaw.core.data.SharedPreferencesTerminalIdStore(
                                                context.getSharedPreferences("terminal_ids", android.content.Context.MODE_PRIVATE),
                                            ),
                                        ),
                                    ),
                                    // 会話ごとの入れ物には SavedStateRegistry が無いので、手元の SavedStateHandle（ViewModel と同じ寿命）
                                    // shortcut: 展開・開いていたファイルはプロセスの終了で消える。残すなら WorkStores に SavedStateHandle を持たせて会話ごとに渡す
                                    saved = androidx.lifecycle.SavedStateHandle(),
                                    settings = container.settings,
                                    memory = jp.miyavi.android.sumahoclaw.feature.files.FileViewMemory(
                                        context.getSharedPreferences("file_view", android.content.Context.MODE_PRIVATE),
                                        "${route.sessionKey}|${route.agentId}",
                                    ),
                                )
                            }
                        },
                    )
                    jp.miyavi.android.sumahoclaw.feature.files.FilesScreen(
                        viewModel = vm,
                        onBack = { gate.run { backStack.pop() } },
                    )
                }
            }
            entry<UsageRoute> { ComingSoon(R.string.usage_title) { backStack.pop() } }
            entry<AgentSettingsRoute> { route ->
                val gate = rememberNavGate(onBack = { backStack.pop() })
                val files = remember { jp.miyavi.android.sumahoclaw.core.data.FilesRepository(container.gateway) }
                val skills = remember { jp.miyavi.android.sumahoclaw.core.data.SkillsRepository(container.gateway) }
                val vm: jp.miyavi.android.sumahoclaw.feature.agent.AgentSettingsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            jp.miyavi.android.sumahoclaw.feature.agent.AgentSettingsViewModel(
                                agentId = route.agentId,
                                agents = container.sessions.agents.map { a -> a.agents to a.loading },
                                files = { files.agentFiles(it) },
                                skills = skills,
                                configs = jp.miyavi.android.sumahoclaw.core.data.AgentConfigRepository(container.gateway),
                                refreshAgents = { container.sessions.refreshAgents() },
                                supports = { method ->
                                    val methods = (container.gateway.state.value as? ConnectionState.Connected)?.methods.orEmpty()
                                    methods.isEmpty() || method in methods
                                },
                            )
                        }
                    },
                )
                jp.miyavi.android.sumahoclaw.feature.agent.AgentSettingsScreen(
                    viewModel = vm,
                    onBack = { gate.run { backStack.pop() } },
                    onOpenFile = { name -> gate.run { backStack.push(AgentFileRoute(route.agentId, name)) } },
                    onOpenWorkspace = { gate.run { backStack.push(WorkspaceFilesRoute(route.agentId)) } },
                )
            }
            entry<AgentFileRoute> { route ->
                val gate = rememberNavGate(onBack = { backStack.pop() })
                val files = remember { jp.miyavi.android.sumahoclaw.core.data.FilesRepository(container.gateway) }
                val vm: jp.miyavi.android.sumahoclaw.feature.agent.AgentFileViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            jp.miyavi.android.sumahoclaw.feature.agent.AgentFileViewModel(route.agentId, route.name, files, container.database.fileDrafts())
                        }
                    },
                )
                jp.miyavi.android.sumahoclaw.feature.agent.AgentFileScreen(vm) { gate.run { backStack.pop() } }
            }
            entry<WorkspaceFilesRoute> { route ->
                val gate = rememberNavGate(onBack = { backStack.pop() })
                val files = remember { jp.miyavi.android.sumahoclaw.core.data.FilesRepository(container.gateway) }
                val vm: jp.miyavi.android.sumahoclaw.feature.files.FilesViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            jp.miyavi.android.sumahoclaw.feature.files.FilesViewModel(
                                files = files,
                                downloader = container.downloads,
                                drafts = container.database.fileDrafts(),
                                sessionKey = "",
                                agentId = route.agentId,
                                hasWorkingFolder = false,
                                saved = createSavedStateHandle(),
                                settings = container.settings,
                                memory = jp.miyavi.android.sumahoclaw.feature.files.FileViewMemory(
                                    context.getSharedPreferences("file_view", android.content.Context.MODE_PRIVATE),
                                    "workspace|${route.agentId}",
                                ),
                            )
                        }
                    },
                )
                jp.miyavi.android.sumahoclaw.feature.files.FilesScreen(viewModel = vm, onBack = { gate.run { backStack.pop() } })
            }
        },
    )

    // 再起動の確認（FR-071）。結果は Snackbar で示す
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.restart_confirm_title)) },
            text = { Text(stringResource(R.string.restart_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    scope.launch {
                        val message = when (container.gatewayControl.restart()) {
                            RestartResult.RECONNECTED -> restartDone
                            RestartResult.TIMED_OUT -> restartTimeout
                            RestartResult.FAILED -> restartFailed
                            RestartResult.ALREADY_RUNNING -> null
                        }
                        message?.let { snackbar.showSnackbar(it) }
                    }
                }) { Text(stringResource(R.string.restart_confirm_action)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** 準備中の画面（後のストーリーで置き換える）。 */
@Composable
private fun ComingSoon(title: Int, onBack: () -> Unit) {
    val gate = rememberNavGate(onBack = onBack)
    val hazeState = rememberHazeState()
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.coming_soon),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = Spacing.l, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), { gate.run(onBack) })
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun NavBackStack<NavKey>.push(route: NavKey) {
    if (lastOrNull() != route) add(route)
}

/** 最初の画面（会話）は消さない。 */
/** いちばん上の画面を入れ替える（ファイル ⇄ ターミナル。戻ると下の画面）。 */
private fun NavBackStack<NavKey>.replaceTop(route: NavKey) {
    if (isEmpty()) add(route) else set(lastIndex, route)
}

private fun NavBackStack<NavKey>.pop() {
    if (size > 1) removeAt(lastIndex)
}

// 画面の遷移は標準のばね（弾むと端に下の画面がのぞくため。AGENTS.md 2.8）
private fun slideSpec(): FiniteAnimationSpec<IntOffset> = ScreenMotionScheme.defaultSpatialSpec()
private fun fadeSpec(): FiniteAnimationSpec<Float> = ScreenMotionScheme.defaultEffectsSpec()

/** 上の画面が右から重なるあいだ、下の画面はその場で暗くなる。 */
internal fun pushTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(animationSpec = slideSpec()) { it },
    initialContentExit = fadeOut(animationSpec = fadeSpec(), targetAlpha = PARALLAX_ALPHA),
    targetContentZIndex = 1f,
    sizeTransform = null,
)

/** 上のレイヤーを右へめくって、下のレイヤーを戻す。 */
internal fun popTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(animationSpec = slideSpec()) { -it / PARALLAX_DIVISOR } +
        fadeIn(animationSpec = fadeSpec(), initialAlpha = PARALLAX_ALPHA),
    initialContentExit = slideOutHorizontally(animationSpec = slideSpec()) { it },
    targetContentZIndex = -1f,
    sizeTransform = null,
)

private const val PARALLAX_DIVISOR = 4
private const val LAUNCH_PREFS = "launch"
/** この接続先に一度つながったか（接続情報を消すと消える）。 */
private const val KEY_CONNECTED_BEFORE = "connected_before"
private const val PARALLAX_ALPHA = 0.9f

/**
 * ファイルとターミナルの ViewModel を、画面ではなく会話ごとの入れ物に置く。画面を閉じても残り、
 * 同じ会話で開き直すと続きから。別の会話で開くと、前の会話の分は捨てる（ターミナルの接続も離す）。
 */
@Composable
private fun WithWorkStore(stores: WorkStores, sessionKey: String?, agentId: String?, content: @Composable () -> Unit) {
    val key = "${sessionKey.orEmpty()}|${agentId.orEmpty()}"
    val owner = remember(key) {
        val store = stores.storeFor(key)
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

/** 会話ごとの ViewModel の入れ物（いまの会話の 1 つだけを持つ）。 */
class WorkStores : ViewModel() {
    private var key: String? = null
    private var store = ViewModelStore()

    fun storeFor(key: String): ViewModelStore {
        if (key != this.key) {
            store.clear()
            store = ViewModelStore()
            this.key = key
        }
        return store
    }

    override fun onCleared() = store.clear()
}
