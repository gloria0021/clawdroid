package jp.miyavi.android.clawdroid.feature.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import coil3.compose.AsyncImage
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.accentContainer
import jp.miyavi.android.clawdroid.feature.chat.message.webUri
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val FullTurn = 2 * PI
private const val WaveSamples = 96
private const val WaveTurnsPerLoop = 3
/** 波の角（山）の数。 */
private const val WaveCrests = 5
/** 角が 1 周のあいだに進む位相（整数で繰り返しをつなぐ。角 8・位相 23 のときとほぼ同じ回転の速さ）。 */
private const val WaveCrestPhase = 14
private const val MillisPerSecond = 1_000

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AgentAvatar(agent: Agent?, label: String? = null, size: Dp = Dimens.avatar, busy: Boolean = false) {
    val waveInset = Spacing.xs
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (busy) {
            val wavelength = WavyProgressIndicatorDefaults.CircularWavelength
            val loopMillis = (PI * size.value / wavelength.value * MillisPerSecond * WaveTurnsPerLoop)
                .roundToInt().coerceAtLeast(1)
            val cycle = rememberInfiniteTransition(label = "avatarWave").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(loopMillis, easing = LinearEasing)),
                label = "clockwiseWaveCycle",
            )
            val waveColor = accentContainer().container
            Box(Modifier.matchParentSize().drawWithCache {
                val path = Path()
                // Let the tallest crests extend beyond the layout box while the image stays put.
                val crestRoom = Spacing.s.toPx()
                val imageRadius = this.size.minDimension / 2f - waveInset.toPx()
                onDrawBehind {
                    val phase = FullTurn * cycle.value
                    path.rewind()
                    for (sample in 0 until WaveSamples) {
                        val angle = FullTurn * sample / WaveSamples
                        // Unequal clockwise speeds let crests overtake and merge rather than
                        // spinning a fixed silhouette. Integer phases keep the loop seamless.
                        val waterline = 0.45 +
                            0.16 * cos(angle - WaveTurnsPerLoop * phase) +
                            0.12 * cos(2 * angle - 5 * phase) +
                            0.05 * cos(3 * angle - 10 * phase + PI / 3) +
                            (0.15 + 0.04 * cos(angle - 2 * phase)) * cos(WaveCrests * angle - WaveCrestPhase * phase)
                        // Keep even the deepest trough visible so the wave remains continuous.
                        val waveHeight = 0.06 + 0.94 * waterline.coerceIn(0.0, 1.0)
                        val radius = imageRadius + crestRoom * waveHeight.toFloat()
                        val x = center.x + radius * cos(angle).toFloat()
                        val y = center.y + radius * sin(angle).toFloat()
                        if (sample == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, waveColor)
                }
            })
        }
        AgentAvatarImage(agent, label, if (busy) size - waveInset * 2 else size)
    }
}

@Composable
private fun AgentAvatarImage(agent: Agent?, label: String?, size: Dp) {
    val url = remember(agent?.avatarUrl) {
        agent?.avatarUrl?.trim()?.takeIf { it.startsWith("data:image/", ignoreCase = true) || webUri(it) != null }
    }
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        Modifier.size(size).clip(CircleShape).clearAndSetSemantics {
            if (label != null) contentDescription = label
        },
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            val glyph = agent?.emoji?.takeIf(String::isNotBlank)
                ?: agent?.name?.firstOrNull()?.takeUnless { it.isSurrogate() }?.toString()
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                if (glyph != null) {
                    Text(glyph, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                } else {
                    Icon(painterResource(R.drawable.ic_huge_launcher_glyph), null, Modifier.size(Spacing.l))
                }
            }
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * サイドパネルの頭の看板の画像。顔の画像を横いっぱい・真ん中基準で敷き、下へいくほど背景に溶かす（色は足さず、画像の透け方だけ）。
 * 画像が無いときは、絵文字・頭文字を大きく出す。
 */
@Composable
internal fun AgentHero(agent: Agent, modifier: Modifier = Modifier) {
    val url = remember(agent.avatarUrl) {
        agent.avatarUrl?.trim()?.takeIf { it.startsWith("data:image/", ignoreCase = true) || webUri(it) != null }
    }
    var loaded by remember(url) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        HERO_FADE_START to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            val glyph = agent.emoji?.takeIf(String::isNotBlank) ?: agent.name.firstOrNull()?.takeUnless { it.isSurrogate() }?.toString()
            Box(Modifier.matchParentSize().background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                if (glyph != null) Text(glyph, style = MaterialTheme.typography.displayMedium, maxLines = 1)
            }
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** 看板の高さのうち、ここから下を消していく。 */
private const val HERO_FADE_START = 0.35f
