package jp.miyavi.android.clawdroid.feature.chat.composer

import jp.miyavi.android.clawdroid.core.designsystem.GlassDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.exp
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import jp.miyavi.android.clawdroid.core.designsystem.MENU_OPACITY
import jp.miyavi.android.clawdroid.core.data.SlashCommand
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.foundation.layout.offset
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ComposerPill(
    hazeState: HazeState,
    state: ComposerState,
    slashCommands: List<SlashCommand> = emptyList(),
    hasQuestion: Boolean = false,
    onOpenQuestion: () -> Unit = {},
    running: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
    voice: VoiceInputState,
    model: String?,
    contextFraction: Float?,
    onOpenSession: () -> Unit,
    pending: List<PendingInput>,
    onCancelPending: (String) -> Unit,
    onRestorePending: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onPickPhotos: () -> Unit,
    onPickFiles: () -> Unit,
    onRemoveImage: (android.net.Uri) -> Unit,
    /** 貼り付けた画像（クリップボード・キーボード）。添付に足す。 */
    onPasteImage: (android.net.Uri) -> Unit = {},
    /** 添付した画像を押したとき（全画面のプレビュー）。並びと押した位置。 */
    onOpenImage: (List<jp.miyavi.android.clawdroid.feature.chat.PendingImage>, Int) -> Unit = { _, _ -> },
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // あとで答えることにしたエージェントからの質問。押すとシートを開き直す
        AnimatedVisibility(visible = hasQuestion, enter = expandEnter(), exit = shrinkExit()) {
            QuestionRow(hazeState, onOpenQuestion, Modifier.padding(bottom = Spacing.xs))
        }
        // 受け付け待ちの送信（steer）。入力欄の上に積み、会話に取り込まれたら消える。取り消しはゴミ箱
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(bottom = Spacing.xs)) {
            pending.forEach { input ->
                key(input.runId) {
                    AnimatedVisibility(visibleState = remember { MutableTransitionState(false) }.apply { targetState = true }, enter = expandEnter(), exit = shrinkExit()) {
                        PendingInputRow(hazeState, input, enabled, onRestore = { onRestorePending(input.runId) }, onCancel = { onCancelPending(input.runId) })
                    }
                }
            }
        }
        // 入力中の文字とカーソル。画像の貼り付け（クリップボード・キーボード）を受けるため、状態型の入力欄にする
        // （マスター指定 2026-11-24: Gemini のように貼り付けたい）。外から文字が変わったとき（送信で空に・音声・下書きの復元）は末尾にカーソル
        val fieldState = rememberTextFieldState(state.text)
        val sync = remember { ComposerTextSync() }
        LaunchedEffect(state.text) {
            if (sync.isEcho(state.text)) return@LaunchedEffect
            if (fieldState.text.toString() != state.text) fieldState.setTextAndPlaceCursorAtEnd(state.text)
        }
        val currentOnTextChange by rememberUpdatedState(onTextChange)
        LaunchedEffect(fieldState) {
            snapshotFlow { fieldState.text.toString() }.collect { text ->
                if (text != state.text) {
                    sync.sent(text)
                    currentOnTextChange(text)
                }
            }
        }
        // 補完。「/」で始めるとコマンド、「$」でスキルの候補を入力欄の上に出す
        val fieldText = fieldState.text.toString()
        val cursor = fieldState.selection.end
        val suggestions = remember(fieldText, cursor, slashCommands) { slashSuggestions(fieldText, slashCommands, cursor) }
        SlashSuggestionPanel(hazeState, suggestions, onPick = { suggestion ->
            val (text, at) = suggestion.applyTo(fieldText)
            fieldState.edit {
                replace(0, length, text)
                selection = TextRange(at)
            }
        })
        // 入力欄は白い面（色付けしない）
        GlassPanel(
            hazeState = hazeState,
            // 影で少し浮かせて立体に（マスター指定。AGENTS.md 2.5 の例外）
            modifier = Modifier.fillMaxWidth().shadow(Dimens.composerElevation, MaterialTheme.shapes.extraLarge),
            fixedOpacity = FIELD_OPACITY,
            tint = GlassTint.FIELD,
        ) {
            // 添付は入力欄の中、文字の上に並べる（マスター指定 2026-11-24。外に浮かせると背景と同化する）。
            // 押すと全画面のプレビュー、右上の × で外す
            AnimatedVisibility(visible = state.images.isNotEmpty(), enter = expandEnter(), exit = shrinkExit()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.m, bottom = Spacing.xs),
                ) {
                    items(state.images, key = { it.uri.toString() }) { pending ->
                        val index = state.images.indexOf(pending)
                        AttachmentThumbnail(
                            pending = pending,
                            onOpen = { onOpenImage(state.images, index) },
                            onRemove = { onRemoveImage(pending.uri) },
                        )
                    }
                }
            }
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
                    // 聞き取り中は、途中の結果（まだ無ければ「聞き取り中…」）を入力欄の位置に薄く出す
                    val hint = when {
                        !voice.listening -> stringResource(R.string.chat_input_placeholder)
                        voice.partial.isNotBlank() -> voice.partial
                        else -> stringResource(R.string.chat_voice_listening)
                    }
                    Text(hint, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    state = fieldState,
                    enabled = enabled,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        // 画像の貼り付け: 貼り付けのメニューと、キーボードのクリップボードの候補（commitContent）。文字はそのまま入力欄へ
                        .contentReceiver { content ->
                            if (!content.hasMediaType(MediaType.Image)) return@contentReceiver content
                            content.consume { item ->
                                val uri = item.uri
                                if (uri != null && enabled) { onPasteImage(uri); true } else false
                            }
                        },
                )
            }
            val canSend = state.text.isNotBlank() || state.images.isNotEmpty()
            // 送信ボタンが角に寄りすぎないよう、右と下にも余白を取る（マスター指定 2026-11-23）
            val rowModifier = Modifier.fillMaxWidth().padding(start = Spacing.xs, end = Spacing.s, bottom = Spacing.s)
            // 聞き取り中は、下の行が「やめる・声の波形・終える・終えて送信」に変わる。行どうしはフェードで入れ替える
            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = voice.listening,
                transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "composerVoiceRow",
            ) { listening ->
                if (listening) {
                    VoiceRow(voice = voice, enabled = enabled, onSend = onSend, modifier = rowModifier)
                } else {
                    Row(
                        rowModifier,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        // 添付は 写真 / ファイル を選ぶメニュー（マスター指定 2026-11-24）
                        Box {
                            var attachMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { attachMenu = true }, enabled = enabled) {
                                Icon(painterResource(R.drawable.ic_huge_attach), contentDescription = stringResource(R.string.chat_attach_cd))
                            }
                            GlassDropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_attach_photos)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_huge_image), contentDescription = null) },
                                    onClick = { attachMenu = false; onPickPhotos() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_attach_files)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_huge_file), contentDescription = null) },
                                    onClick = { attachMenu = false; onPickFiles() },
                                )
                            }
                        }
                        ModelPill(model = model, contextFraction = contextFraction, onClick = onOpenSession)
                        Spacer(Modifier.weight(1f))
                        // 音声入力は送信ボタンの左にいつも置く（入力の途中から言い足せる）
                        if (voice.available) {
                            IconButton(onClick = voice::toggle, enabled = enabled) {
                                Icon(painterResource(R.drawable.ic_huge_mic), contentDescription = stringResource(R.string.chat_mic_cd))
                            }
                        }
                        if (running && !canSend) {
                            // 実行中でも入力があれば送れる（steer で差し込む）。入力が空のときだけ停止を出す
                            FilledIconButton(onClick = onStop) {
                                Icon(painterResource(R.drawable.ic_huge_stop), contentDescription = stringResource(R.string.chat_stop_cd))
                            }
                        } else {
                            FilledIconButton(onClick = onSend, enabled = enabled && canSend && !state.sending && !state.updatingSettings) {
                                Icon(painterResource(R.drawable.ic_huge_send), contentDescription = stringResource(R.string.chat_send_cd))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 聞き取り中の行。左にやめる、まん中に声の大きさの波形、右に終える（入力欄に入れる）と終えて送信。 */
@Composable
private fun VoiceRow(voice: VoiceInputState, enabled: Boolean, onSend: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        IconButton(onClick = voice::cancel) {
            Icon(painterResource(R.drawable.ic_huge_close), contentDescription = stringResource(R.string.chat_voice_cancel_cd))
        }
        VoiceWave(voice.levels, Modifier.weight(1f).height(Dimens.voiceWaveHeight))
        FilledTonalIconButton(onClick = voice::finish) {
            Icon(painterResource(R.drawable.ic_huge_stop), contentDescription = stringResource(R.string.chat_voice_done_cd))
        }
        FilledIconButton(onClick = { voice.finishAndThen(onSend) }, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_huge_send), contentDescription = stringResource(R.string.chat_voice_send_cd))
        }
    }
}

