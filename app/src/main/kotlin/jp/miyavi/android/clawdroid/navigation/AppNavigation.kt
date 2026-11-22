package jp.miyavi.android.clawdroid.navigation

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.AppContainer
import jp.miyavi.android.clawdroid.BuildConfig
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.RestartResult
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.ScreenMotionScheme
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.settings.ThemeSettings
import jp.miyavi.android.clawdroid.feature.chat.ChatNavigation
import jp.miyavi.android.clawdroid.feature.chat.ChatScreen
import jp.miyavi.android.clawdroid.feature.chat.ChatViewModel
import jp.miyavi.android.clawdroid.feature.connect.ConnectScreen
import jp.miyavi.android.clawdroid.feature.connect.ConnectViewModel
import jp.miyavi.android.clawdroid.feature.settings.SettingsActions
import jp.miyavi.android.clawdroid.feature.settings.SettingsContent
import jp.miyavi.android.clawdroid.feature.settings.SettingsState
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object ChatRoute : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data object AutomationsRoute : NavKey
@Serializable data class TerminalRoute(val sessionKey: String? = null) : NavKey
@Serializable data class FilesRoute(val sessionKey: String, val agentId: String) : NavKey
@Serializable data object UsageRoute : NavKey

/**
 * 画面の構成。接続情報が無い・認証が切れたときは接続画面、それ以外は会話を起点にした Navigation 3 の画面の積み重ね。
 */
@Composable
fun AppNavigation(container: AppContainer) {
    val connection by container.gateway.state.collectAsStateWithLifecycle()
    // 一度でも接続できたら、切断や再接続のあいだも会話の画面を出し続ける（表示済みの内容を残す）
    var everConnected by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(container.gateway) {
        container.gateway.state.collect { state ->
            when (state) {
                is ConnectionState.Connected -> everConnected = true
                ConnectionState.NotConfigured -> everConnected = false
                else -> Unit
            }
        }
    }
    val needsConnect = connection is ConnectionState.NotConfigured ||
        (!everConnected && connection !is ConnectionState.Connected)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (needsConnect) {
            val vm: ConnectViewModel = viewModel(factory = viewModelFactory { initializer { ConnectViewModel(container.gateway, container.secrets) } })
            ConnectScreen(vm)
        } else {
            MainNavigation(container)
        }
    }
}

@Composable
private fun MainNavigation(container: AppContainer) {
    val backStack = rememberNavBackStack(ChatRoute)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    val restartDone = stringResource(R.string.restart_done)
    val restartTimeout = stringResource(R.string.restart_timeout)
    val restartFailed = stringResource(R.string.restart_failed)
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
                                settings = container.settings,
                                drafts = container.database.drafts(),
                                positions = container.database.readingPositions(),
                                contentResolver = context.contentResolver,
                                timeFormatter = { ms ->
                                    DateUtils.formatDateTime(context, ms, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL)
                                },
                            )
                        }
                    },
                )
                ChatScreen(
                    viewModel = vm,
                    timeLabel = timeLabel,
                    navigation = ChatNavigation(
                        openSettings = { gate.run { backStack.push(SettingsRoute) } },
                        openAutomations = { gate.run { backStack.push(AutomationsRoute) } },
                        openTerminal = { key -> gate.run { backStack.push(TerminalRoute(key)) } },
                        openFiles = { key, agentId -> gate.run { backStack.push(FilesRoute(key, agentId)) } },
                        restartGateway = { confirmRestart = true },
                    ),
                )
            }
            entry<SettingsRoute> {
                val gate = rememberNavGate(onBack = { backStack.pop() })
                val theme by container.settings.theme.collectAsStateWithLifecycle(ThemeSettings())
                val connection by container.gateway.state.collectAsStateWithLifecycle()
                var url by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) { url = container.secrets.loadConnection()?.url }
                SettingsContent(
                    state = SettingsState(theme, connection, url, BuildConfig.VERSION_NAME),
                    snackbar = snackbar,
                    actions = SettingsActions(
                        onBack = { gate.run { backStack.pop() } },
                        setAppearance = { scope.launch { container.settings.setAppearance(it) } },
                        setTheme = { scope.launch { container.settings.setTheme(it) } },
                        setCustomSeed = { scope.launch { container.settings.setCustomSeed(it) } },
                        setSurfaceStyle = { scope.launch { container.settings.setSurfaceStyle(it) } },
                        reconnect = container.gateway::reconnectNow,
                        disconnect = container.gateway::disconnect,
                        forget = { scope.launch { container.gateway.forget() } },
                        restart = { confirmRestart = true },
                    ),
                )
            }
            entry<AutomationsRoute> { ComingSoon(R.string.automations_title) { backStack.pop() } }
            entry<TerminalRoute> { ComingSoon(R.string.terminal_title) { backStack.pop() } }
            entry<FilesRoute> { ComingSoon(R.string.files_title) { backStack.pop() } }
            entry<UsageRoute> { ComingSoon(R.string.usage_title) { backStack.pop() } }
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
private const val PARALLAX_ALPHA = 0.9f

