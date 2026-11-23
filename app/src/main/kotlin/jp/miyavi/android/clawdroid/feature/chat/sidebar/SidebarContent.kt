package jp.miyavi.android.clawdroid.feature.chat.sidebar

import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
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
import jp.miyavi.android.clawdroid.core.settings.SessionGrouping
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
    /** エージェントごとの並べ方（無ければ更新順）。 */
    groupingByAgent: Map<String, SessionGrouping> = emptyMap(),
    onGroupingChange: (String, SessionGrouping) -> Unit = { _, _ -> },
    onCreateWorktree: (SessionSummary, String?, String?, Boolean) -> Unit = { _, _, _, _ -> },
    onUnpin: (SessionSummary) -> Unit = {},
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
    val grouping = currentAgent?.id?.let { groupingByAgent[it] } ?: SessionGrouping.UPDATED
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
                        // ピン留めしたセッション（ホームの下に、ピンのアイコンで）。長押しでピンを外す
                        val pinned = currentAgent?.id?.let { id -> sessionsByAgent[id]?.sessions?.filter { it.pinned } }.orEmpty()
                        pinned.forEach { session ->
                            PinnedAction(
                                session = session,
                                selected = session.key == selectedSessionKey,
                                onClick = { commitAgent(); onSelectSession(session) },
                                onUnpin = { onUnpin(session) },
                            )
                        }
                        // 履歴の見出しと、並べ方の切り替え（更新順 / 作業フォルダ別）
                        Row(
                            Modifier.fillMaxWidth().padding(start = PanelTextInset, end = Spacing.s, top = Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.panel_history),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).semantics { heading() },
                            )
                            if (currentAgent != null) GroupingToggle(grouping) { onGroupingChange(currentAgent.id, it) }
                        }
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
                                        grouping = grouping,
                                        onCreateWorktree = onCreateWorktree,
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

