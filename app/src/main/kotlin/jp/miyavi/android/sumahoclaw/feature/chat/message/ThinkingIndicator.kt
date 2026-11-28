package jp.miyavi.android.sumahoclaw.feature.chat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.RollingCounter
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.random.Random

/** 設定で書き換えた考え中のひとこと（null なら既定の訳の一覧）。 */
val LocalThinkingPhrases = androidx.compose.runtime.compositionLocalOf<List<String>?> { null }

/**
 * 考え中の表示（Control UI の「10m 13s · …」と同じ役割）。経過時間と、ふざけた言い回し。
 * 経過時間は 1 秒ごとにこの行だけを描き直す。言い回しは数秒ごとに入れ替える（ツールの実行中も名前は出さない。マスター指定 2026-11-23）。
 */
@Composable
fun ThinkingIndicator(
    startedAt: Long?,
    modifier: Modifier = Modifier,
    reportedOutputTokens: Long? = null,
    /** 決まった言い回し（コンテキストの圧縮中など）。null ならネタを入れ替えて出す。 */
    label: String? = null,
) {
    val elapsed = rememberElapsedSeconds(startedAt)
    val defaults = stringArrayResource(R.array.chat_thinking_phrases)
    // 設定で書き換えた一覧があればそちら（マスター指定 2026-11-28）
    val phrases = LocalThinkingPhrases.current ?: remember(defaults) { defaults.toList() }
    // ネタは毎回ランダムに選ぶ。直前と同じネタは続けない（マスター指定 2026-11-23）
    // 切り替えは経過秒で数え、末尾の点が 1 個に戻る瞬間にそろえる
    val phraseTick = elapsed / PHRASE_SECONDS
    var phraseIndex by remember(startedAt, phrases.size) { mutableIntStateOf(Random.nextInt(phrases.size)) }
    val firstTick = remember(startedAt) { phraseTick }
    LaunchedEffect(phraseTick, phrases.size) {
        if (phraseTick != firstTick && phrases.size >= 2) {
            phraseIndex = (phraseIndex + 1 + Random.nextInt(phrases.size - 1)) % phrases.size
        }
    }
    val shown = label ?: phrases[phraseIndex]
    // Gateway から実際の数が届いたときだけ出す（画面の文からの見積もりは、思考やツールの分が入らず小さく出て誤解を招くので出さない。
    // マスター指定 2026-11-23）
    val count = remember(reportedOutputTokens) { reportedOutputTokens?.let(::formatReportedOutputTokens) }
    // 1 行目: 言い回し。2 行目: 経過時間とトークン数（マスター指定 2026-11-23）
    Column(
        modifier.heightIn(min = Dimens.compactTouchTarget).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.Center,
    ) {
        // 末尾の「…」を 1 秒ごとに 1 → 2 → 3 → 0 個と回る点に替える。残りの点は透明で幅を取り、文字が横に揺れないようにする
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        val dots = ((elapsed + 1) % (DOT_COUNT + 1)).toInt()
        Text(
            buildAnnotatedString {
                append(shown.removeSuffix("…"))
                append(".".repeat(dots))
                withStyle(SpanStyle(color = color.copy(alpha = 0f))) { append(".".repeat(DOT_COUNT - dots)) }
            },
            modifier = Modifier.clearAndSetSemantics { contentDescription = shown },
            // 状態の言い回しは 1 段大きく（経過時間とトークン数は labelMedium のまま。マスター指定 2026-11-26）
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            RollingElapsed(elapsed)
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

/**
 * 「12s」「3m 05s」「1h 02m」を、差分の数と同じ桁の回転で数え上げる。
 * 下の単位は上の単位が変わるたびに作り直し、59 → 0 を逆回転させずに 0 から始める。
 */
@Composable
private fun RollingElapsed(seconds: Long) {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row {
        when {
            seconds < 60 -> RollingCounter(seconds.toInt(), style, color, suffix = "s", animateFirst = false)
            seconds < 3_600 -> {
                val minutes = (seconds / 60).toInt()
                RollingCounter(minutes, style, color, suffix = "m ", animateFirst = false)
                key(minutes) { RollingCounter((seconds % 60).toInt(), style, color, suffix = "s", minDigits = 2, animateFirst = false) }
            }
            else -> {
                val hours = (seconds / 3_600).toInt()
                RollingCounter(hours, style, color, suffix = "h ", animateFirst = false)
                key(hours) { RollingCounter(((seconds % 3_600) / 60).toInt(), style, color, suffix = "m", minDigits = 2, animateFirst = false) }
            }
        }
    }
}

/** 画面を離れた間は刻まず、復帰時に開始時刻から計算し直す。 */
@Composable
private fun rememberElapsedSeconds(startedAt: Long?): Long {
    if (startedAt == null) return 0L
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun ageMs() = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
    var seconds by remember(startedAt) { mutableLongStateOf(ageMs() / TICK_MS) }
    LaunchedEffect(startedAt, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val age = ageMs()
                seconds = age / TICK_MS
                delay(TICK_MS - age % TICK_MS)
            }
        }
    }
    return seconds
}

private const val TICK_MS = 1_000L
/** 言い回しの末尾で増える点の最大数。 */
private const val DOT_COUNT = 3
/** ネタを入れ替えるまでに点が回る回数（1 周 = DOT_COUNT + 1 秒）。 */
private const val PHRASE_DOT_LOOPS = 2
/** ネタを入れ替える間隔（秒）。点の周回の切れ目にそろえるため、周回の長さの倍数にする。 */
private const val PHRASE_SECONDS = (DOT_COUNT + 1) * PHRASE_DOT_LOOPS * 1L
