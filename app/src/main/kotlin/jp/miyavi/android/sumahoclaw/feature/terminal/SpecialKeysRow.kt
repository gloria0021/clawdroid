package jp.miyavi.android.sumahoclaw.feature.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassPill

/**
 * ソフトウェアキーボードに無いキー（spec 002 FR-022）。画面下部に浮くガラスのピル（AGENTS.md 2.7）。
 * ^C は割り込みを 1 回で送る。各キーは 48dp 以上、収まらなければ横にスクロール。
 */
@Composable
internal fun SpecialKeysRow(
    hazeState: HazeState,
    onKey: (SpecialKey) -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    /** シェルにつながっているか（送るキーを押せるか）。 */
    connected: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // 1 行目: Esc・Tab・^C、2 行目: コピー・貼り付け・← → ↑ ↓・Enter（マスター指定 2026-11-24）
    GlassPill(hazeState = hazeState, modifier = modifier) {
        // 2 段とも左寄せ（マスター指定 2026-11-24）
        Column(horizontalAlignment = Alignment.Start) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextKey(stringResource(R.string.terminal_key_esc), stringResource(R.string.terminal_key_esc_cd), enabled = connected) { onKey(SpecialKey.ESC) }
                TextKey(stringResource(R.string.terminal_key_tab), stringResource(R.string.terminal_key_tab_cd), enabled = connected) { onKey(SpecialKey.TAB) }
                TextKey(stringResource(R.string.terminal_key_interrupt), stringResource(R.string.terminal_key_interrupt_cd), enabled = connected) { onKey(SpecialKey.INTERRUPT) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                IconKey(R.drawable.ic_huge_copy, R.string.terminal_key_copy_cd, onClick = onCopy)
                IconKey(R.drawable.ic_huge_paste, R.string.terminal_key_paste_cd, enabled = connected, onClick = onPaste)
                ArrowKey(SpecialKey.LEFT, 0f, R.string.terminal_key_left_cd, connected, onKey)
                ArrowKey(SpecialKey.RIGHT, 180f, R.string.terminal_key_right_cd, connected, onKey)
                ArrowKey(SpecialKey.UP, 90f, R.string.terminal_key_up_cd, connected, onKey)
                ArrowKey(SpecialKey.DOWN, -90f, R.string.terminal_key_down_cd, connected, onKey)
                IconKey(R.drawable.ic_huge_enter, R.string.terminal_key_enter_cd, enabled = connected) { onKey(SpecialKey.ENTER) }
            }
        }
    }
}

@Composable
private fun IconKey(icon: Int, description: Int, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(painterResource(icon), contentDescription = stringResource(description)) }
}

@Composable
private fun TextKey(label: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    // Esc・Tab も矢印と同じ文字色（強調の色にしない）
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.heightIn(min = Dimens.minTouchTarget).widthIn(min = Dimens.minTouchTarget).semantics { contentDescription = description },
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

/** 矢印は左向きのアイコンを回して使う（Hugeicons の arrow-left）。 */
@Composable
private fun ArrowKey(key: SpecialKey, rotation: Float, description: Int, enabled: Boolean, onKey: (SpecialKey) -> Unit) {
    IconButton(onClick = { onKey(key) }, enabled = enabled) {
        Icon(painterResource(R.drawable.ic_huge_arrow_left), contentDescription = stringResource(description), modifier = Modifier.rotate(rotation))
    }
}
