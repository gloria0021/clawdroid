package jp.miyavi.android.clawdroid.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import jp.miyavi.android.clawdroid.R

private const val CheckStartScale = 0.6f

/** M3 のセグメントの選択色とチェックを切り替える。 */
@Composable
fun <T> AnimatedSegmentedRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = SegmentedButtonDefaults.ContentPadding,
    label: @Composable (T) -> String,
) {
    val defaults = SegmentedButtonDefaults.colors()
    val motion = MaterialTheme.motionScheme
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, option ->
            val checked = selected == option
            val spec = if (checked) motion.slowEffectsSpec<Color>() else motion.defaultEffectsSpec()
            val container by animateColorAsState(
                if (checked) defaults.activeContainerColor else defaults.inactiveContainerColor,
                animationSpec = spec,
                label = "segmentContainer",
            )
            val content by animateColorAsState(
                if (checked) defaults.activeContentColor else defaults.inactiveContentColor,
                animationSpec = spec,
                label = "segmentContent",
            )
            val checkPosition by animateFloatAsState(
                if (checked) 1f else 0f,
                animationSpec = if (checked) motion.defaultSpatialSpec() else motion.fastSpatialSpec(),
                label = "segmentCheckPosition",
            )
            val checkAlpha by animateFloatAsState(
                if (checked) 1f else 0f,
                animationSpec = if (checked) motion.defaultEffectsSpec() else motion.fastEffectsSpec(),
                label = "segmentCheckAlpha",
            )
            SegmentedButton(
                selected = checked,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = defaults.copy(
                    activeContainerColor = container,
                    inactiveContainerColor = container,
                    activeContentColor = content,
                    inactiveContentColor = content,
                ),
                contentPadding = contentPadding,
                icon = {},
                label = {
                    val iconSize = SegmentedButtonDefaults.IconSize
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_huge_check),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize)
                                .graphicsLayer {
                                    translationX = (checkPosition - 1f) * Spacing.xs.toPx() * direction
                                    val scale = CheckStartScale + (1f - CheckStartScale) * checkPosition
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = checkAlpha
                                },
                        )
                        Text(label(option), Modifier.weight(1f, fill = false), textAlign = TextAlign.Center)
                        // チェックと同じ幅を反対側にも取り、ラベルを中央に保つ。
                        Spacer(Modifier.width(iconSize))
                    }
                },
            )
        }
    }
}
