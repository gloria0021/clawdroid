package jp.miyavi.android.clawdroid.feature.chat

import androidx.compose.foundation.layout.Arrangement
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
/** Gateway がワークツリーのブランチに付ける頭。 */
private const val WORKTREE_BRANCH_PREFIX = "openclaw/"

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
    // ブランチの候補（構成の変更では取り直す。軽い読み取りなので保存しない）
    var branches by remember { mutableStateOf<RepoBranches?>(null) }
    LaunchedEffect(session.key) {
        val loaded = runCatching { loadBranches(session) }.getOrNull()
        branches = loaded
        val initial = loaded?.head ?: loaded?.default
        if (!baseTouched && initial != null) base = initial
        baseLoading = false
    }
    // 手で入れた名前が既にあるブランチ（openclaw/<名前>）と重なると Gateway が断るので、作る前に止める。
    // 空なら Gateway が会話のタイトルから決め、重なれば -2, -3… と番号を付けて避ける
    val takenNames = remember(branches) {
        branches?.branches.orEmpty().filterNot { it.remote }.mapNotNull { it.name.removePrefix(WORKTREE_BRANCH_PREFIX).takeIf { n -> n != it.name } }.toSet()
    }
    val nameTaken = name.isNotEmpty() && name in takenNames
    val validName = (name.isEmpty() || WORKTREE_NAME.matches(name)) && !nameTaken
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
                        supportingText = { Text(stringResource(if (nameTaken) R.string.worktree_name_taken else R.string.worktree_name_hint)) },
                        isError = !validName,
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
            Button(
                onClick = { onCreate(name.ifBlank { null }, base.ifBlank { null }, fork) },
                enabled = validName,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s).heightIn(min = Dimens.minTouchTarget),
            ) {
                Icon(painterResource(R.drawable.ic_huge_branch_plus), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.worktree_create))
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
