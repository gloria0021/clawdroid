package jp.miyavi.android.sumahoclaw.feature.agent

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassMenuDivider
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassDropdownMenu
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.Agent
import jp.miyavi.android.sumahoclaw.core.data.AgentConfig
import jp.miyavi.android.sumahoclaw.core.data.Configured
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.feature.chat.AgentAvatar
import jp.miyavi.android.sumahoclaw.feature.files.LoadingOrFailed

/**
 * 概要（spec 004 US2、FR-010）。モデルとツール。openclaw.json のエージェントの定義を、既定から引き継いだかの印と一緒に
 * 見て分かる形で並べる（思考の段階のメーター、プロファイルの選択、許可・拒否のチップ）。読むだけ。
 */
@Composable
internal fun AgentOverview(
    state: AgentSettingsUiState,
    contentPadding: PaddingValues,
    /** 名前と絵文字を変える（Gateway に `agents.update` が無ければ null で、ボタンを出さない）。 */
    onEditIdentity: (() -> Unit)?,
    /** 既定のモデル・思考を選ぶポップアップ（Gateway に手段が無ければ null で、押せない）。 */
    modelMenu: AgentMenu? = null,
    thinkingMenu: AgentMenu? = null,
    onRetryConfig: () -> Unit,
) {
    val agent = state.agent
    if (agent == null) {
        LoadingOrFailed(failed = !state.agentsLoading, message = R.string.agent_settings_agent_missing, onRetry = {}, modifier = Modifier.padding(contentPadding))
        return
    }
    val created = createdText(agent)
    val config = (state.config as? Load.Loaded)?.value
    val profiles = toolProfiles()
    val levels = agent.thinkingLevels.map { it.id }.ifEmpty { DEFAULT_THINKING_LEVELS }
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hero") { Hero(agent, created, onEditIdentity) }
        when (val c = state.config) {
            Load.Loading -> Unit
            is Load.Failed -> item(key = "configFailed") {
                ConfigNotice(
                    stringResource(if (c.unsupported) R.string.agent_overview_config_unsupported else R.string.agent_overview_config_failed),
                    onRetry = if (c.unsupported) null else onRetryConfig,
                )
            }
            // 読めたときの出どころの説明は出さない（マスター指定 2026-11-24）
            is Load.Loaded -> if (c.value == null) item(key = "configMissing") {
                ConfigNotice(stringResource(R.string.agent_overview_config_missing), onRetry = null)
            }
        }

        // ---- モデル（モデルと思考を 1 行に。マスター指定 2026-11-24）
        section("model", R.string.agent_overview_section_model)
        // 保存中は選んだ値を先に見せる（null は全体の既定に戻す。マスター指定 2026-11-26）
        val pending = state.pendingConfig
        fun savedTick(field: ConfigField) = state.configSaved?.takeIf { it.first == field }?.second ?: 0
        val model = pending?.takeIf { it.field == ConfigField.MODEL }
            ?.let { p -> (p.value ?: config?.defaultModel)?.let { Configured(it, inherited = p.value == null) } }
            ?: config?.model ?: agent.defaultModel?.let { Configured(it, inherited = false) }
        cellRows(
            "model",
            listOfNotNull(
                Cell(
                    R.drawable.ic_huge_model, R.string.agent_settings_model, model?.inherited == true, menu = modelMenu,
                    saving = pending?.field == ConfigField.MODEL, savedTick = savedTick(ConfigField.MODEL),
                ) {
                    // 会話の画面のピルと同じ短い名前（Opus 5.5 など。長い参照名で折り返さない。マスター指定 2026-11-26）
                    Value(model?.value?.let { jp.miyavi.android.sumahoclaw.core.data.shortModelName(it) } ?: stringResource(R.string.agent_settings_model_unknown))
                },
                // 思考の既定は、定義が読めたら（未設定でも）出す。押して変えられる
                if (config != null) {
                    val thinking = pending?.takeIf { it.field == ConfigField.THINKING }
                        ?.let { p -> (p.value ?: config.defaultThinking)?.let { Configured(it, inherited = p.value == null) } }
                        ?: config.thinking
                    Cell(
                        R.drawable.ic_huge_effort, R.string.agent_overview_thinking, thinking?.inherited == true, menu = thinkingMenu,
                        saving = pending?.field == ConfigField.THINKING, savedTick = savedTick(ConfigField.THINKING),
                    ) {
                        if (thinking != null) LevelMeter(levels, thinking.value) else Value(stringResource(R.string.agent_settings_model_unknown))
                    }
                } else null,
            ),
        )

        // ---- ツール（概要はモデルとツールだけ。スキルはスキルのタブ。マスター指定 2026-11-24）
        if (config != null) toolSection(config, profiles)
    }
}

