package jp.miyavi.android.clawdroid.feature.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.GlassPill

/**
 * ソフトウェアキーボードに無いキー（spec 002 FR-022）。画面下部に浮くガラスのピル（AGENTS.md 2.7）。
 * Ctrl は押すと次の 1 文字と組み合わさる（もう一度押すと取り消し）。各キーは 48dp 以上。
 */
@Composable
internal fun SpecialKeysRow(
    hazeState: HazeState,
    ctrlArmed: Boolean,
    onKey: (SpecialKey) -> Unit,
    onToggleCtrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPill(hazeState = hazeState, modifier = modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextKey(stringResource(R.string.terminal_key_esc), stringResource(R.string.terminal_key_esc_cd)) { onKey(SpecialKey.ESC) }
            TextKey(stringResource(R.string.terminal_key_tab), stringResource(R.string.terminal_key_tab_cd)) { onKey(SpecialKey.TAB) }
            val ctrlCd = stringResource(R.string.terminal_key_ctrl_cd)
            FilledTonalIconToggleButton(
                checked = ctrlArmed,
                onCheckedChange = { onToggleCtrl() },
                modifier = Modifier.semantics { contentDescription = ctrlCd },
            ) { Text(stringResource(R.string.terminal_key_ctrl), style = MaterialTheme.typography.labelLarge) }
            ArrowKey(SpecialKey.LEFT, 0f, R.string.terminal_key_left_cd, onKey)
            ArrowKey(SpecialKey.UP, 90f, R.string.terminal_key_up_cd, onKey)
            ArrowKey(SpecialKey.DOWN, -90f, R.string.terminal_key_down_cd, onKey)
            ArrowKey(SpecialKey.RIGHT, 180f, R.string.terminal_key_right_cd, onKey)
        }
    }
}

@Composable
private fun TextKey(label: String, description: String, onClick: () -> Unit) {
    // Esc・Tab も矢印と同じ文字色（強調の色にしない）
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.heightIn(min = Dimens.minTouchTarget).widthIn(min = Dimens.minTouchTarget).semantics { contentDescription = description },
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

/** 矢印は左向きのアイコンを回して使う（Hugeicons の arrow-left）。 */
@Composable
private fun ArrowKey(key: SpecialKey, rotation: Float, description: Int, onKey: (SpecialKey) -> Unit) {
    IconButton(onClick = { onKey(key) }) {
        Icon(painterResource(R.drawable.ic_huge_arrow_left), contentDescription = stringResource(description), modifier = Modifier.rotate(rotation))
    }
}
