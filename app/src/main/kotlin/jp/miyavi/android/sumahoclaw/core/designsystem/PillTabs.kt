package jp.miyavi.android.sumahoclaw.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.miyavi.android.sumahoclaw.core.settings.SurfaceStyle
import kotlin.math.roundToInt

/** ピル型のタブ（Gerbil から移植）。選択中を示す面は、選んだタブへばねで滑って移る。 */
@Composable
fun <T> PillTabs(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    fillWidth: Boolean = false,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    tabHorizontalPadding: Dp = if (fillWidth) Spacing.xs else Spacing.xxl,
    /** 選んだタブの下にガラスの選択面を敷くか（中身で選択を見せるタブは false）。 */
    showIndicator: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val accent = accentContainer()
    val hazeState = LocalScreenHazeState.current
    // 各タブの左端と幅（px）。置かれてから分かる
    val bounds = remember { mutableStateMapOf<T, Pair<Float, Float>>() }
    val target = bounds[selected]
    // 位置が分かった最初の回はその場に出し、以後の変更だけを動かす
    Box {
        if (target != null && showIndicator) {
            val x by animateFloatAsState(target.first, motion.defaultSpatialSpec(), label = "tabIndicatorX")
            val width by animateFloatAsState(target.second, motion.defaultSpatialSpec(), label = "tabIndicatorWidth")
            val surface = if (hazeState != null) {
                val outline = if (LocalSurfaceStyle.current == SurfaceStyle.BLUR) {
                    Modifier.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                } else Modifier
                // 選択面はガラス。下に透かす内容が無い所（短い一覧）でもガラスに見えるよう、縁の影と光を強める。
                // 色味はほかの面と同じ（白にすると平たい面になり、ガラスに見えない。マスター指摘 2026-11-24）
                Modifier.hazeSurface(hazeState, CircleShape, rimStrength = SELECTED_RIM_STRENGTH).then(outline)
            } else {
                Modifier.background(accent.container, CircleShape)
            }
            Box(
                Modifier.matchParentSize().layout { measurable, constraints ->
                    val left = indicatorPadding.calculateLeftPadding(layoutDirection).roundToPx()
                    val right = indicatorPadding.calculateRightPadding(layoutDirection).roundToPx()
                    val top = indicatorPadding.calculateTopPadding().roundToPx()
                    val bottom = indicatorPadding.calculateBottomPadding().roundToPx()
                    val highlightWidth = (width.roundToInt() - left - right).coerceIn(0, constraints.maxWidth)
                    val highlightHeight = (constraints.maxHeight - top - bottom).coerceAtLeast(0)
                    val highlight = measurable.measure(Constraints.fixed(highlightWidth, highlightHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        // onPlaced reports a physical x, including in RTL layouts.
                        highlight.place(x.roundToInt() + left, top)
                    }
                }.then(surface),
            )
        }
        Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                val isSelected = selected == tab
                val interactions = remember { MutableInteractionSource() }
                val contentColor by animateColorAsState(
                    if (isSelected && hazeState == null) accent.content else MaterialTheme.colorScheme.onSurface,
                    motion.fastEffectsSpec(),
                    label = "tabTextColor",
                )
                Box(
                    modifier = (if (fillWidth) Modifier.weight(1f) else Modifier)
                        .onPlaced { bounds[tab] = it.positionInParent().x to it.size.width.toFloat() }
                        .selectable(
                            selected = isSelected,
                            interactionSource = interactions,
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(tab) }
                        .semantics { contentDescription = label(tab) }
                        .heightIn(min = Dimens.minTouchTarget)
                        .padding(indicatorPadding)
                        .clip(CircleShape)
                        .indication(interactions, ripple())
                        .padding(horizontal = tabHorizontalPadding, vertical = Spacing.s),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColor) { content(tab) }
                }
            }
        }
    }
}

/** タブの選択面の縁の影と光の強さ（ふつうの面の倍率）。 */
private const val SELECTED_RIM_STRENGTH = 2.5f
