package jp.miyavi.android.sumahoclaw.feature.files

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassPill
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.codeTextStyle

/**
 * 編集中にキーボードの上に出す行（spec 005 FR-055・056、spec 008 FR-021・022）。左に取り消し・やり直し、区切って Tab と打ちにくい記号。
 * 収まらなければ横にスクロール。各キーは 48dp 以上。
 */
@Composable
internal fun SymbolRow(
    hazeState: HazeState,
    canUndo: Boolean,
    canRedo: Boolean,
    indent: String,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 先頭に置く主操作（エディターの保存。キーボードが出ているあいだも押せるように）。後ろに縦線で区切る。 */
    leading: (@Composable () -> Unit)? = null,
) {
    GlassPill(hazeState = hazeState, modifier = modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                VerticalDivider(Modifier.height(Spacing.xl))
            }
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(painterResource(R.drawable.ic_huge_undo), stringResource(R.string.files_undo_cd))
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(painterResource(R.drawable.ic_huge_redo), stringResource(R.string.files_redo_cd))
            }
            VerticalDivider(Modifier.height(Spacing.xl))
            SymbolKey("⇥", stringResource(R.string.files_indent_cd)) { onInsert(indent) }
            SYMBOLS.forEach { symbol -> SymbolKey(symbol, symbol) { onInsert(symbol) } }
        }
    }
}

@Composable
private fun SymbolKey(label: String, description: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
        modifier = Modifier.heightIn(min = Dimens.minTouchTarget).widthIn(min = Dimens.minTouchTarget).semantics { contentDescription = description },
    ) { Text(label, style = codeTextStyle()) }
}

private val SYMBOLS = listOf("(", ")", "{", "}", "[", "]", "<", ">", "\"", "'", "`", ";", ":", "=", "/", "\\", "|", "-", "_", "#", "$")