/** 1 つの項目（2 つ並べる升目）。 */
private class Cell(
    val icon: Int,
    val title: Int,
    val inherited: Boolean,
    val menu: AgentMenu? = null,
    /** 保存中（∨ の位置に読み込み表示。押せない）。 */
    val saving: Boolean = false,
    /** 保存できた回数（増えたら ∨ の位置に一瞬チェック）。 */
    val savedTick: Int = 0,
    val value: @Composable () -> Unit,
)

/**
 * 升目を押すと出す選択のポップアップ（会話の画面のモデル・エフォートと同じ透過のドロップダウン。AGENTS.md 2.7）。
 * [options] が null のあいだは読み込み中。選ぶとすぐ保存する。
 */
internal class AgentMenu(
    val options: List<AgentChoice>?,
    val selected: String?,
    val onOpen: () -> Unit,
    val onSelect: (String?) -> Unit,
)

/** 項目を 1 行に 2 つまで並べる（マスター指定 2026-11-24）。 */
private fun LazyListScope.cellRows(key: String, cells: List<Cell>) {
    cells.chunked(2).forEachIndexed { index, pair ->
        item(key = "$key:$index") {
            // 升目の内側に 4dp の余白（押せる升目の反応の形のため）。外側と合わせて左端は 16dp
            Row(
                Modifier.readingWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                pair.forEach { cell -> CellView(cell, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * 横いっぱいを使う項目（プロファイルの 4 つの選択を 1 行で見せる）。[groupStart] なら上に区切りの見出しと同じ余白を取り、
 * 新しいまとまりの始まりとして見せる（マスター指摘 2026-11-24）。
 */
private fun LazyListScope.fullRow(key: String, cell: Cell, groupStart: Boolean = false) {
    item(key = key) {
        CellView(
            cell,
            Modifier.readingWidth().padding(start = Spacing.m, end = Spacing.m, top = if (groupStart) Spacing.l else Spacing.xs, bottom = Spacing.xs),
        )
    }
}

@Composable
private fun CellView(cell: Cell, modifier: Modifier) {
    val menu = cell.menu
    var expanded by remember { mutableStateOf(false) }
    val click: (() -> Unit)? = menu?.let { { it.onOpen(); expanded = true } }
    Box(modifier) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (click != null) {
                    Modifier.clickable(enabled = !cell.saving, onClickLabel = stringResource(R.string.agent_overview_change_cd), onClick = click)
                } else Modifier,
            )
            .padding(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(painterResource(cell.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Dimens.smallIcon))
            Text(
                stringResource(cell.title),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (cell.inherited) InheritedBadge()
            // 押して変えられる項目の印。保存中はその場所で読み込み表示、保存できたら一瞬チェック（マスター指定 2026-11-26）
            if (click != null) {
                jp.miyavi.android.sumahoclaw.core.designsystem.SaveStateIcon(
                    saving = cell.saving,
                    savedTick = cell.savedTick,
                    modifier = Modifier.size(Dimens.smallIcon),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_huge_arrow_down),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.smallIcon),
                    )
                }
            }
        }
        cell.value()
    }
    if (menu != null) {
        GlassDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AgentMenuItems(menu) { value ->
                expanded = false
                if (value != menu.selected) menu.onSelect(value)
            }
        }
    }
    }
}

/** ポップアップの中身（会話の画面の設定の選択肢と同じ並び: 名前と補足、選んでいるものにチェック）。 */
@Composable
internal fun AgentMenuItems(menu: AgentMenu, onSelect: (String?) -> Unit) {
    val options = menu.options
    Column(Modifier.widthIn(max = Dimens.settingMenuMaxWidth).heightIn(max = Dimens.settingMenuMaxHeight).verticalScroll(rememberScrollState()).padding(vertical = Spacing.xs)) {
        if (options == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(Spacing.m))
        } else {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(option.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            option.supporting?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    },
                    trailingIcon = if (option.id == menu.selected) ({ Icon(painterResource(R.drawable.ic_huge_check), contentDescription = null) }) else null,
                    onClick = { onSelect(option.id) },
                )
                // 「全体の既定を使う」と個別の選択肢のあいだに区切り（ガラスのメニューの区切り線。AGENTS.md 2.2）
                if (index == 0 && option.id == null && options.size > 1) GlassMenuDivider()
            }
        }
    }
}

