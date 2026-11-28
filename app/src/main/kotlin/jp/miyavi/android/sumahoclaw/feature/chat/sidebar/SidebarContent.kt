package jp.miyavi.android.sumahoclaw.feature.chat.sidebar

import kotlinx.coroutines.launch
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassTint
import jp.miyavi.android.sumahoclaw.core.designsystem.TITLE_BAR_OPACITY
import jp.miyavi.android.sumahoclaw.core.designsystem.hazeSurface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import jp.miyavi.android.sumahoclaw.core.designsystem.FloatingIconButton
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import jp.miyavi.android.sumahoclaw.core.designsystem.accentContainer
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import jp.miyavi.android.sumahoclaw.core.designsystem.DisabledAlpha
import jp.miyavi.android.sumahoclaw.core.gateway.ConnectionState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.Agent
import jp.miyavi.android.sumahoclaw.core.data.SessionListState
import jp.miyavi.android.sumahoclaw.core.settings.SessionGrouping
import jp.miyavi.android.sumahoclaw.core.data.SessionSummary
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassPill
import jp.miyavi.android.sumahoclaw.core.designsystem.PillTabs
import jp.miyavi.android.sumahoclaw.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.motionItem
import jp.miyavi.android.sumahoclaw.feature.chat.AgentAvatar

