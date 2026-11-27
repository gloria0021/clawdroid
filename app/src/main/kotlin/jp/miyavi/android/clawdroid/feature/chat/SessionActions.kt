package jp.miyavi.android.clawdroid.feature.chat

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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
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
import jp.miyavi.android.clawdroid.core.designsystem.GlassMenuDivider
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.codeTextStyle
import jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor

/** タイトルバーの ⋮ から開くもの。 */
enum class SessionAction { RENAME, DIFF, WORKTREE, FINISH }

/** 会話のタイトルバーのメニュー（名前を変更・ピン留め・ファイル・ターミナル・新規ワークツリー・変更量）。 */
@Composable
internal fun SessionMoreMenu(
    /** いまのセッション（新しいチャットでは null。ファイル・ターミナル以外は出さない）。 */
    session: SessionSummary?,
    onAction: (SessionAction) -> Unit,
    onTogglePin: () -> Unit,
    loadDiffSummary: suspend (SessionSummary) -> SessionDiff? = { null },
    /** デバッグ版だけの「デバッグ → サイドパネルヒント」（null なら節ごと出さない）。 */
    onDebugPanelHint: (() -> Unit)? = null,
    /** ファイル（開けないときは null）。 */
    onOpenFiles: (() -> Unit)? = null,
    onOpenTerminal: (() -> Unit)? = null,
    /** メニューを開く部品（既定は ⋮ のアイコン）。 */
    anchor: @Composable (onClick: () -> Unit) -> Unit = { onClick ->
        IconButton(onClick = onClick) { Icon(painterResource(R.drawable.ic_huge_more), stringResource(R.string.session_more_cd)) }
    },
) {
    var open by remember { mutableStateOf(false) }
    // メニューを開くたびに合計の +/− を取り直す（取れるまでは数を出さない）
    val summary by produceState<SessionDiff?>(null, open, session?.key) {
        value = null
        if (open && session?.cwd != null) value = loadDiffSummary(session)
    }
    Box {
        anchor { open = true }
        // メニューの右端をボタンの右端にそろえる（画面の右上のボタンから出るので）。M3 のメニューは起点の左端から
        // 右へ開こうとするため、ボタンの右下に幅 0 の起点を置き、入りきらない側として右揃えで開かせる（マスター指定 2026-11-26）
        Box(Modifier.align(androidx.compose.ui.Alignment.BottomEnd).requiredSize(androidx.compose.ui.unit.DpSize.Zero)) {
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // 並び（マスター指定 2026-11-23）: 名前を変更・ピン留め、区切って ファイル・ターミナル・並行開発・変更量
            // （別のフォルダで続けるは廃止）。新しいチャットではファイル・ターミナルだけ
            val files: @Composable () -> Unit = {
                onOpenFiles?.let { MenuRow(R.drawable.ic_huge_folder, R.string.menu_files) { open = false; it() } }
                onOpenTerminal?.let { MenuRow(R.drawable.ic_huge_terminal, R.string.menu_terminal) { open = false; it() } }
            }
            // デバッグ版だけの節（最後に区切って置く）
            val debug: @Composable () -> Unit = {
                onDebugPanelHint?.let { hint ->
                    jp.miyavi.android.clawdroid.core.designsystem.GlassMenuDivider()
                    Text(
                        stringResource(R.string.menu_debug_section),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    )
                    MenuRow(R.drawable.ic_huge_play, R.string.menu_debug_panel_hint) { open = false; hint() }
                }
            }
            if (session == null) {
                files()
                debug()
                return@GlassDropdownMenu
            }
            MenuRow(R.drawable.ic_huge_edit, R.string.session_menu_rename) { open = false; onAction(SessionAction.RENAME) }
            MenuRow(
                if (session.pinned) R.drawable.ic_huge_pin_off else R.drawable.ic_huge_pin,
                if (session.pinned) R.string.session_menu_unpin else R.string.session_menu_pin,
            ) { open = false; onTogglePin() }
            // ここから下は開発の操作（ファイル・ターミナル・並行開発・変更量）。区切りはここだけ（マスター指定 2026-11-23）
            GlassMenuDivider()
            files()
            // 並行開発は本体のセッションから始め、ワークツリーのセッションでは終えるだけ（入れ子にしない。マスター指定 2026-11-27）。
            // ワークツリーの中から始めると取り込み先が別のワークツリーのブランチになり、本体のフォルダでマージする前提が崩れるため
            if (session.worktreeBranch == null) {
                MenuRow(R.drawable.ic_huge_branch, R.string.session_menu_new_worktree) { open = false; onAction(SessionAction.WORKTREE) }
            } else if (!session.archived) {
                MenuRow(R.drawable.ic_huge_archive, R.string.session_menu_finish) { open = false; onAction(SessionAction.FINISH) }
            }
            // 変更量は合計の +/− を添えて（押すと詳細のシート）
            if (session.cwd != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.session_menu_diff)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_huge_diff), contentDescription = null) },
                    trailingIcon = summary?.takeIf { it.unavailable == null }?.let { diff ->
                        { StackedDiffCounts(diff.additions, diff.deletions, MaterialTheme.typography.labelMedium) }
                    },
                    onClick = { open = false; onAction(SessionAction.DIFF) },
                )
            }
            debug()
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
 * 「並行開発を終える」のシート（マスター指定 2026-11-27）。既定のブランチとの差分を見て、終え方を選んでもらう。
 * - 差分がある: 「マージを依頼して終える」（エージェントに依頼を送り、済んだら終える）か「ボツにして終える」
 * - 差分が無い（取り込み済み・変更なし）: 「並行開発を終える」だけ
 * ボツでも作業内容は Gateway がスナップショットに残す（30 日）。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FinishWorktreeSheet(
    session: SessionSummary,
    hazeState: HazeState,
    loadDiffSummary: suspend (SessionSummary) -> SessionDiff?,
    onDismiss: () -> Unit,
    onMerge: (request: String) -> Unit,
    onDiscard: () -> Unit,
) {
    val diff by produceState<Result<SessionDiff?>?>(null, session.key) { value = Result.success(loadDiffSummary(session)) }
    val loaded = diff?.getOrNull()
    val base = loaded?.baseRef ?: "main"
    // 差分を確かめられなかったときは、取り込み済みとみなさず両方を出す
    val pending = diff != null && (loaded == null || loaded.unavailable != null || loaded.additions > 0 || loaded.deletions > 0)
    val branch = session.worktreeBranch.orEmpty()
    val request = stringResource(R.string.worktree_merge_request, branch, session.worktreeRoot ?: session.cwd.orEmpty(), base)
    AppBottomSheet(onDismissRequest = onDismiss, hazeState = hazeState, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(vertical = Spacing.s).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.worktree_finish_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.xl),
            )
            when {
                diff == null -> Box(Modifier.fillMaxWidth().padding(Spacing.l), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.LoadingIndicator()
                }
                pending -> {
                    if (loaded != null && loaded.unavailable == null) {
                        Row(
                            Modifier.padding(horizontal = Spacing.xl),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.worktree_finish_pending, base),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            DiffCounts(loaded.additions, loaded.deletions, MaterialTheme.typography.labelLarge)
                        }
                    }
                    FinishChoice(
                        icon = R.drawable.ic_huge_branch,
                        title = stringResource(R.string.worktree_finish_merge),
                        description = stringResource(R.string.worktree_finish_merge_desc, base),
                        onClick = { onMerge(request) },
                    )
                    FinishChoice(
                        icon = R.drawable.ic_huge_delete,
                        title = stringResource(R.string.worktree_finish_confirm),
                        description = stringResource(R.string.worktree_finish_desc),
                        onClick = onDiscard,
                    )
                }
                else -> FinishChoice(
                    icon = R.drawable.ic_huge_check_circle,
                    title = stringResource(R.string.worktree_finish_plain),
                    description = stringResource(R.string.worktree_finish_plain_desc, base),
                    onClick = onDiscard,
                )
            }
        }
    }
}

