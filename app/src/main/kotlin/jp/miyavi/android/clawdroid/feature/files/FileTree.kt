package jp.miyavi.android.clawdroid.feature.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.FileEntry
import jp.miyavi.android.clawdroid.core.data.FileRoot
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.accentContainer

/** ツリーの行から頼む操作（spec 005 US6）。 */
internal sealed interface TreeAction {
    data class NewFile(val parent: String) : TreeAction
    data class NewFolder(val parent: String) : TreeAction
    data class Rename(val entry: FileEntry) : TreeAction
    data class Delete(val entry: FileEntry) : TreeAction
    /** [parent] に端末のファイルをアップロードする（複数を選べる）。 */
    data class Upload(val parent: String) : TreeAction
}

/**
 * ファイルのツリー（spec 005 US4）。フォルダはその場で開閉し、開いているファイルの行は選んだ行の面（会話のサイドパネルと同じ）。
 * 行を長押しすると、作成・名前の変更・削除のメニュー（[canModify] のときだけ）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileTree(
    state: FilesUiState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onSelectRoot: (FileRoot) -> Unit,
    onOpen: (FileEntry) -> Unit,
    onDownload: (FileEntry) -> Unit,
    onAction: (TreeAction) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(state.tree) { state.rows }
    val motion = MaterialTheme.motionScheme
    // 行の 2 行目の大きさと更新日時。書式は一度だけ作り、「n 分前」は分が変わるたびに進める
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val metaFormat = remember(context, locale) { FileMetaFormat(context, locale) }
    val now by rememberMinuteClock()
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        item(key = "header", contentType = "header") {
            if (state.sessionRoot != null) RootSwitcher(state, onSelectRoot)
        }

        items(rows, key = { it.key }, contentType = { it::class }) { row ->
            val itemModifier = Modifier.animateItem(
                fadeInSpec = motion.defaultEffectsSpec(),
                placementSpec = motion.defaultSpatialSpec(),
                fadeOutSpec = motion.fastEffectsSpec(),
            )
            when (row) {
                is TreeRow.Item -> TreeItem(
                    row = row,
                    meta = remember(row.entry, metaFormat, now) { metaFormat.format(row.entry, now) },
                    selected = !row.entry.isDirectory && row.entry.path == state.opened?.path,
                    busy = state.pendingOp == row.entry.path,
                    canModify = state.canModify,
                    onClick = { onOpen(row.entry) },
                    onDownload = { onDownload(row.entry) },
                    onAction = onAction,
                    modifier = itemModifier,
                )
                is TreeRow.Loading -> TreeNote(row.depth, itemModifier) {
                    CircularProgressIndicator(Modifier.size(Dimens.runningIndicator), strokeWidth = Dimens.runningIndicatorStroke)
                }
                is TreeRow.Failed -> TreeNote(row.depth, itemModifier) {
                    Text(stringResource(R.string.files_load_failed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                is TreeRow.Empty -> TreeNote(row.depth, itemModifier) {
                    Text(stringResource(R.string.files_empty), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is TreeRow.Truncated -> TreeNote(row.depth, itemModifier) {
                    Text(stringResource(R.string.files_truncated), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // 一覧の下の空白を長押しすると、最上位に作る（フォルダが空でも作れる。マスター指定 2026-11-24）
        if (state.canModify) item(key = "blank", contentType = "blank") { BlankArea(onAction) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.BlankArea(onAction: (TreeAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .fillParentMaxHeight(BLANK_AREA_FRACTION)
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = {},
                onLongClick = { menu = true },
                onLongClickLabel = stringResource(R.string.files_menu_open_label),
            ),
    ) {
        GlassDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            MenuRow(R.drawable.ic_huge_file_add, R.string.files_new_file) { menu = false; onAction(TreeAction.NewFile("")) }
            MenuRow(R.drawable.ic_huge_folder_add, R.string.files_new_folder) { menu = false; onAction(TreeAction.NewFolder("")) }
            MenuRow(R.drawable.ic_huge_upload, R.string.files_upload) { menu = false; onAction(TreeAction.Upload("")) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeItem(
    row: TreeRow.Item,
    /** 名前の下に出す大きさと更新日時（無ければ 1 行）。 */
    meta: String?,
    selected: Boolean,
    busy: Boolean,
    canModify: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onAction: (TreeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = row.entry
    var menu by remember { mutableStateOf(false) }
    val accent = accentContainer()
    val colors = MaterialTheme.colorScheme
    val hasMenu = canModify || !entry.isDirectory
    Box(modifier.fillMaxWidth().padding(horizontal = Spacing.m)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .clip(CircleShape)
                .then(if (selected) Modifier.background(accent.container) else Modifier)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (hasMenu) ({ menu = true }) else null,
                    onLongClickLabel = if (hasMenu) stringResource(R.string.files_menu_open_label) else null,
                )
                .padding(start = Spacing.m + Spacing.l * indentLevel(row.depth), end = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            // フォルダの開閉の矢印（開くと下向き）。ファイルは同じ幅をあけて名前をそろえる
            if (entry.isDirectory) {
                Icon(
                    painterResource(R.drawable.ic_huge_chevron_right),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.smallIcon).rotate(if (row.expanded) EXPANDED_ROTATION else 0f),
                )
            } else {
                Spacer(Modifier.width(Dimens.smallIcon))
            }
            Icon(
                painterResource(if (entry.isDirectory) R.drawable.ic_huge_folder else R.drawable.ic_huge_file),
                contentDescription = null,
                tint = if (entry.isDirectory) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(Dimens.smallIcon),
            )
            val nameColor = if (selected) accent.content else colors.onSurface
            val metaColor = if (selected) accent.content else colors.onSurfaceVariant
            // 1 行目に名前、2 行目に大きさと更新日時（名前を潰さない）
            Column(Modifier.weight(1f).padding(vertical = Spacing.xs)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = nameColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta != null) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = metaColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (busy) CircularProgressIndicator(Modifier.size(Dimens.runningIndicator), strokeWidth = Dimens.runningIndicatorStroke, color = colors.tertiary)
        }
        GlassDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            // フォルダはその中に、ファイルは同じフォルダに作る
            if (canModify) {
                val parent = if (entry.isDirectory) entry.path else entry.path.substringBeforeLast('/', "")
                MenuRow(R.drawable.ic_huge_file_add, R.string.files_new_file) { menu = false; onAction(TreeAction.NewFile(parent)) }
                MenuRow(R.drawable.ic_huge_folder_add, R.string.files_new_folder) { menu = false; onAction(TreeAction.NewFolder(parent)) }
                MenuRow(R.drawable.ic_huge_upload, R.string.files_upload) { menu = false; onAction(TreeAction.Upload(parent)) }
            }
            if (!entry.isDirectory) MenuRow(R.drawable.ic_huge_download, R.string.files_download_cd) { menu = false; onDownload() }
            if (canModify) {
                MenuRow(R.drawable.ic_huge_edit, R.string.files_rename) { menu = false; onAction(TreeAction.Rename(entry)) }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.files_delete), color = colors.error) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_huge_delete), contentDescription = null, tint = colors.error) },
                    onClick = { menu = false; onAction(TreeAction.Delete(entry)) },
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