/**
 * 声の大きさの波形。右端がいまの声で、一定の間隔で左へ流れる。認識サービスの値は間隔がばらつくので、
 * 毎フレームなめらかに追わせ、決まった間隔で履歴に積み、そのあいだはなめらかにずらす（カクつかせない）。
 * 黙っていれば点の列のまま。聞き取り中だけ動かし、読むのは描画の中だけ（再構成は起こさない。AGENTS.md 2.8）。
 */
@Composable
private fun VoiceWave(levels: VoiceLevels, modifier: Modifier = Modifier) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val loud = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current
    val bar = with(density) { Dimens.voiceWaveBar.toPx() }
    val pitch = bar + with(density) { Dimens.voiceWaveGap.toPx() }
    val wave = remember { WaveHistory() }
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(levels) {
        var last = 0L
        var lastSample = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) {
                    last = now
                    lastSample = now
                }
                val dt = (now - last) / 1_000_000_000f
                last = now
                wave.current += (levels.target - wave.current) * (1f - exp(-dt * VOICE_WAVE_SMOOTHING))
                while (now - lastSample >= VOICE_WAVE_SAMPLE_NS) {
                    wave.push(wave.current)
                    lastSample += VOICE_WAVE_SAMPLE_NS
                }
                wave.phase = (now - lastSample).toFloat() / VOICE_WAVE_SAMPLE_NS
                frame.longValue = now
            }
        }
    }
    Spacer(
        modifier
            .clearAndSetSemantics { }
            .drawBehind {
                frame.longValue // 毎フレーム描き直す（描画だけ）
                val center = size.height / 2
                val count = (size.width / pitch).toInt() + 2
                for (age in -1 until count) {
                    // age = -1 は、いま積もうとしている声（右端から出てくる）
                    val level = if (age < 0) wave.current else wave.at(age)
                    val x = size.width - bar / 2 - (age + wave.phase) * pitch
                    if (x < bar / 2 || x > size.width - bar / 2) continue
                    val height = bar + (size.height - bar) * level
                    drawLine(
                        color = lerp(quiet.copy(alpha = VOICE_WAVE_QUIET_ALPHA), loud, level),
                        start = Offset(x, center - (height - bar) / 2),
                        end = Offset(x, center + (height - bar) / 2),
                        strokeWidth = bar,
                        cap = StrokeCap.Round,
                    )
                }
            },
    )
}

