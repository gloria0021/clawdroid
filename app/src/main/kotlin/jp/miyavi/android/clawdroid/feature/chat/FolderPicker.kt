package jp.miyavi.android.clawdroid.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.DirListing
import jp.miyavi.android.clawdroid.core.designsystem.AppBottomSheet
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor

/**
 * 新しいセッションの作業フォルダを選ぶシート。既定（エージェントのワークスペース）と、直近 5 つのフォルダ。
 * 「フォルダを選ぶ」で、Gateway のマシンのフォルダを辿って選ぶ（エクスプローラーのように）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderPickerSheet(
    hazeState: HazeState,
    current: String?,
    load: suspend () -> List<String>,
    listDir: suspend (String?) -> DirListing?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    title: Int = R.string.new_session_folder_title,
    /** 「既定の作業フォルダ」を出すか（別のフォルダで続けるときは出さない）。 */
    allowDefault: Boolean = true,
) {
    val candidates by produceState<List<String>?>(null) { value = load() }
    // 辿っている場所（null なら候補の一覧を出している）
    var browsing by rememberSaveable { mutableStateOf(false) }
    var browsePath by rememberSaveable { mutableStateOf(current ?: candidates?.firstOrNull()?.substringBeforeLast('/')) }
    BackHandler(enabled = browsing) { browsing = false }
    AppBottomSheet(
        onDismissRequest = onDismiss,
        hazeState = hazeState,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        if (browsing) {
            FolderBrowser(
                start = browsePath,
                listDir = listDir,
                onOpen = { browsePath = it },
                onBack = { browsing = false },
                onUse = onPick,
            )
        } else {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.s),
            )
            Surface(
                color = sheetFieldColor(),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = Spacing.s)) {
                    if (allowDefault) FolderRow(
                        title = stringResource(R.string.new_session_folder_default),
                        subtitle = stringResource(R.string.new_session_folder_default_desc),
                        selected = current == null,
                        icon = R.drawable.ic_huge_home,
                    ) { onPick(null) }
                    when (val list = candidates) {
                        null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m))
                        else -> list.forEach { path ->
                            FolderRow(
                                title = path.substringAfterLast('/').ifEmpty { path },
                                subtitle = path,
                                selected = path == current,
                                icon = R.drawable.ic_huge_folder,
                            ) { onPick(path) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
                    FolderRow(
                        title = stringResource(R.string.new_session_folder_browse),
                        subtitle = stringResource(R.string.new_session_folder_browse_desc),
                        selected = false,
                        icon = R.drawable.ic_huge_search,
                        trailing = R.drawable.ic_huge_chevron_right,
                    ) {
                        if (browsePath == null) browsePath = current ?: candidates?.firstOrNull()?.substringBeforeLast('/')
                        browsing = true
                    }
                }
            }
        }
    }
}

/**
 * Gateway のマシンのフォルダを辿る。上にいまの場所と、上へ・ホーム、下に中のフォルダ（隠しフォルダは後ろに薄く）。
 * 「このフォルダを使う」で決める。
 */
@Composable
private fun FolderBrowser(
    start: String?,
    listDir: suspend (String?) -> DirListing?,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onUse: (String) -> Unit,
) {
    var retry by rememberSaveable { mutableStateOf(0) }
    val listing by produceState<Result<DirListing>?>(null, start, retry) {
        value = null
        value = listDir(start)?.let { Result.success(it) } ?: Result.failure(IllegalStateException())
    }
    val loaded = listing?.getOrNull()
    // 開いた場所を覚えておく（戻ってきたときに同じ場所から）
    LaunchedEffect(loaded?.path) { loaded?.path?.let { if (it != start) onOpen(it) } }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = Spacing.xs, end = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd)) }
            Column(Modifier.weight(1f)) {
                Text(
                    loaded?.path?.substringAfterLast('/')?.ifEmpty { "/" } ?: stringResource(R.string.new_session_folder_browse),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                loaded?.path?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Button(onClick = { loaded?.path?.let(onUse) }, enabled = loaded != null) { Text(stringResource(R.string.new_session_folder_use_this)) }
        }
        Surface(
            color = sheetFieldColor(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            Box(Modifier.heightIn(min = Dimens.emptyStateIcon * 2, max = Dimens.folderBrowserMaxHeight)) {
                when {
                    listing == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(Spacing.l))
                    loaded == null -> Column(Modifier.padding(Spacing.l)) {
                        Text(stringResource(R.string.new_session_folder_browse_failed), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.action_retry)) }
                    }
                    else -> LazyColumn(Modifier.padding(vertical = Spacing.xs)) {
                        // 上へ・ホームは一覧の先頭に（いつも同じ所にあるので迷わない）
                        loaded.parent?.let { parent ->
                            item(key = "up") { BrowseRow(R.drawable.ic_huge_arrow_up, "..", dim = false) { onOpen(parent) } }
                        }
                        loaded.home?.takeIf { it != loaded.path }?.let { home ->
                            item(key = "home") { BrowseRow(R.drawable.ic_huge_home, stringResource(R.string.new_session_folder_home), dim = false) { onOpen(home) } }
                        }
                        if (loaded.entries.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    stringResource(R.string.new_session_folder_no_subfolders),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(Spacing.l),
                                )
                            }
                        }
                        items(loaded.entries, key = { it.path }) { entry ->
                            BrowseRow(R.drawable.ic_huge_folder, entry.name, dim = entry.hidden) { onOpen(entry.path) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseRow(icon: Int, name: String, dim: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val color = if (dim) colors.onSurfaceVariant.copy(alpha = HIDDEN_ALPHA) else colors.onSurface
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(icon), contentDescription = null, tint = if (dim) color else colors.onSurfaceVariant) },
        headlineContent = { Text(name, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Icon(painterResource(R.drawable.ic_huge_chevron_right), contentDescription = null, tint = colors.onSurfaceVariant) },
        modifier = Modifier.heightIn(min = Dimens.minTouchTarget).clickable(onClick = onClick),
    )
}

@Composable
private fun FolderRow(title: String, subtitle: String, selected: Boolean, icon: Int, trailing: Int? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(icon), contentDescription = null, tint = if (selected) colors.primary else colors.onSurfaceVariant) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (selected) colors.primary else colors.onSurface) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = when {
            selected -> ({ Icon(painterResource(R.drawable.ic_huge_check), contentDescription = null, tint = colors.primary) })
            trailing != null -> ({ Icon(painterResource(trailing), contentDescription = null, tint = colors.onSurfaceVariant) })
            else -> null
        },
        modifier = Modifier.heightIn(min = Dimens.minTouchTarget).clickable(onClick = onClick),
    )
}

/** 隠しフォルダの薄さ（AGENTS.md 2.2: 無効・折りたたみは 0.6 まで）。 */
private const val HIDDEN_ALPHA = 0.6f
