package jp.miyavi.android.sumahoclaw.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.hazeSurface
import jp.miyavi.android.sumahoclaw.core.text.LineSeparator
import jp.miyavi.android.sumahoclaw.core.text.TextCharset
import jp.miyavi.android.sumahoclaw.feature.files.CodeLanguage
import java.text.NumberFormat

/** 情報の帯に出すもの（値だけ）。 */
data class StatusBarState(
    val line: Int,
    val column: Int,
    val chars: Int,
    val selectedChars: Int?,
    val lines: Int,
    val charset: TextCharset,
    val bom: Boolean,
    val lineSeparator: LineSeparator,
    val language: CodeLanguage?,
    val canChangeCharset: Boolean,
)

/**
 * 情報の帯（spec 008 FR-030〜032、contracts/editor-ui.md）。「行:列 · 文字数 · 行数 · 文字コード · 改行 · 言語」。
 * 収まらないときは 言語 → 改行 → 文字コード → 行数 の順に省き、文字数は残す。文字コードと言語は押すと選び直せる。
 */
@Composable
fun EditorStatusBar(
    hazeState: HazeState,
    state: StatusBarState,
    onSelectCharset: (TextCharset) -> Unit,
    onSelectLanguage: (CodeLanguage?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val number = remember { NumberFormat.getIntegerInstance() }
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    var charsetMenu by remember { mutableStateOf(false) }
    var languageMenu by remember { mutableStateOf(false) }
    val position = stringResource(R.string.editor_status_position, state.line, state.column)
    val chars = state.selectedChars?.let { stringResource(R.string.editor_status_selected, number.format(it)) }
        ?: stringResource(R.string.editor_status_chars, number.format(state.chars))
    val lines = stringResource(R.string.editor_status_lines, number.format(state.lines))
    val charset = if (state.bom) stringResource(R.string.editor_status_bom, state.charset.label) else state.charset.label
    val separator = if (state.lineSeparator == LineSeparator.MIXED) stringResource(R.string.editor_status_mixed) else state.lineSeparator.label
    val language = state.language?.let { stringResource(it.label) } ?: stringResource(R.string.editor_status_plain)

    Row(
        modifier
            .fillMaxWidth()
            .hazeSurface(hazeState, RoundedCornerShape(0.dp), rim = false)
            .heightIn(min = Dimens.compactTouchTarget)
            .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 幅に収まる分だけ並べる（後ろから省く。文字数と位置は必ず残す）
        Layout(
            content = {
                StatusText(position, style, color)
                StatusText(chars, style, color)
                StatusText(lines, style, color)
                Box {
                    StatusText(
                        charset, style, color,
                        onClick = if (state.canChangeCharset) ({ charsetMenu = true }) else null,
                        clickLabel = stringResource(R.string.editor_charset_cd),
                    )
                    GlassDropdownMenu(expanded = charsetMenu, onDismissRequest = { charsetMenu = false }) {
                        selectableCharsets.forEach { cs ->
                            DropdownMenuItem(text = { Text(cs.label) }, onClick = { charsetMenu = false; onSelectCharset(cs) })
                        }
                    }
                }
                StatusText(separator, style, color)
                Box {
                    StatusText(language, style, color, onClick = { languageMenu = true }, clickLabel = stringResource(R.string.editor_language_cd))
                    LanguageMenu(languageMenu, { languageMenu = false }, onSelectLanguage, selected = state.language)
                }
            },
            modifier = Modifier.weight(1f),
        ) { measurables, constraints ->
            val gap = Spacing.m.roundToPx()
            val loose = constraints.copy(minWidth = 0)
            val placeables = measurables.map { it.measure(loose) }
            // 残す順（大事なものから）: 位置・文字数・行数・文字コード・改行・言語
            val keep = BooleanArray(placeables.size)
            var used = 0
            for (i in placeables.indices) {
                val w = placeables[i].width + if (i == 0) 0 else gap
                if (i < 2 || used + w <= constraints.maxWidth) {
                    keep[i] = true
                    used += w
                } else break
            }
            val height = placeables.maxOf { it.height }
            layout(constraints.maxWidth, height) {
                var x = 0
                placeables.forEachIndexed { i, p ->
                    if (!keep[i]) return@forEachIndexed
                    p.placeRelative(x, (height - p.height) / 2)
                    x += p.width + gap
                }
            }
        }
    }
}

@Composable
private fun StatusText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
) {
    Text(
        text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = (if (onClick != null) Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = Dimens.compactTouchTarget)
            .padding(vertical = Spacing.s),
    )
}
