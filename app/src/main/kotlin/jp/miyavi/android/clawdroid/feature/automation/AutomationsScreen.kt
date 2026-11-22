package jp.miyavi.android.clawdroid.feature.automation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.AgentAutomation
import jp.miyavi.android.clawdroid.core.data.listAgentAutomations
import jp.miyavi.android.clawdroid.core.designsystem.AppTitleBar
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

private sealed interface AutomationListState {
    data object Loading : AutomationListState
    data object Failed : AutomationListState
    data class Loaded(val jobs: List<AgentAutomation>) : AutomationListState
}

@Composable
internal fun AutomationsScreen(gateway: GatewayClient, agentId: String, agentName: String, onBack: () -> Unit) {
    val connection by gateway.state.collectAsStateWithLifecycle()
    var retry by remember(agentId) { mutableIntStateOf(0) }
    val state by produceState<AutomationListState>(AutomationListState.Loading, agentId, connection, retry) {
        value = AutomationListState.Loading
        if (connection !is ConnectionState.Connected) {
            value = AutomationListState.Failed
            return@produceState
        }
        value = try {
            AutomationListState.Loaded(listAgentAutomations(agentId) { gateway.request("cron.list", it) })
        } catch (_: TimeoutCancellationException) {
            AutomationListState.Failed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AutomationListState.Failed
        }
    }
    AutomationsContent(agentName, state, onBack, onRetry = { retry++ })
}

@Composable
private fun AutomationsContent(agentName: String, state: AutomationListState, onBack: () -> Unit, onRetry: () -> Unit) {
    val hazeState = rememberHazeState()
    Scaffold(topBar = {
        AppTitleBar(
            hazeState = hazeState,
            title = {
                Column {
                    Text(stringResource(R.string.automations_title), style = MaterialTheme.typography.titleMedium)
                    Text(agentName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = {
                FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), onBack)
            },
            actions = {},
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).hazeSource(hazeState), contentAlignment = Alignment.Center) {
            when (state) {
                AutomationListState.Loading -> CircularProgressIndicator()
                AutomationListState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.l)) {
                    Text(stringResource(R.string.automations_load_failed))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                is AutomationListState.Loaded -> if (state.jobs.isEmpty()) {
                    Text(stringResource(R.string.automations_empty), modifier = Modifier.padding(Spacing.l))
                } else {
                    LazyColumn(
                        modifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().align(Alignment.TopCenter),
                        contentPadding = PaddingValues(vertical = Spacing.s),
                    ) {
                        items(state.jobs, key = { it.id }) { job ->
                            ListItem(
                                headlineContent = {
                                    Text(job.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(stringResource(if (job.enabled) R.string.automation_enabled else R.string.automation_disabled), style = MaterialTheme.typography.labelMedium)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
