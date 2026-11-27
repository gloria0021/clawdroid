package jp.miyavi.android.sumahoclaw.feature.automation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.TimeZone
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.Automation
import jp.miyavi.android.sumahoclaw.core.data.AutomationDraft
import jp.miyavi.android.sumahoclaw.core.data.AutomationRun
import jp.miyavi.android.sumahoclaw.core.data.CronDeliveryMode
import jp.miyavi.android.sumahoclaw.core.data.CronPayload
import jp.miyavi.android.sumahoclaw.core.data.CronRunStatus
import jp.miyavi.android.sumahoclaw.core.data.ScheduleDraft
import jp.miyavi.android.sumahoclaw.core.designsystem.ConnectedChoiceRow
import jp.miyavi.android.sumahoclaw.core.designsystem.AppBottomSheet
import jp.miyavi.android.sumahoclaw.core.designsystem.AppTitleBar
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.FloatingIconButton
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassPill
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.sumahoclaw.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.accentContainer
import jp.miyavi.android.sumahoclaw.core.designsystem.bodyContainerColor
import jp.miyavi.android.sumahoclaw.core.designsystem.bodyTextStyle
import jp.miyavi.android.sumahoclaw.core.designsystem.codeTextStyle
import jp.miyavi.android.sumahoclaw.core.designsystem.sheetFieldColor
import jp.miyavi.android.sumahoclaw.feature.files.LoadingOrFailed
import jp.miyavi.android.sumahoclaw.feature.agent.AgentChoice
import jp.miyavi.android.sumahoclaw.feature.agent.AgentMenu
import jp.miyavi.android.sumahoclaw.feature.agent.AgentMenuItems
import jp.miyavi.android.sumahoclaw.feature.agent.Load
import jp.miyavi.android.sumahoclaw.feature.agent.THINKING_FALLBACK

/** 自動化の詳細の ViewModel をつなぐ部分。 */
@Composable
internal fun AutomationDetailScreen(viewModel: AutomationDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val messages = automationMessages()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event == AutomationEvent.Deleted) onBack()
            messages(event)?.let { snackbar.showSnackbar(it) }
        }
    }
    AutomationDetailContent(
        state = state,
        snackbar = snackbar,
        actions = AutomationDetailActions(
            back = onBack,
            retry = viewModel::load,
            selectTab = viewModel::selectTab,
            setEnabled = viewModel::setEnabled,
            commit = viewModel::commit,
            runNow = viewModel::runNow,
            askDelete = viewModel::askDelete,
            dismissDelete = viewModel::dismissDelete,
            delete = viewModel::delete,
            retryRuns = viewModel::loadRuns,
            loadMoreRuns = viewModel::loadMoreRuns,
            loadModels = viewModel::loadModelCatalog,
        ),
    )
}

internal class AutomationDetailActions(
    val back: () -> Unit,
    val retry: () -> Unit,
    val selectTab: (AutomationTab) -> Unit,
    val setEnabled: (Boolean) -> Unit,
    /** 1 項目の編集を確定して保存する。 */
    val commit: (AutomationDraft) -> Unit,
    val runNow: () -> Unit,
    val askDelete: () -> Unit,
    val dismissDelete: () -> Unit,
    val delete: () -> Unit,
    val retryRuns: () -> Unit,
    val loadMoreRuns: () -> Unit,
    /** モデル・思考のメニューを開いたとき、選べるモデルを読む。 */
    val loadModels: () -> Unit = {},
)

/** 編集するシート（項目ごと）。モデルと思考はシートではなくガラスのメニューで選ぶ。 */
private enum class EditTarget { NAME, DESCRIPTION, SCHEDULE, PROMPT, TIMEOUT }

/**
 * 自動化の詳細（M3 の設定画面の作り）。上にメインスイッチと次回・前回、その下に「設定 / 実行履歴」。
 * 設定は 1 行ずつ値を見せ、押すとその項目だけのシートで編集して、保存するとすぐ反映する。
 */
