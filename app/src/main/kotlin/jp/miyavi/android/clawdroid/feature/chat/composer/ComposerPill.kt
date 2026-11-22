package jp.miyavi.android.clawdroid.feature.chat.composer

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import jp.miyavi.android.clawdroid.core.designsystem.accentContainer
import jp.miyavi.android.clawdroid.core.data.PendingInput
import androidx.compose.runtime.key
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
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
import java.text.NumberFormat
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.shortModelName
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.FIELD_OPACITY
import jp.miyavi.android.clawdroid.core.designsystem.GlassPanel
import jp.miyavi.android.clawdroid.core.designsystem.GlassTint
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.bodyTextStyle
import jp.miyavi.android.clawdroid.core.designsystem.expandEnter
import jp.miyavi.android.clawdroid.core.designsystem.shrinkExit
import jp.miyavi.android.clawdroid.feature.chat.ComposerState
import jp.miyavi.android.clawdroid.feature.chat.message.rememberBase64Bitmap

/**
 * 画面下部に浮くガラスの入力欄（AGENTS.md 2.7）。上に入力、下の行に 添付 ｜ モデルのピル ｜ 送信。
 * モデルのピルはコンテキストの消費で色が変わり、押すとセッションのシート（設定と使用量）を開く。
 * 入力が空のあいだは送信の位置に音声入力（生成中は停止）。生成中でも入力があれば送れる（FR-022・029）。
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
    contextFraction: Float?,
    onOpenSession: () -> Unit,
    pending: List<PendingInput>,
    onCancelPending: (String) -> Unit,
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
        // 受け付け待ちの送信（steer）。入力欄の上に積み、会話に取り込まれたら消える。取り消しはゴミ箱
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(bottom = Spacing.xs)) {
            pending.forEach { input ->
                key(input.runId) {
                    AnimatedVisibility(visibleState = remember { MutableTransitionState(false) }.apply { targetState = true }, enter = expandEnter(), exit = shrinkExit()) {
                        PendingInputRow(hazeState, input, onCancel = { onCancelPending(input.runId) })
                    }
                }
            }
        }
        // 入力欄は白い面（色付けしない）
        GlassPanel(hazeState = hazeState, modifier = Modifier.fillMaxWidth(), fixedOpacity = FIELD_OPACITY, tint = GlassTint.FIELD) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = Dimens.composerMaxHeight)
                    .nestedScroll(ConsumeRemainingScroll)
                    .padding(start = Spacing.l, end = Spacing.l, top = Spacing.m, bottom = Spacing.xs),
                contentAlignment = Alignment.CenterStart,
            ) {
                val textStyle = bodyTextStyle().copy(color = LocalContentColor.current)
                if (state.text.isEmpty()) {
                    Text(stringResource(R.string.chat_input_placeholder), style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            val canSend = state.text.isNotBlank() || state.images.isNotEmpty()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                IconButton(onClick = onAttach, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_huge_attach), contentDescription = stringResource(R.string.chat_attach_cd))
                }
                ModelPill(model = model, contextFraction = contextFraction, onClick = onOpenSession)
                Spacer(Modifier.weight(1f))
                when {
                    // 実行中でも入力があれば送れる（steer で差し込む）。入力が空のときだけ停止を出す
                    running && !canSend -> FilledIconButton(onClick = onStop) {
                        Icon(painterResource(R.drawable.ic_huge_stop), contentDescription = stringResource(R.string.chat_stop_cd))
                    }
                    !canSend && voice.available -> {
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
                    else -> FilledIconButton(onClick = onSend, enabled = enabled && canSend && !state.sending && !state.updatingSettings) {
                        Icon(painterResource(R.drawable.ic_huge_send), contentDescription = stringResource(R.string.chat_send_cd))
                    }
                }
            }
        }
    }
}

/** 受け付け待ちの 1 行。ガラスの面に、待ちの目印・本文（1 行）・取り消し。 */
@Composable
private fun PendingInputRow(hazeState: HazeState, input: PendingInput, onCancel: () -> Unit) {
    GlassPanel(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Icon(
                painterResource(R.drawable.ic_huge_queue),
                contentDescription = stringResource(R.string.chat_pending_label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Spacing.l),
            )
            Text(
                input.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCancel) {
                Icon(painterResource(R.drawable.ic_huge_delete), contentDescription = stringResource(R.string.chat_pending_cancel_cd))
            }
        }
    }
}

/** 入力欄の中で余ったスクロールを外へ渡さない（長文のスクロールで会話が動かないように）。 */
private object ConsumeRemainingScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/** コンテキストの消費の段階。 */
internal enum class ContextLevel { LOW, MEDIUM, HIGH }

internal fun contextLevel(fraction: Float?): ContextLevel = when {
    fraction == null || fraction < CONTEXT_MEDIUM -> ContextLevel.LOW
    fraction < CONTEXT_HIGH -> ContextLevel.MEDIUM
    else -> ContextLevel.HIGH
}

/**
 * モデル名のピル。ふだんはテーマカラー（primaryContainer）、コンテキストの消費が進むと 中 = tertiaryContainer、高 = errorContainer。
 * 色の切り替えは弾ませない（effects のトークン）。
 */
@Composable
private fun ModelPill(model: String?, contextFraction: Float?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val accent = accentContainer()
    val motion = MaterialTheme.motionScheme
    val level = contextLevel(contextFraction)
    // 利用者のメッセージと同じくすみ色。コンテキストが残り少ない（80% 以上）ときだけ error のくすみ色にして知らせる
    val mix = if (colors.surface.luminance() < 0.5f) PILL_MIX_DARK else PILL_MIX_LIGHT
    val container by animateColorAsState(
        when (level) {
            ContextLevel.LOW, ContextLevel.MEDIUM -> accent.container
            ContextLevel.HIGH -> lerp(colors.error, colors.surface, mix)
        },
        motion.defaultEffectsSpec(),
        label = "modelPillContainer",
    )
    val content by animateColorAsState(
        if (level == ContextLevel.HIGH) colors.onSurface else accent.content,
        motion.defaultEffectsSpec(),
        label = "modelPillContent",
    )
    val name = model?.let(::shortModelName) ?: stringResource(R.string.chat_setting_default)
    val percent = contextFraction?.let { NumberFormat.getPercentInstance().format(it) }
    val cd = if (percent != null) stringResource(R.string.chat_model_pill_cd, name, percent) else name
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = modifier.heightIn(min = Dimens.compactTouchTarget).widthIn(max = Dimens.modelPillMaxWidth).semantics { contentDescription = cd },
    ) {
        Row(Modifier.padding(horizontal = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** モデルのピルで、基準の色を背景に溶かす割合。 */
private const val PILL_MIX_LIGHT = 0.72f
private const val PILL_MIX_DARK = 0.62f

/** コンテキストの消費の境目（中・高）。 */
private const val CONTEXT_MEDIUM = 0.5f
private const val CONTEXT_HIGH = 0.8f
