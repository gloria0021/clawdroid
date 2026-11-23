package jp.miyavi.android.clawdroid.feature.chat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 考え中の表示（Control UI の「10m 13s · …」と同じ役割）。経過時間と、ふざけた言い回し。
 * 経過時間は 1 秒ごとにこの行だけを描き直す。言い回しは数秒ごとに入れ替える（ツールの実行中も名前は出さない。マスター指定 2026-11-23）。
 */
@Composable
fun ThinkingIndicator(
    startedAt: Long?,
    modifier: Modifier = Modifier,
    outputText: String = "",
    reportedOutputTokens: Long? = null,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(TICK_MS)
        }
    }
    val elapsed = ((now - (startedAt ?: now)) / 1_000).coerceAtLeast(0)
    val phrases = stringArrayResource(R.array.chat_thinking_phrases)
    // ネタは毎回ランダムに選ぶ。直前と同じネタは続けない（マスター指定 2026-11-23）
    var phraseIndex by remember(startedAt, phrases.size) { mutableIntStateOf(Random.nextInt(phrases.size)) }
    LaunchedEffect(startedAt, phrases.size) {
        if (phrases.size < 2) return@LaunchedEffect
        while (true) {
            delay(PHRASE_SECONDS * 1_000)
            phraseIndex = (phraseIndex + 1 + Random.nextInt(phrases.size - 1)) % phrases.size
        }
    }
    val label = phrases[phraseIndex]
    // Gateway から実際の数が届いたときだけ出す（画面の文からの見積もりは、思考やツールの分が入らず小さく出て誤解を招くので出さない。
    // マスター指定 2026-11-23）
    val count = remember(reportedOutputTokens) { reportedOutputTokens?.let(::formatReportedOutputTokens) }
    // 1 行目: 言い回し。2 行目: 経過時間とトークン数（マスター指定 2026-11-23）
    Column(
        modifier.heightIn(min = Dimens.compactTouchTarget).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                formatElapsed(elapsed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (count != null) Text(
                stringResource(R.string.chat_output_tokens_live, count),
                style = MaterialTheme.typography.labelMedium,
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

private const val TICK_MS = 1_000L
/** ネタを入れ替える間隔（秒）。 */
private const val PHRASE_SECONDS = 5L
