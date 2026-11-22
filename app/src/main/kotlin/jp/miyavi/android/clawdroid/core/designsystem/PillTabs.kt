package jp.miyavi.android.clawdroid.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** ピル型のタブ（Gerbil から移植）。選択中を示す面は、選んだタブへばねで滑って移る。 */
@Composable
fun <T> PillTabs(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    fillWidth: Boolean = false,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    tabHorizontalPadding: Dp = if (fillWidth) Spacing.xs else Spacing.xxl,
) {
    val motion = MaterialTheme.motionScheme
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    // 各タブの左端と幅（px）。置かれてから分かる
    val bounds = remember { mutableStateMapOf<T, Pair<Float, Float>>() }
    val target = bounds[selected]
    // 位置が分かった最初の回はその場に出し、以後の変更だけを動かす
    val indicator = if (target != null) {
        val x by animateFloatAsState(target.first, motion.defaultSpatialSpec(), label = "tabIndicatorX")
        val width by animateFloatAsState(target.second, motion.defaultSpatialSpec(), label = "tabIndicatorWidth")
        Modifier.drawBehind {
            val left = indicatorPadding.calculateLeftPadding(layoutDirection).toPx()
            val right = indicatorPadding.calculateRightPadding(layoutDirection).toPx()
            val top = indicatorPadding.calculateTopPadding().toPx()
            val height = (size.height - top - indicatorPadding.calculateBottomPadding().toPx()).coerceAtLeast(0f)
            drawRoundRect(
                color = indicatorColor,
                topLeft = Offset(x + left, top),
                size = Size((width - left - right).coerceAtLeast(0f), height),
                cornerRadius = CornerRadius(height / 2f),
            )
        }
    } else {
        Modifier
    }
    Row(indicator.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        tabs.forEach { tab ->
            val isSelected = selected == tab
            val interactions = remember { MutableInteractionSource() }
            val textColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
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
                    .heightIn(min = Dimens.minTouchTarget)
                    .padding(indicatorPadding)
                    .clip(CircleShape)
                    .indication(interactions, ripple())
                    .padding(horizontal = tabHorizontalPadding, vertical = Spacing.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(tab), style = MaterialTheme.typography.labelLarge, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