/** 波形の履歴（新しい順）。描画の中から読むだけなので、状態にしない。 */
private class WaveHistory {
    private val samples = FloatArray(VOICE_WAVE_CAPACITY)
    private var head = 0
    private var size = 0
    var current = 0f
    var phase = 0f

    fun push(level: Float) {
        samples[head] = level
        head = (head + 1) % VOICE_WAVE_CAPACITY
        size = minOf(size + 1, VOICE_WAVE_CAPACITY)
    }

    fun at(age: Int): Float = if (age >= size) 0f else samples[(head - 1 - age + VOICE_WAVE_CAPACITY) % VOICE_WAVE_CAPACITY]
}

/** 波形が 1 本ずれる間隔（ナノ秒）。 */
private const val VOICE_WAVE_SAMPLE_NS = 60_000_000L
/** 声の大きさに追いつく速さ（大きいほど速い）。 */
private const val VOICE_WAVE_SMOOTHING = 18f
private const val VOICE_WAVE_CAPACITY = 160

/** 声が無いところの点の濃さ。 */
private const val VOICE_WAVE_QUIET_ALPHA = 0.5f

/**
 * 補完の候補の一覧（コマンド・スキル）。受け付け待ちの行と同じガラスの面に、名前（primary）と説明を並べる。
 * ポップアップにしないので、出ているあいだもキーボードで打ち続けられる。押すと入力欄をその候補で置き換える。
 */
