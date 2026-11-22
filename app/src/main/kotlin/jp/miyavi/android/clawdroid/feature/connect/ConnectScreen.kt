package jp.miyavi.android.clawdroid.feature.connect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.bodyContainerColor
import jp.miyavi.android.clawdroid.core.designsystem.expandEnter
import jp.miyavi.android.clawdroid.core.designsystem.shrinkExit
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.FailureReason

@Composable
fun ConnectScreen(viewModel: ConnectViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConnectContent(
        state = state,
        onUrlChange = viewModel::onUrlChange,
        onTokenChange = viewModel::onTokenChange,
        onToggleTokenVisible = viewModel::toggleTokenVisible,
        onSubmit = viewModel::submit,
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
) {
    val form = state.form
    val connection = state.connection
    val busy = form.submitted && (connection is ConnectionState.Connecting || connection is ConnectionState.Reconnecting)
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = Dimens.maxReadingWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.l, vertical = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Icon(
                    painterResource(R.drawable.ic_huge_launcher_glyph),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.emptyStateIcon),
                )
                Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.connect_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = form.url,
                    onValueChange = onUrlChange,
                    label = { Text(stringResource(R.string.connect_url_label)) },
                    placeholder = { Text(stringResource(R.string.connect_url_placeholder)) },
                    singleLine = true,
                    isError = form.urlError != null,
                    supportingText = form.urlError?.let { e -> { Text(stringResource(e.message())) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.token,
                    onValueChange = onTokenChange,
                    label = { Text(stringResource(R.string.connect_token_label)) },
                    singleLine = true,
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

                AnimatedVisibility(visible = form.submitted, enter = expandEnter(), exit = shrinkExit()) {
                    ConnectionFeedback(connection)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        LoadingIndicator(Modifier.size(Dimens.minTouchTarget))
                        Spacer(Modifier.size(Spacing.s))
                    }
                    Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                        Text(stringResource(R.string.connect_action))
                    }
                }
            }
        }
    }
}

/** 接続の結果。失敗の理由を区別して示す（FR-004）。 */
@Composable
private fun ConnectionFeedback(connection: ConnectionState) {
    val shape = MaterialTheme.shapes.large
    when (connection) {
        is ConnectionState.AwaitingApproval -> Surface(color = bodyContainerColor(), shape = shape) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.connect_awaiting_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.connect_awaiting_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    Text(
                        stringResource(R.string.connect_awaiting_command, connection.requestId ?: connection.deviceId.take(12)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
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
        ConnectionState.Connecting -> Text(
            stringResource(R.string.connect_connecting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
