package jp.miyavi.android.clawdroid.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.QuestionItem
import jp.miyavi.android.clawdroid.core.data.QuestionOptionItem
import jp.miyavi.android.clawdroid.core.data.QuestionsRepository
import jp.miyavi.android.clawdroid.core.data.SessionsRepository
import jp.miyavi.android.clawdroid.core.designsystem.AppBottomSheet
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.accentContainer
import jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor
import jp.miyavi.android.clawdroid.feature.chat.AgentAvatar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * エージェントからの質問（`ask_user`）に答えるボトムシート。M3 の選択式の作り:
 * 見出し（アバター・誰から・何問目）→ 問い 1 つずつ（ラジオ / チェックの行、選んだ行はピルで塗る、おすすめは小さな印）
 * → 下に「スキップ」と「戻る / 次へ / 答える」。自由入力は「その他」を選んだときだけ欄を開く。
 * シートを下げて閉じると「あとで」になり、会話の入力欄の上から開き直せる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuestionPrompt(repository: QuestionsRepository, sessions: SessionsRepository, hazeState: HazeState) {
    val requests by repository.requests.collectAsStateWithLifecycle()
    val snoozed by repository.snoozed.collectAsStateWithLifecycle()
    val request = requests.firstOrNull { it.id !in snoozed } ?: return
    val agents by sessions.agents.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppBottomSheet(onDismissRequest = { repository.snooze(request.id) }, hazeState = hazeState, sheetState = sheetState) {
        key(request.id) {
            // questionId → 選んだ値 / その他の文。構成の変更を越えて残す
            var selected by rememberSaveable { mutableStateOf(mapOf<String, List<String>>()) }
            var other by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
            var otherOpen by rememberSaveable { mutableStateOf(setOf<String>()) }
            var step by rememberSaveable { mutableIntStateOf(0) }
            var busy by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val agent = agents.agents.firstOrNull { it.id == request.agentId }
            val questions = request.questions
            val current = questions[step.coerceIn(0, questions.lastIndex)]
            val last = step >= questions.lastIndex

            fun answerOf(q: QuestionItem): List<String> =
                selected[q.questionId].orEmpty() + listOfNotNull(other[q.questionId]?.trim()?.takeIf { it.isNotEmpty() && q.questionId in otherOpen })
            fun answered(q: QuestionItem) = q.allowEmpty || answerOf(q).isNotEmpty()

            fun submit(action: suspend () -> Unit) {
                if (busy) return
                failed = false
                busy = true
                scope.launch {
                    try {
                        action()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        failed = true
                    } finally {
                        busy = false
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                // 見出し: 誰からの質問か、何問目か
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    AgentAvatar(agent, label = null, size = Dimens.avatarLarge)
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.question_sheet_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            agent?.name ?: request.agentId.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (questions.size > 1) {
                        Text(
                            stringResource(R.string.question_step, step + 1, questions.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (questions.size > 1) {
                    val progress by androidx.compose.animation.core.animateFloatAsState(
                        (step + 1f) / questions.size,
                        MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "questionProgress",
                    )
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }

                // 中身はシートより明るい不透明な面に載せ、長いときはその中だけスクロール（AGENTS.md 2.5）
                Surface(
                    color = sheetFieldColor(),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                ) {
                    // 問いの切り替えは、出る側を標準・消える側を速めのフェードで（AGENTS.md 2.8）
                    val enterFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    val exitFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = step.coerceIn(0, questions.lastIndex),
                        transitionSpec = { fadeIn(enterFade) togetherWith fadeOut(exitFade) },
                        label = "questionStep",
                    ) { index ->
                        val q = questions[index]
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.l)) {
                            if (!request.answerable) {
                                Text(
                                    stringResource(R.string.question_unsupported),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = Spacing.m),
                                )
                            }
                            QuestionBlock(
                                question = q,
                                selected = selected[q.questionId].orEmpty(),
                                other = other[q.questionId].orEmpty(),
                                otherOpen = q.questionId in otherOpen,
                                enabled = request.answerable && !busy,
                                onSelect = { values ->
                                    selected = selected + (q.questionId to values)
                                    // 1 つ選ぶ問いでは、選択肢を選んだら「その他」を閉じる
                                    if (!q.multiSelect) otherOpen = otherOpen - q.questionId
                                },
                                onToggleOther = { open ->
                                    otherOpen = if (open) otherOpen + q.questionId else otherOpen - q.questionId
                                    if (open && !q.multiSelect) selected = selected + (q.questionId to emptyList())
                                },
                                onOther = { text -> other = other + (q.questionId to text) },
                            )
                        }
                    }
                }

                if (failed) {
                    Text(stringResource(R.string.question_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }

                // 操作: 左にスキップ、右に戻る・次へ / 答える
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    TextButton(enabled = !busy, onClick = { submit { repository.skip(request) } }) {
                        Text(stringResource(R.string.question_skip))
                    }
                    Spacer(Modifier.weight(1f))
                    if (step > 0) {
                        OutlinedButton(enabled = !busy, onClick = { step-- }) { Text(stringResource(R.string.question_back)) }
                    }
                    Button(
                        enabled = !busy && request.answerable && answered(current) && (!last || questions.all(::answered)),
                        onClick = {
                            if (!last) step++
                            else submit { repository.answer(request, questions.associate { it.questionId to answerOf(it) }) }
                        },
                    ) {
                        Text(stringResource(if (last) R.string.question_submit else R.string.question_next))
                    }
                }
            }
        }
    }
}

/** 1 つの問い。見出し（チップの代わりの小さな見出し）・問い・選択肢の行・その他。 */
@Composable
private fun QuestionBlock(
    question: QuestionItem,
    selected: List<String>,
    other: String,
    otherOpen: Boolean,
    enabled: Boolean,
    onSelect: (List<String>) -> Unit,
    onToggleOther: (Boolean) -> Unit,
    onOther: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (question.header.isNotBlank()) {
            Text(question.header, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(question.question, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.s))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            question.options.forEach { option ->
                val isSelected = option.value in selected
                OptionRow(
                    option = option,
                    selected = isSelected,
                    multiSelect = question.multiSelect,
                    enabled = enabled,
                    onClick = {
                        onSelect(
                            if (question.multiSelect) (if (isSelected) selected - option.value else selected + option.value)
                            else listOf(option.value),
                        )
                    },
                )
            }
            // 自由入力は「その他」を選んだときだけ欄を開く（OpenClaw は必ず自由入力を受け付ける）
            OptionRow(
                option = QuestionOptionItem(stringResource(R.string.question_other_option), null, ""),
                selected = otherOpen,
                multiSelect = question.multiSelect,
                enabled = enabled,
                onClick = { onToggleOther(!otherOpen) },
            )
            AnimatedVisibility(visible = otherOpen, enter = jp.miyavi.android.clawdroid.core.designsystem.expandEnter(), exit = jp.miyavi.android.clawdroid.core.designsystem.shrinkExit()) {
                TextField(
                    value = other,
                    onValueChange = onOther,
                    enabled = enabled,
                    placeholder = { Text(stringResource(R.string.question_other_hint)) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                )
            }
        }
    }
}

/** 選択肢の 1 行。選んだ行はテーマの濃い色のピルで塗る（サイドパネルの選択と同じ）。おすすめは小さなバッジ（secondaryContainer・角は full。AGENTS.md 2.2・2.5）にする。 */
@Composable
private fun OptionRow(option: QuestionOptionItem, selected: Boolean, multiSelect: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val accent = accentContainer()
    val (label, recommended) = remember(option.label) { splitRecommended(option.label) }
    val rowModifier = if (multiSelect) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onClick() }
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(if (selected) accent.container else Color.Transparent)
            .then(rowModifier)
            .heightIn(min = Dimens.minTouchTarget)
            .padding(horizontal = Spacing.s, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        val content = if (selected) accent.content else MaterialTheme.colorScheme.onSurface
        if (multiSelect) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = content, checkmarkColor = accent.container))
        } else {
            RadioButton(selected = selected, onClick = null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = content))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs / 2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = content, modifier = Modifier.weight(1f, fill = false))
                if (recommended) {
                    Text(
                        stringResource(R.string.question_recommended),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = Spacing.s, vertical = Spacing.xs / 2),
                    )
                }
            }
            option.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 「(Recommended)」「（推奨）」などの印を外し、別の小さな印として出す。送る値は元のまま。 */
internal fun splitRecommended(label: String): Pair<String, Boolean> {
    val marks = listOf("(Recommended)", "（推奨）", "(推奨)", "(おすすめ)", "（おすすめ）")
    val mark = marks.firstOrNull { label.trimEnd().endsWith(it, ignoreCase = true) } ?: return label to false
    return label.trimEnd().dropLast(mark.length).trimEnd() to true
}
