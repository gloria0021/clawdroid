package jp.miyavi.android.clawdroid.feature.chat.sidebar

import jp.miyavi.android.clawdroid.core.designsystem.accentContainer
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.State
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

// The adaptive icon foreground includes a bleed area outside its visible viewport.
private const val LauncherForegroundViewportScale = 1.5f
/** サイドパネルのフェード（エージェントの切り替え・選んだ行の色）の速さ。slowEffects に対する倍率で、約 0.5 秒になる（マスター指定 2026-11-23）。 */
private const val PanelFadeSpeed = 0.5f

/** 行の面（ピル）を画面の端からあける幅。M3 のナビゲーションドロワーの項目と同じ 12dp。 */
private val PanelItemInset = Spacing.m
/** 文字・アイコンの始まり。ピルの内側の 16dp（ListItem の既定）を足した 28dp に、見出しもそろえる。 */
private val PanelTextInset = Spacing.m + Spacing.l

/** slowEffects のばねを、速さだけ PanelFadeSpeed 倍に落としたもの（ばねの時間は硬さの平方根に反比例するので、硬さを速さの 2 乗倍にする）。 */
@Composable
private fun <T> panelFadeSpec(): FiniteAnimationSpec<T> {
    val slowEffects = MaterialTheme.motionScheme.slowEffectsSpec<T>()
    return remember(slowEffects) {
        (slowEffects as? SpringSpec<T>)?.let {
            spring(it.dampingRatio, it.stiffness * PanelFadeSpeed * PanelFadeSpeed, it.visibilityThreshold)
        } ?: slowEffects
    }
}

/**
 * 全画面のサイドパネル（Gemini 型）。上から 新しいセッション・自動化・セッション履歴。
 * 下部にエージェントの数だけピル型のタブが浮き、切り替えるとセッション履歴が切り替わる（FR-010・011）。
 * 選択を切り替えたら、新しいエージェントの内容だけをフェードインする。
 * パネルの表示は押した瞬間に切り替え、選択の確定（裏の会話を開く重い処理）はフェードが終わってから行う（AGENTS.md 2.8）。
 */
