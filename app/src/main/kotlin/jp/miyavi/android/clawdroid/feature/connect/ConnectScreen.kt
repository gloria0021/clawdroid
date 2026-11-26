package jp.miyavi.android.clawdroid.feature.connect

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.miyavi.android.clawdroid.BuildConfig
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.bodyContainerColor
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.FailureReason
import jp.miyavi.android.clawdroid.feature.chat.sidebar.LauncherForegroundViewportScale
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(viewModel: ConnectViewModel, onContinue: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConnectContent(
        state = state,
        onUrlChange = viewModel::onUrlChange,
        onTokenChange = viewModel::onTokenChange,
        onToggleTokenVisible = viewModel::toggleTokenVisible,
        onSubmit = viewModel::submit,
        onEditConnection = viewModel::editConnection,
        onRetry = viewModel::retry,
        onContinue = onContinue,
    )
}

/**
 * 接続の段階（本家の Android アプリのオンボーディングに倣う。マスター指定 2026-11-24）。
 * 入力 → 接続中 → 承認待ち（コマンドと手順）→ 接続できた、と、つまずいたとき（理由ごとの次の一手）。
 */
private enum class ConnectPhase { FORM, CONNECTING, APPROVAL, FAILED, CONNECTED }

private fun ConnectionState.phase(): ConnectPhase = when (this) {
    ConnectionState.NotConfigured, ConnectionState.Disconnected -> ConnectPhase.FORM
    ConnectionState.Connecting, ConnectionState.Restarting, ConnectionState.Paused -> ConnectPhase.CONNECTING
    is ConnectionState.AwaitingApproval -> ConnectPhase.APPROVAL
    is ConnectionState.Reconnecting, is ConnectionState.Failed -> ConnectPhase.FAILED
    is ConnectionState.Connected -> ConnectPhase.CONNECTED
}

@Composable
fun ConnectContent(
    state: ConnectUiState,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onToggleTokenVisible: () -> Unit,
    onSubmit: () -> Unit,
    onEditConnection: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    val phase = state.connection.phase()
    // 状態の画面の「戻る」は入力に戻る（接続できたあとは戻らない）
    BackHandler(enabled = phase != ConnectPhase.FORM && phase != ConnectPhase.CONNECTED, onBack = onEditConnection)
    val motion = MaterialTheme.motionScheme
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "connectPhase",
        ) { shown ->
            when (shown) {
                ConnectPhase.FORM -> ConnectForm(state.form, onUrlChange, onTokenChange, onToggleTokenVisible, onSubmit)
                // 同じ段階のあいだは中身（再接続の回数など）だけ変わる。最新の状態で描く
                else -> StatusPage(shown, state, onEditConnection, onRetry, onContinue)
            }
        }
    }
}

// ---------------------------------------------------------------- 入力

