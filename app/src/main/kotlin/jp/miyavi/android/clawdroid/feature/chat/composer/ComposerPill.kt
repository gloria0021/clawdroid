package jp.miyavi.android.clawdroid.feature.chat.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.SessionSetting
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.GlassPanel
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.bodyTextStyle
import jp.miyavi.android.clawdroid.core.designsystem.expandEnter
import jp.miyavi.android.clawdroid.core.designsystem.shrinkExit
import jp.miyavi.android.clawdroid.feature.chat.ComposerState
import jp.miyavi.android.clawdroid.feature.chat.message.rememberBase64Bitmap

/**
 * 画面下部に浮くガラスのピル型の入力欄（AGENTS.md 2.7）。左に添付、中央に入力、右にマイク／送信。
 * 生成中は送信の位置が停止に変わる（FR-022）。
 */
@Composable
fun ComposerPill(
    hazeState: HazeState,
    state: ComposerState,
    running: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
    voice: VoiceInputState,
    model: String?,
    effort: String?,
    permission: String?,
    canEditModel: Boolean,
    canEditEffort: Boolean,
    canEditPermissions: Boolean,
    onOpenSetting: (SessionSetting) -> Unit,
    contextFraction: Float?,
    onShowUsage: () -> Unit,
    onTextChange: (String) -> Unit,
    onAttach: () -> Unit,
    onRemoveImage: (android.net.Uri) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        AnimatedVisibility(visible = state.images.isNotEmpty(), enter = expandEnter(), exit = shrinkExit()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.xs),
            ) {
                items(state.images, key = { it.uri.toString() }) { pending ->
                    Box {
                        rememberBase64Bitmap(pending.image.base64)?.let { bitmap ->
                            Image(
                                bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(Dimens.minTouchTarget * 1.5f).clip(MaterialTheme.shapes.medium),
                            )
                        }
                        IconButton(onClick = { onRemoveImage(pending.uri) }, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(
                                painterResource(R.drawable.ic_huge_close),
                                contentDescription = stringResource(R.string.chat_remove_attachment_cd),
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape),
                            )
                        }
                    }
                }
            }
        }
        GlassPanel(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget, max = Dimens.composerMaxHeight)
                    .padding(horizontal = Spacing.l, vertical = Spacing.m),
                contentAlignment = Alignment.CenterStart,
            ) {
                val textStyle = bodyTextStyle().copy(color = LocalContentColor.current)
                if (state.text.isEmpty()) {
                    Text(
                        stringResource(R.string.chat_input_placeholder),
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                var editingValue by remember { mutableStateOf(TextFieldValue(state.text)) }
                val fieldValue = if (editingValue.text == state.text) editingValue else {
                    TextFieldValue(state.text, selection = TextRange(state.text.length))
                }
                BasicTextField(
                    value = fieldValue,
                    onValueChange = {
                        editingValue = it
                        if (it.text != state.text) onTextChange(it.text)
                    },
                    enabled = enabled,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val usageLabel = stringResource(R.string.usage_title)
                IconButton(onClick = onShowUsage, modifier = Modifier.semantics { contentDescription = usageLabel }) {
                    if (contextFraction != null) {
                        CircularProgressIndicator(
                            progress = { contextFraction },
                            modifier = Modifier.size(Spacing.l),
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                            strokeWidth = Dimens.usageRingStroke,
                        )
                    } else {
                        Icon(painterResource(R.drawable.ic_huge_usage), null)
                    }
                }
                val modelText = model?.substringAfterLast('/') ?: stringResource(R.string.chat_setting_model)
                if (canEditModel) {
                    TextButton(
                        onClick = { onOpenSetting(SessionSetting.MODEL) },
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget),
                        contentPadding = PaddingValues(horizontal = Spacing.xs),
                    ) {
                        Text(modelText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Icon(painterResource(R.drawable.ic_huge_arrow_down), null, Modifier.size(Spacing.l))
                    }
                } else {
                    Text(modelText, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                if (canEditEffort) {
                    TextButton(
                        onClick = { onOpenSetting(SessionSetting.EFFORT) },
                        modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                        contentPadding = PaddingValues(horizontal = Spacing.xs),
                    ) {
                        Text(effort ?: stringResource(R.string.chat_setting_effort), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(painterResource(R.drawable.ic_huge_arrow_down), null, Modifier.size(Spacing.l))
                    }
                }
            }
            val canSend = state.text.isNotBlank() || state.images.isNotEmpty()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onAttach, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_huge_attach), contentDescription = stringResource(R.string.chat_attach_cd))
                }
                val permissionChoice = PermissionChoice.entries.firstOrNull { it.value == permission } ?: PermissionChoice.Default
                IconButton(
                    onClick = { onOpenSetting(SessionSetting.PERMISSIONS) },
                    enabled = canEditPermissions,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_huge_key),
                        contentDescription = stringResource(
                            R.string.chat_permission_button,
                            stringResource(permissionChoice.label),
                        ),
                        tint = if (canEditPermissions && permissionChoice == PermissionChoice.Full) {
                            MaterialTheme.colorScheme.error
                        } else LocalContentColor.current,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (voice.available && !canSend && !running) {
                    val micColor by animateColorAsState(
                        if (voice.listening) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "micColor",
                    )
                    IconButton(onClick = voice::toggle, enabled = enabled) {
                        Icon(
                            painterResource(R.drawable.ic_huge_mic),
                            contentDescription = stringResource(if (voice.listening) R.string.chat_mic_listening_cd else R.string.chat_mic_cd),
                            tint = micColor,
                        )
                    }
                }
                when {
                    running -> FilledIconButton(onClick = onStop) {
                        Icon(painterResource(R.drawable.ic_huge_stop), contentDescription = stringResource(R.string.chat_stop_cd))
                    }
                    else -> FilledIconButton(
                        onClick = onSend,
                        enabled = enabled && canSend && !state.sending && !state.updatingSettings,
                        colors = IconButtonDefaults.filledIconButtonColors(),
                    ) {
                        Icon(painterResource(R.drawable.ic_huge_send), contentDescription = stringResource(R.string.chat_send_cd))
                    }
                }
            }
        }
    }
}
