package jp.miyavi.android.clawdroid.feature.chat

import androidx.compose.material3.HorizontalDivider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.DiffFile
import jp.miyavi.android.clawdroid.core.data.SessionDiff
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.designsystem.AppBottomSheet
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.codeTextStyle
import jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor

/** タイトルバーの ⋮ から開くもの。 */
enum class SessionAction { RENAME, DIFF, MOVE }

/** 会話のタイトルバーの ⋮（名前を変更・ピン留め・変更量・別のフォルダで続ける）。 */
@Composable
internal fun SessionMoreMenu(
    session: SessionSummary,
    onAction: (SessionAction) -> Unit,
    onTogglePin: () -> Unit,
    loadDiffSummary: suspend (SessionSummary) -> SessionDiff? = { null },
) {
    var open by remember { mutableStateOf(false) }
    // メニューを開くたびに合計の +/− を取り直す（取れるまでは数を出さない）
    val summary by produceState<SessionDiff?>(null, open, session.key) {
        value = null
        if (open && session.cwd != null) value = loadDiffSummary(session)
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(painterResource(R.drawable.ic_huge_more), stringResource(R.string.session_more_cd)) }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuRow(R.drawable.ic_huge_edit, R.string.session_menu_rename) { open = false; onAction(SessionAction.RENAME) }
            MenuRow(
                if (session.pinned) R.drawable.ic_huge_pin_off else R.drawable.ic_huge_pin,
                if (session.pinned) R.string.session_menu_unpin else R.string.session_menu_pin,
            ) { open = false; onTogglePin() }
            MenuRow(R.drawable.ic_huge_folder_move, R.string.session_menu_move) { open = false; onAction(SessionAction.MOVE) }
            // 変更量は区切りの下に、合計の +/− を添えて（押すと詳細のシート）
            if (session.cwd != null) {
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.session_menu_diff)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_huge_diff), contentDescription = null) },
                    trailingIcon = summary?.takeIf { it.unavailable == null }?.let { diff ->
                        { DiffCounts(diff.additions, diff.deletions, MaterialTheme.typography.labelMedium) }
                    },
                    onClick = { open = false; onAction(SessionAction.DIFF) },
                )
            }
        }
    }
}

@Composable
private fun MenuRow(icon: Int, label: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
internal fun SessionRenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_rename_title)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * 変更量（Git の差分）のシート。上にブランチと合計の +/−、下にファイルごとの +/−。ファイルを押すと差分の中身を開く。
 * 追加は tertiary、削除は error（意味のある色分け。AGENTS.md 2.2）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionDiffSheet(session: SessionSummary, hazeState: HazeState, load: suspend (SessionSummary) -> SessionDiff?, onDismiss: () -> Unit) {
    var retry by remember { mutableStateOf(0) }
    val diff by produceState<Result<SessionDiff>?>(null, session.key, retry) {
        value = null
        value = load(session)?.let { Result.success(it) } ?: Result.failure(IllegalStateException())
    }
    val colors = MaterialTheme.colorScheme
    AppBottomSheet(onDismissRequest = onDismiss, hazeState = hazeState, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val loaded = diff?.getOrNull()
        Column(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.session_diff_title), style = MaterialTheme.typography.titleMedium)
            if (loaded != null && loaded.unavailable == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    loaded.branch?.let {
                        Text(
                            listOfNotNull(it, loaded.baseRef?.let { base -> "← $base" }).joinToString(" "),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    DiffCounts(loaded.additions, loaded.deletions, MaterialTheme.typography.labelLarge)
                }
            }
        }
        Surface(
            color = sheetFieldColor(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            when {
                diff == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(Spacing.l))
                loaded == null -> Column(Modifier.padding(Spacing.l)) {
                    Text(stringResource(R.string.session_diff_failed), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.action_retry)) }
                }
                loaded.unavailable != null -> Text(
                    stringResource(if (loaded.unavailable == "not_git") R.string.session_diff_not_git else R.string.session_diff_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.l),
                )
                loaded.files.isEmpty() -> Text(
                    stringResource(R.string.session_diff_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.l),
                )
                else -> LazyColumn(Modifier.heightIn(max = Dimens.folderBrowserMaxHeight * 2).padding(vertical = Spacing.xs)) {
                    items(loaded.files, key = { it.path }) { DiffFileRow(it) }
                }
            }
        }
    }
}

@Composable
private fun DiffFileRow(file: DiffFile) {
    var open by rememberSaveable(file.path) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (file.patch != null) Modifier.clickable { open = !open } else Modifier)
                .heightIn(min = Dimens.minTouchTarget)
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            // 状態の頭文字（追加・変更・削除・名前の変更）
            Text(
                when (file.status) { "added" -> "A"; "deleted" -> "D"; "renamed" -> "R"; else -> "M" },
                style = codeTextStyle(),
                color = when (file.status) { "added" -> colors.tertiary; "deleted" -> colors.error; else -> colors.onSurfaceVariant },
            )
            Column(Modifier.weight(1f)) {
                Text(file.path.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val dir = file.path.substringBeforeLast('/', "")
                if (dir.isNotEmpty()) Text(dir, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (file.binary) AnnotatedString(stringResource(R.string.session_diff_binary)) else counts(file.additions, file.deletions), style = MaterialTheme.typography.labelMedium)
        }
        AnimatedVisibility(open) {
            file.patch?.let { patch ->
                Text(
                    colorizePatch(patch, colors.tertiary, colors.error, colors.onSurfaceVariant),
                    style = codeTextStyle(),
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
            }
        }
    }
}

/** 合計の「+12 −3」。数は桁ごとに回して数え上げる（追加は tertiary、削除は error）。 */
@Composable
private fun DiffCounts(additions: Int, deletions: Int, style: androidx.compose.ui.text.TextStyle) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        jp.miyavi.android.clawdroid.core.designsystem.RollingCounter(additions, style, colors.tertiary, prefix = "+")
        jp.miyavi.android.clawdroid.core.designsystem.RollingCounter(deletions, style, colors.error, prefix = "−")
    }
}

/** 「+12 −3」（追加は tertiary、削除は error）。 */
@Composable
private fun counts(additions: Int, deletions: Int): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return buildAnnotatedString {
        withStyle(SpanStyle(color = colors.tertiary)) { append("+$additions") }
        append(" ")
        withStyle(SpanStyle(color = colors.error)) { append("−$deletions") }
    }
}

private fun colorizePatch(patch: String, add: androidx.compose.ui.graphics.Color, del: androidx.compose.ui.graphics.Color, meta: androidx.compose.ui.graphics.Color) =
    buildAnnotatedString {
        patch.lineSequence().forEachIndexed { index, line ->
            if (index > 0) append('\n')
            val color = when {
                line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@") -> meta
                line.startsWith("+") -> add
                line.startsWith("-") -> del
                else -> null
            }
            if (color != null) withStyle(SpanStyle(color = color)) { append(line) } else append(line)
        }
    }
