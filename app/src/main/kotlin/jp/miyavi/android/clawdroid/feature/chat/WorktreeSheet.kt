package jp.miyavi.android.clawdroid.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import jp.miyavi.android.clawdroid.core.data.WORKTREE_BRANCH_PREFIX
import jp.miyavi.android.clawdroid.core.data.freeWorktreeName
import jp.miyavi.android.clawdroid.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.clawdroid.core.designsystem.GlassMenuDivider
import jp.miyavi.android.clawdroid.core.data.RepoBranches
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.designsystem.AppBottomSheet
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.LocalScreenHazeState
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor

/** Gateway のワークツリー名の決まり（`^[a-z0-9][a-z0-9-]{0,63}$`）。 */
private val WORKTREE_NAME = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
private val WORKTREE_NAME_INVALID = Regex("[^a-z0-9-]")
private const val WORKTREE_NAME_MAX = 64

/**
 * 並行開発の名前を決めるための道具（会話の画面が渡す）。
 * 同じ名前を何度も使えるよう、使えない名前を知って -2, -3… を先に見せ、
 * その名前をアーカイブ済みのセッションが持っていれば「戻す」も選べるようにする（マスター指定 2026-11-24）。
 */
@Stable
internal class WorktreeNaming(
    /** 同じリポジトリで使えない名前（取れなければ null）。 */
    val takenNames: suspend (SessionSummary) -> Set<String>?,
    /** エージェントのアーカイブ済みのセッション。 */
    val archived: (agentId: String) -> List<SessionSummary>,
    /** アーカイブ済みのセッションを戻して開く。 */
    val restore: (SessionSummary) -> Unit,
)

internal val LocalWorktreeNaming = staticCompositionLocalOf<WorktreeNaming?> { null }

/**
 * 新規ワークツリーのシート（会話の画面のメニューと、サイドパネルの長押しから）。
 * 上に題と作業フォルダ、中の不透明な面にブランチ名・元にするブランチ・会話を引き継ぐか、下に横いっぱいの「作成」。
 * 元にするブランチは候補から選び、今のブランチを最初から入れておく（マスター指定 2026-11-23）。閉じるのはスワイプ・戻る・外側を押す。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorktreeSheet(
    session: SessionSummary,
    onDismiss: () -> Unit,
    /** 元にするブランチの候補を取る（取れなければ null。そのときは自由入力）。 */
    loadBranches: suspend (SessionSummary) -> RepoBranches? = { null },
    onCreate: (name: String?, base: String?, fork: Boolean) -> Unit,
) {
    val hazeState = LocalScreenHazeState.current ?: rememberHazeState()
    var name by rememberSaveable { mutableStateOf("") }
    var base by rememberSaveable { mutableStateOf("") }
    // 利用者が触ったら、あとから届いた今のブランチで上書きしない
    var baseTouched by rememberSaveable { mutableStateOf(false) }
    var baseLoading by rememberSaveable { mutableStateOf(true) }
    var fork by rememberSaveable { mutableStateOf(true) }
    // 名前がアーカイブ済みのセッションと重なったとき、戻すほうを選んだか（既定は新しく作る）
    var restoreChosen by rememberSaveable { mutableStateOf(false) }
    val naming = LocalWorktreeNaming.current
    // ブランチの候補（構成の変更では取り直す。軽い読み取りなので保存しない）
    var branches by remember { mutableStateOf<RepoBranches?>(null) }
    LaunchedEffect(session.key) {
        val loaded = runCatching { loadBranches(session) }.getOrNull()
        branches = loaded
        val initial = loaded?.head ?: loaded?.default
        if (!baseTouched && initial != null) base = initial
        baseLoading = false
    }
    // 使えない名前（Gateway の台帳とローカルのブランチ）。重なれば作るときに -2, -3… が付くので、先にその名前を見せる
    var takenNames by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(session.key) {
        takenNames = naming?.let { runCatching { it.takenNames(session) }.getOrNull() }.orEmpty()
    }
    val validName = name.isEmpty() || WORKTREE_NAME.matches(name)
    val resolvedName = if (name.isNotEmpty() && validName) freeWorktreeName(name, takenNames) else name
    // その名前のワークツリーをアーカイブ済みのセッションが持っていれば、戻すこともできる（削除済みなら番号だけ）
    val archivedOwner = remember(name, naming, session.key) {
        if (name.isEmpty() || naming == null) null
        else naming.archived(session.agentId.orEmpty()).firstOrNull {
            it.worktreeBranch == WORKTREE_BRANCH_PREFIX + name && it.projectFolder == session.projectFolder
        }
    }
    val restoring = archivedOwner != null && restoreChosen
    AppBottomSheet(
        onDismissRequest = onDismiss,
        hazeState = hazeState,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.imePadding().navigationBarsPadding()) {
            // 題と、どのフォルダから分けるか（フォルダ名だけ。長いパスで題を押し出さない）
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(stringResource(R.string.worktree_title), style = MaterialTheme.typography.titleMedium)
                session.cwd?.let { cwd ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Icon(
                            painterResource(R.drawable.ic_huge_folder),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Dimens.smallIcon),
                        )
                        Text(
                            cwd.trimEnd('/').substringAfterLast('/').ifEmpty { cwd },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Surface(
                color = sheetFieldColor(),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
            ) {
                Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.lowercase().replace(WORKTREE_NAME_INVALID, "-").take(WORKTREE_NAME_MAX) },
                        label = { Text(stringResource(R.string.worktree_name)) },
                        placeholder = { Text(stringResource(R.string.worktree_name_auto)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_huge_branch_plus), contentDescription = null) },
                        prefix = { Text(stringResource(R.string.worktree_name_prefix)) },
                        supportingText = {
                            Text(
                                if (resolvedName != name) stringResource(R.string.worktree_name_numbered, WORKTREE_BRANCH_PREFIX + resolvedName)
                                else stringResource(R.string.worktree_name_hint),
                            )
                        },
                        isError = !validName,
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(
                        visible = archivedOwner != null,
                        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                        label = "worktreeRestoreChoice",
                    ) {
                        RestoreChoice(name = name, numbered = resolvedName, restore = restoreChosen, onChange = { restoreChosen = it })
                    }
                    // 戻すときは元のワークツリーと会話がそのまま戻るので、元にするブランチと引き継ぎは要らない
                    AnimatedVisibility(
                        visible = !restoring,
                        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                        label = "worktreeNewFields",
                    ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    BaseBranchField(
                        value = base,
                        onValueChange = { base = it.trim(); baseTouched = true },
                        branches = branches,
                        loading = baseLoading,
                    )
                    // 会話を引き継ぐか（行のどこを押しても切り替わる）
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.minTouchTarget)
                            .toggleable(value = fork, role = Role.Switch, onValueChange = { fork = it })
                            .padding(vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(stringResource(R.string.worktree_fork), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.worktree_fork_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 行の toggleable が受けるので、スイッチ自身は押せなくする（読み上げが二重にならない）
                        Switch(checked = fork, onCheckedChange = null)
                    }
                    }
                    }
                }
            }
            Button(
                onClick = {
                    val owner = archivedOwner
                    if (restoring && owner != null && naming != null) {
                        onDismiss()
                        naming.restore(owner)
                    } else {
                        onCreate(name.ifBlank { null }, base.ifBlank { null }, fork)
                    }
                },
                enabled = validName,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s).heightIn(min = Dimens.minTouchTarget),
            ) {
                Icon(
                    painterResource(if (restoring) R.drawable.ic_huge_archive else R.drawable.ic_huge_branch_plus),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(if (restoring) R.string.worktree_restore else R.string.worktree_create))
            }
        }
    }
}

