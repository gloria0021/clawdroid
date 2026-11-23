package jp.miyavi.android.clawdroid.feature.settings

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.designsystem.AppTitleBar
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.settings.AgentLayout
import jp.miyavi.android.clawdroid.core.settings.MAX_SIDEBAR_AGENTS
import jp.miyavi.android.clawdroid.core.settings.visible
import jp.miyavi.android.clawdroid.core.settings.ordered
import jp.miyavi.android.clawdroid.core.settings.reordered
import jp.miyavi.android.clawdroid.core.settings.withVisibility
import jp.miyavi.android.clawdroid.feature.chat.AgentAvatar

/**
 * サイドパネルに出すエージェントの並びと表示（設定 → エージェント）。
 * 右の持ち手を長押しして上下に動かすと並びが変わり、スイッチで表示する・しないを切り替える。
 * 表示するエージェントが 1 体だけなら、サイドパネルの切り替えのタブは出ない。
 */
@Composable
internal fun AgentLayoutScreen(agents: List<Agent>, layout: AgentLayout, onChange: (AgentLayout) -> Unit, onBack: () -> Unit) {
    val hazeState = rememberHazeState()
    // ドラッグのあいだは手元の並びで動かし、離したときに保存する
    val saved = remember(agents, layout) { layout.ordered(agents) }
    var items by remember(saved) { mutableStateOf(saved) }
    val listState = rememberLazyListState()
    val drag = remember { DragState() }
    val shownIds = remember(items, layout) { layout.visible(items).map(Agent::id).toSet() }
    val latestItems by rememberUpdatedState(items)
    val latestLayout by rememberUpdatedState(layout)

    fun move(from: Int, to: Int) {
        if (from == to || to !in items.indices) return
        items = items.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun commit() = onChange(latestLayout.reordered(latestItems.map(Agent::id)))

    ProvideScreenHaze(hazeState) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                AppTitleBar(
                    hazeState = hazeState,
                    title = { Text(stringResource(R.string.settings_agents_layout), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), onBack,
                            modifier = Modifier.padding(start = Spacing.s))
                    },
                    actions = {},
                )
            },
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.settings_agents_layout_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
                    )
                }
                itemsIndexed(items, key = { _, agent -> agent.id }) { index, agent ->
                    val dragging = drag.id == agent.id
                    val visible = agent.id in shownIds
                    val moveUp = stringResource(R.string.settings_agents_move_up)
                    val moveDown = stringResource(R.string.settings_agents_move_down)
                    ListItem(
                        modifier = Modifier
                            .widthIn(max = Dimens.maxReadingWidth)
                            .fillMaxWidth()
                            .then(if (dragging) Modifier.zIndex(1f) else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec()))
                            .graphicsLayer { if (dragging) translationY = drag.offset }
                            // TalkBack からも並べ替えられるように（AGENTS.md 2.11）
                            .semantics {
                                customActions = listOfNotNull(
                                    CustomAccessibilityAction(moveUp) { move(index, index - 1); commit(); true }.takeIf { index > 0 },
                                    CustomAccessibilityAction(moveDown) { move(index, index + 1); commit(); true }.takeIf { index < items.lastIndex },
                                )
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                        ),
                        leadingContent = { AgentAvatar(agent) },
                        headlineContent = { Text(agent.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = if (!visible) ({
                            Text(stringResource(R.string.settings_agents_hidden), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }) else null,
                        trailingContent = {
                            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                val showLabel = stringResource(R.string.settings_agents_show_cd, agent.name)
                                Switch(
                                    checked = visible,
                                    // 4 体出ているときは、ほかを出せない（1 体止めると選べる）
                                    enabled = visible || shownIds.size < MAX_SIDEBAR_AGENTS,
                                    onCheckedChange = { onChange(layout.withVisibility(agent.id, it, agents)) },
                                    modifier = Modifier.semantics { contentDescription = showLabel },
                                )
                                Box(
                                    Modifier
                                        .size(Dimens.minTouchTarget)
                                        .pointerInput(agent.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { drag.start(agent.id) },
                                                onDragEnd = { drag.stop(); commit() },
                                                onDragCancel = { drag.stop(); commit() },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    drag.offset += amount.y
                                                    drag.swapIfCrossed(listState, latestItems, ::move)
                                                },
                                            )
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(painterResource(R.drawable.ic_huge_drag), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                    )
                }
                item(key = "bottom") { Box(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

/** ドラッグ中の項目と、元の位置からのずれ（描画の中で読む）。 */
private class DragState {
    var id by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)

    fun start(id: String) {
        this.id = id
        offset = 0f
    }

    fun stop() {
        id = null
        offset = 0f
    }

    /** 隣の項目の中ほどを越えたら入れ替え、ずれをその分だけ戻す。 */
    fun swapIfCrossed(listState: LazyListState, items: List<Agent>, move: (Int, Int) -> Unit) {
        val current = id ?: return
        val from = items.indexOfFirst { it.id == current }.takeIf { it >= 0 } ?: return
        val infos = listState.layoutInfo.visibleItemsInfo
        val target = if (offset > 0) from + 1 else from - 1
        val neighbor = infos.firstOrNull { it.key == items.getOrNull(target)?.id } ?: return
        val crossed = if (offset > 0) offset > neighbor.size / 2f else -offset > neighbor.size / 2f
        if (!crossed) return
        move(from, target)
        offset += if (offset > 0) -neighbor.size.toFloat() else neighbor.size.toFloat()
    }
}