private fun LazyListScope.toolSection(config: AgentConfig, profiles: List<Pair<String, String>>) {
    section("tools", R.string.agent_overview_section_tools)
    // 短い状態（実行・ファイル・昇格・Web 検索）を先に、ツールの名前が並ぶもの（プロファイル・許可・拒否）を最後に（マスター指定 2026-11-24）
    cellRows(
        "toolStates",
        listOfNotNull(
            config.execMode?.let { mode -> Cell(R.drawable.ic_huge_terminal, R.string.agent_overview_exec, mode.inherited) { Value(execModeLabel(mode.value)) } },
            config.workspaceOnly?.let { only ->
                Cell(R.drawable.ic_huge_folder, R.string.agent_overview_fs, only.inherited) {
                    Value(stringResource(if (only.value) R.string.agent_overview_fs_workspace_only else R.string.agent_overview_fs_anywhere))
                }
            },
            // 値はどれも同じ文字だけの形にそろえる（オン・オフに点を付けない。マスター指定 2026-11-26）
            config.elevated?.let { e ->
                Cell(R.drawable.ic_huge_key, R.string.agent_overview_elevated, e.inherited) {
                    Value(stringResource(if (e.value) R.string.agent_overview_on else R.string.agent_overview_off))
                }
            },
            // Web 検索はエージェントごとに決められない（openclaw.json 全体の tools.web.search）。ほかの項目と同じく
            // 全体から引き継いだ印を付ける。このエージェントが web_search を拒否していれば、使えないのでオフ（マスター指定 2026-11-26）
            config.webSearch?.let { enabled ->
                val denied = config.deny.any { it == "web_search" || it == "group:web" }
                Cell(R.drawable.ic_huge_globe, R.string.agent_overview_web_search, inherited = !denied) {
                    Value(
                        when {
                            denied || !enabled -> stringResource(R.string.agent_overview_off)
                            else -> config.webSearchProvider ?: stringResource(R.string.agent_overview_on)
                        },
                    )
                }
            },
        ),
    )
    // プロファイルは 4 つの選択を 1 行で見せるので横いっぱい
    config.toolProfile?.let { profile ->
        fullRow("profile", Cell(R.drawable.ic_huge_tool, R.string.agent_overview_profile, profile.inherited) { ProfileChoices(profiles, profile.value) }, groupStart = true)
    }
    cellRows(
        "toolLists",
        listOfNotNull(
            config.allow.takeIf { it.isNotEmpty() }?.let { Cell(R.drawable.ic_huge_check_circle, R.string.agent_overview_allow, false) { Chips(it, deny = false) } },
            config.deny.takeIf { it.isNotEmpty() }?.let { Cell(R.drawable.ic_huge_minus_circle, R.string.agent_overview_deny, false) { Chips(it, deny = true) } },
        ),
    )
}

private fun LazyListScope.section(key: String, title: Int) {
    item(key = "section:$key") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.readingWidth().padding(start = Spacing.l, end = Spacing.l, top = Spacing.xl, bottom = Spacing.xs).semantics { heading() },
        )
    }
}

