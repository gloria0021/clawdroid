package jp.miyavi.android.clawdroid.feature.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** アメーバの寸法（デザインの引き継ぎ資料の SVG 基準。顔の半径 72 に対する値）。 */
private const val AmoebaFaceRadius = 72f
private const val AmoebaThickness = 11f
private const val AmoebaAmplitude = 8f
private const val AmoebaSpeed = 0.9f
/** アメーバが届く外周の半径（72 + 11 + 8 × 1.1 + 1）。この円が [AgentAvatar] の箱に収まるように縮尺する。枠の線は描かない（マスター指定 2026-11-26）。 */
private const val AmoebaFrameRadius = AmoebaFaceRadius + AmoebaThickness + AmoebaAmplitude * 1.1f + 1f
/** 輪郭の点の数（三次ベジェでつなぐ）。 */
private const val AmoebaSamples = 72
private const val NanosPerSecond = 1_000_000_000f

/**
 * エージェントの顔。[busy] のあいだは、顔と外周の枠のすきまを利用者のメッセージの面と同じ色の
 * アメーバが囲み、輪郭がゆっくりうねる（デザインの引き継ぎ資料「Amoeba Avatar Progress」の 100% の状態。マスター指定 2026-11-26）。
 * 枠は動かさない。端末の「アニメーションを削除」では止めた形で出す。
 */
@Composable
internal fun AgentAvatar(
    agent: Agent?,
    label: String? = null,
    size: Dp = Dimens.avatar,
    busy: Boolean = false,
    /**
     * アメーバを箱の外に描き、顔は [size] のまま縮めない（周りに余白のある所。サイドパネルのタブ。マスター指定 2026-11-26）。
     * false なら箱の中に収め、そのぶん顔を縮める。
     */
    ringOutside: Boolean = false,
    /** 選ばれていない（タブ）。顔だけを薄くし、アメーバは薄くしない（稼働中は見えたままにする）。 */
    dimmed: Boolean = false,
) {
    val faceAlpha by animateFloatAsState(
        if (dimmed) DimmedFaceAlpha else 1f,
        if (dimmed) MaterialTheme.motionScheme.fastEffectsSpec() else MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "avatarFaceAlpha",
    )
    val faceSize by animateDpAsState(
        if (busy && !ringOutside) (size - Dimens.amoebaReach * 2) * (AmoebaFaceRadius / AmoebaFrameRadius) else size,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "avatarFaceSize",
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (busy) {
            // 外に描くときは、顔の大きさから枠の大きさを決めて箱からはみ出させる（重なり順は顔の後ろ）
            val ringSize = if (ringOutside) size * (AmoebaFrameRadius / AmoebaFaceRadius) + Dimens.amoebaReach * 2 else size
            // 色は利用者のメッセージの面と同じ（マスター指定 2026-11-26。以前は送信ボタンの primary）
            AmoebaRing(jp.miyavi.android.clawdroid.core.designsystem.accentContainer().container, Modifier.requiredSize(ringSize))
        }
        Box(Modifier.graphicsLayer { alpha = faceAlpha }) { AgentAvatarImage(agent, label, faceSize) }
    }
}

/** 選ばれていないタブの顔の不透明度（AGENTS.md 2.2: 無効・折りたたみは 0.6 まで）。 */
private const val DimmedFaceAlpha = 0.6f

/** 顔を囲むアメーバと枠。時間は描画の段階で読み、再構成を起こさない（AGENTS.md 2.8）。 */
@Composable
private fun AmoebaRing(color: Color, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val still = remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val time = remember { mutableFloatStateOf(0f) }
    if (!still) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { now -> time.floatValue = (now - start) / NanosPerSecond * AmoebaSpeed }
        }
    }
    Box(modifier.drawWithCache {
        // アメーバは資料の寸法より [Dimens.amoebaReach] だけ外へ広げる（マスター指定 2026-11-26）
        val reach = Dimens.amoebaReach.toPx()
        val unit = (this.size.minDimension / 2f - reach) / AmoebaFrameRadius
        val middle = androidx.compose.ui.geometry.Offset(this.size.width / 2f, this.size.height / 2f)
        val face = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(middle, AmoebaFaceRadius * unit))
        }
        val path = Path()
        val xs = FloatArray(AmoebaSamples)
        val ys = FloatArray(AmoebaSamples)
        onDrawBehind {
            val t = time.floatValue
            for (i in 0 until AmoebaSamples) {
                // 12 時を 0 とする時計回りの角度
                val a = (2 * PI * i / AmoebaSamples).toFloat()
                val wave = 0.52f * sin(4 * a - 2 * t) + 0.32f * sin(7 * a - 1.5f * t) + 0.24f * cos(3 * a - 0.9f * t)
                val r = maxOf(AmoebaFaceRadius + 0.5f, AmoebaFaceRadius + AmoebaThickness + AmoebaAmplitude * wave) * unit + reach
                xs[i] = center.x + r * sin(a)
                ys[i] = center.y - r * cos(a)
            }
            // Catmull-Rom を三次ベジェでつなぎ、全周を閉じる
            path.rewind()
            path.moveTo(xs[0], ys[0])
            for (i in 0 until AmoebaSamples) {
                val p0 = (i - 1 + AmoebaSamples) % AmoebaSamples
                val p2 = (i + 1) % AmoebaSamples
                val p3 = (i + 2) % AmoebaSamples
                path.cubicTo(
                    xs[i] + (xs[p2] - xs[p0]) / 6f, ys[i] + (ys[p2] - ys[p0]) / 6f,
                    xs[p2] - (xs[p3] - xs[i]) / 6f, ys[p2] - (ys[p3] - ys[i]) / 6f,
                    xs[p2], ys[p2],
                )
            }
            path.close()
            // 顔の円の内側は塗らない（透けた顔の画像の後ろに色が出ないように）
            clipPath(face, ClipOp.Difference) { drawPath(path, color) }
        }
    })
}

@Composable
private fun AgentAvatarImage(agent: Agent?, label: String?, size: Dp) {
    val url = remember(agent?.avatarUrl) {
        jp.miyavi.android.clawdroid.core.data.AgentAvatarBitmaps.usableUrl(agent?.avatarUrl)
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
        jp.miyavi.android.clawdroid.core.data.AgentAvatarBitmaps.usableUrl(agent.avatarUrl)
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
