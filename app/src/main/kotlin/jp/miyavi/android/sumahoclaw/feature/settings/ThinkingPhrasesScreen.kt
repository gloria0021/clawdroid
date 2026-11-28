package jp.miyavi.android.sumahoclaw.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.designsystem.AppTitleBar
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.FloatingIconButton
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.sumahoclaw.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.bodyContainerColor
import jp.miyavi.android.sumahoclaw.core.designsystem.bodyTextStyle
import jp.miyavi.android.sumahoclaw.core.settings.ThinkingPhrases
import jp.miyavi.android.sumahoclaw.feature.chat.message.ThinkingIndicator
import kotlinx.coroutines.launch

/**
 * 考え中のひとこと（設定 → エージェント）。1 行 = 1 つのひとことのエディタで、まとめて書き換える（マスター指定 2026-11-28）。
 * 誤って消さないよう、反映は「保存」を押したときだけ。保存していない変更があれば戻る前に確かめる。⋮ から既定に戻せる。
 * 上の見え方は、カーソルのある行を会話と同じ考え中の表示で出す。
 *
 * [initial] は開いたときの保存済みの一覧（null なら既定）。既定と同じ内容で保存したら、既定のまま（言語に合わせて切り替わる）にする。
 */
@Composable
internal fun ThinkingPhrasesScreen(initial: List<String>?, onSave: (List<String>?) -> Unit, onBack: () -> Unit) {
    val defaultsArray = stringArrayResource(R.array.chat_thinking_phrases)
    val defaults = remember(defaultsArray) { ThinkingPhrases.parse(defaultsArray.joinToString("\n")) }
    // 保存済みの内容（既定なら null）。編集の途中の文字とともに、プロセスの終了と構成の変更を越えて残す（AGENTS.md 2.9）
    var baseline by rememberSaveable { mutableStateOf(initial?.let(ThinkingPhrases::format)) }
    val editor = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState(ThinkingPhrases.format(initial ?: defaults)) }
    val phrases by remember { derivedStateOf { ThinkingPhrases.parse(editor.text) } }
    val saved = remember(baseline, defaults) { baseline?.let(ThinkingPhrases::parse) ?: defaults }
    val dirty = phrases != saved
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    fun save() {
        if (phrases.isEmpty()) return
        val next = phrases.takeIf { it != defaults }
        onSave(next)
        baseline = next?.let(ThinkingPhrases::format)
        scope.launch { snackbar.showSnackbar(resources.getString(R.string.files_saved)) }
    }
    fun back() = if (dirty) confirmDiscard = true else onBack()

    BackHandler(enabled = dirty) { confirmDiscard = true }
    ProvideScreenHaze(hazeState) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { GlassSnackbarHost(snackbar, Modifier.navigationBarsPadding().imePadding()) },
            topBar = {
                AppTitleBar(
                    hazeState = hazeState,
                    title = { Text(stringResource(R.string.settings_thinking_phrases), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), ::back,
                            modifier = Modifier.padding(start = Spacing.s))
                    },
                    actions = {
                        IconButton(onClick = ::save, enabled = dirty && phrases.isNotEmpty()) {
                            Icon(painterResource(R.drawable.ic_huge_check), stringResource(R.string.files_save_cd))
                        }
                        MoreMenu(enabled = baseline != null || dirty, onReset = { confirmReset = true })
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().hazeSource(hazeState), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = Dimens.maxReadingWidth)
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding())
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Text(
                        stringResource(R.string.thinking_phrases_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                    // 見え方: カーソルのある行（空の行なら最初のひとこと）を、会話と同じ表示で
                    val current by remember {
                        derivedStateOf {
                            val text = editor.text
                            val cursor = editor.selection.start.coerceIn(0, text.length)
                            val start = text.lastIndexOf('\n', cursor - 1) + 1
                            val end = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
                            ThinkingPhrases.normalize(text.substring(start, end))
                        }
                    }
                    val startedAt = remember { System.currentTimeMillis() }
                    Text(stringResource(R.string.thinking_phrases_preview), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ThinkingIndicator(
                        startedAt = startedAt,
                        label = current.ifEmpty { phrases.firstOrNull().orEmpty() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (phrases.isEmpty()) stringResource(R.string.thinking_phrases_empty)
                        else stringResource(R.string.settings_thinking_phrases_count, phrases.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (phrases.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PhraseEditor(editor, Modifier.fillMaxWidth().weight(1f).padding(bottom = Spacing.l))
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.files_discard_title)) },
            text = { Text(stringResource(R.string.files_discard_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) { Text(stringResource(R.string.files_discard_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.thinking_phrases_reset_title)) },
            text = { Text(stringResource(R.string.thinking_phrases_reset_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    editor.setTextAndPlaceCursorAtEnd(ThinkingPhrases.format(defaults))
                    onSave(null)
                    baseline = null
                }) { Text(stringResource(R.string.thinking_phrases_reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun MoreMenu(enabled: Boolean, onReset: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(painterResource(R.drawable.ic_huge_more), stringResource(R.string.thinking_phrases_more_cd)) }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.thinking_phrases_reset)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_huge_undo), contentDescription = null) },
                enabled = enabled,
                onClick = {
                    open = false
                    onReset()
                },
            )
        }
    }
}

/**
 * 1 行 = 1 つのひとことのエディタ。本文の下地の角丸の面に、論理行の先頭にだけ行番号を付ける
 * （折り返した行には付けない。番号は描画の中で位置を読み、入力のたびに組み立て直さない）。
 */
@Composable
private fun PhraseEditor(state: TextFieldState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = bodyTextStyle().copy(color = colors.onSurface)
    val numberStyle = style.copy(color = colors.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val gutterWidth = remember(numberStyle, density) { with(density) { measurer.measure("00", numberStyle).size.width.toDp() } } + Spacing.s
    var layout by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    val scroll = rememberScrollState()
    Row(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(bodyContainerColor())
            .padding(vertical = Spacing.s),
    ) {
        Box(
            Modifier
                .padding(start = Spacing.s)
                .width(gutterWidth)
                .fillMaxHeight()
                .drawBehind {
                    val result = layout?.invoke() ?: return@drawBehind
                    val text = result.layoutInput.text.text
                    var start = 0
                    var line = 1
                    while (start <= text.length) {
                        val top = result.getLineTop(result.getLineForOffset(start)) - scroll.value
                        if (top > size.height) break
                        if (top > -result.size.height) {
                            val number = measurer.measure(line.toString(), numberStyle)
                            drawText(number, topLeft = Offset(size.width - Spacing.s.toPx() - number.size.width, top))
                        }
                        val next = text.indexOf('\n', start)
                        if (next < 0) break
                        start = next + 1
                        line++
                    }
                },
        )
        BasicTextField(
            state = state,
            textStyle = style,
            lineLimits = TextFieldLineLimits.MultiLine(),
            scrollState = scroll,
            cursorBrush = SolidColor(colors.primary),
            onTextLayout = { layout = it },
            modifier = Modifier.weight(1f).fillMaxHeight().padding(end = Spacing.m),
        )
    }
}