@Composable
private fun SlashSuggestionPanel(hazeState: HazeState, suggestions: List<SlashSuggestion>, onPick: (SlashSuggestion) -> Unit) {
    // 消える動きのあいだは、最後に出していた候補を残す
    val last = remember { LastSuggestions() }
    if (suggestions.isNotEmpty()) last.value = suggestions
    AnimatedVisibility(visible = suggestions.isNotEmpty(), enter = expandEnter(), exit = shrinkExit()) {
        GlassPanel(hazeState = hazeState, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs), fixedOpacity = MENU_OPACITY) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = Dimens.slashMenuMaxHeight)) {
                items(last.value, key = { it.key }) { suggestion ->
                    val (title, detail) = when (suggestion) {
                        is SlashSuggestion.Command -> "/${suggestion.command.name}" to localizedDescription(suggestion.command)
                        is SlashSuggestion.Choice -> suggestion.choice.label to suggestion.choice.id.takeIf { it != suggestion.choice.label }
                        is SlashSuggestion.Skill -> suggestion.command.skillName.orEmpty() to suggestion.command.description
                    }
                    ListItem(
                        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) },
                        supportingContent = detail?.takeIf(String::isNotBlank)?.let { text ->
                            {
                                Text(
                                    text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPick(suggestion) },
                    )
                }
            }
        }
    }
}

/** コマンドの説明をアプリの言語で。訳が無い（空・未登録）ときは Gateway の説明。 */
@Composable
private fun localizedDescription(command: SlashCommand): String {
    val id = if (command.isSkill) null else SlashCommandDescriptions[command.name]
    return id?.let { stringResource(it) }?.takeIf(String::isNotBlank) ?: command.description
}

/** 候補の一覧が空になったあとも、消える動きのあいだだけ前の中身を描くための入れ物（状態ではない）。 */
private class LastSuggestions {
    var value: List<SlashSuggestion> = emptyList()
}

