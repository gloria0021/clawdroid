package jp.miyavi.android.sumahoclaw.feature.agent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import jp.miyavi.android.sumahoclaw.core.designsystem.bodyContainerColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import jp.miyavi.android.sumahoclaw.core.designsystem.motionItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.SkillOrigin
import jp.miyavi.android.sumahoclaw.core.data.SkillState
import jp.miyavi.android.sumahoclaw.core.data.SkillStatus
import jp.miyavi.android.sumahoclaw.core.designsystem.AppBottomSheet
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.FIELD_OPACITY
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassPill
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.sheetFieldColor
import jp.miyavi.android.sumahoclaw.feature.files.MarkdownPreview

/**
 * スキルのタブ（spec 004 US3）。Material 3 の一覧として組む（マスター指定 2026-11-24: デザインに拘る）:
 * 出どころの FilterChip の列 → フィルタの説明の面（参照パスと説明の 2 段）→ 状態ごとの小見出し（件数バッジ・開閉）→ ListItem。
 * 左端は 16dp にそろえ、区切りは余白で取る（AGENTS.md 2.4）。未対応はふだん閉じる。検索中はすべて開く。
 */
@Composable
internal fun AgentSkills(
    state: AgentSettingsUiState,
    query: String,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    onToggle: (SkillStatus, Boolean) -> Unit,
    onOpen: (SkillStatus) -> Unit,
) {
    when (val skills = state.skills) {
        Load.Loading -> jp.miyavi.android.sumahoclaw.feature.files.LoadingOrFailed(
            failed = false, message = R.string.agent_settings_load_failed, onRetry = onRetry, modifier = Modifier.padding(contentPadding),
        )
        is Load.Failed -> UnavailableOrFailed(skills.unsupported, onRetry, Modifier.padding(contentPadding))
        is Load.Loaded -> {
            var collapsed by rememberSaveable { mutableStateOf(listOf(SkillState.UNMET.name)) }
            var filter by rememberSaveable { mutableStateOf(SkillFilter.ALL) }
            val searching = query.isNotBlank()
            val filterCounts = remember(skills.value) { SkillFilter.entries.associateWith { f -> skills.value.count(f::matches) } }
            val groups = remember(skills.value, filter, query) {
                val q = query.trim()
                skills.value.asSequence()
                    .filter(filter::matches)
                    .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.description?.contains(q, ignoreCase = true) == true }
                    .groupBy { it.state ?: SkillState.ENABLED }
                    .toSortedMap(compareBy { it.ordinal })
            }
            LazyColumn(contentPadding = contentPadding, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                item(key = "filters") { FilterChips(filter, filterCounts) { filter = it } }
                item(key = "info") { FilterInfo(filter) }
                groups.forEach { (skillState, rows) ->
                    val open = searching || skillState.name !in collapsed
                    item(key = "group:${skillState.name}") {
                        GroupHeader(skillState, rows.size, open, enabled = !searching, modifier = motionItem()) {
                            collapsed = if (open) collapsed + skillState.name else collapsed - skillState.name
                        }
                    }
                    // 未対応の意味（オンでも読み込まれない・オフにする必要は無い）を見出しの下に（マスター指定 2026-11-24）
                    if (open && skillState == SkillState.UNMET) item(key = "note:UNMET") { UnmetNote(modifier = motionItem()) }
                    if (open) items(rows, key = { it.skillKey }) { skill ->
                        SkillRow(
                            skill = skill,
                            enabled = state.enabled(skill),
                            // 選んだフィルタと同じ出どころの印は出さない（どれも同じで、情報にならない）
                            showOrigin = filter == SkillFilter.ALL,
                            onToggle = { onToggle(skill, it) },
                            onOpen = { onOpen(skill) },
                            modifier = motionItem(),
                        )
                    }
                }
            }
        }
    }
}

/** 出どころのフィルタ（すべて・専用・学習・共有など）。1 つだけ選ぶ。件数が 0 のものは出さない。 */
enum class SkillFilter(private val origins: Set<SkillOrigin>?) {
    ALL(null),
    /**
     * 全エージェントで使えるもの。`~/.openclaw/skills`（OpenClaw だけ）と `~/.agents/skills`（ほかの AI ツールとも共有）を
     * まとめる（専用が skills と .agents/skills をまとめるのと同じ軸。マスター指定 2026-11-24）。
     */
    SHARED(setOf(SkillOrigin.MANAGED, SkillOrigin.PERSONAL)),
    AGENT(setOf(SkillOrigin.WORKSPACE, SkillOrigin.PROJECT)),
    WORKSHOP(setOf(SkillOrigin.WORKSHOP)),
    EXTRA(setOf(SkillOrigin.EXTRA)),
    LIBRARY(setOf(SkillOrigin.LIBRARY)),
    PLUGIN(setOf(SkillOrigin.PLUGIN)),
    BUNDLED(setOf(SkillOrigin.BUNDLED, SkillOrigin.UNKNOWN)),
    ;