@Composable
internal fun AutomationDetailContent(state: AutomationDetailUi, snackbar: SnackbarHostState, actions: AutomationDetailActions) {
    val hazeState = rememberHazeState()
    val job = state.job
    var editing by rememberSaveable { mutableStateOf<EditTarget?>(null) }
    var openedRun by remember { mutableStateOf<AutomationRun?>(null) }
    val listState = rememberLazyListState()
    // 履歴のいちばん下が見えたら続きを読む
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.tab, state.runs.nextOffset) {
        if (nearEnd && state.tab == AutomationTab.HISTORY && state.runs.nextOffset != null) actions.loadMoreRuns()
    }

    ProvideScreenHaze(hazeState) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                AppTitleBar(
                    hazeState = hazeState,
                    title = { Text(job?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), actions.back,
                            modifier = Modifier.padding(start = Spacing.s))
                    },
                    actions = { if (job != null) MoreMenu(actions.askDelete) },
                )
            },
            snackbarHost = { GlassSnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                if (job == null) {
                    LoadingOrFailed(failed = state.failed, message = R.string.automation_failed_load, onRetry = actions.retry, modifier = Modifier.padding(padding))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + Dimens.floatingButtonSize + Spacing.xl,
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item(key = "hero") { Hero(state, job, actions.setEnabled) }
                        item(key = "tabs") {
                            ConnectedChoiceRow(
                                AutomationTab.entries, state.tab, actions.selectTab,
                                Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m),
                            ) { stringResource(if (it == AutomationTab.SETTINGS) R.string.automation_tab_settings else R.string.automation_tab_history) }
                        }
                        when (state.tab) {
                            AutomationTab.SETTINGS -> settingsItems(state, job, onEdit = { editing = it }, onLoadModels = actions.loadModels, onCommit = actions.commit)
                            AutomationTab.HISTORY -> historyItems(state, actions.retryRuns) { openedRun = it }
                        }
                    }
                    RunNowPill(
                        hazeState = hazeState,
                        onRun = actions.runNow,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Spacing.l),
                    )
                }
            }
        }
    }

    val draft = state.draft
    if (job != null && draft != null) {
        editing?.let { target ->
            EditSheet(target, draft, job, hazeState, onDismiss = { editing = null }) { next ->
                editing = null
                actions.commit(next)
            }
        }
    }
    openedRun?.let { RunSheet(it, hazeState) { openedRun = null } }
    if (state.confirmDelete && job != null) {
        AlertDialog(
            onDismissRequest = actions.dismissDelete,
            title = { Text(stringResource(R.string.automation_delete_title)) },
            text = { Text(stringResource(R.string.automation_delete_body, job.name)) },
            confirmButton = {
                TextButton(onClick = actions.delete) { Text(stringResource(R.string.automation_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = actions.dismissDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun MoreMenu(onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(painterResource(R.drawable.ic_huge_more), stringResource(R.string.automation_more_cd)) }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_huge_delete), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

// ---- 上部 ----

/**
 * メインスイッチ（Android の設定と同じ、有効のときはテーマの強い色の面）と、次回・前回の 2 列。
 * 前回が失敗なら、その下にエラーの面を出す。
 */
@Composable
private fun Hero(state: AutomationDetailUi, job: Automation, onEnabled: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = accentContainer()
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(if (job.enabled) accent.container else colors.surfaceContainerHighest, motion.defaultEffectsSpec(), label = "mainSwitchContainer")
    Column(
        Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().padding(horizontal = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Surface(
            onClick = { onEnabled(!job.enabled) },
            shape = MaterialTheme.shapes.extraLarge,
            color = container,
            contentColor = if (job.enabled) accent.content else colors.onSurface,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
        ) {
            Row(Modifier.padding(start = Spacing.xl, end = Spacing.l, top = Spacing.m, bottom = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (job.enabled) R.string.automation_enabled else R.string.automation_disabled),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = job.enabled, onCheckedChange = onEnabled)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Stat(
                label = stringResource(R.string.automation_next_label),
                value = job.nextRunAtMs?.takeIf { job.enabled }?.let { relativeTime(it) } ?: stringResource(R.string.automation_none),
                modifier = Modifier.weight(1f),
            )
            Stat(
                label = stringResource(R.string.automation_last_label),
                value = job.lastRunAtMs?.let { relativeTime(it) } ?: stringResource(R.string.automation_none),
                status = job.lastRunStatus,
                running = job.runningAtMs != null,
                modifier = Modifier.weight(1f),
            )
        }
        AnimatedVisibility(state.saving) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        val error = job.lastError?.takeIf { job.lastRunStatus == CronRunStatus.ERROR }
        if (error != null) ErrorPanel(error)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, status: CronRunStatus? = null, running: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            when {
                running -> Text(stringResource(R.string.automation_running), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                else -> {
                    status?.let { StatusIcon(it, Modifier.size(Dimens.smallIcon)) }
                    Text(value, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: CronRunStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (icon, tint) = when (status) {
        CronRunStatus.OK -> R.drawable.ic_huge_check_circle to colors.primary
        CronRunStatus.ERROR -> R.drawable.ic_huge_alert_circle to colors.error
        CronRunStatus.SKIPPED -> R.drawable.ic_huge_minus_circle to colors.onSurfaceVariant
    }
    Icon(painterResource(icon), contentDescription = statusLabel(status), tint = tint, modifier = modifier)
}

/** 前回のエラー。押すと全文。 */
@Composable
private fun ErrorPanel(error: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.errorContainer)
            .clickable(onClickLabel = stringResource(R.string.automation_run_expand_label)) { expanded = !expanded }
            .padding(horizontal = Spacing.m, vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(painterResource(R.drawable.ic_huge_alert_circle), contentDescription = null, tint = colors.onErrorContainer)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.automation_section_last_error), style = MaterialTheme.typography.labelLarge, color = colors.onErrorContainer)
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onErrorContainer,
                maxLines = if (expanded) Int.MAX_VALUE else ERROR_COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 下に浮く「今すぐ実行」（AGENTS.md 2.7: 主操作は画面下部に浮く透過効果のピル）。 */
@Composable
private fun RunNowPill(hazeState: HazeState, onRun: () -> Unit, modifier: Modifier = Modifier) {
    GlassPill(hazeState = hazeState, modifier = modifier) {
        TextButton(onClick = onRun, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
            Icon(painterResource(R.drawable.ic_huge_play), contentDescription = null)
            Text(stringResource(R.string.automation_run_now), modifier = Modifier.padding(start = Spacing.s))
        }
    }
}

// ---- 設定 ----

private fun LazyListScope.settingsItems(
    state: AutomationDetailUi,
    job: Automation,
    onEdit: (EditTarget) -> Unit,
    onLoadModels: () -> Unit,
    onCommit: (AutomationDraft) -> Unit,
) {
    val draft = state.draft ?: return
    val onDelivery: (CronDeliveryMode) -> Unit = { mode -> onCommit(draft.copy(delivery = mode)) }
    item(key = "basic") {
        Group(R.string.automation_section_basic) {
            SettingRow(R.drawable.ic_huge_text, stringResource(R.string.automation_field_name), draft.name) { onEdit(EditTarget.NAME) }
            SettingRow(
                R.drawable.ic_huge_info,
                stringResource(R.string.automation_field_description),
                draft.description.ifBlank { stringResource(R.string.automation_unset) },
                maxLines = 3,
            ) { onEdit(EditTarget.DESCRIPTION) }
        }
    }
    item(key = "schedule") {
        Group(R.string.automation_section_schedule) {
            val editable = draft.schedule.kind != ScheduleDraft.Kind.OTHER
            SettingRow(
                R.drawable.ic_huge_clock,
                scheduleText(job.schedule),
                scheduleDetail(draft.schedule),
                onClick = if (editable) ({ onEdit(EditTarget.SCHEDULE) }) else null,
            )
        }
    }
    item(key = "payload") {
        Group(R.string.automation_section_payload) {
            when (val payload = job.payload) {
                is CronPayload.Other -> {
                    SettingRow(R.drawable.ic_huge_tool, payload.kind, stringResource(R.string.automation_payload_readonly, payload.kind), onClick = null)
                    payload.preview?.let { PreviewBlock(it, code = true, onClick = null) }
                }
                else -> {
                    val isTurn = payload is CronPayload.AgentTurn
                    ListItem(
                        colors = transparent(),
                        leadingContent = { Icon(painterResource(R.drawable.ic_huge_ai_chat), contentDescription = null) },
                        headlineContent = { Text(stringResource(if (isTurn) R.string.automation_field_prompt else R.string.automation_field_event_text)) },
                        trailingContent = { Icon(painterResource(R.drawable.ic_huge_edit), contentDescription = stringResource(R.string.automation_edit_cd), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable { onEdit(EditTarget.PROMPT) },
                    )
                    PreviewBlock(draft.prompt.orEmpty(), code = false) { onEdit(EditTarget.PROMPT) }
                    if (isTurn) {
                        val default = stringResource(R.string.automation_field_default)
                        // モデルと思考は、会話の画面やエージェントの設定と同じガラスのメニューで選ぶ（マスター指定 2026-11-26）
                        val catalog = (state.models as? Load.Loaded)?.value
                        MenuSettingRow(
                            R.drawable.ic_huge_model,
                            stringResource(R.string.automation_field_model),
                            catalog?.find(draft.model.ifBlank { null })?.name ?: draft.model.ifBlank { default },
                            AgentMenu(modelOptions(state, draft), draft.model.ifBlank { null }, onLoadModels) { onCommit(draft.copy(model = it.orEmpty())) },
                        )
                        MenuSettingRow(
                            R.drawable.ic_huge_effort,
                            stringResource(R.string.automation_field_thinking),
                            draft.thinking.ifBlank { default },
                            AgentMenu(thinkingOptions(state, draft), draft.thinking.ifBlank { null }, onLoadModels) { onCommit(draft.copy(thinking = it.orEmpty())) },
                        )
                        SettingRow(
                            R.drawable.ic_huge_timer,
                            stringResource(R.string.automation_field_timeout),
                            draft.timeoutSeconds.toLongOrNull()?.let { durationText(it * MS_PER_SECOND) } ?: default,
                        ) { onEdit(EditTarget.TIMEOUT) }
                    }
                }
            }
        }
    }
    item(key = "delivery") {
        Group(R.string.automation_section_delivery) {
            if (draft.delivery == CronDeliveryMode.WEBHOOK) {
                SettingRow(R.drawable.ic_huge_notification, stringResource(R.string.automation_notify_label), stringResource(R.string.automation_delivery_webhook), onClick = null)
            } else {
                val on = draft.delivery == CronDeliveryMode.ANNOUNCE
                ListItem(
                    colors = transparent(),
                    leadingContent = { Icon(painterResource(R.drawable.ic_huge_notification), contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.automation_notify_label)) },
                    supportingContent = { Text(stringResource(R.string.automation_notify_desc)) },
                    trailingContent = { Switch(checked = on, onCheckedChange = { onDelivery(if (it) CronDeliveryMode.ANNOUNCE else CronDeliveryMode.NONE) }) },
                    modifier = Modifier.clickable { onDelivery(if (on) CronDeliveryMode.NONE else CronDeliveryMode.ANNOUNCE) },
                )
            }
        }
    }
}

/** 見出し（labelLarge / primary）と、その下の行のまとまり（AGENTS.md 2.3）。 */
@Composable
private fun Group(title: Int, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.l, bottom = Spacing.xs).semantics { heading() },
        )
        content()
    }
}

@Composable
private fun SettingRow(icon: Int, title: String, value: String, maxLines: Int = 2, onClick: (() -> Unit)?) {
    ListItem(
        colors = transparent(),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(value, maxLines = maxLines, overflow = TextOverflow.Ellipsis) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

/** 押すとガラスのメニューで選ぶ行（モデル・思考）。メニューの中身はエージェントの設定と共通。 */
@Composable
private fun MenuSettingRow(icon: Int, title: String, value: String, menu: AgentMenu) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ListItem(
            colors = transparent(),
            leadingContent = { Icon(painterResource(icon), contentDescription = null) },
            headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            trailingContent = {
                Icon(painterResource(R.drawable.ic_huge_arrow_down), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            modifier = Modifier.clickable {
                menu.onOpen()
                expanded = true
            },
        )
        GlassDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AgentMenuItems(menu) { picked ->
                expanded = false
                if (picked != menu.selected) menu.onSelect(picked)
            }
        }
    }
}

/** モデルの選択肢。先頭は「既定」、続いて選べるモデル。読み込み中は null。 */
@Composable
private fun modelOptions(state: AutomationDetailUi, draft: AutomationDraft): List<AgentChoice>? {
    val reset = AgentChoice(null, stringResource(R.string.automation_field_default))
    return when (val models = state.models) {
        is Load.Loaded -> listOf(reset) + models.value?.models.orEmpty()
            .filter { it.selectable || it.reference == draft.model }
            .map { AgentChoice(it.reference, it.name, it.reference.takeIf { ref -> ref != it.name }) }
        is Load.Failed -> listOfNotNull(reset, draft.model.ifBlank { null }?.let { AgentChoice(it, it) })
        else -> null
    }
}

/** 思考の選択肢。段階は選んでいるモデルが受け付けるもの（分からなければ設定の決まりの並び）。 */
@Composable
private fun thinkingOptions(state: AutomationDetailUi, draft: AutomationDraft): List<AgentChoice>? {
    val reset = AgentChoice(null, stringResource(R.string.automation_field_default))
    val models = state.models
    val levels = (models as? Load.Loaded)?.value?.find(draft.model.ifBlank { null })?.thinkingLevels.orEmpty()
    return when {
        levels.isNotEmpty() -> listOf(reset) + levels.map { AgentChoice(it.id, it.label) }
        models == null || models == Load.Loading -> null
        else -> listOf(reset) + THINKING_FALLBACK.map { AgentChoice(it, it) }
    }
}

/** プロンプトなど長い文の抜粋（本文の下地の角丸の面。押すと編集）。 */
@Composable
private fun PreviewBlock(text: String, code: Boolean, onClick: (() -> Unit)?) {
    Box(
        Modifier
            .padding(horizontal = Spacing.l)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(bodyContainerColor())
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        Text(
            text,
            style = if (code) codeTextStyle() else MaterialTheme.typography.bodyMedium,
            maxLines = PREVIEW_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun scheduleDetail(draft: ScheduleDraft): String = when (draft.kind) {
    ScheduleDraft.Kind.CRON -> listOf(draft.cronExpr, draft.timezone.ifBlank { TimeZone.getDefault().id }).joinToString(" · ")
    ScheduleDraft.Kind.EVERY -> stringResource(R.string.automation_kind_every)
    ScheduleDraft.Kind.AT -> draft.at
    ScheduleDraft.Kind.OTHER -> stringResource(R.string.automation_schedule_readonly, draft.otherKind.orEmpty())
}

@Composable
private fun transparent() = ListItemDefaults.colors(containerColor = Color.Transparent)

// ---- 実行履歴 ----

private fun LazyListScope.historyItems(state: AutomationDetailUi, onRetry: () -> Unit, onOpen: (AutomationRun) -> Unit) {
    val runs = state.runs
    when {
        runs.runs.isEmpty() && (runs.loading || runs.failed || !runs.loaded) -> item(key = "status") {
            LoadingOrFailed(
                failed = runs.failed,
                message = R.string.automation_runs_failed,
                onRetry = onRetry,
                modifier = Modifier.heightIn(min = Dimens.emptyStateIcon * 2),
            )
        }
        runs.runs.isEmpty() -> item(key = "empty") {
            Text(
                stringResource(R.string.automation_runs_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.xl),
            )
        }
        else -> {
            items(runs.runs, key = { it.key }) { run ->
                RunRow(run, Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()) { onOpen(run) }
            }
            if (runs.loadingMore) item(key = "more") { LinearProgressIndicator(Modifier.padding(Spacing.l)) }
        }
    }
}

/** 履歴の 1 行。左に結果のアイコン、日時、かかった時間とモデル、結果の文の先頭。押すと全文のシート。 */
@Composable
private fun RunRow(run: AutomationRun, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    ListItem(
        modifier = modifier.clickable(onClickLabel = stringResource(R.string.automation_run_expand_label), onClick = onClick),
        colors = transparent(),
        leadingContent = { run.status?.let { StatusIcon(it) } },
        headlineContent = { Text(absoluteTime(context, run.runAtMs), style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                runMeta(run)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) }
                (run.error ?: run.summary)?.let { body ->
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (run.error != null) colors.error else colors.onSurface,
                        maxLines = SUMMARY_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}

@Composable
private fun runMeta(run: AutomationRun): String? = listOfNotNull(
    run.status?.let { statusLabel(it) },
    run.durationMs?.let { durationText(it) },
    run.model,
).joinToString(" · ").ifEmpty { null }

/** 実行 1 回分の全文（読むだけ。AGENTS.md 2.5 の不透明な面に載せる）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunSheet(run: AutomationRun, hazeState: HazeState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AppBottomSheet(onDismissRequest = onDismiss, hazeState = hazeState) {
        Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                run.status?.let { StatusIcon(it) }
                Text(absoluteTime(context, run.runAtMs), style = MaterialTheme.typography.titleMedium)
            }
            runMeta(run)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        SheetField(Modifier.navigationBarsPadding()) {
            SelectionContainer(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.l)) {
                Text(
                    run.error ?: run.summary ?: stringResource(R.string.automation_run_no_output),
                    style = bodyTextStyle(),
                    color = if (run.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ---- 編集のシート ----

/** シートの中の不透明な面（AGENTS.md 2.5）。 */
@Composable
private fun SheetField(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = sheetFieldColor(),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    target: EditTarget,
    draft: AutomationDraft,
    job: Automation,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onSave: (AutomationDraft) -> Unit,
) {
    var value by rememberSaveable(target) {
        mutableStateOf(
            when (target) {
                EditTarget.NAME -> draft.name
                EditTarget.DESCRIPTION -> draft.description
                EditTarget.PROMPT -> draft.prompt.orEmpty()
                EditTarget.TIMEOUT -> draft.timeoutSeconds
                EditTarget.SCHEDULE -> ""
            },
        )
    }
    var schedule by remember(target) { mutableStateOf(draft.schedule) }
    val next = when (target) {
        EditTarget.NAME -> draft.copy(name = value)
        EditTarget.DESCRIPTION -> draft.copy(description = value)
        EditTarget.PROMPT -> draft.copy(prompt = value)
        EditTarget.TIMEOUT -> draft.copy(timeoutSeconds = value)
        EditTarget.SCHEDULE -> draft.copy(schedule = schedule)
    }
    val title = stringResource(
        when (target) {
            EditTarget.NAME -> R.string.automation_field_name
            EditTarget.DESCRIPTION -> R.string.automation_field_description
            EditTarget.PROMPT -> if (job.payload is CronPayload.AgentTurn) R.string.automation_field_prompt else R.string.automation_field_event_text
            EditTarget.TIMEOUT -> R.string.automation_field_timeout
            EditTarget.SCHEDULE -> R.string.automation_section_schedule
        },
    )
    AppBottomSheet(
        onDismissRequest = onDismiss,
        hazeState = hazeState,
        // 長い文を打つシートは、途中で引き下ろして閉じないように開き切る
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.imePadding().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.xl).semantics { heading() },
            )
            when (target) {
                EditTarget.SCHEDULE -> ScheduleEditor(schedule) { schedule = it }
                EditTarget.PROMPT -> SheetInput(value, { value = it }, minLines = PROMPT_MIN_LINES, maxLines = PROMPT_MAX_LINES, isError = value.isBlank(), autoFocus = true)
                EditTarget.DESCRIPTION -> SheetInput(value, { value = it }, minLines = 3, maxLines = 8, autoFocus = true)
                EditTarget.NAME -> SheetInput(value, { value = it }, isError = value.isBlank(), autoFocus = true)
                EditTarget.TIMEOUT -> SheetInput(
                    value, { value = it.filter(Char::isDigit) },
                    placeholder = stringResource(R.string.automation_field_default),
                    keyboardType = KeyboardType.Number,
                    supporting = stringResource(R.string.automation_timeout_hint),
                    autoFocus = true,
                )
            }
            // 操作は下に右寄せ（M3 のダイアログ・シートと同じ並び: 取り消し → 確定。キーボードのすぐ上に来る）
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onSave(next) }, enabled = next.valid && next != draft) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

/**
 * シートの入力欄。不透明な明るい面（AGENTS.md 2.5）そのものを入力欄にし、枠線を引かない（会話の入力欄と同じ見え方）。
 * 説明・エラーは面の下に小さく出す。
 */
@Composable
private fun SheetInput(
    value: String,
    onChange: (String) -> Unit,
    minLines: Int = 1,
    maxLines: Int = 1,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    monospace: Boolean = false,
    autoFocus: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    val clear = Color.Transparent
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SheetField(Modifier.padding(vertical = Spacing.xs)) {
            TextField(
                value = value,
                onValueChange = onChange,
                placeholder = placeholder?.let { { Text(it) } },
                singleLine = maxLines == 1,
                minLines = minLines,
                maxLines = maxLines,
                isError = isError,
                textStyle = if (monospace) codeTextStyle() else bodyTextStyle(),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = clear,
                    unfocusedContainerColor = clear,
                    errorContainerColor = clear,
                    disabledContainerColor = clear,
                    focusedIndicatorColor = clear,
                    unfocusedIndicatorColor = clear,
                    errorIndicatorColor = clear,
                    disabledIndicatorColor = clear,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        supporting?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xl),
            )
        }
    }
}

/**
 * スケジュールの編集。種類は Expressive の連結ボタンで選び、よく使う形はチップで入れられる（入りきらなければ折り返す）。
 * 式は下に言葉で確かめられる。切り替えとチップはシートの上、文字を打つ欄だけを明るい面に載せる。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditor(draft: ScheduleDraft, onChange: (ScheduleDraft) -> Unit) {
    val kinds = listOf(ScheduleDraft.Kind.CRON, ScheduleDraft.Kind.EVERY, ScheduleDraft.Kind.AT)
    val rowModifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l)
    ConnectedChoiceRow(kinds, draft.kind, { onChange(draft.copy(kind = it)) }, rowModifier) { kind ->
        stringResource(when (kind) {
            ScheduleDraft.Kind.CRON -> R.string.automation_kind_cron
            ScheduleDraft.Kind.EVERY -> R.string.automation_kind_every
            else -> R.string.automation_kind_at
        })
    }
    when (draft.kind) {
        ScheduleDraft.Kind.CRON -> {
            // よく使う形（時刻はいまの式から引き継ぐ）
            val (hour, minute) = remember(draft.cronExpr) { cronTime(draft.cronExpr) }
            val presets = listOf(
                R.string.preset_daily to "$minute $hour * * *",
                R.string.preset_weekdays to "$minute $hour * * 1-5",
                R.string.preset_weekly to "$minute $hour * * 1",
                R.string.preset_hourly to "$minute * * * *",
            )
            FlowRow(rowModifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                presets.forEach { (label, expr) ->
                    FilterChip(
                        selected = draft.cronExpr.trim() == expr,
                        onClick = { onChange(draft.copy(cronExpr = expr)) },
                        label = { Text(stringResource(label), maxLines = 1) },
                    )
                }
            }
            val preview = draft.toSchedule()?.takeIf { draft.valid }?.let { scheduleText(it) }
            SheetInput(draft.cronExpr, { onChange(draft.copy(cronExpr = it)) }, monospace = true, isError = !draft.valid,
                supporting = preview ?: stringResource(R.string.automation_field_cron))
            SheetInput(draft.timezone, { onChange(draft.copy(timezone = it)) },
                placeholder = stringResource(R.string.automation_field_timezone_hint, remember { TimeZone.getDefault().id }),
                supporting = stringResource(R.string.automation_field_timezone))
        }
        ScheduleDraft.Kind.EVERY -> {
            SheetInput(draft.everyAmount, { onChange(draft.copy(everyAmount = it.filter(Char::isDigit))) },
                keyboardType = KeyboardType.Number, isError = !draft.valid, supporting = stringResource(R.string.automation_field_every))
            val units = ScheduleDraft.EveryUnit.entries
            ConnectedChoiceRow(units, draft.everyUnit, { onChange(draft.copy(everyUnit = it)) }, rowModifier) { unit ->
                stringResource(when (unit) {
                    ScheduleDraft.EveryUnit.MINUTES -> R.string.automation_unit_minutes
                    ScheduleDraft.EveryUnit.HOURS -> R.string.automation_unit_hours
                    ScheduleDraft.EveryUnit.DAYS -> R.string.automation_unit_days
                })
            }
        }
        ScheduleDraft.Kind.AT -> SheetInput(draft.at, { onChange(draft.copy(at = it)) }, monospace = true, isError = !draft.valid,
            placeholder = stringResource(R.string.automation_field_at_hint), supporting = stringResource(R.string.automation_field_at))
        ScheduleDraft.Kind.OTHER -> Unit
    }
}

/** cron 式の分と時（数で書かれていなければ 9:00）。 */
private fun cronTime(expr: String): Pair<Int, Int> {
    val fields = expr.trim().split(Regex("\\s+"))
    val minute = fields.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
    val hour = fields.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..23 } ?: DEFAULT_PRESET_HOUR
    return hour to minute
}

private const val PROMPT_MIN_LINES = 8
private const val PROMPT_MAX_LINES = 16
private const val PREVIEW_MAX_LINES = 6
private const val SUMMARY_MAX_LINES = 2
private const val ERROR_COLLAPSED_LINES = 3
private const val LOAD_MORE_THRESHOLD = 3
private const val MS_PER_SECOND = 1_000L
private const val DEFAULT_PRESET_HOUR = 9
