package jp.miyavi.android.clawdroid.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownPadding

/**
 * Markdown の文字の大きさ（会話とファイルのプレビューで同じ見た目にする）。
 * 見出しはライブラリの既定（H1 = displayLarge など）だとスマホでは大きすぎるので、本文（bodyLarge）から
 * 1〜2 段だけ大きい M3 のトークンに下げ、太字にする（マスター指定 2026-11-23）。
 */
@Composable
fun appMarkdownTypography(): MarkdownTypography {
    val type = MaterialTheme.typography
    // 見出しは太字（AGENTS.md 2.3: 太字は見出しだけ）。LINE Seed JP は Regular と Bold しか無く、
    // M3 の title の Medium（500）は Regular で描かれて本文と見分けられないため、Bold を明示する
    fun TextStyle.heading() = copy(fontWeight = FontWeight.Bold)
    return markdownTypography(
        h1 = type.headlineSmall.heading(),
        h2 = type.titleLarge.heading(),
        h3 = type.titleMedium.heading(),
        h4 = type.titleSmall.heading(),
        h5 = type.titleSmall.heading(),
        h6 = type.labelLarge.heading(),
        text = bodyTextStyle(),
        paragraph = bodyTextStyle(),
        code = codeTextStyle(),
    )
}

/** Markdown の余白（会話とファイルのプレビューで同じ）。 */
@Composable
fun appMarkdownPadding(): MarkdownPadding = markdownPadding(listItemTop = Spacing.xs, listItemBottom = Spacing.xs)
