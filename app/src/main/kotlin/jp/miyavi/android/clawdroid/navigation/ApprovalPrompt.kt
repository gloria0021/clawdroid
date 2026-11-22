package jp.miyavi.android.clawdroid.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.ApprovalDecision
import jp.miyavi.android.clawdroid.core.data.ApprovalKind
import jp.miyavi.android.clawdroid.core.data.ApprovalsRepository
import jp.miyavi.android.clawdroid.core.data.SessionsRepository
import jp.miyavi.android.clawdroid.core.designsystem.AppBottomSheet
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.bodyTextStyle
import jp.miyavi.android.clawdroid.core.designsystem.codeTextStyle
import jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor
import jp.miyavi.android.clawdroid.feature.chat.AgentAvatar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApprovalPrompt(repository: ApprovalsRepository, sessions: SessionsRepository, hazeState: HazeState) {
    val requests by repository.requests.collectAsStateWithLifecycle()
    val approval = requests.firstOrNull() ?: return
    val agents by sessions.agents.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )
    AppBottomSheet(onDismissRequest = {}, hazeState = hazeState, sheetState = sheetState) {
        key(approval.key) {
            var busy by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }
            var confirmAlways by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val agent = agents.agents.firstOrNull { it.id == approval.agentId }
            val agentName = agent?.name ?: approval.agentId
            fun decide(decision: ApprovalDecision) {
                if (busy) return
                failed = false
                busy = true
                scope.launch {
                    try {
                        repository.resolve(approval, decision)
                    } catch (_: TimeoutCancellationException) {
                        failed = true
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        failed = true
                    } finally {
                        busy = false
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Surface(
                    color = sheetFieldColor(),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                ) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.m, vertical = Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Text(
                            stringResource(if (approval.key.kind == ApprovalKind.EXEC) R.string.approval_exec_title else R.string.approval_plugin_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        agentName?.let {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                                AgentAvatar(agent, label = null)
                                Text(it, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        approval.sessionKey?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (requests.size > 1) Text(stringResource(R.string.approval_count, requests.size), style = MaterialTheme.typography.labelMedium)
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                Text(approval.detail, style = if (approval.key.kind == ApprovalKind.EXEC) codeTextStyle() else bodyTextStyle())
                                approval.target.forEach { Text(it, style = MaterialTheme.typography.labelMedium) }
                                approval.warning?.let { Text(it, style = bodyTextStyle(), color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                if (confirmAlways) Text(stringResource(R.string.approval_always_warning), style = MaterialTheme.typography.bodyMedium)
                if (failed) Text(stringResource(R.string.approval_failed), color = MaterialTheme.colorScheme.error)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                ) {
                    if (confirmAlways) {
                        TextButton(enabled = !busy, onClick = { confirmAlways = false }) { Text(stringResource(R.string.action_cancel)) }
                        Button(enabled = !busy, onClick = { decide(ApprovalDecision.ALWAYS) }) { Text(stringResource(R.string.approval_always)) }
                    } else {
                        if (ApprovalDecision.DENY in approval.decisions) {
                            TextButton(enabled = !busy, onClick = { decide(ApprovalDecision.DENY) }) { Text(stringResource(R.string.approval_deny)) }
                        }
                        if (ApprovalDecision.ALWAYS in approval.decisions) {
                            TextButton(enabled = !busy, onClick = { confirmAlways = true }) { Text(stringResource(R.string.approval_always)) }
                        }
                        if (ApprovalDecision.ONCE in approval.decisions) {
                            Button(enabled = !busy, onClick = { decide(ApprovalDecision.ONCE) }) { Text(stringResource(R.string.approval_once)) }
                        }
                    }
                }
            }
        }
    }
}
