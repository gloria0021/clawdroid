package jp.miyavi.android.clawdroid.feature.connect

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.bodyContainerColor
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.FailureReason
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(viewModel: ConnectViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConnectContent(
        state = state,
        onUrlChange = viewModel::onUrlChange,
        onTokenChange = viewModel::onTokenChange,
        onToggleTokenVisible = viewModel::toggleTokenVisible,
        onSubmit = viewModel::submit,
        onEditConnection = viewModel::editConnection,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectContent(
    state: ConnectUiState,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onToggleTokenVisible: () -> Unit,
    onSubmit: () -> Unit,
    onEditConnection: () -> Unit,
) {
    val form = state.form
    val connection = state.connection
    val busy = connection == ConnectionState.Connecting
    val approval = connection as? ConnectionState.AwaitingApproval
    val focusManager = LocalFocusManager.current
    LaunchedEffect(approval != null) {
        if (approval != null) focusManager.clearFocus()
    }
    val colors = MaterialTheme.colorScheme
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceContainerHigh,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        errorContainerColor = colors.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
    )
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
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
                    .padding(horizontal = Spacing.l, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Box(
                    Modifier.size(Dimens.emptyStateIcon).background(colors.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_huge_launcher_glyph),
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(Spacing.xxl),
                    )
                }
                Text(
                    stringResource(R.string.connect_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                if (approval != null) {
                    ApprovalInstructions(approval.requestId)
                    TextButton(
                        onClick = onEditConnection,
                        modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                    ) {
                        Text(stringResource(R.string.connect_edit_connection))
                    }
                } else {
                    TextField(
                        value = form.url,
                        onValueChange = onUrlChange,
                        label = { Text(stringResource(R.string.connect_url_label)) },
                        placeholder = { Text(stringResource(R.string.connect_url_placeholder)) },
                        singleLine = true,
                        shape = CircleShape,
                        colors = fieldColors,
                        isError = form.urlError != null,
                        supportingText = form.urlError?.let { e -> { Text(stringResource(e.message())) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = form.token,
                        onValueChange = onTokenChange,
                        label = { Text(stringResource(R.string.connect_token_label)) },
                        singleLine = true,
                        shape = CircleShape,
                        colors = fieldColors,
                        isError = form.tokenError != null,
                        supportingText = form.tokenError?.let { e -> { Text(stringResource(e.message())) } },
                        visualTransformation = if (form.tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onDone = { if (!busy) onSubmit() }),
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
                    ConnectionFeedback(connection)
                    Button(
                        onClick = onSubmit,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
                    ) {
                        if (busy) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LoadingIndicator(Modifier.size(Spacing.xl))
                                Text(stringResource(R.string.connect_connecting))
                            }
                        } else {
                            Text(stringResource(R.string.connect_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalInstructions(requestId: String?) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(stringResource(R.string.connect_awaiting_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (requestId == null) R.string.connect_awaiting_control_ui else R.string.connect_awaiting_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (requestId != null) {
            val command = stringResource(R.string.connect_awaiting_command, requestId)
            Surface(color = bodyContainerColor(), shape = MaterialTheme.shapes.large) {
                Text(
                    command,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                )
            }
            OutlinedButton(
                onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, command))) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.connect_copy_command))
            }
        }
    }
}

/** 接続の結果。失敗の理由を区別して示す（FR-004）。 */
@Composable
private fun ConnectionFeedback(connection: ConnectionState) {
    when (connection) {
        is ConnectionState.Failed -> ErrorText(
            when (connection.reason) {
                FailureReason.AUTH_REJECTED -> stringResource(R.string.connect_error_auth)
                FailureReason.PAIRING_REJECTED -> stringResource(R.string.connect_error_pairing_rejected)
                FailureReason.UNREACHABLE -> stringResource(R.string.connect_error_unreachable)
                FailureReason.PROTOCOL_MISMATCH, FailureReason.OTHER ->
                    stringResource(R.string.connect_error_other, connection.message.orEmpty())
            },
        )
        is ConnectionState.Reconnecting -> ErrorText(stringResource(R.string.connect_error_unreachable))
        else -> Unit
    }
}

@Composable
private fun ErrorText(text: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
        )
    }
}

private fun FieldError.message(): Int = when (this) {
    FieldError.URL_INVALID -> R.string.connect_error_url_invalid
    FieldError.CLEARTEXT_PUBLIC -> R.string.connect_error_cleartext_public
    FieldError.TOKEN_EMPTY -> R.string.connect_error_token_empty
}
