package jp.miyavi.android.clawdroid.feature.chat.sidebar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.SessionListState
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.clawdroid.core.designsystem.GlassPill
import jp.miyavi.android.clawdroid.core.designsystem.PillTabs
import jp.miyavi.android.clawdroid.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.motionItem
import jp.miyavi.android.clawdroid.feature.chat.AgentAvatar
import kotlinx.coroutines.launch

/**
 * 全画面のサイドパネル（Gemini 型）。上から 新しいセッション・自動化・セッション履歴。
 * 下部にエージェントの数だけピル型のタブが浮き、切り替えるとセッション履歴が切り替わる（FR-010・011）。
 * 履歴は Pager の 1 ページ＝1 エージェントで、タブの選択面とページが同じ値（PagerState）に追従する。
 * 横スワイプはパネルを閉じる操作なので、Pager は指では送らない（タブでだけ切り替える）。
 */
@Composable
fun SidePanel(
    agents: List<Agent>,
    pagerState: PagerState,
    sessionsByAgent: Map<String, SessionListState>,
    selectedSessionKey: String?,
    timeLabel: (Long) -> String,
    onClose: () -> Unit,
    onNewSession: () -> Unit,
    onSelectSession: (SessionSummary) -> Unit,
    onRename: (SessionSummary, String) -> Unit,
    onArchive: (SessionSummary, Boolean) -> Unit,
    onOpenAutomations: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val hazeState = rememberHazeState()
    val agentsById = remember(agents) { agents.associateBy(Agent::id) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    ProvideScreenHaze(hazeState) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.fillMaxSize().widthIn(max = Dimens.maxReadingWidth).windowInsetsPadding(WindowInsets.statusBars)) {
                    // 見出しの行: アプリ名と、設定・閉じる（アイコンは 2 つまで。AGENTS.md 2.6）
                    Row(
                        Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.xs, top = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = onOpenSettings) {
                            Icon(painterResource(R.drawable.ic_huge_settings), contentDescription = stringResource(R.string.sidebar_settings))
                        }
                        IconButton(onClick = onClose) {
                            Icon(painterResource(R.drawable.ic_huge_close), contentDescription = stringResource(R.string.panel_close_cd))
                        }
                    }
                    PanelAction(R.drawable.ic_huge_new_chat, R.string.panel_new_session, onNewSession)
                    PanelAction(R.drawable.ic_huge_automation, R.string.sidebar_automations, onOpenAutomations)
                    Text(
                        stringResource(R.string.panel_history),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.l, top = Spacing.l, bottom = Spacing.xs).semantics { heading() },
                    )
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f).fillMaxWidth().hazeSource(hazeState),
                        key = { agents.getOrNull(it)?.id ?: it },
                        beyondViewportPageCount = 1,
                        // 横スワイプはパネルを閉じる操作に使う。エージェントの切り替えは下部のタブだけ
                        userScrollEnabled = false,
                    ) { page ->
                        val agent = agents.getOrNull(page) ?: return@HorizontalPager
                        SessionList(
                            state = sessionsByAgent[agent.id] ?: SessionListState(),
                            selectedSessionKey = selectedSessionKey,
                            timeLabel = timeLabel,
                            contentPadding = PaddingValues(bottom = bottomInset + Dimens.minTouchTarget + Spacing.xxl),
                            onSelect = onSelectSession,
                            onRename = onRename,
                            onArchive = onArchive,
                        )
                    }
                }
                // 下部に浮くエージェントのピル型タブ（AGENTS.md 2.7: 画面下部に浮くガラス風のピル型タブ）
                if (agents.size > 1) {
                    GlassPill(
                        hazeState = hazeState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = Spacing.l, vertical = Spacing.l),
                    ) {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            PillTabs(
                                tabs = agentsById.keys.toList(),
                                selected = agents.getOrNull(pagerState.currentPage)?.id ?: agents.first().id,
                                onSelect = { id ->
                                    val index = agents.indexOfFirst { it.id == id }
                                    if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                label = { id -> agentsById.getValue(id).name },
                                tabHorizontalPadding = Spacing.m,
                            ) { id ->
                                // サイドパネルのタブのアイコンは大きめ（ふつうのアイコンの 2 倍）
                                AgentAvatar(agentsById.getValue(id), size = Dimens.avatarLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelAction(icon: Int, label: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label), style = MaterialTheme.typography.titleMedium) },
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
    contentPadding: PaddingValues,
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
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
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
