package jp.miyavi.android.clawdroid.feature.files

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxTheme

/** 字句の種類（色はテーマから当てる。research R7）。 */
enum class TokenKind { KEYWORD, STRING, LITERAL, COMMENT, METADATA, PUNCTUATION }

/** 色分けの 1 区間。 */
data class TokenSpan(val start: Int, val end: Int, val kind: TokenKind)

/**
 * highlights で字句を分ける。色は決めず、種類だけを返す（色はテーマに合わせて画面で当てる）。
 * 重いので ViewModel で一度だけ呼ぶ（AGENTS.md 1）。言語が分からなければ空。
 */
object CodeHighlighter {
    // 色の代わりに種類の番号を入れたテーマ（ColorHighlight.rgb から種類に戻す）
    private val markerTheme = SyntaxTheme(
        key = "clawdroid-markers",
        code = 0,
        keyword = TokenKind.KEYWORD.marker,
        string = TokenKind.STRING.marker,
        literal = TokenKind.LITERAL.marker,
        comment = TokenKind.COMMENT.marker,
        metadata = TokenKind.METADATA.marker,
        multilineComment = TokenKind.COMMENT.marker,
        punctuation = TokenKind.PUNCTUATION.marker,
        mark = TokenKind.PUNCTUATION.marker,
    )

    fun spans(fileName: String, text: String): List<TokenSpan> {
        val language = languageOf(fileName) ?: return emptyList()
        return runCatching {
            Highlights.Builder().code(text).language(language).theme(markerTheme).build().getHighlights()
                .mapNotNull { highlight ->
                    val color = highlight as? ColorHighlight ?: return@mapNotNull null
                    val kind = TokenKind.entries.firstOrNull { it.marker == color.rgb } ?: return@mapNotNull null
                    val start = color.location.start.coerceIn(0, text.length)
                    val end = color.location.end.coerceIn(start, text.length)
                    if (start == end) null else TokenSpan(start, end, kind)
                }
        }.getOrDefault(emptyList())
    }

    /** 拡張子から言語を選ぶ。 */
    internal fun languageOf(fileName: String): SyntaxLanguage? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> SyntaxLanguage.KOTLIN
        "java" -> SyntaxLanguage.JAVA
        "js", "mjs", "cjs", "jsx", "json" -> SyntaxLanguage.JAVASCRIPT
        "ts", "tsx", "mts" -> SyntaxLanguage.TYPESCRIPT
        "py" -> SyntaxLanguage.PYTHON
        "rb" -> SyntaxLanguage.RUBY
        "rs" -> SyntaxLanguage.RUST
        "go" -> SyntaxLanguage.GO
        "swift" -> SyntaxLanguage.SWIFT
        "c", "h" -> SyntaxLanguage.C
        "cc", "cpp", "hpp", "cxx" -> SyntaxLanguage.CPP
        "cs" -> SyntaxLanguage.CSHARP
        "dart" -> SyntaxLanguage.DART
        "php" -> SyntaxLanguage.PHP
        "pl", "pm" -> SyntaxLanguage.PERL
        "sh", "bash", "zsh" -> SyntaxLanguage.SHELL
        "coffee" -> SyntaxLanguage.COFFEESCRIPT
        else -> null
    }

    private val TokenKind.marker: Int get() = ordinal + 1
}

/** 種類ごとの色（colorScheme から。直書きしない）。 */
fun ColorScheme.tokenColor(kind: TokenKind): Color = when (kind) {
    TokenKind.KEYWORD -> primary
    TokenKind.STRING -> tertiary
    TokenKind.LITERAL -> secondary
    TokenKind.COMMENT -> onSurfaceVariant
    TokenKind.METADATA -> tertiary
    TokenKind.PUNCTUATION -> onSurfaceVariant
}

/** 区間に色を当てた文字。画面で `remember(text, spans, colorScheme)` して使う。 */
fun highlighted(text: String, spans: List<TokenSpan>, colors: ColorScheme): AnnotatedString = buildAnnotatedString {
    append(text)
    spans.forEach { addStyle(SpanStyle(color = colors.tokenColor(it.kind)), it.start, it.end) }
}