/**
 * 終え方の 1 つ（押せる面。アイコン・見出し・説明）。シートの地と見分けられるよう、明るい不透明な面
 * （`sheetActionColor()`）を角丸で敷く（マスター指定 2026-11-27）。
 */
@Composable
private fun FinishChoice(icon: Int, title: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = jp.miyavi.android.clawdroid.core.designsystem.sheetActionColor(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs),
    ) {
        androidx.compose.material3.ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(description) },
            leadingContent = { Icon(painterResource(icon), contentDescription = null) },
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
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

/**
 * メニュー用の変更量。追加と削除を 2 行に重ね、幅は 4 桁分に固定する（数が変わってもメニューの幅が変わらないように。
 * マスター指定 2026-11-26）。5 桁以上のときだけ広がる。右揃え。
 */
@Composable
private fun StackedDiffCounts(additions: Int, deletions: Int, style: androidx.compose.ui.text.TextStyle) {
    val colors = MaterialTheme.colorScheme
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // いちばん幅を取る 4 桁（8 が最も広い字形が多い）と符号で測る
    val minWidth = remember(style, density) {
        with(density) {
            maxOf(measurer.measure("+$FOUR_DIGIT_SAMPLE", style).size.width, measurer.measure("−$FOUR_DIGIT_SAMPLE", style).size.width).toDp()
        }
    }
    androidx.compose.foundation.layout.Column(
        Modifier.widthIn(min = minWidth),
        horizontalAlignment = androidx.compose.ui.Alignment.End,
    ) {
        jp.miyavi.android.clawdroid.core.designsystem.RollingCounter(additions, style, colors.tertiary, prefix = "+")
        jp.miyavi.android.clawdroid.core.designsystem.RollingCounter(deletions, style, colors.error, prefix = "−")
    }
}

/** 変更量の幅を決める見本の 4 桁。 */
private const val FOUR_DIGIT_SAMPLE = "8888"

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
