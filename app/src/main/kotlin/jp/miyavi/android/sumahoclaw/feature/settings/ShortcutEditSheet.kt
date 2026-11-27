package jp.miyavi.android.sumahoclaw.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.Agent
import jp.miyavi.android.sumahoclaw.core.data.SessionListState
import jp.miyavi.android.sumahoclaw.core.designsystem.ConnectedChoiceRow
import jp.miyavi.android.sumahoclaw.core.designsystem.AppBottomSheet
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.sheetFieldColor
import jp.miyavi.android.sumahoclaw.core.shortcut.LabelTexts
import jp.miyavi.android.sumahoclaw.core.shortcut.ShortcutDestination
import jp.miyavi.android.sumahoclaw.core.shortcut.ShortcutSlot
import jp.miyavi.android.sumahoclaw.core.shortcut.folderName
import jp.miyavi.android.sumahoclaw.core.shortcut.shortcutLabel
import jp.miyavi.android.sumahoclaw.feature.chat.AgentAvatar
import jp.miyavi.android.sumahoclaw.feature.chat.FolderPickerSheet
import java.util.UUID

private enum class DestinationKind { HOME, FOLDER, SESSION }

/**
 * ショートカットの枠を作る・直すシート（spec 007 FR-031、R8）。
 * エージェント → 行き先（ホーム / 新しい会話 / 会話）→ 名前 → 作業フォルダ・会話を選ぶ、の順に上から並べ、
 * 長い会話の一覧はシートの中の面だけをスクロールさせる（AGENTS.md 2.5）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShortcutEditSheet(
    hazeState: HazeState,
    initial: ShortcutSlot?,
    agents: List<Agent>,
    lists: Map<String, SessionListState>,
    actions: ShortcutSettingsActions,
    onSave: (ShortcutSlot) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val id = rememberSaveable { initial?.id ?: UUID.randomUUID().toString() }
    var agentId by rememberSaveable { mutableStateOf(initial?.agentId ?: agents.firstOrNull()?.id) }
    var kind by rememberSaveable {
        mutableStateOf(
            when (initial?.destination) {
                is ShortcutDestination.Folder -> DestinationKind.FOLDER
                is ShortcutDestination.Session -> DestinationKind.SESSION
                else -> DestinationKind.HOME
            },
        )
    }
    var cwd by rememberSaveable { mutableStateOf((initial?.destination as? ShortcutDestination.Folder)?.cwd) }
    var sessionKey by rememberSaveable { mutableStateOf((initial?.destination as? ShortcutDestination.Session)?.sessionKey) }
    var label by rememberSaveable { mutableStateOf(initial?.customLabel.orEmpty()) }
    var pickingFolder by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(agentId) { agentId?.let(actions.ensureLoaded) }
    val list = agentId?.let { lists[it] }
    val agent = agents.firstOrNull { it.id == agentId }
    val destination: ShortcutDestination? = when (kind) {
        DestinationKind.HOME -> list?.home?.key?.let(ShortcutDestination::Home)
        DestinationKind.FOLDER -> ShortcutDestination.Folder(cwd)
        DestinationKind.SESSION -> sessionKey?.let(ShortcutDestination::Session)
    }
    val slot = if (agentId != null && destination != null) {
        ShortcutSlot(id = id, agentId = agentId!!, destination = destination, customLabel = label.trim().ifEmpty { null }, archived = initial?.archived == true)
    } else {
        null
    }
    val texts = LabelTexts(stringResource(R.string.session_untitled))
    val autoLabel = slot?.copy(customLabel = null)?.let { shortcutLabel(it, agent, lists, texts) }

    AppBottomSheet(
        onDismissRequest = onDismiss,
        hazeState = hazeState,
        // 名前を打つシートは、途中で引き下ろして閉じないように開き切る
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.weight(1f, fill = false).imePadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (initial == null) R.string.shortcut_edit_title_new else R.string.shortcut_edit_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.size(Spacing.xs))
                Button(onClick = { slot?.let(onSave) }, enabled = slot != null && slot != initial) { Text(stringResource(R.string.action_save)) }
            }
            FieldLabel(R.string.shortcut_field_agent)
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.l),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(agents, key = Agent::id) { a ->
                    FilterChip(
                        selected = a.id == agentId,
                        onClick = {
                            if (a.id != agentId) {
                                agentId = a.id
                                // 会話は選び直す（別のエージェントの会話は使えない）
                                sessionKey = null
                            }
                        },
                        label = { Text(a.name, maxLines = 1) },
                        leadingIcon = { AgentAvatar(a, size = Dimens.smallIcon) },
                    )
                }
            }
            FieldLabel(R.string.shortcut_field_destination)
            ConnectedChoiceRow(
                DestinationKind.entries, kind, { kind = it },
                Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
            ) {
                stringResource(
                    when (it) {
                        DestinationKind.HOME -> R.string.shortcut_dest_home
                        DestinationKind.FOLDER -> R.string.shortcut_dest_folder
                        DestinationKind.SESSION -> R.string.shortcut_dest_session
                    },
                )
            }
            FieldLabel(R.string.shortcut_field_label)
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                placeholder = autoLabel?.let { { Text(stringResource(R.string.shortcut_label_placeholder, it), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
            )
            when (kind) {
                DestinationKind.HOME -> if (list?.home == null) Loading()
                DestinationKind.FOLDER -> {
                    FieldLabel(R.string.shortcut_field_folder)
                    SheetField {
                        ListItem(
                            leadingContent = { Icon(painterResource(R.drawable.ic_huge_folder), contentDescription = null) },
                            headlineContent = { Text(cwd?.let(::folderName) ?: stringResource(R.string.shortcut_folder_default), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = cwd?.let { { Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                            trailingContent = { Icon(painterResource(R.drawable.ic_huge_chevron_right), contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = { pickingFolder = true }),
                        )
                    }
                }
                DestinationKind.SESSION -> {
                    FieldLabel(R.string.shortcut_field_session)
                    when {
                        list == null || list.loading && list.sessions.isEmpty() -> Loading()
                        list.sessions.isEmpty() -> Text(
                            stringResource(R.string.shortcut_sessions_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                        )
                        // 長い一覧はこの面の中だけをスクロールさせる
                        else -> SheetField(Modifier.weight(1f, fill = false)) {
                            LazyColumn {
                                items(list.sessions, key = { it.key }) { row ->
                                    val selected = row.key == sessionKey
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                row.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.session_untitled),
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        supportingContent = row.projectFolder?.let { { Text(folderName(it), style = MaterialTheme.typography.labelMedium, maxLines = 1) } },
                                        trailingContent = if (selected) ({ Icon(painterResource(R.drawable.ic_huge_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary) }) else null,
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = { sessionKey = row.key }),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (initial != null) {
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.s),
                ) {
                    Icon(painterResource(R.drawable.ic_huge_delete), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.shortcut_remove), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = Spacing.s))
                }
            } else {
                Spacer(Modifier.size(Spacing.l))
            }
        }
    }
    if (pickingFolder) {
        val pickFor = agentId
        FolderPickerSheet(
            hazeState = hazeState,
            current = cwd,
            load = { pickFor?.let { actions.loadFolderCandidates(it) }.orEmpty() },
            listDir = actions.listDir,
            onPick = { pickingFolder = false; cwd = it },
            onDismiss = { pickingFolder = false },
        )
    }
}

@Composable
private fun FieldLabel(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xl, end = Spacing.l, top = Spacing.l, bottom = Spacing.s),
    )
}

/** シートの中の不透明な面（AGENTS.md 2.5）。 */
@Composable
private fun SheetField(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = sheetFieldColor(),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs),
    ) { content() }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(Spacing.l), contentAlignment = Alignment.Center) { LoadingIndicator() }
}
