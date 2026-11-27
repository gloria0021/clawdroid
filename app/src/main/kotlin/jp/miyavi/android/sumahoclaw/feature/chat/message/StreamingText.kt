package jp.miyavi.android.sumahoclaw.feature.chat.message

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.flow.first
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 生成中の本文。届いた文字を 1 文字ずつフェードで出す（Gerbil の StreamingText に倣う。マスター指定 2026-11-26）。
 *
 * 軽く保つため（AGENTS.md 2.8）、文字の並べ直しは文字が届いたときだけ。出ていく位置は描画の中だけで読み、
 * まだの文字は描いたあとに消す（先頭の数文字は薄く、その先は透明）。透明な文字も最初から場所を取るので、
 * 現れる途中で折り返しや高さが変わらない。
 *
 * @param revealed 出し終えた位置（文字数。小数は途中のフェード）。[rememberStreamingReveal] で作り、本文が空に戻ったら 0 にする。
 */
@Composable
internal fun StreamingText(text: String, style: TextStyle, revealed: MutableFloatState, modifier: Modifier = Modifier) {
    val length by rememberUpdatedState(text.length)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // 消すための面。色は使わず不透明度だけが効く（不透明な色なら何でもよい）
    val mask = MaterialTheme.colorScheme.onSurface

    LaunchedEffect(Unit) {
        // 端末のアニメーションの速さに合わせる（オフなら一度に出す）
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        while (true) {
            // 新しい文字が届くまで待つ（届いていなければフレームを回さない）
            snapshotFlow { length + STREAM_FADE_CHARS > revealed.floatValue }.first { it }
            if (scale == 0f) {
                revealed.floatValue = length + STREAM_FADE_CHARS
                continue
            }
            var last = withFrameNanos { it }
            while (revealed.floatValue < length + STREAM_FADE_CHARS) {
                val now = withFrameNanos { it }
                val seconds = (now - last) / NANOS_PER_SECOND / scale
                last = now
                val target = length + STREAM_FADE_CHARS
                // shortcut: 文字の出る速さは固定（M3 のトークンに文字送りの速さがないため。Gerbil と同じ例外）。
                // 届くのが速いときは、遅れが STREAM_CATCH_UP_SECONDS で埋まる速さまで上げる
                val speed = max(STREAM_CHARS_PER_SECOND, (target - revealed.floatValue) / STREAM_CATCH_UP_SECONDS)
                revealed.floatValue = min(target, revealed.floatValue + speed * seconds)
            }
        }
    }

    Text(
        text,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier
            // 消す描き方（DstOut）は別の層でだけ効く。出し終えているあいだは層を分けない
            .graphicsLayer {
                compositingStrategy = if (revealed.floatValue < length + STREAM_FADE_CHARS) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawWithContent {
                drawContent()
                val l = layout ?: return@drawWithContent
                val n = l.layoutInput.text.length
                val p = revealed.floatValue.coerceAtMost(n + STREAM_FADE_CHARS)
                val solid = (p - STREAM_FADE_CHARS).toInt().coerceIn(0, n)
                if (solid >= n) return@drawWithContent
                // 現れかけの数文字: 先頭ほど薄く
                val hiddenFrom = ceil(p).toInt().coerceIn(solid, n)
                for (i in solid until hiddenFrom) {
                    val shown = ((p - i) / STREAM_FADE_CHARS).coerceIn(0f, 1f)
                    val box = l.getBoundingBox(i)
                    val line = l.getLineForOffset(i)
                    val top = l.getLineTop(line)
                    drawRect(
                        mask, topLeft = Offset(box.left, top), size = Size(box.width, l.getLineBottom(line) - top),
                        alpha = 1f - shown, blendMode = BlendMode.DstOut,
                    )
                }
                // まだの文字: その行の残りと、下の行をまとめて消す（文字ごとには回さない）
                if (hiddenFrom < n) {
                    val line = l.getLineForOffset(hiddenFrom)
                    val top = l.getLineTop(line)
                    val bottom = l.getLineBottom(line)
                    val left = l.getBoundingBox(hiddenFrom).left
                    drawRect(mask, topLeft = Offset(left, top), size = Size(size.width - left, bottom - top), blendMode = BlendMode.DstOut)
                    if (bottom < size.height) {
                        drawRect(mask, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom), blendMode = BlendMode.DstOut)
                    }
                }
            },
    )
}

/** 出し終えた位置。[alreadyShown]（会話を開いた時点で届いていた分）は最初から出しておき、動かさない。 */
@Composable
internal fun rememberStreamingReveal(alreadyShown: Int): MutableFloatState =
    remember { mutableFloatStateOf(if (alreadyShown > 0) alreadyShown + STREAM_FADE_CHARS else 0f) }

/** 1 秒に出す文字数（Gerbil の 1 文字 28ms に合わせる）。 */
private const val STREAM_CHARS_PER_SECOND = 36f
/** 現れかけで薄く出す文字数。 */
private const val STREAM_FADE_CHARS = 6f
/** 届くのが速いとき、遅れを埋めるまでの時間（秒）。 */
private const val STREAM_CATCH_UP_SECONDS = 0.5f
private const val NANOS_PER_SECOND = 1_000_000_000f