    fun matches(skill: SkillStatus): Boolean = origins == null || skill.origin in origins
}

/** 出どころの FilterChip の列（横に流れる。選んだものに M3 の印）。左右の余白は一覧と同じ 16dp。 */
@Composable
private fun FilterChips(selected: SkillFilter, counts: Map<SkillFilter, Int>, onSelect: (SkillFilter) -> Unit) {
    Row(
        Modifier
            .widthIn(max = Dimens.maxReadingWidth)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        SkillFilter.entries.forEach { f ->
            val n = counts[f] ?: 0
            if (f == SkillFilter.ALL || n > 0) {
                val isSelected = selected == f
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(f) },
                    label = { Text(stringResource(R.string.skill_filter_label, stringResource(f.label()), n)) },
                    leadingIcon = if (isSelected) {
                        { Icon(painterResource(R.drawable.ic_huge_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                )
            }
        }
    }
}

private fun SkillFilter.label(): Int = when (this) {
    SkillFilter.ALL -> R.string.skill_filter_all
    SkillFilter.AGENT -> R.string.skill_origin_workspace
    SkillFilter.WORKSHOP -> R.string.skill_origin_workshop
    SkillFilter.SHARED -> R.string.skill_origin_managed
    SkillFilter.LIBRARY -> R.string.skill_origin_library
    SkillFilter.EXTRA -> R.string.skill_origin_extra
    SkillFilter.PLUGIN -> R.string.skill_origin_plugin
    SkillFilter.BUNDLED -> R.string.skill_origin_bundled
}

/** フィルタの説明（選んだときに出す。どこにあって、誰が使えるか）。 */
private fun SkillFilter.explanation(): Int? = when (this) {
    SkillFilter.ALL -> null
    SkillFilter.AGENT -> R.string.skill_filter_desc_agent
    SkillFilter.WORKSHOP -> R.string.skill_filter_desc_workshop
    SkillFilter.SHARED -> R.string.skill_filter_desc_shared
    SkillFilter.LIBRARY -> R.string.skill_filter_desc_library
    SkillFilter.EXTRA -> R.string.skill_filter_desc_extra
    SkillFilter.PLUGIN -> R.string.skill_filter_desc_plugin
    SkillFilter.BUNDLED -> R.string.skill_filter_desc_bundled
}

/** フィルタの参照パス（説明の 1 段目。場所の決まっていないものは null）。 */
private fun SkillFilter.path(): Int? = when (this) {
    SkillFilter.AGENT -> R.string.skill_filter_path_agent
    SkillFilter.WORKSHOP -> R.string.skill_filter_path_workshop
    SkillFilter.SHARED -> R.string.skill_filter_path_shared
    SkillFilter.EXTRA -> R.string.skill_filter_path_extra
    else -> null
}

/**
 * フィルタの説明の面（本文の下地の角丸の面。内側は左右 12dp・上下 8dp。AGENTS.md 2.4）。
 * 1 段目に参照パス（等幅）、2 段目に説明。「すべて」では全エージェント共通の注記。
 */
@Composable
private fun FilterInfo(filter: SkillFilter) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = bodyContainerColor(),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .widthIn(max = Dimens.maxReadingWidth)
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.xs),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Icon(
                painterResource(if (filter.path() != null) R.drawable.ic_huge_folder else R.drawable.ic_huge_info),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs / 2).size(Dimens.smallIcon),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                filter.path()?.let { path ->
                    Text(
                        stringResource(path),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = colors.onSurface,
                    )
                }
                Text(
                    stringResource(filter.explanation() ?: R.string.skill_scope_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** 状態の小見出し（M3 の一覧の見出し: titleSmall、件数バッジ、開閉の印）。左端は一覧と同じ 16dp。 */
@Composable
private fun GroupHeader(skillState: SkillState, count: Int, open: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val toggleLabel = stringResource(if (open) R.string.panel_folder_collapse else R.string.panel_folder_expand)
    Row(
        modifier
            .widthIn(max = Dimens.maxReadingWidth)
            .fillMaxWidth()
            .padding(top = Spacing.m)
            .clickable(enabled = enabled, onClickLabel = toggleLabel, onClick = onToggle)
            .heightIn(min = Dimens.minTouchTarget)
            .padding(horizontal = Spacing.l)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(stringResource(skillState.label()), style = MaterialTheme.typography.titleSmall, color = colors.primary)
        Surface(color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer, shape = CircleShape) {
            Text(count.toString(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs / 2))
        }
        Spacer(Modifier.weight(1f))
        if (enabled) {
            Icon(
                painterResource(if (open) R.drawable.ic_huge_arrow_up else R.drawable.ic_huge_arrow_down),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(Dimens.smallIcon),
            )
        }
    }
}

/** 未対応のまとまりの説明（見出しの下、左端は一覧と同じ 16dp）。 */
@Composable
private fun UnmetNote(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.skill_unmet_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.s),
    )
}

/**
 * スキルの行（M3 の ListItem）。先頭は丸い面に絵文字（無ければスキルのアイコン）、見出しは名前、
 * 補足は出どころの印と簡易説明、未対応なら理由、末尾はスイッチ。
 */
@Composable
private fun SkillRow(
    skill: SkillStatus,
    enabled: Boolean,
    showOrigin: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dim = skill.state == SkillState.DISABLED
    val reason = if (skill.state == SkillState.UNMET) shortReason(skill) else null
    val summary = remember(skill.description) { skill.description?.lineSequence()?.map(String::trim)?.firstOrNull(String::isNotEmpty) }
    val badge = showOrigin && skill.origin != SkillOrigin.BUNDLED && skill.origin != SkillOrigin.UNKNOWN
    ListItem(
        headlineContent = {
            Text(
                skill.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (dim) colors.onSurfaceVariant.copy(alpha = DISABLED_ALPHA) else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        // 2 行目に簡易説明（説明の 1 行目）。頭に出どころの印。未対応なら、その下に理由を 1 行（マスター指定 2026-11-24）
        supportingContent = if (badge || summary != null || reason != null) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs / 2)) {
                    if (badge || summary != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            if (badge) OriginBadge(skill.origin)
                            summary?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (dim) colors.onSurfaceVariant.copy(alpha = DISABLED_ALPHA) else colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    reason?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = colors.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else null,
        leadingContent = {
            Surface(color = colors.surfaceContainerHighest, shape = CircleShape, modifier = Modifier.size(Dimens.listLeading)) {
                Box(contentAlignment = Alignment.Center) {
                    if (skill.emoji != null) Text(skill.emoji, style = MaterialTheme.typography.titleMedium)
                    else Icon(painterResource(R.drawable.ic_huge_skill), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Dimens.smallIcon))
                }
            }
        },
        trailingContent = if (skill.state != null) {
            {
                val toggleLabel = stringResource(R.string.skill_toggle_cd, skill.name)
                Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.semantics { contentDescription = toggleLabel })
            }
        } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().clickable(onClick = onOpen),
    )
}

/**
 * 出どころの印（M3 のバッジの形 `shapes.full`）。このエージェント専用のもの（専用・学習）は塗り、
 * 全エージェント共通のもの（共有・AIツール共通など）は枠だけ。
 */
@Composable
private fun OriginBadge(origin: SkillOrigin) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(origin.description())
    Surface(
        color = if (origin.agentOnly) colors.secondaryContainer else Color.Transparent,
        contentColor = if (origin.agentOnly) colors.onSecondaryContainer else colors.onSurfaceVariant,
        border = if (origin.agentOnly) null else androidx.compose.foundation.BorderStroke(Dp.Hairline, colors.outline),
        shape = CircleShape,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(stringResource(origin.label()), style = MaterialTheme.typography.labelSmall, maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs / 2))
    }
}

