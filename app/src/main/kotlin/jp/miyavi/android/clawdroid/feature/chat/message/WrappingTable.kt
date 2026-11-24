package jp.miyavi.android.clawdroid.feature.chat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.LocalTableRowIndex
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes.HEADER
import org.intellij.markdown.flavours.gfm.GFMElementTypes.ROW
import org.intellij.markdown.flavours.gfm.GFMTokenTypes.CELL
import org.intellij.markdown.flavours.gfm.GFMTokenTypes.TABLE_SEPARATOR

/**
 * Markdown の表（マスター指摘 2026-11-24: 途切れずに全文を見られるように）。
 * ライブラリの表はセルを 1 行で省略記号にするので、セルは折り返して全文を出す。列は最低 [Dimens.tableCellMinWidth] を取り、
 * 画面に収まらなければ表だけを横にスクロールする（会話そのものは横に動かさない）。
 */
@Composable
internal fun WrappingMarkdownTable(model: MarkdownComponentModel) {
    val node: ASTNode = model.node
    val style = model.typography.table
    val settings = annotatorSettings()
    val columns = remember(node) { node.findChildOfType(HEADER)?.children?.count { it.type == CELL } ?: 0 }
    if (columns == 0) return
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(LocalMarkdownColors.current.tableBackground, MaterialTheme.shapes.medium),
    ) {
        // 列は画面を等分し、狭すぎるときは最低幅を取って横にスクロール
        val columnWidth: Dp = max(maxWidth / columns, Dimens.tableCellMinWidth)
        val tableWidth = columnWidth * columns
        val scrollable = tableWidth > maxWidth
        Column(
            if (scrollable) Modifier.horizontalScroll(rememberScrollState()).requiredWidth(tableWidth) else Modifier.fillMaxWidth(),
        ) {
            var rowIndex = 1
            node.children.forEach { child ->
                when (child.type) {
                    HEADER -> MarkdownTableHeader(
                        content = model.content, header = child, tableWidth = tableWidth, style = style,
                        maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip, annotatorSettings = settings,
                    )
                    ROW -> {
                        CompositionLocalProvider(LocalTableRowIndex provides rowIndex) {
                            MarkdownTableRow(
                                content = model.content, header = child, tableWidth = tableWidth, style = style,
                                maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip, annotatorSettings = settings,
                            )
                        }
                        rowIndex++
                    }
                    // 区切り線（AGENTS.md 2.2: outlineVariant）
                    TABLE_SEPARATOR -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