// The adaptive icon foreground includes a bleed area outside its visible viewport.
internal const val LauncherForegroundViewportScale = 1.5f
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
    /** Gateway につながっていなければ、新しいセッションを押せなくし、エージェントの代わりにマスコットと状態を出す。 */
    connection: ConnectionState = ConnectionState.Connecting,
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
    onOpenAgentSettings: (Agent) -> Unit = {},
    modifier: Modifier = Modifier,
    /** エージェントごとの並べ方（無ければ更新順）。 */
    groupingByAgent: Map<String, SessionGrouping> = emptyMap(),
    onGroupingChange: (String, SessionGrouping) -> Unit = { _, _ -> },
    onCreateWorktree: (SessionSummary, String?, String?, Boolean) -> Unit = { _, _, _, _ -> },
    /** 並行開発のシートで、元にするブランチの候補を取る。 */
    loadBranches: suspend (SessionSummary) -> jp.miyavi.android.sumahoclaw.core.data.RepoBranches? = { null },
    onUnpin: (SessionSummary) -> Unit = {},
    /**
     * パネルの中身を下へずらす量（px。引き出すにつれて 0 へ）。看板も一緒に浮き上がる（マスター指定 2026-11-26）。
     * 描画の中だけで読む。ずらしても上端に隙間が出ないよう、看板の画像は [liftRange] だけ上にはみ出して描く。
     */
    contentLift: () -> Float = { 0f },
    /** [contentLift] のいちばん大きい値（看板の画像を上にはみ出させる長さ）。 */
    liftRange: androidx.compose.ui.unit.Dp = Spacing.xl,
    /** パネルが少しでも見えているか。バーチャルオフィス（spec 009）は、見えていないあいだ描かない。 */
    panelVisible: Boolean = true,
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
        // 会話（surface）より一段沈んだ面にして、会話の下にある引き出しに見せる（Gemini 型。マスター指定 2026-11-26）
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.fillMaxSize().widthIn(max = Dimens.maxReadingWidth)) {
                    // 頭の看板（マスター指定 2026-11-23）: 選んでいるエージェントの顔を、ステータスバーの裏まで横いっぱいに敷き、
                    // 下へいくほど背景に溶かす。右上に閉じる（ガラスの面で、どの画像の上でも読める）
                    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    // バーチャルオフィス（spec 009）は、閉じるボタンの行の下に顔の看板と同じ高さの部屋を取る（ボタンを部屋に重ねない。
                    // マスター指定 2026-11-28）。ボタンの行の裏も、奥の壁と床で埋まる
                    val showOffice = currentAgent != null && LocalVirtualOffice.current
                    val closeRow = Spacing.s + Dimens.floatingButtonSize
                    val heroHeight = statusTop + Dimens.agentHeroHeight + if (showOffice) closeRow else 0.dp
                    Box(Modifier.fillMaxWidth().height(heroHeight).graphicsLayer { translationY = contentLift() }) {
                        if (currentAgent == null) {
                            // エージェントがまだ無い（つながっていない）: 淡いマスコットを看板の代わりに置く（マスター指定 2026-11-25）
                            Image(
                                painter = painterResource(R.drawable.launcher_mascot_foreground),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.matchParentSize().windowInsetsPadding(WindowInsets.statusBars)
                                    .graphicsLayer { alpha = DisabledAlpha },
                            )
                        }
                        if (showOffice) {
                            // バーチャルオフィス（実験機能。spec 009）: 顔の代わりに、タブと同じ 4 体が働く・寝るオフィスを枠いっぱいに描く。
                            // タブを切り替えても同じ部屋なので、エージェントの切り替えのフェードはかけない
                            val office = remember(agents, sessionsByAgent) { officeAgents(agents, sessionsByAgent) }
                            AgentOffice(
                                office,
                                active = panelVisible,
                                modifier = Modifier.overhangTop(liftRange).hazeSource(hazeState, zIndex = HERO_HAZE_Z),
                                // デスクと寝室は、見えている枠（上にはみ出した分を除く）の真ん中に置く（マスター指定 2026-11-28）
                                topInset = liftRange,
                            )
                        } else currentAgent?.let { agent ->
                            jp.miyavi.android.sumahoclaw.feature.chat.AgentHero(
                                agent,
                                // 浮き上がる分だけ上にはみ出させて、下へずれても上端に隙間を作らない（はみ出した所は画面の外）
                                Modifier.overhangTop(liftRange).hazeSource(hazeState, zIndex = HERO_HAZE_Z).graphicsLayer {
                                    alpha = if (revealedAgentId == currentAgent.id) agentOpacity.value else 0f
                                },
                            )
                        }
                        // 右上に閉じるボタンだけ（名前は画像の下、ホームの上。マスター指定 2026-11-23）
                        Row(
                            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                                .padding(start = Spacing.m, end = Spacing.m, top = Spacing.s),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_close), stringResource(R.string.panel_close_cd), onClose)
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                            alpha = if (revealedAgentId == currentAgent?.id) agentOpacity.value else 0f
                            translationY = contentLift()
                        },
                    ) {
                        // エージェントの名前（画像の下、ホームの上。titleMedium の 1.5 倍 = headlineSmall。
                        // ガラスは付けない。マスター指定 2026-11-23）
                        if (currentAgent == null) {
                            val trying = connection is ConnectionState.Connecting || connection is ConnectionState.Reconnecting ||
                                connection is ConnectionState.Restarting || connection is ConnectionState.AwaitingApproval ||
                                connection is ConnectionState.Paused
                            Text(
                                stringResource(
                                    when {
                                        connection is ConnectionState.Connected -> R.string.panel_loading_agents
                                        trying -> R.string.offline_connecting
                                        else -> R.string.offline_unreachable
                                    },
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(start = PanelTextInset, end = Spacing.s, top = Spacing.s),
                            )
                        }
                        currentAgent?.let { agent ->
                            // 名前の横に編集のアイコン（エージェントの設定を開く。マスター指定 2026-11-24、spec 004 FR-001）
                            Row(
                                Modifier.fillMaxWidth().padding(start = PanelTextInset, end = Spacing.s, top = Spacing.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    agent.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                                )
                                IconButton(onClick = { commitAgent(); onOpenAgentSettings(agent) }) {
                                    Icon(painterResource(R.drawable.ic_huge_edit), contentDescription = stringResource(R.string.agent_settings_open_cd))
                                }
                            }
                        }
                        // 操作は1行にまとめる（マスター指定 2026-11-23: 3行使うと右側が空く）。
                        // 左にホーム・自動化（文字付き）、右に設定（アイコンだけ）。
                        // 新しいセッションは「セッション」の見出しの行へ移した（マスター指定 2026-11-24）
                        val home = currentAgent?.id?.let { sessionsByAgent[it]?.home }
                        PanelActionRow(
                            homeSelected = home != null && home.key == selectedSessionKey,
                            onHome = home?.let { { commitAgent(); onSelectSession(it) } },
                            onAutomations = currentAgent?.let { agent -> { commitAgent(); onOpenAutomations(agent) } },
                            onSettings = { commitAgent(); onOpenSettings() },
                        )
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
                        // 「セッション」の見出しと、並べ方の切り替え（更新順 / 作業フォルダ別）
                        Row(
                            Modifier.fillMaxWidth().padding(start = PanelTextInset, end = Spacing.s, top = Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.panel_sessions),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).semantics { heading() },
                            )
                            if (currentAgent != null) GroupingToggle(grouping) { onGroupingChange(currentAgent.id, it) }
                        }
                        // 見出しのすぐ下に「+ 新規セッション」の行（アイコンのボタンではなく行で。マスター指定 2026-11-24）。
                        // 一覧の外に置き、空・読み込み中でも出す
                        NewSessionItem(
                            enabled = connection is ConnectionState.Connected && currentAgent != null,
                            onClick = { commitAgent(); onNewSession() },
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
                                        grouping = grouping,
                                        onCreateWorktree = onCreateWorktree,
                                        loadBranches = loadBranches,
                                    )
                                }
                            }
                        }
                    }
                }
                // 下部に浮くエージェントのピル型タブ（AGENTS.md 2.7: 画面下部に浮くガラス風のピル型タブ）
                if (agents.size > 1) {
                    // 稼働中（ホームかどれかのセッションが実行中）のエージェントは、タブの顔をアメーバで囲む（マスター指定 2026-11-26）
                    val busyAgents = remember(sessionsByAgent) {
                        sessionsByAgent.filterValues { list -> list.home?.running == true || list.sessions.any { it.running } }.keys
                    }
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
                                // 選択はガラスの面ではなく顔の濃さで見せる（面の縁の線がアメーバと重なって見えるため。マスター指定 2026-11-26）
                                showIndicator = false,
                            ) { id ->
                                // サイドパネルのタブのアイコンは大きめ（ふつうのアイコンの 2 倍）
                                AgentAvatar(
                                    agentsById.getValue(id),
                                    size = Dimens.avatarLarge,
                                    busy = id in busyAgents,
                                    ringOutside = true,
                                    dimmed = id != (currentAgent?.id ?: agents.first().id),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * パネルの操作の行。左からホーム・自動化（文字付き）、右に設定（アイコンだけ）。
 * ホームは開いているあいだ選択色（マスター指定 2026-11-23）。
 */
@Composable
private fun PanelActionRow(
    homeSelected: Boolean,
    onHome: (() -> Unit)?,
    onAutomations: (() -> Unit)?,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // ホーム・自動化の順（マスター指定 2026-11-24 に戻す）。残りの幅を使い、右のアイコンまでを埋める（重みを空白と分け合わない）。
        // ホームは文字の幅だけ取り、自動化が残りをすべて使う（両方に重みを付けると半分ずつに切られ、
        // 英語の「Automations」が空きがあっても省略されていた。マスター指摘 2026-11-26）
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (onHome != null) {
                val accent = accentContainer()
                LabeledAction(
                    R.drawable.ic_huge_home, R.string.panel_home, onHome,
                    container = if (homeSelected) accent.container else Color.Transparent,
                    // 文字とアイコンはふつうの文字色（テーマカラーにしない。マスター指定 2026-11-24）。開いているあいだは選択の面
                    content = if (homeSelected) accent.content else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { selected = homeSelected },
                )
            }
            if (onAutomations != null) {
                LabeledAction(
                    R.drawable.ic_huge_automation, R.string.sidebar_automations, onAutomations,
                    container = Color.Transparent,
                    content = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        IconButton(onClick = onSettings) {
            Icon(painterResource(R.drawable.ic_huge_settings), contentDescription = stringResource(R.string.sidebar_settings))
        }
    }
}

/** アイコンと文字のボタン（ホーム・自動化）。色はリストで選んだ行の面と同じ色（マスター指定 2026-11-23）。 */
@Composable
private fun LabeledAction(icon: Int, label: Int, onClick: () -> Unit, container: Color, content: Color, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(containerColor = container, contentColor = content),
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        modifier = modifier.heightIn(min = Dimens.minTouchTarget),
    ) {
        // アイコンは右の設定と同じ大きさ（M3 のアイコンの標準 24dp）、文字は titleMedium の太字（マスター指定 2026-11-24）
        Icon(painterResource(icon), contentDescription = null)
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(label), style = panelActionLabelStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** ホーム・自動化・並べ方の切り替えの文字の大きさ: labelLarge より 1sp 大きくする（マスター指定 2026-11-23）。 */
private const val PART_LABEL_EXTRA_SP = 1f

/** ホーム・自動化の文字。titleMedium の太字（マスター指定 2026-11-24）。 */
@Composable
private fun panelActionLabelStyle(): TextStyle {
    val base = MaterialTheme.typography.titleMedium
    return remember(base) { base.copy(fontWeight = FontWeight.Bold) }
}

/** 並べ方の切り替えの文字。labelLarge を 1sp 大きく、太字に（マスター指定 2026-11-23）。 */
@Composable
private fun panelPartLabelStyle(): TextStyle {
    val base = MaterialTheme.typography.labelLarge
    return remember(base) {
        base.copy(fontSize = TextUnit(base.fontSize.value + PART_LABEL_EXTRA_SP, TextUnitType.Sp), fontWeight = FontWeight.Bold)
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
            CopySessionInfoItem(session) { menuOpen = false }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_menu_unpin)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_huge_pin_off), contentDescription = null) },
                onClick = { menuOpen = false; onUnpin() },
            )
        }
    }
}

