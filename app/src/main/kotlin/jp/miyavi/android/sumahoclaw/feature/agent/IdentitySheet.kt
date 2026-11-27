package jp.miyavi.android.sumahoclaw.feature.agent

import android.icu.text.BreakIterator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.designsystem.AppBottomSheet
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.sheetFieldColor

/**
 * 名前と絵文字を変えるシート（spec 004 FR-013）。M3 の編集のシートの型: 見出しと説明 → 入力の面 → 下に右寄せの操作。
 * 絵文字は名前の左に小さな欄で並べ、1 文字（書記素 1 つ）だけを受け付ける。入力の面は不透明（AGENTS.md 2.5）。
 * 保存すると Gateway が openclaw.json の name・identity を書き換え、IDENTITY.md も書き直す（`agents.update`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdentitySheet(
    initialName: String,
    initialEmoji: String,
    saving: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var emoji by rememberSaveable { mutableStateOf(initialEmoji) }
    val changed = name.trim() != initialName || emoji.trim() != initialEmoji
    val canSave = name.isNotBlank() && changed && !saving
    AppBottomSheet(
        onDismissRequest = onDismiss,
        hazeState = hazeState,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.imePadding().navigationBarsPadding()) {
            // 見出しと説明（左右 24dp。M3 のシートの見出し）
            Column(Modifier.padding(horizontal = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.agent_identity_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.agent_identity_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 入力の面: 絵文字 ｜ 表示名 を 1 行に
            Surface(
                color = sheetFieldColor(),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.l, top = Spacing.l),
            ) {
                Row(
                    Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m, bottom = Spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = firstGrapheme(it) },
                        label = { Text(stringResource(R.string.agent_identity_emoji)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.width(Dimens.emojiField),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.agent_identity_name)) },
                        singleLine = true,
                        isError = name.isBlank(),
                        supportingText = if (name.isBlank()) ({ Text(stringResource(R.string.agent_identity_name_required)) }) else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(name, emoji) }),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // 操作は下に右寄せ（M3 のダイアログ・シートと同じ並び: 取り消し → 確定）
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onSave(name, emoji) }, enabled = canSave) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

/** 先頭の 1 文字（書記素 1 つ。肌の色や ZWJ でつないだ絵文字も 1 つとして扱う）。 */
private fun firstGrapheme(text: String): String {
    if (text.isEmpty()) return text
    // 打ち足したときは、新しく入れた方を残す
    val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
    val last = iterator.last()
    val start = iterator.previous()
    return if (start == BreakIterator.DONE) text else text.substring(start, last)
}
