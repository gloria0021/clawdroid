package jp.miyavi.android.clawdroid.feature.chat.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.ModelCatalog
import jp.miyavi.android.clawdroid.core.data.SessionSetting
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor

/**
 * 1 つの設定（モデル・エフォート・実行権限）の選択肢。セッションのシート（[ComposerSessionSheet]）の中に積んで出す。
 */
@Composable
internal fun SettingChoices(
    setting: SessionSetting,
    agent: Agent,
    session: SessionSummary?,
    draftOptions: Map<SessionSetting, String>,
    canSelectFull: Boolean,
    catalog: ModelCatalog?,
    onSelect: (String?) -> Unit,
) {
    val model = draftOptions[SessionSetting.MODEL] ?: session?.modelReference ?: agent.defaultModel
    val selectedModel = catalog?.find(model)
    val effort = draftOptions[SessionSetting.EFFORT] ?: session?.thinkingLevel
        ?: selectedModel?.thinkingDefault ?: agent.thinkingDefault
    val permission = draftOptions[SessionSetting.PERMISSIONS] ?: session?.permissionMode
    val policyDefault = stringResource(R.string.chat_setting_default)
    Column(
        modifier = Modifier.selectableGroup().padding(horizontal = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        when (setting) {
            SessionSetting.PERMISSIONS -> {
                PermissionChoice.entries.forEach { choice ->
                    val description = stringResource(choice.description)
                    val defaultPermission = PermissionChoice.entries.firstOrNull {
                        it.value != null && it.value == agent.defaultPermissionMode
                    }
                    SettingRow(
                        name = stringResource(choice.label),
                        description = if (choice.value == null && defaultPermission != null) {
                            stringResource(R.string.chat_permission_inherits, stringResource(defaultPermission.label))
                        } else description,
                        selected = permission == choice.value,
                        enabled = choice != PermissionChoice.Full || canSelectFull,
                        onClick = { onSelect(choice.value) },
                    )
                }
            }
            SessionSetting.MODEL -> {
                if (catalog == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(Spacing.s))
                } else {
                    val available = catalog.models.filter { it.selectable }
                    if (available.isNotEmpty() && catalog.canReset) {
                        SettingRow(policyDefault, selected = session?.modelReference == null && draftOptions[setting] == null) {
                            onSelect(null)
                        }
                    }
                    if (available.isEmpty()) UnavailableSettings()
                    available.forEach { choice ->
                        SettingRow(
                            choice.name,
                            choice.reference,
                            selected = choice.reference == selectedModel?.reference,
                        ) { onSelect(choice.reference) }
                    }
                }
            }
            SessionSetting.EFFORT -> {
                if (catalog == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(Spacing.s))
                } else {
                    val choices = selectedModel?.thinkingLevels
                        ?: agent.thinkingLevels.takeIf { model == agent.defaultModel }.orEmpty()
                    if (choices.isEmpty()) {
                        UnavailableSettings()
                    } else {
                        SettingRow(policyDefault, selected = draftOptions[setting] == null && session?.thinkingLevel == null) {
                            onSelect(null)
                        }
                        choices.forEach { choice ->
                            SettingRow(
                                choice.label,
                                selected = choice.id == effort && (draftOptions[setting] != null || session?.thinkingLevel != null),
                            ) { onSelect(choice.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnavailableSettings() {
    Text(
        stringResource(R.string.chat_settings_unavailable),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(Spacing.s),
    )
}

@Composable
private fun SettingRow(
    name: String,
    description: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = description?.let { { Text(it) } },
        trailingContent = { RadioButton(selected, onClick = null, enabled = enabled) },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) colors.secondaryContainer else sheetFieldColor(),
            headlineColor = if (enabled) colors.onSurface else colors.onSurfaceVariant,
        ),
        modifier = Modifier.clip(MaterialTheme.shapes.medium).selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        ),
    )
}

internal enum class PermissionChoice(val value: String?, val label: Int, val description: Int) {
    Default(null, R.string.chat_setting_default, R.string.chat_permission_default_description),
    ReadOnly("read-only", R.string.chat_permission_readonly, R.string.chat_permission_readonly_description),
    Guarded("guarded", R.string.chat_permission_guarded, R.string.chat_permission_guarded_description),
    Workspace("workspace", R.string.chat_permission_workspace, R.string.chat_permission_workspace_description),
    Full("full", R.string.chat_permission_full, R.string.chat_permission_full_description),
}
