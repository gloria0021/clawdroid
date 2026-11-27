package jp.miyavi.android.sumahoclaw.feature.chat.composer

import jp.miyavi.android.sumahoclaw.core.designsystem.GlassDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import jp.miyavi.android.sumahoclaw.core.designsystem.sheetFieldColor
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
import jp.miyavi.android.sumahoclaw.core.data.PendingInput
import androidx.compose.runtime.key
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import jp.miyavi.android.sumahoclaw.core.data.SessionSetting
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
import jp.miyavi.android.sumahoclaw.core.designsystem.MENU_OPACITY
import jp.miyavi.android.sumahoclaw.core.data.SlashCommand
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.shortModelName
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.FIELD_OPACITY
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassPanel
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassTint
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.bodyTextStyle
import jp.miyavi.android.sumahoclaw.core.designsystem.expandEnter
import jp.miyavi.android.sumahoclaw.core.designsystem.shrinkExit
import jp.miyavi.android.sumahoclaw.feature.chat.ComposerState
import jp.miyavi.android.sumahoclaw.feature.chat.message.rememberBase64Bitmap

/**
 * 画面下部に浮くガラスの入力欄（AGENTS.md 2.7）。上に入力、下の行に 添付 ｜ 実行権限 ｜ モデルのピル ｜ 送信。
 * モデルのピルはコンテキストの消費で塗りが伸び、押すとセッションのシート（モデル・エフォートと使用量）を開く。
 * 右端は音声入力と送信（入力が空で実行中なら停止）。生成中でも入力があれば送れる（FR-022・029）。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ComposerPill(
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
    /** 実行権限のボタン。null なら出さない（エージェントを読めていない）。 */
    permission: PermissionControl? = null,
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
    onOpenImage: (List<jp.miyavi.android.sumahoclaw.feature.chat.PendingImage>, Int) -> Unit = { _, _ -> },
    /** 引用・コピーしたテキストのタイルを押した（全文のプレビュー）・外した・長い貼り付けがあった。 */
    onOpenText: (jp.miyavi.android.sumahoclaw.feature.chat.PendingText) -> Unit = {},
    onRemoveText: (String) -> Unit = {},
    onPasteLongText: (String) -> Unit = {},
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // シート・メニューを開くときは入力欄のフォーカスを外す。閉じたあとにキーボードが戻ると画面の高さが何度も変わるため、
    // 文字入力に戻るのは入力欄を自分で押したときだけ（マスター指定 2026-11-26）
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val leaveInput = {
        keyboard?.hide()
        focusManager.clearFocus()
    }
    Column(modifier) {
        // あとで答えることにしたエージェントからの質問。押すとシートを開き直す
        AnimatedVisibility(visible = hasQuestion, enter = expandEnter(), exit = shrinkExit()) {
            QuestionRow(hazeState, onOpenQuestion, Modifier.padding(bottom = Spacing.xs))
        }
        // キューにある送信（取り消せるものだけ）。入力欄の上に積み、送られたら消える。取り消しはゴミ箱。
        // 送信済みで読まれ待ちの送信は、会話の末尾に「未読」の吹き出しで出す（ピル = まだ送っていない、にそろえる）
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
        // 最新の値で比べる（最初の値を掴んだままだと、最後の 1 文字を消して空に戻したときに「変わっていない」と見なし、
        // 空になったことが伝わらずプレースホルダーが出なかった）
        val latestText by rememberUpdatedState(state.text)
        LaunchedEffect(fieldState) {
            snapshotFlow { fieldState.text.toString() }.collect { text ->
                if (text != latestText) {
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
            AnimatedVisibility(visible = state.images.isNotEmpty() || state.texts.isNotEmpty(), enter = expandEnter(), exit = shrinkExit()) {
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
                    // 引用とコピーしたテキストも同じ列に、同じ形のタイルで（マスター指定 2026-11-26）
                    items(state.texts, key = { it.id }) { item ->
                        TextAttachmentTile(item, onOpen = { onOpenText(item) }, onRemove = { onRemoveText(item.id) })
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
                // 空かどうかは入力欄そのものの文字で見る（届くのを待たずに出す）
                val fieldEmpty = fieldState.text.isEmpty()
                // プレースホルダーは候補からランダムに 1 つ。空に戻るたびに選び直す（打っている途中では変えない。マスター指定 2026-11-26）
                val placeholders = androidx.compose.ui.res.stringArrayResource(R.array.chat_input_placeholders)
                var placeholderIndex by rememberSaveable { mutableIntStateOf(placeholders.indices.random()) }
                var wasEmpty by remember { mutableStateOf(fieldEmpty) }
                LaunchedEffect(fieldEmpty) {
                    if (fieldEmpty && !wasEmpty) placeholderIndex = placeholders.indices.random()
                    wasEmpty = fieldEmpty
                }
                if (fieldEmpty) {
                    // 聞き取り中は、途中の結果（まだ無ければ「聞き取り中…」）を入力欄の位置に薄く出す
                    val hint = when {
                        !voice.listening -> placeholders.getOrElse(placeholderIndex) { placeholders.first() }
                        voice.partial.isNotBlank() -> voice.partial
                        else -> stringResource(R.string.chat_voice_listening)
                    }
                    // 入力した文字と見分けがつくよう、プレースホルダーは薄く（AGENTS.md 2.2 の「無効・折りたたみ」と同じ
                    // onSurfaceVariant の 0.6。聞き取り中の途中の結果も同じ薄さ。マスター指定 2026-11-26）
                    Text(hint, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = jp.miyavi.android.sumahoclaw.core.designsystem.DisabledAlpha))
                }
                // 長い貼り付け（100 文字以上で改行を含む）は、本文に入れずにコピーしたテキストのタイルにする。
                // 打つ文字は 1 度にこれほど入らないので、貼り付け（メニュー・キーボードのクリップボード）だけが当たる
                val currentOnLongPaste by rememberUpdatedState(onPasteLongText)
                val longPasteGuard = remember {
                    androidx.compose.foundation.text.input.InputTransformation {
                        for (i in changes.changeCount - 1 downTo 0) {
                            val range = changes.getRange(i)
                            if (range.length < jp.miyavi.android.sumahoclaw.feature.chat.LONG_PASTE_CHARS) continue
                            val inserted = asCharSequence().subSequence(range.min, range.max).toString()
                            if (!jp.miyavi.android.sumahoclaw.feature.chat.isLongPaste(inserted)) continue
                            replace(range.min, range.max, "")
                            currentOnLongPaste(inserted)
                        }
                    }
                }
                BasicTextField(
                    state = fieldState,
                    inputTransformation = longPasteGuard,
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
            val canSend = state.text.isNotBlank() || state.images.isNotEmpty() || state.texts.isNotEmpty()
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
                            IconButton(onClick = { leaveInput(); attachMenu = true }, enabled = enabled) {
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
                        // 実行権限はモデルの左に、今の段階のアイコンだけで（押すと選ぶ。マスター指定 2026-11-26）。
                        // 押せる範囲（48dp）は保ったまま、添付と重ねてアイコンどうしの間を詰める（空きすぎ。マスター指定 2026-11-26）
                        permission?.let { PermissionButton(it, onOpen = leaveInput, modifier = Modifier.offset(x = -ICON_PAIR_OVERLAP)) }
                        // ピルは中身の長さに合わせる。残りの幅は外側の箱が受け持ち、足りないときだけ省略する
                        // （ピルと Spacer の両方に weight を付けると残りを半分ずつ分け、短い名前でも切れていた。マスター指定 2026-11-26）
                        Box(
                            Modifier.weight(1f).then(if (permission != null) Modifier.offset(x = -ICON_PAIR_OVERLAP) else Modifier),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            ModelPill(
                                model = model, contextFraction = contextFraction,
                                onClick = { leaveInput(); onOpenSession() },
                            )
                        }
                        // 音声入力は送信ボタンの左にいつも置く（入力の途中から言い足せる。2 つに分けた形に戻す。マスター指定 2026-11-26）
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

/** 入力欄の実行権限のボタンに渡すもの。 */
internal data class PermissionControl(
    /** 今の段階（null はエージェントの既定）。 */
    val value: String?,
    val enabled: Boolean,
    val options: List<SettingOption>,
    /** 保存中（アイコンの場所で読み込み表示）。 */
    val saving: Boolean = false,
    /** 保存できた回数（増えたら一瞬チェック）。 */
    val savedTick: Int = 0,
    val onSelect: (String?) -> Unit,
)

/** 実行権限。今の段階のアイコンだけを出し、押すと透過効果のメニューで選ぶ。 */
@Composable
private fun PermissionButton(control: PermissionControl, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val choice = PermissionChoice.entries.firstOrNull { it.value == control.value } ?: PermissionChoice.Default
    Box(modifier) {
        var open by remember { mutableStateOf(false) }
        IconButton(onClick = { onOpen(); open = true }, enabled = control.enabled && !control.saving) {
            val cd = stringResource(R.string.chat_permission_button, stringResource(choice.label))
            // 保存中は同じ場所で読み込み表示、保存できたら一瞬チェック（マスター指定 2026-11-26）
            jp.miyavi.android.sumahoclaw.core.designsystem.SaveStateIcon(
                control.saving, control.savedTick, Modifier.size(Spacing.xl).semantics { contentDescription = cd },
            ) {
                Icon(painterResource(choice.icon), contentDescription = null)
            }
        }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SettingOptionsList(SessionSetting.PERMISSIONS, control.options) { value ->
                open = false
                control.onSelect(value)
            }
        }
    }
}

/**
 * 引用・コピーしたテキストのタイル。画像の添付と同じ大きさ・形・影・右上の ×（マスター指定 2026-11-26）。
 * 中に先頭の数行を小さく薄く、下に種類と文字数。押すと全文のプレビュー。
 */
@Composable
private fun TextAttachmentTile(item: jp.miyavi.android.sumahoclaw.feature.chat.PendingText, onOpen: () -> Unit, onRemove: () -> Unit) {
    val quote = item.kind == jp.miyavi.android.sumahoclaw.feature.chat.PendingText.Kind.QUOTE
    val label = stringResource(if (quote) R.string.chat_text_quote else R.string.chat_text_paste)
    AttachmentTileFrame(
        onOpen = onOpen,
        openLabel = stringResource(R.string.chat_text_preview_cd),
        onRemove = onRemove,
        contentDescription = stringResource(R.string.chat_text_tile_cd, label, item.text.length),
    ) {
        Column(Modifier.matchParentSize().padding(Spacing.xs)) {
            // 先頭の数行（中身の手がかり。読ませるものではないので小さく薄く）
            Text(
                item.text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = jp.miyavi.android.sumahoclaw.core.designsystem.DisabledAlpha),
                maxLines = 2,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs / 2)) {
                Icon(
                    painterResource(if (quote) R.drawable.ic_huge_ai_chat else R.drawable.ic_huge_text),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.attachmentRemoveIcon),
                )
                Text(stringResource(R.string.chat_text_chars, item.text.length), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
                // ここに出るのは取り消せる送信（OpenClaw のキューにあるもの）だけ。入力欄に戻せるのは文字だけの送信
                if (!input.hasAttachments && input.text.isNotBlank()) {
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
            // 文字はアプリの文字サイズに引っ張られない（大きくすると下の行が並べきれない。マスター指定 2026-11-26）
            Text(name, style = jp.miyavi.android.sumahoclaw.core.designsystem.LocalFixedTypography.current.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = Spacing.s))
        }
    }
}

/** 添付と実行権限のボタンを重ねる幅（押せる範囲は 48dp のまま、アイコンの間だけ詰める）。 */
private val ICON_PAIR_OVERLAP = Spacing.s

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
    pending: jp.miyavi.android.sumahoclaw.feature.chat.PendingImage,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val isImage = pending.image.isImage
    val bitmap = if (isImage) rememberBase64Bitmap(pending.image.base64) else null
    AttachmentTileFrame(
        onOpen = if (isImage) onOpen else null,
        openLabel = stringResource(R.string.chat_image_cd),
        onRemove = onRemove,
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
}

/**
 * 添付のタイルの共通の枠（画像・ファイル・引用・コピーしたテキスト）。大きさ・角丸・影・下地・右上の × と、その押せる範囲・
 * 読み上げをここにまとめる（CJ レビュー 2026-11-26: 枠を別々に作っていて、× の直しが片方だけに残る恐れがあった）。
 * 中身と押したときの動きだけを差し替える。[onOpen] が null なら押せない。
 */
@Composable
private fun AttachmentTileFrame(
    onOpen: (() -> Unit)?,
    openLabel: String,
    onRemove: () -> Unit,
    contentDescription: String? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    // × を角に重ねるので、その分だけ右上をあける
    Box(Modifier.padding(top = Spacing.s, end = Spacing.s)) {
        Box(
            Modifier
                .size(Dimens.attachmentThumbnail)
                .shadow(Dimens.imageElevation, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (onOpen != null) Modifier.clickable(onClickLabel = openLabel, onClick = onOpen) else Modifier)
                .then(if (contentDescription != null) Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription } else Modifier),
            contentAlignment = contentAlignment,
            content = content,
        )
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
