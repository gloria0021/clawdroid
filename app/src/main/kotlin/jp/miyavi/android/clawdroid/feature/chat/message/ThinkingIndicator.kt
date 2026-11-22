package jp.miyavi.android.clawdroid.feature.chat.message

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.RunActivity
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import kotlinx.coroutines.delay

/**
 * 考え中の表示（Control UI の「10m 13s · …」と同じ役割）。動くインジケーター・経過時間・今していること。
 * 経過時間は 1 秒ごとにこの行だけを描き直す。今していることが分からないあいだは、言い回しを数秒ごとに入れ替える。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThinkingIndicator(startedAt: Long?, activity: RunActivity?, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(TICK_MS)
        }
    }
    val elapsed = ((now - (startedAt ?: now)) / 1_000).coerceAtLeast(0)
    val phrases = stringArrayResource(R.array.chat_thinking_phrases)
    val phraseIndex = ((elapsed / PHRASE_SECONDS) % phrases.size).toInt()
    val label = when (activity) {
        is RunActivity.Tool -> stringResource(R.string.chat_activity_tool, activity.name)
        is RunActivity.Phase -> phaseLabel(activity.phase) ?: phrases[phraseIndex]
        null -> phrases[phraseIndex]
    }
    Row(
        modifier.heightIn(min = Dimens.minTouchTarget).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        LoadingIndicator(Modifier.size(Dimens.minTouchTarget))
        Text(
            formatElapsed(elapsed),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = label,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "thinkingLabel",
        ) { text ->
            Text(
                "· $text",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 「12s」「3m 05s」「1h 02m」。 */
private fun formatElapsed(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3_600 -> "%dm %02ds".format(seconds / 60, seconds % 60)
    else -> "%dh %02dm".format(seconds / 3_600, (seconds % 3_600) / 60)
}

/** Gateway の status の phase（ref-schema の ChatRunStartupPhase）を言葉にする。分からないものは null。 */
@Composable
private fun phaseLabel(phase: String): String? = when (phase) {
    "waiting_for_state", "waiting_for_model" -> stringResource(R.string.chat_phase_waiting)
    "preparing_workspace", "naming_worktree", "preparing_worktree_base", "creating_worktree", "running_setup", "provisioning_environment" ->
        stringResource(R.string.chat_phase_workspace)
    "preparing_context", "memory_flushing" -> stringResource(R.string.chat_phase_context)
    "starting_model" -> stringResource(R.string.chat_phase_starting)
    else -> null
}

private const val TICK_MS = 1_000L
/** 言い回しを入れ替える間隔（秒）。 */
private const val PHRASE_SECONDS = 6L
