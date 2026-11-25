package jp.miyavi.android.clawdroid.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.DirListing
import jp.miyavi.android.clawdroid.core.data.SessionListState
import jp.miyavi.android.clawdroid.core.designsystem.AppTitleBar
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.GlassPill
import jp.miyavi.android.clawdroid.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.clawdroid.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.shortcut.LabelTexts
import jp.miyavi.android.clawdroid.core.shortcut.MAX_SHORTCUTS
import jp.miyavi.android.clawdroid.core.shortcut.ShortcutDestination
import jp.miyavi.android.clawdroid.core.shortcut.ShortcutSlot
import jp.miyavi.android.clawdroid.core.shortcut.folderName
import jp.miyavi.android.clawdroid.core.shortcut.shortcutLabel
import jp.miyavi.android.clawdroid.feature.chat.AgentAvatar
import kotlinx.coroutines.launch

/** ショートカットの設定の画面の状態（spec 007 R8）。 */
data class ShortcutSettingsUi(
    val slots: List<ShortcutSlot>,
    val agents: List<Agent>,
    val lists: Map<String, SessionListState>,
)

class ShortcutSettingsActions(
    val onBack: () -> Unit,
    /** 枠を足す・直す（同じ ID なら置き換え）。 */
    val save: (ShortcutSlot) -> Unit,
    val remove: (id: String) -> Unit,
    val reorder: (ids: List<String>) -> Unit,
    /** そのエージェントのセッションの一覧を（まだなら）読む。 */
    val ensureLoaded: (agentId: String) -> Unit,
    val loadFolderCandidates: suspend (agentId: String) -> List<String>,
    val listDir: suspend (String?) -> DirListing?,
)

/** 新しい枠を作っているときの [editing] の値。 */
private const val NEW_SLOT = "\u0000new"

/**
 * ランチャーのショートカット（設定 → ランチャーのショートカット）。枠を縦に並べ、押すと編集のシート、
 * 右の持ち手を長押しして上下に動かすと並びが変わる（`AgentLayoutScreen` と同じ）。下に浮くピルで足す。
 */