private fun SkillOrigin.label(): Int = when (this) {
    SkillOrigin.WORKSPACE -> R.string.skill_origin_workspace
    SkillOrigin.PROJECT -> R.string.skill_origin_project
    SkillOrigin.WORKSHOP -> R.string.skill_origin_workshop
    SkillOrigin.MANAGED -> R.string.skill_origin_managed
    SkillOrigin.LIBRARY -> R.string.skill_origin_library
    SkillOrigin.PERSONAL -> R.string.skill_origin_personal
    SkillOrigin.EXTRA -> R.string.skill_origin_extra
    SkillOrigin.PLUGIN -> R.string.skill_origin_plugin
    SkillOrigin.BUNDLED, SkillOrigin.UNKNOWN -> R.string.skill_origin_bundled
}

private fun SkillOrigin.description(): Int = when (this) {
    SkillOrigin.WORKSPACE -> R.string.skill_origin_desc_workspace
    SkillOrigin.PROJECT -> R.string.skill_origin_desc_project
    SkillOrigin.WORKSHOP -> R.string.skill_origin_desc_workshop
    SkillOrigin.MANAGED -> R.string.skill_origin_desc_managed
    SkillOrigin.LIBRARY -> R.string.skill_origin_desc_library
    SkillOrigin.PERSONAL -> R.string.skill_origin_desc_personal
    SkillOrigin.EXTRA -> R.string.skill_origin_desc_extra
    SkillOrigin.PLUGIN -> R.string.skill_origin_desc_plugin
    SkillOrigin.BUNDLED, SkillOrigin.UNKNOWN -> R.string.skill_origin_desc_bundled
}