/** ピン留めしたセッションの行（ホームと同じ形。長押しでピンを外す）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedAction(session: SessionSummary, selected: Boolean, onClick: () -> Unit, onUnpin: () -> Unit) {
    val highlight = selectionHighlight(selected)
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = PanelItemInset)) {
        SelectionPill(highlight)
        ListItem(
            headlineContent = {
                Text(
                    session.title ?: stringResource(R.string.session_untitled),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = { Icon(painterResource(R.drawable.ic_huge_pin), contentDescription = null) },
            trailingContent = { SessionMarks(session) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = highlight.headline),
            modifier = Modifier.combinedClickable(
                interactionSource = highlight.interactions,
                indication = null,
                onClick = onClick,
                onLongClick = { menuOpen = true },
                onLongClickLabel = stringResource(R.string.session_menu_open_label),
            ),
        )
        GlassDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_menu_unpin)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_huge_pin_off), contentDescription = null) },
                onClick = { menuOpen = false; onUnpin() },
            )
        }
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
    grouping: SessionGrouping = SessionGrouping.UPDATED,
    onCreateWorktree: (SessionSummary, String?, String?, Boolean) -> Unit = { _, _, _, _ -> },
) {
    // 作業フォルダ別で、閉じているフォルダ（回転・言語の切り替えを越えて残す）
    var collapsed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val (active, archived) = remember(state.sessions, state.archived) {
        // 更新日時の新しい順
        val active = state.sessions.distinctBy { it.key }.sortedByDescending { it.updatedAt ?: 0 }
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
            if (grouping == SessionGrouping.FOLDER) {
                // 作業フォルダごとにまとめる。まとまりの並びも、中の並びも、更新日時の新しい順
                val groups = groupByFolder(active)
                groups.forEach { (folder, rows) ->
                    val id = folder ?: ""
                    val open = id !in collapsed
                    item(key = "folder:$id", contentType = "folder") {
                        FolderHeader(folder, rows.size, open, motionItem()) {
                            collapsed = if (open) collapsed + id else collapsed - id
                        }
                    }
                    if (open) itemsIndexed(rows, key = { _, it -> "session:${it.key}" }) { index, session ->
                        SessionItem(
                            session = session,
                            selected = session.key == selectedSessionKey,
                            timeLabel = session.updatedAt?.let(timeLabel),
                            onClick = { onSelect(session) },
                            onRename = { onRename(session, it) },
                            onArchive = { onArchive(session, !session.archived) },
                            onDelete = { onDelete(session) },
                            onCreateWorktree = { name, base, fork -> onCreateWorktree(session, name, base, fork) },
                            // フォルダの下に、木の枝でぶら下げる（文字は小さく、1 行）
                            tree = if (index == rows.lastIndex) TreeBranch.LAST else TreeBranch.MIDDLE,
                            modifier = motionItem(),
                        )
                    }
                }
            } else {
                items(active, key = { "session:${it.key}" }) { session ->
                    SessionItem(
                        session = session,
                        selected = session.key == selectedSessionKey,
                        timeLabel = session.updatedAt?.let(timeLabel),
                        onClick = { onSelect(session) },
                        onRename = { onRename(session, it) },
                        onArchive = { onArchive(session, !session.archived) },
                        onDelete = { onDelete(session) },
                        onCreateWorktree = { name, base, fork -> onCreateWorktree(session, name, base, fork) },
                        modifier = motionItem(),
                    )
                }
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
    onCreateWorktree: ((String?, String?, Boolean) -> Unit)? = null,
    /** フォルダ別のときの枝の形（null なら通常の行）。 */
    tree: TreeBranch? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var worktree by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val title = session.title ?: stringResource(R.string.session_untitled)
    val menuLabel = stringResource(R.string.session_menu_open_label)
    val highlight = selectionHighlight(selected)
    if (tree != null) {
        // 枝（親のフォルダから縦の線、自分の所で横に折れる）。最後の行は自分の所で縦の線を止める
        val guide = MaterialTheme.colorScheme.outlineVariant
        val density = LocalDensity.current
        val guideX = with(density) { TreeGuideX.toPx() }
        val tick = with(density) { TreeTick.toPx() }
        val stroke = with(density) { TreeStroke.toPx() }
        Box(
            modifier.fillMaxWidth().drawBehind {
                val mid = size.height / 2
                drawLine(guide, Offset(guideX, 0f), Offset(guideX, if (tree == TreeBranch.LAST) mid else size.height), stroke)
                drawLine(guide, Offset(guideX, mid), Offset(guideX + tick, mid), stroke)
            },
        ) {
            Box(Modifier.padding(start = TreeChildStart, end = PanelItemInset)) {
                SelectionPill(highlight)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.minTouchTarget)
                        .combinedClickable(
                            interactionSource = highlight.interactions,
                            indication = null,
                            onClick = onClick,
                            onLongClick = { menuOpen = true },
                            onLongClickLabel = menuLabel,
                        )
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = highlight.headline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    timeLabel?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    SessionMarks(session)
                }
                SessionMenu(session, menuOpen, onDismiss = { menuOpen = false }, onRename = { renaming = true },
                    onWorktree = if (onCreateWorktree != null) ({ worktree = true }) else null,
                    onArchive = onArchive, onDelete = { confirmDelete = true })
            }
        }
    } else Box(modifier.padding(horizontal = PanelItemInset)) {
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
        SessionMenu(session, menuOpen, onDismiss = { menuOpen = false }, onRename = { renaming = true },
            onWorktree = if (onCreateWorktree != null) ({ worktree = true }) else null,
            onArchive = onArchive, onDelete = { confirmDelete = true })
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
    if (worktree && onCreateWorktree != null) {
        WorktreeSheet(session, onDismiss = { worktree = false }) { name, base, fork ->
            worktree = false
            onCreateWorktree(name, base, fork)
        }
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

/** 作業フォルダごとのまとまり（まとまりは中のいちばん新しい更新日時の順。作業フォルダの無いものは最後）。 */
internal fun groupByFolder(sessions: List<SessionSummary>): List<Pair<String?, List<SessionSummary>>> =
    sessions.groupBy { it.cwd?.trimEnd('/') }
        .map { (folder, rows) -> folder to rows.sortedByDescending { it.updatedAt ?: 0 } }
        .sortedWith(compareBy<Pair<String?, List<SessionSummary>>> { it.first == null }.thenByDescending { it.second.first().updatedAt ?: 0 })

/** 履歴の並べ方の切り替え（押すたびに入れ替わる小さなボタン）。 */
@Composable
private fun GroupingToggle(grouping: SessionGrouping, onChange: (SessionGrouping) -> Unit) {
    TextButton(
        onClick = { onChange(if (grouping == SessionGrouping.UPDATED) SessionGrouping.FOLDER else SessionGrouping.UPDATED) },
        modifier = Modifier.heightIn(min = Dimens.compactTouchTarget),
    ) {
        Icon(
            painterResource(if (grouping == SessionGrouping.FOLDER) R.drawable.ic_huge_folder else R.drawable.ic_huge_clock),
            contentDescription = null,
            modifier = Modifier.size(Dimens.smallIcon),
        )
        Text(
            stringResource(if (grouping == SessionGrouping.FOLDER) R.string.panel_group_folder else R.string.panel_group_updated),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/** 作業フォルダの見出し（親の行。フォルダ名と件数）。押すと開閉。フルパスは読み上げと長押しのラベルで分かる。 */
@Composable
private fun FolderHeader(folder: String?, count: Int, open: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val toggleLabel = stringResource(if (open) R.string.panel_folder_collapse else R.string.panel_folder_expand)
    val name = folder?.substringAfterLast('/')?.ifEmpty { folder } ?: stringResource(R.string.panel_folder_none)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PanelItemInset)
            .clip(CircleShape)
            .clickable(onClickLabel = toggleLabel, onClick = onToggle)
            .heightIn(min = Dimens.minTouchTarget)
            .padding(start = PanelTextInset - PanelItemInset, end = Spacing.m, top = Spacing.s, bottom = Spacing.s)
            .semantics { folder?.let { contentDescription = "$name, $it" } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(painterResource(R.drawable.ic_huge_folder), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Dimens.smallIcon))
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Icon(
            painterResource(if (open) R.drawable.ic_huge_arrow_up else R.drawable.ic_huge_arrow_down),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(Dimens.smallIcon),
        )
    }
}

/**
 * ワークツリーで分ける（セッションの長押しメニュー）。ブランチ名（空なら Gateway が会話のタイトルから付ける）、
 * 元にするブランチ（空なら今のブランチ）、会話を引き継ぐか。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorktreeSheet(session: SessionSummary, onDismiss: () -> Unit, onCreate: (String?, String?, Boolean) -> Unit) {
    val hazeState = jp.miyavi.android.clawdroid.core.designsystem.LocalScreenHazeState.current ?: rememberHazeState()
    var name by rememberSaveable { mutableStateOf("") }
    var base by rememberSaveable { mutableStateOf("") }
    var fork by rememberSaveable { mutableStateOf(true) }
    val validName = name.isEmpty() || WORKTREE_NAME.matches(name)
    jp.miyavi.android.clawdroid.core.designsystem.AppBottomSheet(
        onDismissRequest = onDismiss,
        hazeState = hazeState,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.imePadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.worktree_title), style = MaterialTheme.typography.titleMedium)
                    Text(session.cwd.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                androidx.compose.material3.Button(onClick = { onCreate(name.ifBlank { null }, base.ifBlank { null }, fork) }, enabled = validName) {
                    Text(stringResource(R.string.worktree_create))
                }
            }
            androidx.compose.material3.Surface(
                color = jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor(),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
            ) {
                Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.lowercase().replace(Regex("[^a-z0-9-]"), "-").take(64) },
                        label = { Text(stringResource(R.string.worktree_name)) },
                        placeholder = { Text(stringResource(R.string.worktree_name_auto)) },
                        prefix = { Text("openclaw/") },
                        supportingText = { Text(stringResource(R.string.worktree_name_hint)) },
                        isError = !validName,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = base,
                        onValueChange = { base = it.trim() },
                        label = { Text(stringResource(R.string.worktree_base)) },
                        placeholder = { Text(stringResource(R.string.worktree_base_current)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(R.string.worktree_fork)) },
                        supportingContent = { Text(stringResource(R.string.worktree_fork_desc)) },
                        trailingContent = { androidx.compose.material3.Switch(checked = fork, onCheckedChange = { fork = it }) },
                        modifier = Modifier.clickable { fork = !fork },
                    )
                }
            }
        }
    }
}

/** Gateway のワークツリー名の決まり（`^[a-z0-9][a-z0-9-]{0,63}$`）。 */
private val WORKTREE_NAME = Regex("^[a-z0-9][a-z0-9-]{0,63}$")

/** フォルダの下の行の、枝の形。 */
internal enum class TreeBranch { MIDDLE, LAST }

/** 枝の縦の線の位置（フォルダのアイコンの中ほど）、横の線の長さ、線の太さ、子の行の始まり。 */
private val TreeGuideX = PanelTextInset + Dimens.smallIcon / 2
private val TreeTick = Spacing.m
private val TreeStroke = 1.dp
private val TreeChildStart = PanelTextInset + Dimens.smallIcon / 2 + Spacing.m

/** セッションの長押しメニュー（名前を変更・ワークツリーで分岐・アーカイブ・削除）。 */
@Composable
private fun SessionMenu(
    session: SessionSummary,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onWorktree: (() -> Unit)?,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.session_menu_rename)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_huge_edit), contentDescription = null) },
            onClick = { onDismiss(); onRename() },
        )
        // 作業フォルダのある会話から、ワークツリーのセッションを分ける
        if (onWorktree != null && session.cwd != null && !session.archived) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_menu_worktree)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_huge_branch), contentDescription = null) },
                onClick = { onDismiss(); onWorktree() },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(if (session.archived) R.string.session_menu_unarchive else R.string.session_menu_archive)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_huge_archive), contentDescription = null) },
            onClick = { onDismiss(); onArchive() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.session_menu_delete), color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_huge_delete), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onDelete() },
        )
    }
}
