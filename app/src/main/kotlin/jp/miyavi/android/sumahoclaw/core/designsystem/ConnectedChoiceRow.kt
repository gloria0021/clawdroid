package jp.miyavi.android.sumahoclaw.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp

/**
 * 1 つを選ぶ切り替え（M3 Expressive の連結したボタングループ。AGENTS.md 2.7: 切り替えは ToggleButton / ButtonGroup）。
 * 押すとボタンの形が変わり、選んだものはテーマの強い色の面になる。高さはどのボタンも同じ（文字は 1 行）。
 */
@Composable
fun <T> ConnectedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String,
) = ConnectedChoiceRowOf(options, selected, onSelect, modifier, minHeight = Dimens.minTouchTarget) {
    Text(label(it), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * 絵で選ぶ [ConnectedChoiceRow]（バーチャルオフィスのキャラクターなど。spec 009）。
 * 絵には読み上げ用の名前（[description]）を付ける。
 */
@Composable
fun <T> ConnectedImageChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    description: @Composable (T) -> String,
    image: @Composable (T) -> Painter,
) = ConnectedChoiceRowOf(options, selected, onSelect, modifier, minHeight = Dimens.imageChoiceHeight) {
    Image(image(it), contentDescription = description(it), modifier = Modifier.size(Dimens.avatarLarge))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedChoiceRowOf(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier,
    minHeight: Dp,
    content: @Composable (T) -> Unit,
) {
    Row(
        modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = minHeight)
                    .semantics { role = Role.RadioButton },
            ) { content(option) }
        }
    }
}