/** 読み込み中・失敗・空などの小さな行（字下げはその中身の深さ）。 */
@Composable
private fun TreeNote(depth: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .padding(start = Spacing.xl + Spacing.l * indentLevel(depth) + Dimens.smallIcon, end = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) { content() }
}

/** 作業フォルダ / ワークスペースの切り替え（002 FR-002）。 */
@Composable
private fun RootSwitcher(state: FilesUiState, onSelect: (FileRoot) -> Unit) {
    val roots = listOfNotNull(state.sessionRoot, state.workspaceRoot)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        roots.forEachIndexed { index, root ->
            SegmentedButton(
                selected = state.root == root,
                onClick = { onSelect(root) },
                shape = SegmentedButtonDefaults.itemShape(index, roots.size),
            ) {
                Text(stringResource(if (root is FileRoot.Session) R.string.files_root_session else R.string.files_root_workspace))
            }
        }
    }
}

/** 読み込み中・失敗の表示（開いたファイルと、ほかの画面で共通）。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LoadingOrFailed(failed: Boolean, message: Int, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!failed) {
            LoadingIndicator()
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(Spacing.l)) {
                Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

private const val EXPANDED_ROTATION = 90f
/** 一覧の下に残す、長押しできる空白（表示の高さに対する割合）。 */
private const val BLANK_AREA_FRACTION = 0.5f