/**
 * 名前がアーカイブ済みのセッションのワークツリーと重なったときの選択。既定は番号を付けて新しく作る。
 * 戻すと、Gateway がワークツリーを復元し、そのセッションの会話をそのまま続けられる。
 */
@Composable
private fun RestoreChoice(name: String, numbered: String, restore: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.selectableGroup().padding(top = Spacing.xs)) {
        Text(
            stringResource(R.string.worktree_archived_owner, name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChoiceRow(stringResource(R.string.worktree_choice_new, numbered), null, selected = !restore) { onChange(false) }
        ChoiceRow(
            stringResource(R.string.worktree_choice_restore, name),
            stringResource(R.string.worktree_choice_restore_desc),
            selected = restore,
        ) { onChange(true) }
    }
}

@Composable
private fun ChoiceRow(label: String, description: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        // 行の selectable が受けるので、ボタン自身は押せなくする
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 元にするブランチ。候補が取れたら選ぶだけ（押すとガラスのメニュー。ローカル、区切ってリモート）、取れなければ自由入力。
 * 今のブランチには印を付ける（マスター指定 2026-11-23）。
 */
@Composable
private fun BaseBranchField(value: String, onValueChange: (String) -> Unit, branches: RepoBranches?, loading: Boolean) {
    val choices = branches?.branches.orEmpty()
    val picking = choices.isNotEmpty()
    var open by remember { mutableStateOf(false) }
    val press = remember { MutableInteractionSource() }
    LaunchedEffect(press, picking) {
        if (!picking) return@LaunchedEffect
        press.interactions.collect { if (it is PressInteraction.Release) open = true }
    }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = picking,
            label = { Text(stringResource(R.string.worktree_base)) },
            placeholder = { Text(stringResource(if (loading) R.string.worktree_base_loading else R.string.worktree_base_current)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_huge_branch), contentDescription = null) },
            trailingIcon = if (picking) {
                { Icon(painterResource(if (open) R.drawable.ic_huge_arrow_up else R.drawable.ic_huge_arrow_down), contentDescription = null) }
            } else null,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            interactionSource = press,
            modifier = Modifier.fillMaxWidth(),
        )
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val (local, remote) = remember(choices) { choices.partition { !it.remote } }
            local.forEach { BranchItem(it.name, it.name == value, it.name == branches?.head) { onValueChange(it.name); open = false } }
            if (local.isNotEmpty() && remote.isNotEmpty()) {
                GlassMenuDivider()
            }
            remote.forEach { BranchItem(it.name, it.name == value, false) { onValueChange(it.name); open = false } }
        }
    }
}

@Composable
private fun BranchItem(name: String, selected: Boolean, current: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            // 選んでいるものに印。幅はそろえる
            Icon(
                painterResource(R.drawable.ic_huge_check),
                contentDescription = null,
                modifier = Modifier.graphicsLayer { alpha = if (selected) 1f else 0f },
            )
        },
        trailingIcon = if (current) {
            { Text(stringResource(R.string.worktree_base_head), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected },
    )
}
