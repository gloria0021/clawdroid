package jp.miyavi.android.clawdroid.feature.chat.sidebar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.SessionListState
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.motionItem
import kotlinx.coroutines.launch

/**
 * サイドバー。上部にエージェントのタブ、その下にエージェントごとのセッション一覧（Pager の 1 ページ＝1 エージェント）。
 * タブの選択面と一覧が同じ位置の値（PagerState）に追従するので、タップでもスワイプでも同じ動きになる（research R7）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidebarContent(
    agents: List<Agent>,
    pagerState: PagerState,
    sessionsByAgent: Map<String, SessionListState>,
    selectedSessionKey: String?,
    timeLabel: (Long) -> String,
    onSelectSession: (SessionSummary) -> Unit,
    onRename: (SessionSummary, String) -> Unit,
    onArchive: (SessionSummary, Boolean) -> Unit,
    onOpenAutomations: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.statusBars)) {
        if (agents.isNotEmpty()) {
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage.coerceIn(0, agents.lastIndex),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                edgePadding = Spacing.l,
                divider = {},
                indicator = {
                    // 下線の代わりに、選んだタブの文字の後ろへピル（secondaryContainer）を敷き、ページの位置に合わせて滑らせる
                    Box(
                        Modifier
                            .tabIndicatorOffset(pagerState.currentPage.coerceIn(0, agents.lastIndex), matchContentSize = false)
                            .fillMaxHeight()
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = Spacing.xs)
                            .height(Dimens.tabPillHeight)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    )
                },
            ) {
                agents.forEachIndexed { index, agent ->
                    val selected = pagerState.currentPage == index
                    Tab(
                        selected = selected,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                listOfNotNull(agent.emoji, agent.name).joinToString(" "),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            key = { agents.getOrNull(it)?.id ?: it },
            beyondViewportPageCount = 1,
        ) { page ->
            val agent = agents.getOrNull(page) ?: return@HorizontalPager
            SessionList(
                state = sessionsByAgent[agent.id] ?: SessionListState(),
                selectedSessionKey = selectedSessionKey,
                timeLabel = timeLabel,
                onSelect = onSelectSession,
                onRename = onRename,
                onArchive = onArchive,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            SidebarAction(R.drawable.ic_huge_automation, R.string.sidebar_automations, onOpenAutomations)
            SidebarAction(R.drawable.ic_huge_terminal, R.string.sidebar_terminal, onOpenTerminal)
            SidebarAction(R.drawable.ic_huge_settings, R.string.sidebar_settings, onOpenSettings)
        }
    }
}

@Composable
private fun SidebarAction(icon: Int, label: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label), style = MaterialTheme.typography.titleSmall) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.combinedClickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SessionList(
    state: SessionListState,
    selectedSessionKey: String?,
    timeLabel: (Long) -> String,
    onSelect: (SessionSummary) -> Unit,
    onRename: (SessionSummary, String) -> Unit,
    onArchive: (SessionSummary, Boolean) -> Unit,
) {
    when {
        state.loading && state.sessions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        state.sessions.isEmpty() -> Column(
            Modifier.fillMaxSize().padding(Spacing.l),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.sidebar_empty_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.sidebar_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(state.sessions, key = { it.key }) { session ->
                SessionItem(
                    session = session,
                    selected = session.key == selectedSessionKey,
                    timeLabel = session.updatedAt?.let(timeLabel),
                    onClick = { onSelect(session) },
                    onRename = { onRename(session, it) },
                    onArchive = { onArchive(session, !session.archived) },
                    modifier = motionItem(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(
    session: SessionSummary,
    selected: Boolean,
    timeLabel: String?,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    val title = session.title ?: stringResource(R.string.session_untitled)
    val menuLabel = stringResource(R.string.session_menu_open_label)
    Box(modifier) {
        ListItem(
            headlineContent = {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = timeLabel?.let { label ->
                { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            trailingContent = { SessionMarks(session) },
            colors = ListItemDefaults.colors(
                containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                headlineColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true },
                onLongClickLabel = menuLabel,
            ),
        )
        GlassDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_menu_rename)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_huge_edit), contentDescription = null) },
                onClick = { menuOpen = false; renaming = true },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (session.archived) R.string.session_menu_unarchive else R.string.session_menu_archive)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_huge_archive), contentDescription = null) },
                onClick = { menuOpen = false; onArchive() },
            )
        }
    }
    if (renaming) {
        RenameDialog(initial = session.title.orEmpty(), onDismiss = { renaming = false }, onConfirm = { renaming = false; onRename(it) })
    }
}

/** 実行中・未読の目印（実行中 = tertiary、未読 = primary。AGENTS.md 2.2）。 */
@Composable
private fun SessionMarks(session: SessionSummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        if (session.running) {
            val cd = stringResource(R.string.session_running_cd)
            Box(Modifier.size(Dimens.unreadDot).background(MaterialTheme.colorScheme.tertiary, CircleShape).semantics { contentDescription = cd })
        }
        if (session.unread) {
            val cd = stringResource(R.string.session_unread_cd)
            Box(Modifier.size(Dimens.unreadDot).background(MaterialTheme.colorScheme.primary, CircleShape).semantics { contentDescription = cd })
        }
        if (!session.running && !session.unread) Spacer(Modifier.size(Dimens.unreadDot))
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_rename_title)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