@Composable
fun SidePanel(
    agents: List<Agent>,
    selectedAgentId: String?,
    onSelectAgent: (String) -> Unit,
    sessionsByAgent: Map<String, SessionListState>,
    selectedSessionKey: String?,
    timeLabel: (Long) -> String,
    onClose: () -> Unit,
    onNewSession: () -> Unit,
    onSelectSession: (SessionSummary) -> Unit,
    onRename: (SessionSummary, String) -> Unit,
    onArchive: (SessionSummary, Boolean) -> Unit,
    onDelete: (SessionSummary) -> Unit,
    onOpenAutomations: (Agent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberHazeState()
    val agentsById = remember(agents) { agents.associateBy(Agent::id) }
    // タブで選んだが、まだ確定していないエージェント（フェードのあいだだけ持つ）
    var requestedAgentId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedAgentId) { if (selectedAgentId == requestedAgentId) requestedAgentId = null }
    val currentAgent = agentsById[requestedAgentId ?: selectedAgentId] ?: agents.firstOrNull()
    val onSelectAgentLatest by rememberUpdatedState(onSelectAgent)
    val commitAgent = remember { { requestedAgentId?.let { onSelectAgentLatest(it) } } }
    // フェードの途中でパネルが消えても、選んだエージェントを確定する
    DisposableEffect(Unit) { onDispose { commitAgent() } }
    val historyState = rememberSaveableStateHolder()
    val agentOpacity = remember { Animatable(1f) }
    var revealedAgentId by remember { mutableStateOf(currentAgent?.id) }
    val fadeSpec = panelFadeSpec<Float>()
    LaunchedEffect(currentAgent?.id) {
        val id = currentAgent?.id
        if (revealedAgentId == id) return@LaunchedEffect
        val switchingAgents = revealedAgentId != null && id != null
        agentOpacity.snapTo(if (switchingAgents) 0f else 1f)
        revealedAgentId = id
        if (switchingAgents) agentOpacity.animateTo(1f, fadeSpec)
        // 動きが終わってから選択を確定する（裏で会話を開く重い処理をフェードと重ねない）
        if (id != null && id == requestedAgentId) onSelectAgentLatest(id)
    }
    val selectAgent: (String) -> Unit = { id ->
        if (id != currentAgent?.id) requestedAgentId = id.takeUnless { it == selectedAgentId }
    }
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
                        Image(
                            painter = painterResource(R.drawable.launcher_mascot_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.sidebarAppIcon).graphicsLayer {
                                scaleX = LauncherForegroundViewportScale
                                scaleY = LauncherForegroundViewportScale
                            },
                        )
                        Spacer(Modifier.size(Spacing.s))
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        IconButton(onClick = { commitAgent(); onOpenSettings() }) {
                            Icon(painterResource(R.drawable.ic_huge_settings), contentDescription = stringResource(R.string.sidebar_settings))
                        }
                        IconButton(onClick = onClose) {
                            Icon(painterResource(R.drawable.ic_huge_close), contentDescription = stringResource(R.string.panel_close_cd))
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                            alpha = if (revealedAgentId == currentAgent?.id) agentOpacity.value else 0f
                        },
                    ) {
                        currentAgent?.let { agent ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = PanelTextInset, vertical = Spacing.s),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AgentAvatar(agent, size = Dimens.avatarLarge)
                                Text(
                                    agent.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.semantics { heading() },
                                )
                            }
                        }
                        PanelAction(R.drawable.ic_huge_new_chat, R.string.panel_new_session, { commitAgent(); onNewSession() })
                        currentAgent?.let { agent ->
                            PanelAction(R.drawable.ic_huge_automation, R.string.sidebar_automations, { commitAgent(); onOpenAutomations(agent) })
                        }
                        // ホーム（選んでいるエージェントの main のセッション）。セッション履歴より上に置く
                        val home = currentAgent?.id?.let { sessionsByAgent[it]?.home }
                        if (home != null) {
                            PanelAction(
                                R.drawable.ic_huge_home,
                                R.string.panel_home,
                                onClick = { commitAgent(); onSelectSession(home) },
                                selected = home.key == selectedSessionKey,
                            )
                        }
                        Text(
                            stringResource(R.string.panel_history),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = PanelTextInset, top = Spacing.l, bottom = Spacing.xs).semantics { heading() },
                        )
                        if (currentAgent != null) {
                            Box(Modifier.weight(1f).fillMaxWidth().hazeSource(hazeState)) {
                                historyState.SaveableStateProvider(currentAgent.id) {
                                    SessionList(
                                        state = sessionsByAgent[currentAgent.id] ?: SessionListState(),
                                        selectedSessionKey = selectedSessionKey,
                                        timeLabel = timeLabel,
                                        contentPadding = PaddingValues(bottom = bottomInset + Dimens.minTouchTarget + Spacing.xxl),
                                        onSelect = { commitAgent(); onSelectSession(it) },
                                        onRename = onRename,
                                        onArchive = onArchive,
                                        onDelete = onDelete,
                                    )
                                }
                            }
                        }
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
                                selected = currentAgent?.id ?: agents.first().id,
                                onSelect = selectAgent,
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
private fun PanelAction(icon: Int, label: Int, onClick: () -> Unit, selected: Boolean = false) {
    val highlight = selectionHighlight(selected)
    Box(Modifier.padding(horizontal = PanelItemInset)) {
        SelectionPill(highlight)
        ListItem(
            headlineContent = { Text(stringResource(label), style = MaterialTheme.typography.titleMedium) },
            leadingContent = { Icon(painterResource(icon), contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = highlight.headline),
            modifier = Modifier.combinedClickable(interactionSource = highlight.interactions, indication = null, onClick = onClick),
        )
    }
}

/**
 * 選んでいる行の色。背景の面と文字色を、出すときは約 0.5 秒（panelFadeSpec）、消すときは fast でフェードさせる。
 * 背景は行の幅いっぱいではなく、左右を 12dp あけたピル型（M3 のナビゲーションドロワーの項目と同じ寸法。マスター指定 2026-11-23）。
 */
private class SelectionHighlight(val container: State<Color>, val headline: Color, val interactions: MutableInteractionSource)

/** 行の後ろに敷くピル型の面。押した反応（ripple）も同じ形に収める。文字は切り取らない。 */
@Composable
private fun BoxScope.SelectionPill(highlight: SelectionHighlight) {
    Box(
        Modifier.matchParentSize()
            .clip(CircleShape)
            // 背景は描画の中で読み、フェードのあいだ行を組み立て直さない
            .drawBehind { drawRect(highlight.container.value) }
            .indication(highlight.interactions, ripple()),
    )
}

@Composable
private fun selectionHighlight(selected: Boolean): SelectionHighlight {
    val accent = accentContainer()
    val appear = panelFadeSpec<Color>()
    val spec = if (selected) appear else MaterialTheme.motionScheme.fastEffectsSpec()
    // 透明は同じ色の不透明度 0 にする（黒の透明を経由すると、途中で暗くなる）
    val container = animateColorAsState(
        if (selected) accent.container else accent.container.copy(alpha = 0f),
        spec,
        label = "selectionContainer",
    )
    val headline by animateColorAsState(
        if (selected) accent.content else MaterialTheme.colorScheme.onSurface,
        spec,
        label = "selectionHeadline",
    )
    val interactions = remember { MutableInteractionSource() }
    return SelectionHighlight(container, headline, interactions)
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
    onDelete: (SessionSummary) -> Unit,
) {
    val (active, archived) = remember(state.sessions, state.archived) {
        val active = state.sessions.distinctBy { it.key }
        val activeKeys = active.mapTo(mutableSetOf()) { it.key }
        active to state.archived.distinctBy { it.key }.filterNot { it.key in activeKeys }
    }
    when {
        state.loading && state.sessions.isEmpty() && state.archived.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        state.sessions.isEmpty() && state.archived.isEmpty() -> Column(
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
            // アクティブとアーカイブ済みを分けて出す
            item(key = "header-active", contentType = "header") { ListHeader(R.string.panel_active, active.size) }
            items(active, key = { "session:${it.key}" }) { session ->
                SessionItem(
                    session = session,
                    selected = session.key == selectedSessionKey,
                    timeLabel = session.updatedAt?.let(timeLabel),
                    onClick = { onSelect(session) },
                    onRename = { onRename(session, it) },
                    onArchive = { onArchive(session, !session.archived) },
                    onDelete = { onDelete(session) },
                    modifier = motionItem(),
                )
            }
            if (archived.isNotEmpty()) {
                item(key = "header-archived", contentType = "header") {
                    ListHeader(R.string.panel_archived, archived.size, motionItem())
                }
                // 区切りをまたいでも同じ key にして、行が消えて現れるのではなく「移動」として動くようにする
                items(archived, key = { "session:${it.key}" }) { session ->
                    SessionItem(
                        session = session,
                        selected = session.key == selectedSessionKey,
                        timeLabel = session.updatedAt?.let(timeLabel),
                        onClick = { onSelect(session) },
                        onRename = { onRename(session, it) },
                        onArchive = { onArchive(session, !session.archived) },
                        onDelete = { onDelete(session) },
                        modifier = motionItem(),
                    )
                }
            }
        }
    }
}

/** 一覧の区切りの見出し（件数付き）。 */
@Composable
private fun ListHeader(label: Int, count: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.panel_section_count, stringResource(label), count),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = PanelTextInset, top = Spacing.m, bottom = Spacing.xs).semantics { heading() },
    )
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
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val title = session.title ?: stringResource(R.string.session_untitled)
    val menuLabel = stringResource(R.string.session_menu_open_label)
    val highlight = selectionHighlight(selected)
    Box(modifier.padding(horizontal = PanelItemInset)) {
        SelectionPill(highlight)
        ListItem(
            headlineContent = {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = timeLabel?.let { label ->
                { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            trailingContent = { SessionMarks(session) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = highlight.headline),
            modifier = Modifier.combinedClickable(
                interactionSource = highlight.interactions,
                indication = null,
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
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_menu_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_huge_delete), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; confirmDelete = true },
            )
        }
    }
    // 削除は元に戻せないので確認する（AGENTS.md 2.7: 確認は AlertDialog）
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.session_delete_title)) },
            text = { Text(stringResource(R.string.session_delete_body, title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
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
