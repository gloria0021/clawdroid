package jp.miyavi.android.clawdroid.feature.automation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Automation
import jp.miyavi.android.clawdroid.core.data.CronRunStatus
import jp.miyavi.android.clawdroid.core.designsystem.AppTitleBar
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.clawdroid.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.feature.files.LoadingOrFailed

/** 自動化の一覧の ViewModel をつなぐ部分。 */
@Composable
internal fun AutomationsScreen(viewModel: AutomationsViewModel, agentName: String, onOpen: (Automation) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val messages = automationMessages()
    // 画面に出るたびに読み直す（詳細で変えた内容・実行の結果を映す）
    LaunchedEffect(viewModel) { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> messages(event)?.let { snackbar.showSnackbar(it) } }
    }
    AutomationsContent(agentName, state, snackbar, onBack, viewModel::refresh, onOpen, viewModel::setEnabled)
}

@Composable
internal fun AutomationsContent(
    agentName: String,
    state: AutomationListUi,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (Automation) -> Unit,
    onToggle: (Automation, Boolean) -> Unit,
) {
    val hazeState = rememberHazeState()
    ProvideScreenHaze(hazeState) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                AppTitleBar(
                    hazeState = hazeState,
                    title = {
                        Column {
                            Text(stringResource(R.string.automations_title), style = MaterialTheme.typography.titleMedium)
                            Text(agentName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = {
                        FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), onBack,
                            modifier = Modifier.padding(start = Spacing.s))
                    },
                    actions = {},
                )
            },
            snackbarHost = { GlassSnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        ) { padding ->
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                when {
                    state.jobs.isEmpty() && (state.loading || state.failed) ->
                        LoadingOrFailed(failed = state.failed, message = R.string.automations_load_failed, onRetry = onRetry, modifier = Modifier.padding(padding))
                    state.jobs.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.automations_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.l),
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.l),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        items(state.jobs, key = { it.id }) { job ->
                            AutomationRow(job, onOpen = { onOpen(job) }, onToggle = { onToggle(job, it) },
                                modifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

/** 一覧の 1 行。名前・スケジュール・次回と前回、右に有効・無効のスイッチ。押すと詳細。 */
@Composable
private fun AutomationRow(job: Automation, onOpen: () -> Unit, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val openLabel = stringResource(R.string.automation_open_cd, job.name)
    ListItem(
        modifier = modifier.clickable(onClickLabel = openLabel, onClick = onOpen),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(job.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(scheduleText(job.schedule), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RunLine(job)
            }
        },
        trailingContent = {
            val toggleLabel = stringResource(R.string.automation_toggle_cd, job.name)
            Switch(checked = job.enabled, onCheckedChange = onToggle, modifier = Modifier.semantics { contentDescription = toggleLabel })
        },
    )
}

/** 「次回 11 時間後 · 前回 13 時間前 成功」。失敗は error の色、実行中はそれだけを出す。 */
@Composable
internal fun RunLine(job: Automation) {
    val colors = MaterialTheme.colorScheme
    val parts = buildList {
        if (job.runningAtMs != null) add(stringResource(R.string.automation_running) to colors.primary)
        if (job.enabled) job.nextRunAtMs?.let { add(stringResource(R.string.automation_next_run, relativeTime(it)) to colors.onSurfaceVariant) }
        job.lastRunAtMs?.let { last ->
            val status = job.lastRunStatus?.let { statusLabel(it) }.orEmpty()
            val color = if (job.lastRunStatus == CronRunStatus.ERROR) colors.error else colors.onSurfaceVariant
            add("${stringResource(R.string.automation_last_run, relativeTime(last))} $status".trim() to color)
        }
    }
    if (parts.isEmpty()) return
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            parts.forEachIndexed { index, (text, color) ->
                if (index > 0) append(" · ")
                pushStyle(androidx.compose.ui.text.SpanStyle(color = color))
                append(text)
                pop()
            }
        },
        style = MaterialTheme.typography.labelMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun statusLabel(status: CronRunStatus): String = stringResource(
    when (status) {
        CronRunStatus.OK -> R.string.automation_status_ok
        CronRunStatus.ERROR -> R.string.automation_status_error
        CronRunStatus.SKIPPED -> R.string.automation_status_skipped
    },
)

/** 知らせの文言（無いものは null）。 */
@Composable
internal fun automationMessages(): (AutomationEvent) -> String? {
    val saved = stringResource(R.string.automation_saved)
    val started = stringResource(R.string.automation_run_started)
    val deleted = stringResource(R.string.automation_deleted)
    val problems = mapOf(
        AutomationProblem.LOAD to stringResource(R.string.automation_failed_load),
        AutomationProblem.TOGGLE to stringResource(R.string.automation_failed_toggle),
        AutomationProblem.SAVE to stringResource(R.string.automation_failed_save),
        AutomationProblem.CONFLICT to stringResource(R.string.automation_failed_conflict),
        AutomationProblem.RUN to stringResource(R.string.automation_failed_run),
        AutomationProblem.DELETE to stringResource(R.string.automation_failed_delete),
        AutomationProblem.NO_PERMISSION to stringResource(R.string.automation_failed_permission),
    )
    return remember(saved, started, deleted, problems) {
        { event ->
            when (event) {
                AutomationEvent.Saved -> saved
                AutomationEvent.RunStarted -> started
                AutomationEvent.Deleted -> deleted
                is AutomationEvent.Failed -> problems[event.problem]
            }
        }
    }
}
