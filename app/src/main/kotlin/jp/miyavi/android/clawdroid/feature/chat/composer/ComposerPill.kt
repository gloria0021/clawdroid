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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.GlassPill
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
    voice: VoiceInputState,
    onTextChange: (String) -> Unit,
    onAttach: () -> Unit,
    onRemoveImage: (android.net.Uri) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(Spacing.xl)
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
        GlassPill(
            hazeState = hazeState,
            shape = pillShape,
            contentPadding = PaddingValues(Spacing.xs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onAttach, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_huge_attach), contentDescription = stringResource(R.string.chat_attach_cd))
            }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.minTouchTarget, max = Dimens.composerMaxHeight)
                    .padding(horizontal = Spacing.xs, vertical = Spacing.m),
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
                BasicTextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    enabled = enabled,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val canSend = state.text.isNotBlank() || state.images.isNotEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        enabled = enabled && canSend && !state.sending,
                        colors = IconButtonDefaults.filledIconButtonColors(),
                    ) {
                        Icon(painterResource(R.drawable.ic_huge_send), contentDescription = stringResource(R.string.chat_send_cd))
                    }
                }
            }
        }
    }
}
