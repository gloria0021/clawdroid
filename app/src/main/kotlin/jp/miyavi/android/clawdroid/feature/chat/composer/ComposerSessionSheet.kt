package jp.miyavi.android.clawdroid.feature.chat.composer

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.ModelCatalog
import jp.miyavi.android.clawdroid.core.data.ProviderUsage
import jp.miyavi.android.clawdroid.core.data.SessionSetting
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.designsystem.AppBottomSheet
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.expandEnter
import jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor
import jp.miyavi.android.clawdroid.core.designsystem.shrinkExit

/** セッションのシートで、各設定を変えられるか。 */
data class SettingAvailability(val model: Boolean, val effort: Boolean, val permissions: Boolean)

/**
 * 入力欄のモデルのピルから開くシート。上にこのセッションの設定（モデル・エフォート・実行権限）、
 * その下にコンテキストとプロバイダの使用量。設定を選ぶと、同じシートの中で選択肢の面に切り替わる（AGENTS.md 2.7: 多段はシートの中身を積む）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerSessionSheet(
    hazeState: HazeState,
    agent: Agent?,
    session: SessionSummary?,
    model: String?,
    effort: String?,
    permission: String?,
    draftOptions: Map<SessionSetting, String>,
    availability: SettingAvailability,
    canSelectFull: Boolean,
    loadModels: suspend (String, String?) -> ModelCatalog?,
    loadUsage: suspend () -> List<ProviderUsage>,
    onSelect: (SessionSetting, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf<SessionSetting?>(null) }
    val providers by produceState<List<ProviderUsage>?>(null) { value = loadUsage() }
    // モデルの一覧は、モデルかエフォートの面を初めて開いたときに一度だけ取る
    var wantCatalog by remember { mutableStateOf(false) }
    val catalog by produceState<ModelCatalog?>(null, wantCatalog, agent?.id, session?.key) {
        if (wantCatalog && agent != null) value = loadModels(agent.id, session?.key) ?: ModelCatalog(canReset = false)
    }
    BackHandler(enabled = page != null) { page = null }

    AppBottomSheet(onDismissRequest = onDismiss, hazeState = hazeState) {
        Surface(
            color = sheetFieldColor(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState != null
                    (slideInHorizontally(motion.defaultSpatialSpec()) { if (forward) it / 4 else -it / 4 } + fadeIn(motion.defaultEffectsSpec()))
                        .togetherWith(fadeOut(motion.fastEffectsSpec()))
                },
                label = "sessionSheetPage",
            ) { current ->
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = Spacing.s)) {
                    if (current == null || agent == null) {
                        Overview(
                            model = model,
                            effort = effort,
                            permission = permission,
                            availability = availability,
                            session = session,
                            providers = providers,
                            onOpen = { setting ->
                                if (setting != SessionSetting.PERMISSIONS) wantCatalog = true
                                page = setting
                            },
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.xs)) {
                            IconButton(onClick = { page = null }) {
                                Icon(painterResource(R.drawable.ic_huge_chevron_left), contentDescription = stringResource(R.string.back_cd))
                            }
                            Text(stringResource(current.label()), style = MaterialTheme.typography.titleMedium)
                        }
                        SettingChoices(
                            setting = current,
                            agent = agent,
                            session = session,
                            draftOptions = draftOptions,
                            canSelectFull = canSelectFull,
                            catalog = catalog,
                            onSelect = { value ->
                                onSelect(current, value)
                                page = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Overview(
    model: String?,
    effort: String?,
    permission: String?,
    availability: SettingAvailability,
    session: SessionSummary?,
    providers: List<ProviderUsage>?,
    onOpen: (SessionSetting) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val timeOnly = remember(locale) { DateFormat.getTimeInstance(DateFormat.SHORT, locale) }
    val dateTime = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }
    val defaultLabel = stringResource(R.string.chat_setting_default)

    // このセッションの設定
    SettingLink(R.string.chat_setting_model, model?.substringAfterLast('/') ?: defaultLabel, availability.model) { onOpen(SessionSetting.MODEL) }
    SettingLink(R.string.chat_setting_effort, effort ?: defaultLabel, availability.effort) { onOpen(SessionSetting.EFFORT) }
    val permissionLabel = stringResource((PermissionChoice.entries.firstOrNull { it.value == permission } ?: PermissionChoice.Default).label)
    SettingLink(R.string.chat_setting_permissions, permissionLabel, availability.permissions) { onOpen(SessionSetting.PERMISSIONS) }

    Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SectionTitle(R.string.chat_context_title)
        val fraction = session?.contextFraction
        if (fraction != null && session.totalTokens != null && session.contextTokens != null) {
            UsageMeter(
                fraction = fraction,
                label = stringResource(R.string.chat_context_tokens, numbers.format(session.totalTokens), numbers.format(session.contextTokens)),
                percentage = percent.format(fraction),
            )
        } else {
            MutedText(stringResource(R.string.chat_usage_unavailable))
        }
        val input = session?.inputTokens
        val output = session?.outputTokens
        if (input != null || output != null) {
            val unknown = stringResource(R.string.chat_usage_unknown)
            MutedText(stringResource(R.string.chat_usage_tokens, input?.let(numbers::format) ?: unknown, output?.let(numbers::format) ?: unknown))
        }

        SectionTitle(R.string.chat_usage_provider)
        AnimatedVisibility(providers == null, enter = expandEnter(), exit = shrinkExit()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (providers != null && providers.isEmpty()) MutedText(stringResource(R.string.chat_usage_unavailable))
        providers?.forEach { provider ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    listOfNotNull(provider.name, provider.plan).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                )
                provider.windows.forEach { window ->
                    val reset = window.resetAt?.let {
                        stringResource(R.string.chat_usage_reset, (if (DateUtils.isToday(it)) timeOnly else dateTime).format(Date(it)))
                    }
                    UsageMeter(window.usedFraction, window.label, percent.format(window.usedFraction), reset)
                }
            }
        }
    }
}

@Composable
private fun SettingLink(label: Int, value: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label), style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(value, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = if (enabled) ({ Icon(painterResource(R.drawable.ic_huge_chevron_right), contentDescription = null) }) else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
    )
}

@Composable
private fun MutedText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * 使用量の 1 行。ラベルと割合の下に細い棒を 1 本だけ引く（枠や塗りの面は重ねない）。
 * 上限に近いと棒を error の色にする。
 */
@Composable
private fun UsageMeter(fraction: Float, label: String, percentage: String, detail: String? = null) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    LaunchedEffect(fraction) { progress.animateTo(fraction.coerceIn(0f, 1f), motion.defaultSpatialSpec()) }
    val level = contextLevel(fraction)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(percentage, style = MaterialTheme.typography.labelLarge, color = if (level == ContextLevel.HIGH) colors.error else colors.onSurface)
        }
        LinearProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.fillMaxWidth().height(Dimens.usageBarHeight),
            color = if (level == ContextLevel.HIGH) colors.error else colors.primary,
            trackColor = colors.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) }
    }
}

private fun SessionSetting.label(): Int = when (this) {
    SessionSetting.MODEL -> R.string.chat_setting_model
    SessionSetting.EFFORT -> R.string.chat_setting_effort
    SessionSetting.PERMISSIONS -> R.string.chat_setting_permissions
}