@Composable
private fun ConnectForm(
    form: ConnectForm,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onToggleTokenVisible: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceContainerHigh,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        errorContainerColor = colors.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
    )
    PageColumn {
        Mascot()
        Headline(stringResource(R.string.connect_title))
        Body(stringResource(R.string.connect_subtitle))
        TextField(
            value = form.url,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.connect_url_label)) },
            placeholder = { Text(stringResource(R.string.connect_url_placeholder)) },
            singleLine = true,
            shape = CircleShape,
            colors = fieldColors,
            isError = form.urlError != null,
            supportingText = { Text(stringResource(form.urlError?.message() ?: R.string.connect_url_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = form.token,
            onValueChange = onTokenChange,
            label = { Text(stringResource(R.string.connect_token_label)) },
            placeholder = if (form.hasSavedToken) ({ Text(stringResource(R.string.connect_token_saved_placeholder)) }) else null,
            singleLine = true,
            shape = CircleShape,
            colors = fieldColors,
            isError = form.tokenError != null,
            supportingText = form.tokenError?.let { e -> { Text(stringResource(e.message())) } },
            visualTransformation = if (form.tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            trailingIcon = {
                IconButton(onClick = onToggleTokenVisible) {
                    Icon(
                        painterResource(R.drawable.ic_huge_key),
                        contentDescription = stringResource(if (form.tokenVisible) R.string.connect_hide_token_cd else R.string.connect_show_token_cd),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // トークンの調べ方（本家の資料: Gateway ホストで `openclaw gateway auth-token --show`）
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.connect_token_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            CommandBox(stringResource(R.string.connect_token_command))
        }
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) {
            Text(stringResource(R.string.connect_action))
        }
    }
}

// ---------------------------------------------------------------- 状態の画面

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusPage(
    phase: ConnectPhase,
    state: ConnectUiState,
    onEditConnection: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    val connection = state.connection
    val host = state.form.host ?: stringResource(R.string.connect_gateway_fallback)
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusManager.clearFocus() }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val failure = remember(connection, state.tailscale) { failureOf(connection, state.tailscale) }

    Box(Modifier.fillMaxSize()) {
        PageColumn(bottomSpace = true) {
            Mascot()
            when (phase) {
                ConnectPhase.CONNECTING -> {
                    Headline(stringResource(R.string.connect_status_connecting_title))
                    Body(stringResource(R.string.connect_status_connecting_body, host))
                    LoadingIndicator()
                }
                ConnectPhase.APPROVAL -> {
                    val requestId = (connection as? ConnectionState.AwaitingApproval)?.requestId
                    Headline(stringResource(R.string.connect_awaiting_title))
                    Body(stringResource(if (requestId == null) R.string.connect_awaiting_control_ui else R.string.connect_awaiting_body))
                    if (requestId != null) CommandBox(stringResource(R.string.connect_awaiting_command, requestId))
                    Steps(
                        listOf(
                            Step(stringResource(R.string.connect_step_received), StepStatus.DONE),
                            Step(stringResource(R.string.connect_step_waiting), StepStatus.CURRENT),
                            Step(stringResource(R.string.connect_step_auto), StepStatus.PENDING),
                        ),
                    )
                    if (requestId != null) Hint(stringResource(R.string.connect_awaiting_or_control_ui))
                }
                ConnectPhase.FAILED -> {
                    Headline(stringResource(R.string.connect_problem_title))
                    Body(failure.body(host))
                    failure.command?.let { CommandBox(stringResource(it)) }
                    (connection as? ConnectionState.Reconnecting)?.let {
                        Hint(stringResource(R.string.connect_problem_auto_retry, it.attempt))
                    }
                }
                ConnectPhase.CONNECTED -> {
                    Headline(stringResource(R.string.connect_paired_title))
                    Body(stringResource(R.string.connect_paired_body, host))
                }
                ConnectPhase.FORM -> Unit
            }
            // 補助の操作（文字のボタンを縦に）
            if (phase == ConnectPhase.APPROVAL || phase == ConnectPhase.FAILED) {
                TextButton(onClick = { showDetails = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                    Text(stringResource(R.string.connect_show_details))
                }
            }
            if (phase == ConnectPhase.FAILED && failure.tailscale) TailscaleButton()
            if (phase != ConnectPhase.CONNECTED && !(phase == ConnectPhase.FAILED && failure.primary == Primary.EDIT)) {
                TextButton(onClick = onEditConnection, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                    Text(stringResource(R.string.connect_edit_connection))
                }
            }
        }
        // 下に主な操作（本家と同じく、親指の届く位置に横いっぱい）
        val primary = when (phase) {
            ConnectPhase.APPROVAL -> Primary.RETRY
            ConnectPhase.FAILED -> failure.primary
            ConnectPhase.CONNECTED -> Primary.CONTINUE
            else -> null
        }
        if (primary != null) {
            Button(
                onClick = when (primary) {
                    Primary.RETRY -> onRetry
                    Primary.EDIT -> onEditConnection
                    Primary.CONTINUE -> onContinue
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .widthIn(max = Dimens.maxReadingWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.l, vertical = Spacing.l)
                    .heightIn(min = Dimens.minTouchTarget),
            ) {
                if (primary == Primary.RETRY) {
                    Icon(painterResource(R.drawable.ic_huge_refresh), contentDescription = null, modifier = Modifier.size(Dimens.smallIcon))
                    Text(stringResource(primary.label), modifier = Modifier.padding(start = Spacing.s))
                } else {
                    Text(stringResource(primary.label))
                }
            }
        }
    }
    if (showDetails) {
        DetailsDialog(diagnosticText(connection, state.form.host), onDismiss = { showDetails = false })
    }
}

private enum class Primary(val label: Int) {
    RETRY(R.string.connect_retry),
    EDIT(R.string.connect_edit_connection),
    CONTINUE(R.string.connect_continue),
}

/** つまずいた理由ごとの、説明・コマンド・主な操作。 */
private data class Failure(
    val kind: Kind,
    val message: String?,
    val tailscale: Boolean,
    val command: Int?,
    val primary: Primary,
) {
    enum class Kind { UNREACHABLE, AUTH, PAIRING_REJECTED, PROTOCOL, OTHER }

    @Composable
    fun body(host: String): String = when (kind) {
        Kind.UNREACHABLE -> stringResource(if (tailscale) R.string.connect_problem_tailscale else R.string.connect_problem_unreachable, host)
        Kind.AUTH -> stringResource(R.string.connect_problem_auth)
        Kind.PAIRING_REJECTED -> stringResource(R.string.connect_problem_pairing_rejected)
        Kind.PROTOCOL -> stringResource(R.string.connect_problem_protocol)
        Kind.OTHER -> message?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.connect_error_other, it) }
            ?: stringResource(R.string.connect_problem_other)
    }
}

private fun failureOf(connection: ConnectionState, tailscaleHost: Boolean): Failure {
    val (reason, message) = when (connection) {
        is ConnectionState.Failed -> connection.reason to connection.message
        is ConnectionState.Reconnecting -> connection.reason to null
        else -> FailureReason.OTHER to null
    }
    return when (reason) {
        FailureReason.UNREACHABLE -> Failure(Failure.Kind.UNREACHABLE, message, tailscaleHost, null, Primary.RETRY)
        FailureReason.AUTH_REJECTED -> Failure(Failure.Kind.AUTH, message, false, R.string.connect_token_command, Primary.EDIT)
        FailureReason.PAIRING_REJECTED -> Failure(Failure.Kind.PAIRING_REJECTED, message, false, null, Primary.RETRY)
        FailureReason.PROTOCOL_MISMATCH -> Failure(Failure.Kind.PROTOCOL, message, false, R.string.connect_update_command, Primary.RETRY)
        FailureReason.OTHER -> Failure(Failure.Kind.OTHER, message, false, null, Primary.RETRY)
    }
}

/** 「詳細を表示」の中身。見出しは訳し、値は Gateway の状態をそのまま（貼って調べられるように）。トークンは含めない。 */
@Composable
private fun diagnosticText(connection: ConnectionState, host: String?): String {
    val na = "n/a"
    val failed = connection as? ConnectionState.Failed
    val lines = listOf(
        stringResource(R.string.connect_diag_header),
        stringResource(R.string.connect_diag_app, BuildConfig.VERSION_NAME),
        stringResource(R.string.connect_diag_gateway, host ?: na),
        stringResource(R.string.connect_diag_state, connection::class.simpleName ?: na),
        stringResource(R.string.connect_diag_reason, failed?.reason?.name ?: (connection as? ConnectionState.Reconnecting)?.reason?.name ?: na),
        stringResource(R.string.connect_diag_code, failed?.code ?: na),
        stringResource(R.string.connect_diag_message, failed?.message ?: na),
        stringResource(R.string.connect_diag_request, (connection as? ConnectionState.AwaitingApproval)?.requestId ?: na),
    )
    return lines.joinToString("\n")
}

@Composable
private fun DetailsDialog(text: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connect_details_title)) },
        text = {
            SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text))) } }) {
                Text(stringResource(R.string.connect_copy))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.connect_close)) } },
    )
}

