package jp.miyavi.android.clawdroid.feature.chat.sidebar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.SessionDiff
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.bodyContainerColor

/**
 * ワークツリーのセッションを消す前の確認（マスター指定 2026-11-24）。何が起きるかを説明して、選んでもらう。
 * Gateway の扱い（docs/concepts/managed-worktrees.md・実装で確認）:
 * - 削除: 未コミットの変更をスナップショットに保存してから、ワークツリーのフォルダを片付ける。ブランチはリポジトリに残る
 * - アーカイブ: 会話とワークツリーの結び付きを残す。フォルダは裏で片付け、戻すと元に戻す
 * 操作が 3 つあるので、M3 のダイアログの作法どおり縦に積む（AGENTS.md 2.7: 確認はダイアログ）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorktreeDeleteDialog(
    session: SessionSummary,
    title: String,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val loader = LocalDiffLoader.current
    // 未コミットの変更とブランチの先行を読む（読めなければ書かない）
    val diff by produceState<DiffState>(DiffState.Loading, session.key) {
        value = runCatching { loader(session) }.getOrNull()?.let(DiffState::Ready) ?: DiffState.Unknown
    }
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier = Modifier.widthIn(max = Dimens.dialogMaxWidth),
        ) {
            Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                Text(stringResource(R.string.worktree_delete_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.worktree_delete_body, title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 何が起きるか（本文の下地の面に、項目ごとにアイコンと 2 段）
                Surface(color = bodyContainerColor(), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        Fact(
                            icon = R.drawable.ic_huge_branch,
                            headline = session.worktreeBranch.orEmpty(),
                            monospace = true,
                            supporting = aheadText(diff) ?: stringResource(R.string.worktree_delete_branch_kept),
                        )
                        Fact(
                            icon = R.drawable.ic_huge_diff,
                            headline = changesText(diff),
                            supporting = stringResource(R.string.worktree_delete_snapshot),
                        )
                    }
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(stringResource(R.string.worktree_delete_confirm))
                    }
                    // アーカイブ済みのセッションでは出さない（もうアーカイブしてある）
                    if (!session.archived) {
                        TextButton(onClick = onArchive) { Text(stringResource(R.string.worktree_delete_archive)) }
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
    }
}

private sealed interface DiffState {
    data object Loading : DiffState
    data object Unknown : DiffState
    data class Ready(val diff: SessionDiff) : DiffState
}

@Composable
private fun changesText(state: DiffState): String = when (state) {
    DiffState.Loading -> stringResource(R.string.worktree_delete_changes_loading)
    DiffState.Unknown -> stringResource(R.string.worktree_delete_changes_unknown)
    is DiffState.Ready -> {
        val d = state.diff
        if (d.unavailable != null) stringResource(R.string.worktree_delete_changes_unknown)
        else if (d.files.isEmpty() && d.additions == 0 && d.deletions == 0) stringResource(R.string.worktree_delete_changes_none)
        else stringResource(R.string.worktree_delete_changes, d.additions, d.deletions, d.files.size)
    }
}

/** ブランチがまだ取り込まれていないコミットを持っていれば、その件数（マージや削除は Git で）。 */
@Composable
private fun aheadText(state: DiffState): String? {
    val ahead = (state as? DiffState.Ready)?.diff?.aheadCount?.takeIf { it > 0 } ?: return null
    return stringResource(R.string.worktree_delete_branch_ahead, ahead)
}

@Composable
private fun Fact(icon: Int, headline: String, supporting: String, monospace: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs / 2).size(Dimens.smallIcon),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs / 2)) {
            Text(
                headline,
                style = if (monospace) MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
