package jp.miyavi.android.sumahoclaw.feature.chat.composer

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.Agent
import jp.miyavi.android.sumahoclaw.core.data.ModelCatalog
import jp.miyavi.android.sumahoclaw.core.data.SessionSetting
import jp.miyavi.android.sumahoclaw.core.data.SessionSummary
import jp.miyavi.android.sumahoclaw.core.data.shortModelName
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.accentContainer

/** 1 つの選択肢。`value` が null なら「デフォルト」（上書きを外す）。 */
internal data class SettingOption(
    val name: String,
    val description: String?,
    val value: String?,
    val selected: Boolean,
    val enabled: Boolean = true,
    val icon: Int? = null,
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
    availability: SettingAvailability,
    onOpen: (SessionSetting) -> Unit,
    modifier: Modifier = Modifier,
    /** ボタンに重ねるポップアップ（ボタンの位置を起点に出す）。 */
    popup: @Composable (SessionSetting) -> Unit = {},
    /** 実行権限も並べるか（セッションのシートでは入力欄に移したので出さない。マスター指定 2026-11-26）。 */
    showPermissions: Boolean = true,
    /** 保存中の設定（アイコンの場所で読み込み表示。マスター指定 2026-11-26）。 */
    saving: SessionSetting? = null,
    /** 設定ごとの保存できた回数（増えたらアイコンの場所で一瞬チェック）。 */
    savedTick: (SessionSetting) -> Int = { 0 },
) {
    val defaultLabel = stringResource(R.string.chat_setting_default)
    val permissionChoice = PermissionChoice.entries.firstOrNull { it.value == permission } ?: PermissionChoice.Default
    val permissionLabel = stringResource(permissionChoice.label)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        listOf(
            Triple(SessionSetting.MODEL, R.drawable.ic_huge_model, model?.let(::shortModelName) ?: defaultLabel),
            Triple(SessionSetting.EFFORT, R.drawable.ic_huge_effort, effort ?: defaultLabel),
            Triple(SessionSetting.PERMISSIONS, permissionChoice.icon, permissionLabel).takeIf { showPermissions },
        ).filterNotNull().forEach { (setting, icon, value) ->
            val enabled = when (setting) {
                SessionSetting.MODEL -> availability.model
                SessionSetting.EFFORT -> availability.effort
                SessionSetting.PERMISSIONS -> availability.permissions
            }
            Box(if (setting == SessionSetting.PERMISSIONS) Modifier else Modifier.weight(1f)) {
                SettingIconButton(
                    setting = setting,
                    icon = icon,
                    value = value,
                    // 保存中は押せない（重ねて選んだ値が捨てられないように）
                    enabled = enabled && agent != null && saving == null,
                    saving = saving == setting,
                    savedTick = savedTick(setting),
                    onOpen = { onOpen(setting) },
                    // 実行権限はアイコンだけ（ラベルは読み上げとメニューで分かる）
                    showValue = setting != SessionSetting.PERMISSIONS,
                )
                popup(setting)
            }
        }
    }
}

@Composable
private fun SettingIconButton(
    setting: SessionSetting,
    icon: Int,
    value: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    showValue: Boolean = true,
    saving: Boolean = false,
    savedTick: Int = 0,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(setting.title())
    val cd = "$title: $value"
    Surface(
        onClick = onOpen,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        // テーマの強い色（利用者のメッセージ・モデルのピルと同じ）
        color = accentContainer().container,
        contentColor = accentContainer().content,
        modifier = modifier
            .then(if (showValue) Modifier.fillMaxWidth() else Modifier.widthIn(min = Dimens.minTouchTarget))
            .heightIn(min = Dimens.minTouchTarget).semantics { contentDescription = cd },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 設定のアイコンの場所で、保存中は読み込み表示、保存できたら一瞬チェック
            jp.miyavi.android.sumahoclaw.core.designsystem.SaveStateIcon(saving, savedTick, Modifier.size(Spacing.l + Spacing.xs)) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(Spacing.l + Spacing.xs))
            }
            if (showValue) Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 設定の選択肢の一覧（押したボタンを中心に出すポップアップの中身）。
 * 本物のポップアップ（別のウィンドウ）ではシートの中身を透かせないため、同じ画面の上に重ねて出す。
 */
@Composable
internal fun SettingOptionsList(setting: SessionSetting, options: List<SettingOption>?, onSelect: (String?) -> Unit) {
    // 実行権限だけ文字を 1 段階小さく（説明文があって長いため）
    val compact = setting == SessionSetting.PERMISSIONS
    Column(Modifier.heightIn(max = Dimens.settingMenuMaxHeight).verticalScroll(rememberScrollState()).padding(vertical = Spacing.xs)) {
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
                        Column(Modifier.padding(vertical = if (compact) Spacing.s else Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(option.name, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
                            option.description?.let {
                                Text(
                                    it,
                                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingIcon = option.icon?.let { icon -> { Icon(painterResource(icon), contentDescription = null) } },
                    trailingIcon = if (option.selected) ({ Icon(painterResource(R.drawable.ic_huge_check), contentDescription = null) }) else null,
                    enabled = option.enabled,
                    onClick = { onSelect(option.value) },
                )
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
                    icon = choice.icon,
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

/** 実行権限。アイコンだけで見分けられるよう、段階ごとに別のアイコンを持つ。 */
internal enum class PermissionChoice(val value: String?, val label: Int, val description: Int, val icon: Int) {
    Default(null, R.string.chat_setting_default, R.string.chat_permission_default_description, R.drawable.ic_huge_perm_default),
    ReadOnly("read-only", R.string.chat_permission_readonly, R.string.chat_permission_readonly_description, R.drawable.ic_huge_perm_readonly),
    Guarded("guarded", R.string.chat_permission_guarded, R.string.chat_permission_guarded_description, R.drawable.ic_huge_perm_guarded),
    Workspace("workspace", R.string.chat_permission_workspace, R.string.chat_permission_workspace_description, R.drawable.ic_huge_perm_workspace),
    Full("full", R.string.chat_permission_full, R.string.chat_permission_full_description, R.drawable.ic_huge_perm_full),
}