@Composable
internal fun ShortcutSettingsScreen(state: ShortcutSettingsUi, actions: ShortcutSettingsActions) {
    val hazeState = rememberHazeState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // ドラッグのあいだは手元の並びで動かし、離したときに保存する
    var items by remember(state.slots) { mutableStateOf(state.slots) }
    val listState = rememberLazyListState()
    val drag = remember { ReorderDragState() }
    val latestItems by rememberUpdatedState(items)
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val texts = LabelTexts(stringResource(R.string.session_untitled))
    val full = stringResource(R.string.shortcuts_full)

    fun move(from: Int, to: Int) {
        if (from == to || to !in items.indices) return
        items = items.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun commit() = actions.reorder(latestItems.map(ShortcutSlot::id))

    ProvideScreenHaze(hazeState) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { GlassSnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
            topBar = {
                AppTitleBar(
                    hazeState = hazeState,
                    title = { Text(stringResource(R.string.settings_shortcuts), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), actions.onBack,
                            modifier = Modifier.padding(start = Spacing.s))
                    },
                    actions = {},
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        // 下に浮く「追加」のピルの分を空ける
                        bottom = padding.calculateBottomPadding() + Dimens.floatingButtonSize + Spacing.l * 2,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item(key = "hint") {
                        Text(
                            stringResource(R.string.shortcuts_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
                        )
                    }
                    if (items.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.shortcuts_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m),
                            )
                        }
                    }
                    itemsIndexed(items, key = { _, slot -> slot.id }) { index, slot ->
                        val agent = state.agents.firstOrNull { it.id == slot.agentId }
                        val label = shortcutLabel(slot, agent, state.lists, texts)
                        SlotRow(
                            slot = slot,
                            agent = agent,
                            label = label,
                            description = describe(slot, agent),
                            dragging = drag.id == slot.id,
                            dragOffset = { drag.offset },
                            canMoveUp = index > 0,
                            canMoveDown = index < items.lastIndex,
                            onMove = { delta -> move(index, index + delta); commit() },
                            onEdit = { editing = slot.id },
                            dragHandle = Modifier.pointerInput(slot.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { drag.start(slot.id) },
                                    onDragEnd = { drag.stop(); commit() },
                                    onDragCancel = { drag.stop(); commit() },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        drag.offset += amount.y
                                        drag.swapIfCrossed(listState, latestItems.map(ShortcutSlot::id), ::move)
                                    },
                                )
                            },
                        )
                    }
                }
                AddPill(
                    hazeState = hazeState,
                    onAdd = {
                        if (state.slots.size >= MAX_SHORTCUTS) scope.launch { snackbar.showSnackbar(full) } else editing = NEW_SLOT
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Spacing.l),
                )
            }
        }
        editing?.let { id ->
            val initial = state.slots.firstOrNull { it.id == id }
            // 編集中の枠が片付けで消えたら閉じる
            if (id != NEW_SLOT && initial == null) {
                androidx.compose.runtime.LaunchedEffect(id) { editing = null }
                return@let
            }
            ShortcutEditSheet(
                hazeState = hazeState,
                initial = initial,
                agents = state.agents,
                lists = state.lists,
                actions = actions,
                onSave = { slot -> editing = null; actions.save(slot) },
                onRemove = { editing = null; actions.remove(id) },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun SlotRow(
    slot: ShortcutSlot,
    agent: Agent?,
    label: String,
    description: String,
    dragging: Boolean,
    dragOffset: () -> Float,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    dragHandle: Modifier,
) {
    val moveUp = stringResource(R.string.settings_agents_move_up)
    val moveDown = stringResource(R.string.settings_agents_move_down)
    val editLabel = stringResource(R.string.shortcut_edit_cd, label)
    // アーカイブ済みは押せるが目立たせない（AGENTS.md 2.2 の無効）
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        modifier = Modifier
            .widthIn(max = Dimens.maxReadingWidth)
            .fillMaxWidth()
            .then(if (dragging) Modifier.zIndex(1f) else Modifier)
            .graphicsLayer { if (dragging) translationY = dragOffset() }
            .clickable(role = Role.Button, onClickLabel = editLabel, onClick = onEdit)
            // TalkBack からも並べ替えられるように（AGENTS.md 2.11）
            .semantics {
                customActions = listOfNotNull(
                    CustomAccessibilityAction(moveUp) { onMove(-1); true }.takeIf { canMoveUp },
                    CustomAccessibilityAction(moveDown) { onMove(1); true }.takeIf { canMoveDown },
                )
            },
        colors = ListItemDefaults.colors(
            containerColor = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        ),
        leadingContent = { AgentAvatar(agent) },
        headlineContent = {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (slot.archived) dim else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                if (slot.archived) "$description\n${stringResource(R.string.shortcut_archived)}" else description,
                style = MaterialTheme.typography.labelMedium,
                color = dim,
            )
        },
        trailingContent = {
            Box(dragHandle.size(Dimens.minTouchTarget), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_huge_drag), contentDescription = null, tint = dim)
            }
        },
    )
}

/** 行き先の説明（「キノ · ホーム」など）。 */
@Composable
private fun describe(slot: ShortcutSlot, agent: Agent?): String {
    val name = agent?.name ?: slot.agentId
    return when (val d = slot.destination) {
        is ShortcutDestination.Home -> stringResource(R.string.shortcut_desc_home, name)
        is ShortcutDestination.Folder -> d.cwd?.let { stringResource(R.string.shortcut_desc_folder, name, folderName(it)) }
            ?: stringResource(R.string.shortcut_desc_folder_default, name)
        is ShortcutDestination.Session -> stringResource(R.string.shortcut_desc_session, name)
    }
}

/** 下に浮く「追加」（AGENTS.md 2.7: 主操作は画面下部に浮く透過効果のピル）。 */
@Composable
private fun AddPill(hazeState: HazeState, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    GlassPill(hazeState = hazeState, modifier = modifier) {
        TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
            Icon(painterResource(R.drawable.ic_huge_plus), contentDescription = null)
            Text(stringResource(R.string.shortcuts_add), modifier = Modifier.padding(start = Spacing.s))
        }
    }
}
