package jp.miyavi.android.clawdroid.feature.chat.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.ModelCatalog
import jp.miyavi.android.clawdroid.core.data.SessionSetting
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.clawdroid.core.designsystem.Spacing

/** 1 つの選択肢。`value` が null なら「デフォルト」（上書きを外す）。 */
internal data class SettingOption(
    val name: String,
    val description: String?,
    val value: String?,
    val selected: Boolean,
    val enabled: Boolean = true,
)

/**
 * 設定の 1 行（モデル・エフォート・実行権限）。アイコンと今の値を横 1 行に並べ、押すと透過効果のメニューで選ぶ。
 */
@Composable
internal fun SettingIconRow(
    agent: Agent?,
    session: SessionSummary?,
    model: String?,
    effort: String?,
    permission: String?,
    draftOptions: Map<SessionSetting, String>,
    availability: SettingAvailability,
    canSelectFull: Boolean,
    catalog: ModelCatalog?,
    onNeedCatalog: () -> Unit,
    onSelect: (SessionSetting, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultLabel = stringResource(R.string.chat_setting_default)
    val permissionLabel = stringResource((PermissionChoice.entries.firstOrNull { it.value == permission } ?: PermissionChoice.Default).label)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        listOf(
            Triple(SessionSetting.MODEL, R.drawable.ic_huge_model, model?.substringAfterLast('/') ?: defaultLabel),
            Triple(SessionSetting.EFFORT, R.drawable.ic_huge_effort, effort ?: defaultLabel),
            Triple(SessionSetting.PERMISSIONS, R.drawable.ic_huge_permission, permissionLabel),
        ).forEach { (setting, icon, value) ->
            val enabled = when (setting) {
                SessionSetting.MODEL -> availability.model
                SessionSetting.EFFORT -> availability.effort
                SessionSetting.PERMISSIONS -> availability.permissions
            }
            SettingIconButton(
                setting = setting,
                icon = icon,
                value = value,
                enabled = enabled && agent != null,
                options = if (agent == null) null else settingOptions(setting, agent, session, draftOptions, canSelectFull, catalog),
                onOpen = { if (setting != SessionSetting.PERMISSIONS) onNeedCatalog() },
                onSelect = { onSelect(setting, it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SettingIconButton(
    setting: SessionSetting,
    icon: Int,
    value: String,
    enabled: Boolean,
    options: List<SettingOption>?,
    onOpen: () -> Unit,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(setting.title())
    val cd = "$title: $value"
    Box(modifier) {
        Surface(
            onClick = { onOpen(); open = true },
            enabled = enabled,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).semantics { contentDescription = cd },
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(Spacing.l + Spacing.xs))
                Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(max = Dimens.settingMenuMaxWidth)) {
            when {
                options == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(Spacing.m))
                options.isEmpty() -> Text(
                    stringResource(R.string.chat_settings_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.m),
                )
                else -> options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.name, style = MaterialTheme.typography.bodyLarge)
                                option.description?.let {
                                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        trailingIcon = if (option.selected) ({ Icon(painterResource(R.drawable.ic_huge_check), contentDescription = null) }) else null,
                        enabled = option.enabled,
                        onClick = {
                            open = false
                            onSelect(option.value)
                        },
                    )
                }
            }
        }
    }
}

/** その設定の選択肢。モデルの一覧がまだなら null（読み込み中）。 */
@Composable
internal fun settingOptions(
    setting: SessionSetting,
    agent: Agent,
    session: SessionSummary?,
    draftOptions: Map<SessionSetting, String>,
    canSelectFull: Boolean,
    catalog: ModelCatalog?,
): List<SettingOption>? {
    val model = draftOptions[SessionSetting.MODEL] ?: session?.modelReference ?: agent.defaultModel
    val selectedModel = catalog?.find(model)
    val effort = draftOptions[SessionSetting.EFFORT] ?: session?.thinkingLevel
        ?: selectedModel?.thinkingDefault ?: agent.thinkingDefault
    val permission = draftOptions[SessionSetting.PERMISSIONS] ?: session?.permissionMode
    val policyDefault = stringResource(R.string.chat_setting_default)
    return when (setting) {
        SessionSetting.PERMISSIONS -> {
            val defaultPermission = PermissionChoice.entries.firstOrNull { it.value != null && it.value == agent.defaultPermissionMode }
            PermissionChoice.entries.map { choice ->
                SettingOption(
                    name = stringResource(choice.label),
                    description = if (choice.value == null && defaultPermission != null) {
                        stringResource(R.string.chat_permission_inherits, stringResource(defaultPermission.label))
                    } else stringResource(choice.description),
                    value = choice.value,
                    selected = permission == choice.value,
                    enabled = choice != PermissionChoice.Full || canSelectFull,
                )
            }
        }
        SessionSetting.MODEL -> catalog?.let { models ->
            val available = models.models.filter { it.selectable }
            buildList {
                if (available.isNotEmpty() && models.canReset) {
                    add(SettingOption(policyDefault, null, null, session?.modelReference == null && draftOptions[setting] == null))
                }
                available.forEach { choice ->
                    add(SettingOption(choice.name, choice.reference, choice.reference, choice.reference == selectedModel?.reference))
                }
            }
        }
        SessionSetting.EFFORT -> catalog?.let {
            val choices = selectedModel?.thinkingLevels ?: agent.thinkingLevels.takeIf { model == agent.defaultModel }.orEmpty()
            if (choices.isEmpty()) emptyList() else buildList {
                val overridden = draftOptions[setting] != null || session?.thinkingLevel != null
                add(SettingOption(policyDefault, null, null, !overridden))
                choices.forEach { choice -> add(SettingOption(choice.label, null, choice.id, overridden && choice.id == effort)) }
            }
        }
    }
}

private fun SessionSetting.title(): Int = when (this) {
    SessionSetting.MODEL -> R.string.chat_setting_model
    SessionSetting.EFFORT -> R.string.chat_setting_effort
    SessionSetting.PERMISSIONS -> R.string.chat_setting_permissions
}

internal enum class PermissionChoice(val value: String?, val label: Int, val description: Int) {
    Default(null, R.string.chat_setting_default, R.string.chat_permission_default_description),
    ReadOnly("read-only", R.string.chat_permission_readonly, R.string.chat_permission_readonly_description),
    Guarded("guarded", R.string.chat_permission_guarded, R.string.chat_permission_guarded_description),
    Workspace("workspace", R.string.chat_permission_workspace, R.string.chat_permission_workspace_description),
    Full("full", R.string.chat_permission_full, R.string.chat_permission_full_description),
}