/** 「質問が届いています」の 1 行。ガラスの面で、受け付け待ちの行と同じ形。 */
@Composable
private fun QuestionRow(hazeState: HazeState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(hazeState = hazeState, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = Dimens.minTouchTarget).padding(horizontal = Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                stringResource(R.string.question_pending),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(R.string.question_open), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 受け付け待ちの 1 行。ガラスの面に、待ちの目印・本文（1 行）・取り消し。 */
@Composable
private fun PendingInputRow(hazeState: HazeState, input: PendingInput, enabled: Boolean, onRestore: () -> Unit, onCancel: () -> Unit) {
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
            Row {
                // 取り下げて入力欄に戻せるのは、Gateway が待ちに積んだ送信だけ。差し込みで受け付け済みのあいだはボタンを出さない
                if (!input.local && !input.hasAttachments && input.text.isNotBlank()) {
                    IconButton(enabled = enabled, onClick = onRestore) {
                        Icon(painterResource(R.drawable.ic_huge_edit), contentDescription = stringResource(R.string.chat_pending_restore_cd))
                    }
                }
                IconButton(enabled = enabled, onClick = onCancel) {
                    Icon(painterResource(R.drawable.ic_huge_delete), contentDescription = stringResource(R.string.chat_pending_cancel_cd))
                }
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
    val motion = MaterialTheme.motionScheme
    // 使用量の帯と同じ表し方（マスター指定 2026-11-23）: 使ったコンテキストの分だけ淡いテーマの色で塗り、下の縁にテーマの色の線。
    // 残りが少なくても赤にしない（帯の長さで消費率が分かるため。マスター指定 2026-11-23）。伸びはばね
    val strong = colors.primary
    val progress = remember { Animatable(0f) }
    LaunchedEffect(contextFraction) { progress.animateTo((contextFraction ?: 0f).coerceIn(0f, 1f), motion.defaultSpatialSpec()) }
    val density = LocalDensity.current
    val edge = with(density) { Dimens.usageMeterEdge.toPx() }
    val name = model?.let(::shortModelName) ?: stringResource(R.string.chat_setting_default)
    val percent = contextFraction?.let { NumberFormat.getPercentInstance().format(it) }
    val cd = if (percent != null) stringResource(R.string.chat_model_pill_cd, name, percent) else name
    Surface(
        onClick = onClick,
        shape = CircleShape,
        // 下地は入力欄の面に文字色を少し重ねた色（ダークでは surfaceContainerHigh が入力欄と見分けられず、ピルが途切れて見えたため）
        color = lerp(sheetFieldColor(), colors.onSurface, if (colors.surface.luminance() < 0.5f) PILL_TRACK_DARK else PILL_TRACK_LIGHT),
        contentColor = colors.onSurface,
        modifier = modifier.heightIn(min = Dimens.compactTouchTarget).widthIn(max = Dimens.modelPillMaxWidth).semantics { contentDescription = cd },
    ) {
        Row(
            Modifier
                .drawBehind {
                    val width = size.width * progress.value
                    if (width > 0f) {
                        drawRect(lerp(strong, colors.surface, PILL_FILL_MIX), size = size.copy(width = width))
                        drawRect(strong, topLeft = Offset(0f, size.height - edge), size = Size(width, edge))
                    }
                }
                .padding(horizontal = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = Spacing.s))
        }
    }
}

/** モデルのピルの塗りで、強調の色を背景に溶かす割合（使用量の帯と同じ）。 */
private const val PILL_FILL_MIX = 0.78f
/** モデルのピルの下地で、入力欄の面に重ねる文字色の割合（ライト / ダーク）。 */
private const val PILL_TRACK_LIGHT = 0.08f
private const val PILL_TRACK_DARK = 0.16f


/** コンテキストの消費の境目（中・高）。 */
private const val CONTEXT_MEDIUM = 0.5f
private const val CONTEXT_HIGH = 0.8f

/**
 * 添付の 1 枚。会話の画像と同じく影で少し浮かせる（AGENTS.md 2.5 の画像の例外。マスター指定 2026-11-24: 背景と同化しないように）。
 * 押すとプレビュー、右上の小さな丸い × で外す（押せる範囲はチップと同じ 32dp）。
 */
@Composable
private fun AttachmentThumbnail(
    pending: jp.miyavi.android.clawdroid.feature.chat.PendingImage,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    // × を角に重ねるので、その分だけ右上をあける
    Box(Modifier.padding(top = Spacing.s, end = Spacing.s)) {
        val isImage = pending.image.isImage
        val bitmap = if (isImage) rememberBase64Bitmap(pending.image.base64) else null
        Box(
            Modifier
                .size(Dimens.attachmentThumbnail)
                .shadow(Dimens.imageElevation, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (isImage) Modifier.clickable(onClickLabel = stringResource(R.string.chat_image_cd), onClick = onOpen) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (!isImage) {
                // ファイルはアイコンと名前（中身は開かない）
                Column(Modifier.padding(Spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_huge_file), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        pending.image.fileName.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (bitmap != null) {
                Image(bitmap, contentDescription = stringResource(R.string.chat_image_cd), contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = Spacing.s, y = -Spacing.s)
                .size(Dimens.compactTouchTarget)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.chat_remove_attachment_cd), onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = Dimens.imageElevation,
                modifier = Modifier.size(Dimens.attachmentRemove),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_huge_close),
                        contentDescription = stringResource(R.string.chat_remove_attachment_cd),
                        modifier = Modifier.size(Dimens.attachmentRemoveIcon),
                    )
                }
            }
        }
    }
}

/**
 * 入力欄（端末側）と会話の状態（ViewModel 側）の文字のすり合わせ。入力欄から送った文字が遅れて戻ってきた（こだま）のか、
 * 外で変わった（送信で空・音声・下書きの復元）のかを見分ける。こだまなら入力欄を書き戻さない（打ち込み中の文字を巻き戻さない）。
 */
private class ComposerTextSync {
    private val pending = ArrayDeque<String>()

    fun sent(text: String) {
        pending.addLast(text)
        while (pending.size > MAX_PENDING) pending.removeFirst()
    }

    /** [text] が送ったもののこだまなら、それまでの分を捨てて true。 */
    fun isEcho(text: String): Boolean {
        val index = pending.indexOf(text)
        if (index < 0) return false
        repeat(index + 1) { pending.removeFirst() }
        return true
    }

    private companion object {
        const val MAX_PENDING = 64
    }
}
