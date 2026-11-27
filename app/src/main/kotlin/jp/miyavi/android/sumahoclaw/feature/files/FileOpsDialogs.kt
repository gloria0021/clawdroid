package jp.miyavi.android.sumahoclaw.feature.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.FileOp
import jp.miyavi.android.sumahoclaw.core.data.NameError
import jp.miyavi.android.sumahoclaw.core.data.validateName
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing

/**
 * 作成・名前の変更・削除の確認（spec 005 US6、contracts/ui.md）。名前は検証を通るまで確定できない。
 * [siblings] は同じフォルダの読み込み済みの名前（同じ名前は先に断る。最後の判断はシェル）。
 */
@Composable
internal fun FileOpDialog(action: TreeAction, siblings: List<String>, losesEdits: Boolean, onDismiss: () -> Unit, onConfirm: (FileOp) -> Unit) {
    when (action) {
        is TreeAction.NewFile -> NameDialog(R.string.files_new_file, R.string.files_create, "", siblings, onDismiss) {
            onConfirm(FileOp.CreateFile(action.parent, it))
        }
        is TreeAction.NewFolder -> NameDialog(R.string.files_new_folder, R.string.files_create, "", siblings, onDismiss) {
            onConfirm(FileOp.CreateFolder(action.parent, it))
        }
        is TreeAction.Rename -> NameDialog(R.string.files_rename, R.string.action_save, action.entry.name, siblings - action.entry.name, onDismiss, losesEdits) {
            if (it == action.entry.name) onDismiss() else onConfirm(FileOp.Rename(action.entry.path, it))
        }
        is TreeAction.Upload -> Unit
        is TreeAction.Delete -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.files_delete_title, action.entry.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (action.entry.isDirectory) Text(stringResource(R.string.files_delete_folder_note))
                    if (losesEdits) Text(stringResource(R.string.files_discard_body))
                    Text(stringResource(R.string.files_delete_irreversible))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onConfirm(FileOp.Delete(action.entry.path, action.entry.isDirectory)) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.files_delete)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun NameDialog(
    title: Int,
    confirm: Int,
    initial: String,
    siblings: List<String>,
    onDismiss: () -> Unit,
    losesEdits: Boolean = false,
    onConfirm: (String) -> Unit,
) {
    // 名前の変更では、拡張子の前までを選んだ状態で始める
    var value by remember {
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length)))
    }
    val error = validateName(value.text, siblings)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (losesEdits) Text(stringResource(R.string.files_discard_body))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text(stringResource(R.string.files_name_label)) },
                isError = value.text.isNotEmpty() && error != null,
                supportingText = error?.takeIf { value.text.isNotEmpty() }?.let { e -> { Text(stringResource(e.message)) } },
                modifier = Modifier.focusRequester(focus),
            )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.text) }, enabled = error == null) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val NameError.message: Int
    get() = when (this) {
        NameError.EMPTY -> R.string.files_name_error_empty
        NameError.SLASH -> R.string.files_name_error_slash
        NameError.DOT -> R.string.files_name_error_dot
        NameError.CONTROL -> R.string.files_name_error_control
        NameError.TOO_LONG -> R.string.files_name_error_too_long
        NameError.EXISTS -> R.string.files_name_error_exists
    }
