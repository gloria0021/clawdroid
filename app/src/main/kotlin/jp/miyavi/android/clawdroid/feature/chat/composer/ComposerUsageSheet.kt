package jp.miyavi.android.clawdroid.feature.chat.composer

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.ProviderUsage
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.designsystem.AppBottomSheet
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.expandEnter
import jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor
import jp.miyavi.android.clawdroid.core.designsystem.shrinkExit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerUsageSheet(
    hazeState: HazeState,
    model: String?,
    session: SessionSummary?,
    loadUsage: suspend () -> List<ProviderUsage>,
    onDismiss: () -> Unit,
) {
    val providers by produceState<List<ProviderUsage>?>(null) { value = loadUsage() }
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val timeOnly = remember(locale) { DateFormat.getTimeInstance(DateFormat.SHORT, locale) }
    val dateTime = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }

    AppBottomSheet(onDismissRequest = onDismiss, hazeState = hazeState) {
        Surface(
            color = sheetFieldColor(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.titleMedium)
                model?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.chat_context_title), style = MaterialTheme.typography.titleSmall)
                val fraction = session?.contextFraction
                if (fraction != null) {
                    UsageMeter(
                        fraction = fraction,
                        percentage = percent.format(fraction),
                        label = stringResource(
                            R.string.chat_context_tokens,
                            numbers.format(session.totalTokens),
                            numbers.format(session.contextTokens),
                        ),
                    )
                } else {
                    Text(stringResource(R.string.chat_usage_unavailable), style = MaterialTheme.typography.bodyMedium)
                }
                val input = session?.inputTokens
                val output = session?.outputTokens
                if (input != null || output != null) {
                    val unknown = stringResource(R.string.chat_usage_unknown)
                    Text(
                        stringResource(R.string.chat_usage_tokens, input?.let(numbers::format) ?: unknown, output?.let(numbers::format) ?: unknown),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(stringResource(R.string.chat_usage_provider), style = MaterialTheme.typography.titleSmall)
                AnimatedVisibility(providers == null, enter = expandEnter(), exit = shrinkExit()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                val usage = providers
                if (usage != null && usage.isEmpty()) {
                    Text(stringResource(R.string.chat_usage_unavailable), style = MaterialTheme.typography.bodyMedium)
                }
                usage?.forEach { provider ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_huge_usage),
                                null,
                                Modifier.size(Spacing.l),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(provider.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (provider.plan != null) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ) { Text(provider.plan, Modifier.padding(horizontal = Spacing.xs)) }
                            }
                        }
                        provider.windows.forEach { window ->
                            val reset = window.resetAt?.let {
                                val format = if (DateUtils.isToday(it)) timeOnly else dateTime
                                stringResource(R.string.chat_usage_reset, format.format(Date(it)))
                            }
                            UsageMeter(window.usedFraction, percent.format(window.usedFraction), window.label, reset)
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsageMeter(fraction: Float, percentage: String, label: String, detail: String? = null) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    LaunchedEffect(fraction, motion) {
        progress.animateTo(fraction, animationSpec = motion.defaultSpatialSpec())
    }
    val nearLimit = fraction >= NEAR_USAGE_LIMIT
    Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Box {
            LinearProgressIndicator(
                progress = { progress.value.coerceIn(0f, 1f) },
                modifier = Modifier.matchParentSize(),
                color = if (nearLimit) colors.errorContainer else colors.primaryContainer,
                trackColor = Color.Transparent,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = Spacing.xxl).padding(horizontal = Spacing.m, vertical = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    if (detail != null) {
                        Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
                Text(
                    percentage,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (nearLimit) colors.onErrorContainer else colors.onSurface,
                )
            }
        }
    }
}

private const val NEAR_USAGE_LIMIT = 0.9f
