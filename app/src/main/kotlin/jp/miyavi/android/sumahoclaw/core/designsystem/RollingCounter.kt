package jp.miyavi.android.sumahoclaw.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.MotionDurationScale
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

/**
 * 数を、走行距離計のように桁ごとに回して数え上げる・数え下げる（Gerbil のレス数の数え方を元に、桁の回転を足したもの）。
 * 増えるときは 1 の位が上へ回り、繰り上がるときだけ 10 の位が回る。減るときはその逆に下へ回る。
 * 回る量は描画（配置）の中で読むので、毎フレームの組み立て直しは桁の数字が変わるときだけ。
 */
@Composable
fun RollingCounter(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    /** 0 で埋めて常に見せる桁数（「3m 05s」の秒など）。 */
    minDigits: Int = 1,
    /** false なら最初の表示は今の値から始める（スクロールで入ってきた行や開き直しで数え上げない）。 */
    animateFirst: Boolean = true,
) {
    val animated = remember { Animatable(if (animateFirst) 0f else value.toFloat()) }
    val motion = MaterialTheme.motionScheme
    val spec = motion.defaultEffectsSpec<Float>()
    LaunchedEffect(value) {
        if (animated.value == value.toFloat()) return@LaunchedEffect
        // 色・不透明度用のばねは速すぎるので、時間だけ延ばす（Gerbil と同じ考え方。弾まないばねのまま）
        val systemScale = coroutineContext[MotionDurationScale]
        withContext(object : MotionDurationScale {
            override val scaleFactor: Float get() = (systemScale?.scaleFactor ?: 1f) * COUNT_DURATION_MULTIPLIER
        }) {
            animated.animateTo(value.toFloat(), spec)
        }
    }
    // 幅を動かさない（揺れないように）: 桁の数は「動く前と後の大きいほう」で決め、上の桁の 0 は見えなくするだけで場所は取っておく。
    // 桁の幅は 0〜9 でいちばん広い数字に合わせる（プロポーショナルな字体でも 1 と 8 で幅が変わらない）
    val digits by remember(minDigits, value) { derivedStateOf { maxOf(minDigits, digitCount(maxOf(abs(animated.value), abs(value.toFloat())).roundToInt())) } }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val digitWidth = remember(style, density) {
        with(density) { (0..9).maxOf { measurer.measure(it.toString(), style).size.width }.toDp() }
    }
    val lineHeight = with(density) { style.lineHeight.toDp() }
    Row(modifier.clearAndSetSemantics { contentDescription = "$prefix$value$suffix" }) {
        if (prefix.isNotEmpty()) Text(prefix, style = style, color = color)
        for (position in digits - 1 downTo 0) {
            Digit(position, position < minDigits, { animated.value }, style, color, Modifier.width(digitWidth).height(lineHeight))
        }
        if (suffix.isNotEmpty()) Text(suffix, style = style, color = color)
    }
}

/** 1 つの桁。いまの数字と次の数字を縦に並べ、回る量だけずらして窓から見せる。 */
@Composable
private fun Digit(position: Int, alwaysShown: Boolean, value: () -> Float, style: TextStyle, color: Color, modifier: Modifier) {
    val unit = 10f.pow(position)
    val current by remember(position) { derivedStateOf { (floor(value().coerceAtLeast(0f) / unit).toInt()) % 10 } }
    val density = LocalDensity.current
    val lineHeightPx = with(density) { style.lineHeight.toPx() }
    val lineHeight = with(density) { style.lineHeight.toDp() }
    Box(
        modifier.clipToBounds().graphicsLayer {
            // 上の桁の 0（まだ届いていない桁）は見せない。1 の位と 0 で埋める桁はいつも見せる
            alpha = if (alwaysShown || value().coerceAtLeast(0f) >= unit - 1f) 1f else 0f
        },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // 枠（1 行ぶん）に縛られると 2 つ目の数字が高さ 0 になるので、2 行ぶんで並べて枠で切る（去る数字と来る数字を同時に見せる）
            modifier = Modifier.wrapContentHeight(Alignment.Top, unbounded = true).graphicsLayer {
                // 下の桁がすべて 9 のあいだだけ、1 の位の端数ぶん回る（繰り上がり）
                val v = value().coerceAtLeast(0f)
                val below = v % unit
                // 回るのは端数の終わりの区間だけ（ばねの終わりは 1 の位がゆっくり進むので、途中の半端な位置で止まって
                // 数字が読めなくなるのを防ぐ。それまでは今の数字のまま、区間に入ったら素早く次へ回る）
                val progress = if (below >= unit - 1f) rollProgress(v - floor(v)) else 0f
                translationY = -progress * lineHeightPx
            },
        ) {
            Text(current.toString(), style = style, color = color, modifier = Modifier.height(lineHeight))
            Text(((current + 1) % 10).toString(), style = style, color = color, modifier = Modifier.height(lineHeight))
        }
    }
}

private fun digitCount(n: Int): Int = if (n < 10) 1 else n.toString().length

/** 端数（0〜1）から回る量へ。最後の [ROLL_WINDOW] の区間だけで 0→1 に回る。 */
private fun rollProgress(fraction: Float): Float = ((fraction - (1f - ROLL_WINDOW)) / ROLL_WINDOW).coerceIn(0f, 1f)

/** 1 つの数字が次へ回り切るまでに使う端数の区間。 */
private const val ROLL_WINDOW = 0.35f

/** 数える動きの長さ（effects のばねの何倍か）。 */
private const val COUNT_DURATION_MULTIPLIER = 6f
