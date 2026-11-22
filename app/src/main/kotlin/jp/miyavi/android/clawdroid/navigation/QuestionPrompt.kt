package jp.miyavi.android.clawdroid.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.QuestionItem
import jp.miyavi.android.clawdroid.core.data.QuestionsRepository
import jp.miyavi.android.clawdroid.core.data.SessionsRepository
import jp.miyavi.android.clawdroid.core.designsystem.AppBottomSheet
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.bodyTextStyle
import jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor
import jp.miyavi.android.clawdroid.feature.chat.AgentAvatar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * エージェントからの質問（`ask_user`）に、ボトムシートの選択式で答える。
 * 複数の問いはシートの中に縦に積む（OpenClaw の Android の作法）。選択肢のほかに自由入力（その他）を必ず出す。
 * スワイプや「あとで」で閉じた質問は、会話の入力欄の上から開き直せる。スキップは質問全体を断る。
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
            // questionId → 選んだ値。自由入力は別に持つ（構成の変更を越えて残す）
            var selected by rememberSaveable { mutableStateOf(mapOf<String, List<String>>()) }
            var other by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
            var busy by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val agent = agents.agents.firstOrNull { it.id == request.agentId }

            fun answers(): Map<String, List<String>> = request.questions.associate { q ->
                q.questionId to (selected[q.questionId].orEmpty() + listOfNotNull(other[q.questionId]?.trim()?.takeIf(String::isNotEmpty)))
            }
            val complete = request.answerable && request.questions.all { q -> q.allowEmpty || answers()[q.questionId].orEmpty().isNotEmpty() }

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
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Spacing.l, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                // 読む・書く中身は、シートより明るい不透明な面に載せ、長いときはその中だけスクロール（AGENTS.md 2.5）
                Surface(
                    color = sheetFieldColor(),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                ) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.m, vertical = Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.l),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                            AgentAvatar(agent, label = null)
                            Text(
                                stringResource(R.string.question_title, agent?.name ?: request.agentId.orEmpty()),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                        if (!request.answerable) {
                            Text(stringResource(R.string.question_unsupported), style = bodyTextStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        request.questions.forEach { q ->
                            QuestionBlock(
                                question = q,
                                selected = selected[q.questionId].orEmpty(),
                                other = other[q.questionId].orEmpty(),
                                enabled = request.answerable && !busy,
                                onSelect = { values -> selected = selected + (q.questionId to values) },
                                onOther = { text -> other = other + (q.questionId to text) },
                            )
                        }
                    }
                }
                if (failed) Text(stringResource(R.string.question_failed), color = MaterialTheme.colorScheme.error)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                ) {
                    TextButton(enabled = !busy, onClick = { submit { repository.skip(request) } }) { Text(stringResource(R.string.question_skip)) }
                    TextButton(enabled = !busy, onClick = { repository.snooze(request.id) }) { Text(stringResource(R.string.question_later)) }
                    Button(enabled = !busy && complete, onClick = { submit { repository.answer(request, answers()) } }) {
                        Text(stringResource(R.string.question_submit))
                    }
                }
            }
        }
    }
}

/** 1 つの問い。見出し・問い・選択肢（1 つ選ぶならラジオ、複数ならチェック）・その他の自由入力。 */
@Composable
private fun QuestionBlock(
    question: QuestionItem,
    selected: List<String>,
    other: String,
    enabled: Boolean,
    onSelect: (List<String>) -> Unit,
    onOther: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (question.header.isNotBlank()) {
            Text(question.header, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(question.question, style = bodyTextStyle())
        question.options.forEach { option ->
            val isSelected = option.value in selected
            val rowModifier = if (question.multiSelect) {
                Modifier.toggleable(value = isSelected, enabled = enabled, role = Role.Checkbox) { checked ->
                    onSelect(if (checked) selected + option.value else selected - option.value)
                }
            } else {
                Modifier.selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) {
                    onSelect(listOf(option.value))
                    // 1 つ選ぶ問いでは、選択肢を選んだら自由入力は使わない
                    if (other.isNotEmpty()) onOther("")
                }
            }
            Row(
                rowModifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                if (question.multiSelect) Checkbox(checked = isSelected, onCheckedChange = null, enabled = enabled)
                else RadioButton(selected = isSelected, onClick = null, enabled = enabled)
                Column(Modifier.weight(1f)) {
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    option.description?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        OutlinedTextField(
            value = other,
            onValueChange = { text ->
                onOther(text)
                // 1 つ選ぶ問いでは、自由入力を始めたら選択肢を外す
                if (!question.multiSelect && text.isNotBlank() && selected.isNotEmpty()) onSelect(emptyList())
            },
            enabled = enabled,
            label = { Text(stringResource(R.string.question_other)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