@Composable
private fun Hero(agent: Agent, created: String?, onEditIdentity: (() -> Unit)?) {
    Column(
        Modifier.readingWidth().padding(horizontal = Spacing.l, vertical = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        AgentAvatar(agent, label = agent.name, size = Dimens.emptyStateIcon)
        Text(
            listOfNotNull(agent.emoji, agent.name).joinToString(" "),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        created?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        // 名前・絵文字は openclaw.json を Gateway の agents.update で書き換える（IDENTITY.md も Gateway が書き直す。FR-013 改訂）
        if (onEditIdentity != null) {
            TextButton(onClick = onEditIdentity) {
                Icon(painterResource(R.drawable.ic_huge_edit), contentDescription = null, modifier = Modifier.size(Dimens.smallIcon))
                Spacer(Modifier.size(Spacing.xs))
                Text(stringResource(R.string.agent_settings_edit_identity))
            }
        }
    }
}

@Composable
private fun Value(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun InheritedBadge() {
    val description = stringResource(R.string.agent_overview_inherited_cd)
    Pill(stringResource(R.string.agent_overview_inherited), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
        Modifier.semantics { contentDescription = description })
}

/** 思考の段階のメーター。今の段階までを塗り、名前を添える。 */
@Composable
private fun LevelMeter(levels: List<String>, current: String) {
    val colors = MaterialTheme.colorScheme
    val index = levels.indexOf(current)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        levels.forEachIndexed { i, _ ->
            Box(
                Modifier.weight(1f).height(Dimens.usageBarHeight)
                    .background(if (index >= 0 && i <= index) colors.primary else colors.surfaceContainerHighest, MaterialTheme.shapes.extraSmall),
            )
        }
    }
    Value(current)
}

/** ツールのプロファイル。4 つを並べ、選ばれているものを塗る（読むだけ）。知らない値はそれだけを出す。 */
@Composable
private fun ProfileChoices(profiles: List<Pair<String, String>>, current: String) {
    val colors = MaterialTheme.colorScheme
    val shown = if (profiles.any { it.first == current }) profiles else listOf(current to current)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        shown.forEach { (id, label) ->
            if (id == current) Pill(label, colors.secondaryContainer, colors.onSecondaryContainer)
            else Pill(label, Color.Transparent, colors.onSurfaceVariant, border = BorderStroke(Dp.Hairline, colors.outlineVariant))
        }
    }
}

/** 許可・拒否のチップ。拒否はエラーの色（止めているもの）。 */
@Composable
private fun Chips(items: List<String>, deny: Boolean) {
    val colors = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items.forEach { item ->
            if (deny) Pill(item, colors.errorContainer, colors.onErrorContainer)
            else Pill(item, colors.secondaryContainer, colors.onSecondaryContainer)
        }
    }
}

/** バッジ・チップの形（`shapes.full`、AGENTS.md 2.5）。 */
@Composable
private fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, border: BorderStroke? = null) {
    Surface(color = container, contentColor = content, shape = CircleShape, border = border, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs))
    }
}

@Composable
private fun ConfigNotice(text: String, onRetry: (() -> Unit)?) {
    Row(Modifier.readingWidth().padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.agent_settings_retry)) }
    }
}

@Composable
private fun toolProfiles(): List<Pair<String, String>> = listOf(
    "minimal" to stringResource(R.string.tool_profile_minimal),
    "coding" to stringResource(R.string.tool_profile_coding),
    "messaging" to stringResource(R.string.tool_profile_messaging),
    "full" to stringResource(R.string.tool_profile_full),
)

@Composable
private fun execModeLabel(mode: String): String = when (mode) {
    "auto" -> stringResource(R.string.exec_mode_auto)
    "ask" -> stringResource(R.string.exec_mode_ask)
    "full" -> stringResource(R.string.exec_mode_full)
    "deny" -> stringResource(R.string.exec_mode_deny)
    "allowlist" -> stringResource(R.string.exec_mode_allowlist)
    else -> mode
}

@Composable
private fun createdText(agent: Agent): String? {
    val via = when (agent.createdVia) {
        "operator" -> stringResource(R.string.agent_settings_created_operator)
        "agent" -> stringResource(R.string.agent_settings_created_agent)
        "template" -> stringResource(R.string.agent_settings_created_template)
        else -> null
    } ?: return null
    val context = LocalContext.current
    val date = agent.createdAt?.let { DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR) }
    val by = listOfNotNull(via, agent.creatorAgentId?.takeIf { agent.createdVia == "agent" }).joinToString(" ")
    return if (date != null) stringResource(R.string.agent_settings_created_by, by, date) else by
}

private fun Modifier.readingWidth(): Modifier = widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()

/** Gateway が段階の一覧を返さないときの並び（弱い → 強い）。 */
private val DEFAULT_THINKING_LEVELS = listOf("off", "minimal", "low", "medium", "high")

/** 選択肢の 1 つ。[id] が null なら「全体の既定に戻す」。 */
internal data class AgentChoice(val id: String?, val title: String, val supporting: String? = null)
