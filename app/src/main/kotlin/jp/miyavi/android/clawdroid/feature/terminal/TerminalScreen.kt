package jp.miyavi.android.clawdroid.feature.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.TerminalDenial
import jp.miyavi.android.clawdroid.core.designsystem.AppTitleBar
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.GlassPanel
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.codeTextStyle
import jp.miyavi.android.clawdroid.core.designsystem.expandEnter
import jp.miyavi.android.clawdroid.core.designsystem.shrinkExit

/**
 * ターミナル（spec 002 US4）。Termux の TerminalView を AndroidView で埋め込み（001 research R9 で承認）、
 * 下に特殊キーの行。端末の中身（ANSI の色）は「内容」として扱い、既定の背景と文字色だけテーマに合わせる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: TerminalViewModel, cwd: String?, onBack: () -> Unit, onEnd: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val ctrlArmed by viewModel.ctrlArmed.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()
    val colors = MaterialTheme.colorScheme
    val textSizePx = with(LocalDensity.current) { codeTextStyle().fontSize.toPx() }.toInt()

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            AppTitleBar(
                hazeState = hazeState,
                title = {
                    Column {
                        Text(stringResource(R.string.terminal_title), style = MaterialTheme.typography.titleMedium)
                        cwd?.let { Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                },
                navigationIcon = {
                    FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), onBack,
                        modifier = Modifier.padding(start = Spacing.s))
                },
                actions = {
                    // 終える（PTY ごと閉じる）。戻るだけなら PTY は残り、また開くと続きから
                    if (status == TerminalStatus.Connected) {
                        IconButton(onClick = onEnd) { Icon(painterResource(R.drawable.ic_huge_stop), stringResource(R.string.terminal_end_cd)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding().hazeSource(hazeState)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { context -> createTerminalView(context, viewModel, textSizePx) },
                    update = { view ->
                        // テーマ（ライト / ダーク）に合わせて、既定の背景・文字色・カーソルを当てる
                        val palette = viewModel.bridge.emulator.mColors.mCurrentColors
                        palette[TextStyle.COLOR_INDEX_BACKGROUND] = colors.surface.toArgb()
                        palette[TextStyle.COLOR_INDEX_FOREGROUND] = colors.onSurface.toArgb()
                        palette[TextStyle.COLOR_INDEX_CURSOR] = colors.primary.toArgb()
                        view.setBackgroundColor(colors.surface.toArgb())
                        view.onScreenUpdated()
                    },
                    modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.s),
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = status != TerminalStatus.Connected,
                    enter = expandEnter(),
                    exit = shrinkExit(),
                    modifier = Modifier.align(Alignment.TopCenter).padding(Spacing.l),
                ) {
                    StatusPanel(hazeState, status, onReopen = viewModel::reopen, onTakeBack = viewModel::takeBack)
                }
            }
            SpecialKeysRow(
                hazeState = hazeState,
                ctrlArmed = ctrlArmed,
                onKey = viewModel::sendKey,
                onToggleCtrl = viewModel::toggleCtrl,
                modifier = Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding().padding(Spacing.s),
            )
        }
    }
    // 画面が消えたら、描き直しの知らせを外す（ViewModel は回転を越えて残る）
    DisposableEffect(viewModel) { onDispose { viewModel.onScreenChanged = null; viewModel.onCopy = null; viewModel.onPasteRequest = null } }
}

/** 状態の表示（開いている途中・終了・消えた・ほかで開かれた・使えない）と、開き直す・取り戻す操作（FR-025・026）。 */
@Composable
private fun StatusPanel(hazeState: dev.chrisbanes.haze.HazeState, status: TerminalStatus, onReopen: () -> Unit, onTakeBack: () -> Unit) {
    GlassPanel(hazeState = hazeState) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val message = when (status) {
                TerminalStatus.Opening -> R.string.terminal_opening
                TerminalStatus.Reattaching -> R.string.terminal_reattaching
                is TerminalStatus.Exited -> R.string.terminal_exited
                TerminalStatus.Lost -> R.string.terminal_lost
                TerminalStatus.TakenElsewhere -> R.string.terminal_taken
                is TerminalStatus.Denied -> when (status.reason) {
                    TerminalDenial.NOT_CONNECTED -> R.string.terminal_denied_offline
                    TerminalDenial.NO_PERMISSION -> R.string.terminal_denied_permission
                    TerminalDenial.DISABLED -> R.string.terminal_denied_disabled
                    TerminalDenial.FAILED -> R.string.terminal_denied_failed
                }
                TerminalStatus.Connected -> R.string.terminal_opening
            }
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
            when (status) {
                is TerminalStatus.Exited, TerminalStatus.Lost -> TextButton(onClick = onReopen) { Text(stringResource(R.string.terminal_reopen)) }
                TerminalStatus.TakenElsewhere -> TextButton(onClick = onTakeBack) { Text(stringResource(R.string.terminal_take_back)) }
                is TerminalStatus.Denied -> if (status.reason == TerminalDenial.FAILED || status.reason == TerminalDenial.NOT_CONNECTED) {
                    TextButton(onClick = onReopen) { Text(stringResource(R.string.action_retry)) }
                }
                else -> Unit
            }
        }
    }
}

private fun createTerminalView(context: Context, viewModel: TerminalViewModel, textSizePx: Int): TerminalView {
    val view = TerminalView(context, null)
    view.setTerminalViewClient(object : TerminalViewClient {
        override fun onScale(scale: Float): Float = 1f
        override fun onSingleTapUp(e: MotionEvent?) {
            view.requestFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, 0)
        }
        override fun shouldBackButtonBeMappedToEscape(): Boolean = false
        override fun shouldEnforceCharBasedInput(): Boolean = true
        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
        override fun isTerminalViewSelected(): Boolean = true
        override fun copyModeChanged(copyMode: Boolean) = Unit
        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false
        override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
        override fun onLongPress(event: MotionEvent?): Boolean = false
        override fun readControlKey(): Boolean = viewModel.consumeCtrl()
        override fun readAltKey(): Boolean = false
        override fun readShiftKey(): Boolean = false
        override fun readFnKey(): Boolean = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
        override fun onEmulatorSet() {
            val emulator = view.mEmulator ?: return
            viewModel.onSize(emulator.mColumns, emulator.mRows)
        }
        override fun logError(tag: String?, message: String?) = Unit
        override fun logWarn(tag: String?, message: String?) = Unit
        override fun logInfo(tag: String?, message: String?) = Unit
        override fun logDebug(tag: String?, message: String?) = Unit
        override fun logVerbose(tag: String?, message: String?) = Unit
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
        override fun logStackTrace(tag: String?, e: Exception?) = Unit
    })
    view.setTextSize(textSizePx)
    view.setTypeface(Typeface.MONOSPACE)
    view.isFocusable = true
    view.isFocusableInTouchMode = true
    view.attachSession(viewModel.bridge.session)
    viewModel.onScreenChanged = { view.post { view.onScreenUpdated() } }
    viewModel.onCopy = { text ->
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("terminal", text))
    }
    viewModel.onPasteRequest = {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    }
    view.requestFocus()
    return view
}