/** 「+ 新規セッション」の行。セッションの行（CompactSessionRow）と同じ高さ・余白・文字（マスター指定 2026-11-24）。 */
@Composable
private fun NewSessionItem(enabled: Boolean, onClick: () -> Unit) {
    val highlight = selectionHighlight(false)
    // 押せないあいだは淡くして、押しても何も起きない（つながっていない・エージェントが無い）
    Box(Modifier.padding(horizontal = PanelItemInset).graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }) {
        SelectionPill(highlight)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .clickable(enabled = enabled, interactionSource = highlight.interactions, indication = null, onClick = onClick)
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(painterResource(R.drawable.ic_huge_plus), contentDescription = null, modifier = Modifier.size(Dimens.smallIcon))
            Text(
                stringResource(R.string.panel_new_session),
                style = MaterialTheme.typography.bodyMedium,
                color = highlight.headline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    loadBranches: suspend (SessionSummary) -> jp.miyavi.android.sumahoclaw.core.data.RepoBranches? = { null },
) {
    // 作業フォルダ別で、閉じているフォルダ（回転・言語の切り替えを越えて残す）
    var collapsed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // アーカイブ済みは、ふだん閉じておく（マスター指定 2026-11-24）
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    val (active, archived) = remember(state.sessions, state.archived) {
        // 更新日時の新しい順。ピン留めしたものはホームの下に出すので、ここには出さない（マスター指定 2026-11-23）。
        // チャット ⇔ 開発 は同じ全件の見え方の切り替え（作業フォルダの有無で絞らない。マスター指定 2026-11-24）
        val unique = state.sessions.distinctBy { it.key }
        val activeKeys = unique.mapTo(mutableSetOf()) { it.key }
        unique.filterNot { it.pinned }.sortedByDescending { it.updatedAt ?: 0 } to
            state.archived.distinctBy { it.key }.filterNot { it.key in activeKeys }
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
            // ふだんはアクティブなセッションだけを並べ、見出しは付けない。アーカイブ済みは末尾の閉じた見出しの中（マスター指定 2026-11-24）
            if (grouping == SessionGrouping.FOLDER) {
                // 開発: ワークツリーの無いプロジェクトは 1 行、ワークツリーがあれば元のセッションの下に枝でぶら下げる
                // （親子関係を見せる。マスター指定 2026-11-24）。元のセッションが無ければプロジェクトの見出しを親にする
                developmentRows(groupByFolder(active)).forEach { row ->
                    when (row) {
                        is DevRow.Header -> {
                            val id = row.folder ?: ""
                            val open = id !in collapsed
                            item(key = "folder:$id", contentType = "folder") {
                                FolderHeader(row.folder, row.count, open, motionItem()) {
                                    collapsed = if (open) collapsed + id else collapsed - id
                                }
                            }
                        }
                        is DevRow.Session -> {
                            val session = row.session
                            // 見出しを閉じているあいだは、その下の枝を出さない
                            if (row.underHeader != null && row.underHeader in collapsed) return@forEach
                            item(key = "session:${session.key}", contentType = if (row.tree != null) "branch" else "session") {
                                SessionItem(
                                    session = session,
                                    selected = session.key == selectedSessionKey,
                                    timeLabel = session.updatedAt?.let(timeLabel),
                                    onClick = { onSelect(session) },
                                    onRename = { onRename(session, it) },
                                    onArchive = { onArchive(session, !session.archived) },
                                    onDelete = { onDelete(session) },
                                    onCreateWorktree = { name, base, fork -> onCreateWorktree(session, name, base, fork) },
                                    loadBranches = loadBranches,
                                    tree = row.tree,
                                    development = true,
                                    modifier = motionItem(),
                                )
                            }
                        }
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
                            loadBranches = loadBranches,
                        modifier = motionItem(),
                    )
                }
            }
            if (archived.isNotEmpty()) {
                item(key = "header-archived", contentType = "folder") {
                    CollapsibleHeader(
                        icon = R.drawable.ic_huge_archive,
                        name = stringResource(R.string.panel_archived),
                        count = archived.size,
                        open = archivedOpen,
                        modifier = motionItem(),
                    ) { archivedOpen = !archivedOpen }
                }
                // 区切りをまたいでも同じ key にして、行が消えて現れるのではなく「移動」として動くようにする
                if (archivedOpen) items(archived, key = { "session:${it.key}" }) { session ->
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
    loadBranches: suspend (SessionSummary) -> jp.miyavi.android.sumahoclaw.core.data.RepoBranches? = { null },
    /** 開発のときの枝の形（null なら通常の行）。 */
    tree: TreeBranch? = null,
    /** 開発の並びの行（2 行目にブランチと変更量を出す）。更新順ではタイトルと時刻だけ。 */
    development: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var worktree by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // 名前の無いワークツリーのセッションは、ブランチ名を名前にする（マスター指定 2026-11-24）
    val branchTitle = session.worktreeBranch?.let { it.removePrefix(stringResource(R.string.worktree_name_prefix)).ifEmpty { it } }
    val title = session.title ?: branchTitle ?: stringResource(R.string.session_untitled)
    val menuLabel = stringResource(R.string.session_menu_open_label)
    val highlight = selectionHighlight(selected)
    if (tree != null) {
        // 枝（親のフォルダから縦の線、自分の所で角を丸めて横に折れる）。最後の行は自分の所で縦の線を止める。
        // 線は太めで端も丸く（M3 の丸みに合わせる。マスター指定 2026-11-23）
        val guide = MaterialTheme.colorScheme.outlineVariant
        val density = LocalDensity.current
        val guideX = with(density) { TreeGuideX.toPx() }
        val tick = with(density) { TreeTick.toPx() }
        val radius = with(density) { TreeCornerRadius.toPx() }
        val stroke = remember(density) { Stroke(width = with(density) { TreeStroke.toPx() }, cap = StrokeCap.Round) }
        Box(
            modifier.fillMaxWidth().drawWithCache {
                val mid = size.height / 2
                val branch = Path().apply {
                    moveTo(guideX, 0f)
                    lineTo(guideX, mid - radius)
                    quadraticTo(guideX, mid, guideX + radius, mid)
                    lineTo(guideX + tick, mid)
                    if (tree != TreeBranch.LAST) {
                        moveTo(guideX, mid - radius)
                        lineTo(guideX, size.height)
                    }
                }
                onDrawBehind { drawPath(branch, guide, style = stroke) }
            },
        ) {
            Box(Modifier.padding(start = TreeChildStart, end = PanelItemInset)) {
                CompactSessionRow(session, development, title, timeLabel, highlight, menuLabel, onClick, child = true) { menuOpen = true }
                SessionMenu(session, menuOpen, onDismiss = { menuOpen = false }, onRename = { renaming = true },
                    onWorktree = if (onCreateWorktree != null) ({ worktree = true }) else null,
                    onArchive = onArchive, onDelete = { confirmDelete = true })
            }
        }
    } else Box(modifier.padding(horizontal = PanelItemInset)) {
        // 更新順もフォルダ別と同じ1行の行（選んだ面がフォルダ別と同じ大きさになる。マスター指定 2026-11-23）
        CompactSessionRow(session, development, title, timeLabel, highlight, menuLabel, onClick) { menuOpen = true }
        SessionMenu(session, menuOpen, onDismiss = { menuOpen = false }, onRename = { renaming = true },
            onWorktree = if (onCreateWorktree != null) ({ worktree = true }) else null,
            onArchive = onArchive, onDelete = { confirmDelete = true })
    }
    // ワークツリーのセッションは、何が起きるかを説明してから選んでもらう（マスター指定 2026-11-24）
    if (confirmDelete && session.worktreeBranch != null) {
        WorktreeDeleteDialog(
            session = session,
            title = title,
            onDismiss = { confirmDelete = false },
            onArchive = { confirmDelete = false; onArchive() },
            onDelete = { confirmDelete = false; onDelete() },
        )
    }
    // 削除は元に戻せないので確認する（AGENTS.md 2.7: 確認は AlertDialog）
    if (confirmDelete && session.worktreeBranch == null) {
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
        jp.miyavi.android.sumahoclaw.feature.chat.WorktreeSheet(session, onDismiss = { worktree = false }, loadBranches = loadBranches) { name, base, fork ->
            worktree = false
            onCreateWorktree(name, base, fork)
        }
    }
}

/** セッションの1行（タイトル・時刻・目印）。後ろに選んだ面を敷く。更新順・フォルダ別で共通。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactSessionRow(
    session: SessionSummary,
    /** 開発の並びの行か。開発では 2 行目にブランチ（ワークツリー）と変更量、更新順ではタイトルと時刻だけ（マスター指定 2026-11-24）。 */
    development: Boolean,
    title: String,
    timeLabel: String?,
    highlight: SelectionHighlight,
    menuLabel: String,
    onClick: () -> Unit,
    /** 親（元のセッション）の下にぶら下がる行か。リポジトリは親で分かるので、名前の下に出さない。 */
    child: Boolean = false,
    onLongClick: () -> Unit,
) {
    // 2 行目: ワークツリーはそのブランチ、ほかは作業フォルダの今のブランチ（Git でなければ出さない）。変更量は作業フォルダの未コミットの分
    val folder = session.cwd?.trimEnd('/')
    val prefix = stringResource(R.string.worktree_name_prefix)
    val branch = if (!development) null
    else session.worktreeBranch?.let { it.removePrefix(prefix).ifEmpty { it } } ?: folder?.let { LocalHeadBranches.current[it] }
    val diff = if (!development) null else folder?.let { LocalFolderDiffs.current[it] }
    val twoLines = branch != null || diff != null
    Box {
        SelectionPill(highlight)
        Row(
            Modifier
                .fillMaxWidth()
                // 2 行の行は M3 の 2 行の一覧と同じ高さ（64dp）と上下 12dp（マスター指定 2026-11-24）
                .heightIn(min = if (twoLines) Dimens.listItemTwoLine else Dimens.minTouchTarget)
                .combinedClickable(
                    interactionSource = highlight.interactions,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = menuLabel,
                )
                .padding(horizontal = Spacing.m, vertical = if (twoLines) Spacing.m else Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (twoLines) {
                // 開発の 2 行。左に 名前／リポジトリ · ⑂ ブランチ +変更量（量はブランチのすぐ横）、
                // 右に時刻を 2 行の真ん中に一回り大きく（マスター指定 2026-11-24）
                val repo = session.projectFolder?.substringAfterLast('/')?.takeIf { !child && it.isNotBlank() }
                val selectedColor = highlight.headline.takeIf { it != MaterialTheme.colorScheme.onSurface }
                val metaColor = selectedColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = highlight.headline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        repo?.let {
                            Text(
                                if (branch != null || diff != null) "$it ·" else it,
                                style = MaterialTheme.typography.labelSmall,
                                color = metaColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        branch?.let { WorktreeBranchLabel(it, highlight.headline, worktree = session.worktreeBranch != null) }
                        diff?.let { DiffTotalsText(it, selectedColor) }
                    }
                }
                timeLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = metaColor, maxLines = 1) }
                SessionMarks(session)
            } else {
                // 1 行: 左にセッション名、右に時刻
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = highlight.headline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                timeLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                SessionMarks(session)
            }
        }
    }
}

/** セッションの変更量を読む（会話の画面が渡す。ワークツリーの削除の確認で使う）。 */
val LocalDiffLoader = androidx.compose.runtime.staticCompositionLocalOf<suspend (SessionSummary) -> jp.miyavi.android.sumahoclaw.core.data.SessionDiff?> { { null } }

/** 作業フォルダごとの未コミットの変更量（会話の画面が渡す）。 */
val LocalFolderDiffs = androidx.compose.runtime.compositionLocalOf<Map<String, jp.miyavi.android.sumahoclaw.core.data.DiffTotals>> { emptyMap() }

/** 作業フォルダごとの今のブランチ（会話の画面が渡す）。 */
val LocalHeadBranches = androidx.compose.runtime.compositionLocalOf<Map<String, String>> { emptyMap() }

/** 行のブランチ（アイコンと名前。長い名前は省略記号）。 */
@Composable
private fun WorktreeBranchLabel(branch: String, rowColor: Color, worktree: Boolean = false) {
    val shown = branch
    val cd = stringResource(if (worktree) R.string.session_worktree_label_cd else R.string.session_worktree_cd, branch)
    // 選んだ行（色の付いた面）の上では行の文字色に合わせ、ふだんは補足の色
    val color = if (rowColor == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurfaceVariant else rowColor
    Row(
        Modifier.widthIn(max = Dimens.branchLabelMaxWidth).semantics(mergeDescendants = true) { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(painterResource(R.drawable.ic_huge_branch), contentDescription = null, tint = color, modifier = Modifier.size(Dimens.smallIcon))
        Text(shown, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * 実行中・未読の目印（実行中 = tertiary、未読 = primary。AGENTS.md 2.2）。
 * 実行中は小さな回るプログレス、終わったら未読の点。両方は同時に出さない（実行中を優先。マスター指定 2026-11-24）。
 */
@Composable
private fun SessionMarks(session: SessionSummary) {
    when {
        session.running -> {
            val cd = stringResource(R.string.session_running_cd)
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.tertiary,
                strokeWidth = Dimens.runningIndicatorStroke,
                modifier = Modifier.size(Dimens.runningIndicator).semantics { contentDescription = cd },
            )
        }
        session.unread -> {
            val cd = stringResource(R.string.session_unread_cd)
            Box(Modifier.size(Dimens.unreadDot).background(MaterialTheme.colorScheme.primary, CircleShape).semantics { contentDescription = cd })
        }
        else -> Spacer(Modifier.size(Dimens.unreadDot))
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

/** 開発の並びの 1 行。 */
internal sealed interface DevRow {
    /** プロジェクトの見出し（ワークツリーはあるが、元のセッションが無いときだけ）。 */
    data class Header(val folder: String?, val count: Int) : DevRow
    /** セッションの行。[tree] があれば親の下の枝。[underHeader] は見出しの下の枝のときの見出しの id（閉じる用）。 */
    data class Session(val session: SessionSummary, val tree: TreeBranch? = null, val underHeader: String? = null) : DevRow
}

/**
 * 開発の並び（マスター指定 2026-11-24）。プロジェクトごとに:
 * - ワークツリーが無い: セッションをそのまま 1 行ずつ
 * - ワークツリーがある: いちばん新しい元のセッションを親にし、その下にワークツリーを枝で。ほかの元のセッションは続けて 1 行ずつ
 * - ワークツリーだけ（元のセッションが無い）: プロジェクトの見出しを親にする
 */
internal fun developmentRows(groups: List<Pair<String?, List<SessionSummary>>>): List<DevRow> = buildList {
    groups.forEach { (folder, rows) ->
        val worktrees = rows.filter { it.worktreeBranch != null }
        val mains = rows.filter { it.worktreeBranch == null }
        if (worktrees.isEmpty()) {
            mains.forEach { add(DevRow.Session(it)) }
            return@forEach
        }
        val parent = mains.firstOrNull()
        val headerId = if (parent == null) folder ?: "" else null
        if (parent != null) add(DevRow.Session(parent)) else add(DevRow.Header(folder, worktrees.size))
        worktrees.forEachIndexed { index, wt ->
            add(DevRow.Session(wt, if (index == worktrees.lastIndex) TreeBranch.LAST else TreeBranch.MIDDLE, headerId))
        }
        mains.drop(1).forEach { add(DevRow.Session(it)) }
    }
}

/** 作業フォルダごとのまとまり（まとまりは中のいちばん新しい更新日時の順。作業フォルダの無いものは最後）。 */
internal fun groupByFolder(sessions: List<SessionSummary>): List<Pair<String?, List<SessionSummary>>> =
    sessions.groupBy { it.projectFolder }
        .map { (folder, rows) -> folder to rows.sortedByDescending { it.updatedAt ?: 0 } }
        .sortedWith(compareBy<Pair<String?, List<SessionSummary>>> { it.first == null }.thenByDescending { it.second.first().updatedAt ?: 0 })

/** 履歴の見え方の切り替え（チャット = 更新日時の順の一覧、開発 = プロジェクトごと。押すたびに入れ替わる小さなボタン）。 */
@Composable
private fun GroupingToggle(grouping: SessionGrouping, onChange: (SessionGrouping) -> Unit) {
    TextButton(
        onClick = { onChange(if (grouping == SessionGrouping.UPDATED) SessionGrouping.FOLDER else SessionGrouping.UPDATED) },
        // 会話の送信ボタン（FilledIconButton の面）と同じ濃いテーマカラー（マスター指定 2026-11-24。以前は選んだ行の面の色で薄かった）
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.heightIn(min = Dimens.compactTouchTarget),
    ) {
        Icon(
            painterResource(if (grouping == SessionGrouping.FOLDER) R.drawable.ic_huge_source_code else R.drawable.ic_huge_ai_chat),
            contentDescription = null,
            modifier = Modifier.size(Dimens.smallIcon),
        )
        Text(
            stringResource(if (grouping == SessionGrouping.FOLDER) R.string.panel_group_folder else R.string.panel_group_updated),
            style = panelPartLabelStyle(),
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/** 作業フォルダの見出し（親の行。フォルダ名と件数）。押すと開閉。フルパスは読み上げと長押しのラベルで分かる。 */
@Composable
private fun FolderHeader(folder: String?, count: Int, open: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val name = folder?.substringAfterLast('/')?.ifEmpty { folder } ?: stringResource(R.string.panel_folder_none)
    // 見出しはプロジェクトの名前と件数だけ。ブランチと変更量は各行に出す（マスター指摘 2026-11-24）
    CollapsibleHeader(
        R.drawable.ic_huge_folder, name, count, open, modifier,
        description = folder?.let { "$name, $it" },
        onToggle = onToggle,
    )
}

/**
 * 未コミットの変更の「+12 −3」（追加は tertiary、削除は error。変更量のシートと同じ色）。
 * 選んだ行（色の付いた面）の上では、色分けをやめて行の文字色 [selectedColor] にする（tertiary・error は塗った面で読みにくい）。
 */
@Composable
private fun DiffTotalsText(diff: jp.miyavi.android.sumahoclaw.core.data.DiffTotals, selectedColor: Color? = null) {
    val colors = MaterialTheme.colorScheme
    val cd = stringResource(R.string.session_diff_cd, diff.additions, diff.deletions)
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            withStyle(androidx.compose.ui.text.SpanStyle(color = selectedColor ?: colors.tertiary)) { append("+${diff.additions}") }
            append(" ")
            withStyle(androidx.compose.ui.text.SpanStyle(color = selectedColor ?: colors.error)) { append("−${diff.deletions}") }
        },
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = Modifier.semantics { contentDescription = cd },
    )
}

/** 開閉できる見出しの行（作業フォルダ・アーカイブ済み）。アイコン・名前・件数・開閉の矢印。 */
@Composable
private fun CollapsibleHeader(
    icon: Int,
    name: String,
    count: Int,
    open: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val toggleLabel = stringResource(if (open) R.string.panel_folder_collapse else R.string.panel_folder_expand)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PanelItemInset)
            .clip(CircleShape)
            .clickable(onClickLabel = toggleLabel, onClick = onToggle)
            .heightIn(min = Dimens.minTouchTarget)
            .padding(start = PanelTextInset - PanelItemInset, end = Spacing.m, top = Spacing.s, bottom = Spacing.s)
            .semantics { description?.let { contentDescription = it } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Dimens.smallIcon))
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing?.invoke()
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
/** フォルダの下の行の、枝の形。 */
internal enum class TreeBranch { MIDDLE, LAST }

/** 枝の縦の線の位置（フォルダのアイコンの中ほど）、横の線の長さ、線の太さ、子の行の始まり。 */
private val TreeGuideX = PanelTextInset + Dimens.smallIcon / 2
private val TreeTick = Spacing.m
private val TreeStroke = 2.dp
private val TreeCornerRadius = Spacing.s
private val TreeChildStart = PanelTextInset + Dimens.smallIcon / 2 + Spacing.m

/**
 * クリップボードに写すセッションの情報（マスター指定 2026-11-28）。`sessions_history` などで会話を指すときに使う。
 * ID は Gateway のセッション ID（無ければ空）、Key は会話の鍵、Name は表示名（無ければ [untitled]）。
 */
internal fun sessionInfoText(session: SessionSummary, untitled: String): String = listOf(
    "SessionID: ${session.sessionId.orEmpty()}",
    "SessionKey: ${session.key}",
    "SessionName: ${session.title ?: untitled}",
).joinToString("\n")

/** 「セッション情報をコピー」の項目（長押しメニューの先頭）。 */
@Composable
private fun CopySessionInfoItem(session: SessionSummary, onDismiss: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val untitled = stringResource(R.string.session_untitled)
    DropdownMenuItem(
        text = { Text(stringResource(R.string.session_menu_copy_info)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_huge_copy), contentDescription = null) },
        onClick = {
            val text = sessionInfoText(session, untitled)
            onDismiss()
            // 端末が「コピーしました」を出すので、アプリからは知らせない（Android 13 以上）
            scope.launch {
                clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText(session.title ?: untitled, text)))
            }
        },
    )
}

/** セッションの長押しメニュー（情報をコピー・名前を変更・ワークツリーで分岐・アーカイブ・削除）。 */
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
        CopySessionInfoItem(session, onDismiss)
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

/** 看板の画像を、画面の透かす元に重ねる順（ガラスのボタンが画像を透かすように）。 */
private const val HERO_HAZE_Z = 1f

/** 親の大きさに合わせたうえで、上へ [overhang] だけはみ出して描く（大きさは親のまま）。 */
private fun Modifier.overhangTop(overhang: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val extra = overhang.roundToPx()
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, height + extra))
    layout(width, height) { placeable.place(0, -extra) }
}