/** Tailscale を開く（入っていなければ Play ストアの入手ページ）。 */
@Composable
private fun TailscaleButton() {
    val context = LocalContext.current
    val installed = remember { context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE) != null }
    TextButton(onClick = { openTailscale(context) }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
        Text(stringResource(if (installed) R.string.connect_open_tailscale else R.string.connect_install_tailscale))
    }
}

private fun openTailscale(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
    val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$TAILSCALE_PACKAGE"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$TAILSCALE_PACKAGE"))
    listOfNotNull(launch, store, web).firstNotNullOfOrNull { intent ->
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.getOrNull()
    }
}

private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

// ---------------------------------------------------------------- 部品

/** 段階の画面の骨格。中央寄せの縦の並び。下に主な操作の分の余白を空ける。 */
@Composable
private fun PageColumn(bottomSpace: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = Dimens.maxReadingWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.l, end = Spacing.l, top = Spacing.xl, bottom = if (bottomSpace) Dimens.minTouchTarget + Spacing.xxl else Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            content = content,
        )
    }
}

/** アプリのマスコット（丸い面の上）。本家の公式キャラクターは使わない。 */
@Composable
private fun Mascot() {
    Box(
        Modifier.size(Dimens.onboardingMascot).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.launcher_mascot_foreground),
            contentDescription = null,
            modifier = Modifier.size(Dimens.onboardingMascot).graphicsLayer {
                scaleX = LauncherForegroundViewportScale
                scaleY = LauncherForegroundViewportScale
            },
        )
    }
}

@Composable
private fun Headline(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

/** コマンドの面（等幅）。右にコピーのボタン。 */
@Composable
private fun CommandBox(command: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Surface(color = bodyContainerColor(), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = Spacing.m, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(command, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            }
            IconButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, command))) } }) {
                Icon(painterResource(R.drawable.ic_huge_copy), contentDescription = stringResource(R.string.connect_copy_command))
            }
        }
    }
}

private enum class StepStatus { DONE, CURRENT, PENDING }

private data class Step(val label: String, val status: StepStatus)

/** 進み具合の手順（済み = チェック、いま = 塗った点、これから = 薄い点）。 */
@Composable
private fun Steps(steps: List<Step>) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        steps.forEach { step ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Box(Modifier.size(Dimens.smallIcon), contentAlignment = Alignment.Center) {
                    when (step.status) {
                        StepStatus.DONE -> Icon(painterResource(R.drawable.ic_huge_check), contentDescription = null, tint = colors.primary)
                        StepStatus.CURRENT -> Box(Modifier.size(Dimens.unreadDot).background(colors.primary, CircleShape))
                        StepStatus.PENDING -> Box(Modifier.size(Dimens.unreadDot).background(colors.outlineVariant, CircleShape))
                    }
                }
                Text(
                    step.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (step.status == StepStatus.PENDING) colors.onSurfaceVariant else colors.onSurface,
                )
            }
        }
    }
}

private fun FieldError.message(): Int = when (this) {
    FieldError.URL_INVALID -> R.string.connect_error_url_invalid
    FieldError.CLEARTEXT_PUBLIC -> R.string.connect_error_cleartext_public
    FieldError.TOKEN_EMPTY -> R.string.connect_error_token_empty
}