/** 足りないものの短い 1 行（一覧用）。詳しくはシート。 */
@Composable
private fun shortReason(skill: SkillStatus): String? = missingLines(skill).firstOrNull()

private fun SkillState?.label(): Int = when (this) {
    SkillState.ENABLED, null -> R.string.skill_state_enabled
    SkillState.UNMET -> R.string.skill_state_unmet
    SkillState.DISABLED -> R.string.skill_state_disabled
}

/** 検索中の下部のピル（入力欄なので不透明度 80%。AGENTS.md 2.10）。戻るで閉じる。 */
@Composable
internal fun SkillSearchPill(hazeState: HazeState, query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onClose)
    val colors = MaterialTheme.colorScheme
    GlassPill(hazeState = hazeState, fixedOpacity = FIELD_OPACITY, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_huge_search), contentDescription = null, modifier = Modifier.padding(start = Spacing.m))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.m).focusRequester(focus),
                decorationBox = { field ->
                    Box {
                        if (query.isEmpty()) {
                            Text(stringResource(R.string.skill_search_placeholder), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                        }
                        field()
                    }
                },
            )
            IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_huge_close), stringResource(R.string.skill_search_close_cd)) }
        }
    }
}

/** スキルの詳細（共通のボトムシート）。足りないもの、SKILL.md（不透明な面の中だけスクロール）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SkillDetailSheet(detail: SkillDetail, hazeState: HazeState, onDismiss: () -> Unit) {
    val skill = detail.skill
    AppBottomSheet(onDismissRequest = onDismiss, hazeState = hazeState, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(listOfNotNull(skill.emoji, skill.name).joinToString(" "), style = MaterialTheme.typography.titleMedium)
            Text(
                listOf(stringResource(skill.state.label()), stringResource(skill.origin.description())).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            missingLines(skill).takeIf { it.isNotEmpty() }?.let { lines ->
                Text(
                    stringResource(R.string.skill_missing_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = Spacing.s),
                )
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (skill.state == SkillState.UNMET) {
                    Text(
                        stringResource(R.string.skill_unmet_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
        Surface(
            color = sheetFieldColor(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s).navigationBarsPadding(),
        ) {
            when (detail) {
                is SkillDetail.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = Dimens.imagePlaceholder), contentAlignment = Alignment.Center) { LoadingIndicator() }
                is SkillDetail.Failed -> Text(
                    stringResource(R.string.skill_file_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.l),
                )
                is SkillDetail.Ready -> MarkdownPreview(detail.blocks, search = null, contentPadding = PaddingValues(vertical = Spacing.m))
            }
        }
    }
}

@Composable
private fun missingLines(skill: SkillStatus): List<String> {
    val m = skill.missing
    return listOfNotNull(
        m.bins.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.skill_missing_bins, it.joinToString(", ")) },
        m.anyBins.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.skill_missing_any_bins, it.joinToString(", ")) },
        m.env.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.skill_missing_env, it.joinToString(", ")) },
        m.config.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.skill_missing_config, it.joinToString(", ")) },
        m.os.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.skill_missing_os, it.joinToString(", ")) },
        stringResource(R.string.skill_missing_allowlist).takeIf { skill.blockedByAllowlist },
        stringResource(R.string.skill_missing_agent_filter).takeIf { skill.blockedByAgentFilter },
    )
}
