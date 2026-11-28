package jp.miyavi.android.sumahoclaw.feature.files

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import dev.snipme.highlights.Highlights
import jp.miyavi.android.sumahoclaw.core.designsystem.CodeColors
import jp.miyavi.android.sumahoclaw.core.designsystem.codeColors
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxTheme

/** 字句の種類（色はテーマから当てる。research R7）。 */
enum class TokenKind { KEYWORD, STRING, LITERAL, COMMENT, METADATA, PUNCTUATION, TYPE, FUNCTION }

/** 色分けの 1 区間。 */
data class TokenSpan(val start: Int, val end: Int, val kind: TokenKind)

/**
 * highlights で字句を分ける。色は決めず、種類だけを返す（色はテーマに合わせて画面で当てる）。
 * 重いので ViewModel で一度だけ呼ぶ（AGENTS.md 1）。言語が分からなければ空。
 */
object CodeHighlighter {
    // 色の代わりに種類の番号を入れたテーマ（ColorHighlight.rgb から種類に戻す）
    private val markerTheme = SyntaxTheme(
        key = "sumahoclaw-markers",
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

    fun spans(fileName: String, text: String): List<TokenSpan> = languageFor(fileName)?.let { spans(it, text) }.orEmpty()

    /** 言語を決めて分ける（エディターで選び直したとき。spec 008 FR-015）。 */
    fun spans(language: CodeLanguage, text: String): List<TokenSpan> {
        // highlights が持たない言語（XML・YAML・TOML・CSS・Markdown など）は、手元の簡単な規則で分ける
        language.markup?.let { return MarkupHighlighter.spans(it, text) }
        val syntax = language.syntax ?: return emptyList()
        val base = runCatching {
            Highlights.Builder().code(text).language(syntax).theme(markerTheme).build().getHighlights()
                .mapNotNull { highlight ->
                    val color = highlight as? ColorHighlight ?: return@mapNotNull null
                    val kind = TokenKind.entries.firstOrNull { it.marker == color.rgb } ?: return@mapNotNull null
                    val start = color.location.start.coerceIn(0, text.length)
                    val end = color.location.end.coerceIn(start, text.length)
                    if (start == end) null else TokenSpan(start, end, kind)
                }
        }.getOrDefault(emptyList())
        // highlights は型と関数を分けないので、文字列・コメントの外の「大文字で始まる名前」を型、「名前(」を関数として足す
        return base + identifierSpans(text, base)
    }

    /** 拡張子から言語を決める（無ければ色分けしない）。 */
    fun languageFor(fileName: String): CodeLanguage? =
        markupOf(fileName)?.let { m -> CodeLanguage.entries.first { it.markup == m } }
            ?: languageOf(fileName)?.let { l -> CodeLanguage.entries.first { it.syntax == l } }

    private val TYPE_NAME = Regex("\\b[A-Z][A-Za-z0-9_]*\\b")
    private val FUNCTION_CALL = Regex("\\b[a-z_][A-Za-z0-9_]*(?=\\s*\\()")

    internal fun identifierSpans(text: String, taken: List<TokenSpan>): List<TokenSpan> {
        val used = java.util.BitSet(text.length)
        taken.forEach { used.set(it.start, it.end) }
        val extra = mutableListOf<TokenSpan>()
        for ((regex, kind) in listOf(TYPE_NAME to TokenKind.TYPE, FUNCTION_CALL to TokenKind.FUNCTION)) {
            for (match in regex.findAll(text)) {
                val start = match.range.first
                val end = match.range.last + 1
                if (used.nextSetBit(start).let { it != -1 && it < end }) continue
                used.set(start, end)
                extra += TokenSpan(start, end, kind)
            }
        }
        return extra
    }

    /** 拡張子から言語を選ぶ。 */
    internal fun languageOf(fileName: String): SyntaxLanguage? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> SyntaxLanguage.KOTLIN
        "java", "gradle", "groovy", "scala" -> SyntaxLanguage.JAVA
        "js", "mjs", "cjs", "jsx", "json", "jsonc", "json5" -> SyntaxLanguage.JAVASCRIPT
        "ts", "tsx", "mts", "cts" -> SyntaxLanguage.TYPESCRIPT
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
        "sh", "bash", "zsh", "fish" -> SyntaxLanguage.SHELL
        "coffee" -> SyntaxLanguage.COFFEESCRIPT
        else -> when (fileName.substringAfterLast('/').lowercase()) {
            "dockerfile", "makefile", ".bashrc", ".zshrc", ".profile" -> SyntaxLanguage.SHELL
            else -> null
        }
    }

    /** highlights が持たない、手元で分ける種類。 */
    internal fun markupOf(fileName: String): Markup? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "xml", "html", "htm", "svg", "xhtml", "vue", "plist" -> Markup.XML
        "yaml", "yml" -> Markup.YAML
        "toml", "ini", "properties", "conf", "cfg", "env" -> Markup.CONFIG
        "css", "scss", "sass", "less" -> Markup.CSS
        "md", "markdown" -> Markup.MARKDOWN
        else -> if (fileName.substringAfterLast('/').lowercase().let { it == ".env" || it.startsWith(".env.") || it == ".gitignore" }) Markup.CONFIG else null
    }

    private val TokenKind.marker: Int get() = ordinal + 1
}

/** 種類ごとの色（テーマの定義ファイルの codeColors から。直書きしない）。 */
fun CodeColors.of(kind: TokenKind): Color = when (kind) {
    TokenKind.KEYWORD -> keyword
    TokenKind.STRING -> string
    TokenKind.LITERAL -> literal
    TokenKind.COMMENT -> comment
    TokenKind.METADATA -> metadata
    TokenKind.PUNCTUATION -> punctuation
    TokenKind.TYPE -> type
    TokenKind.FUNCTION -> function
}

/** 区間に色を当てた文字。画面で `remember(text, spans, colorScheme)` して使う。 */
fun highlighted(text: String, spans: List<TokenSpan>, colors: ColorScheme): AnnotatedString = buildAnnotatedString {
    append(text)
    val palette = codeColors(colors)
    spans.forEach { addStyle(SpanStyle(color = palette.of(it.kind)), it.start, it.end) }
}

enum class Markup { XML, YAML, CONFIG, CSS, MARKDOWN }

/** 色分けの言語（エディターで選び直せるもの）。名前は `strings.xml`（訳さない）。 */
enum class CodeLanguage(@androidx.annotation.StringRes val label: Int, val syntax: SyntaxLanguage? = null, val markup: Markup? = null) {
    KOTLIN(jp.miyavi.android.sumahoclaw.R.string.lang_kotlin, SyntaxLanguage.KOTLIN),
    JAVA(jp.miyavi.android.sumahoclaw.R.string.lang_java, SyntaxLanguage.JAVA),
    JAVASCRIPT(jp.miyavi.android.sumahoclaw.R.string.lang_javascript, SyntaxLanguage.JAVASCRIPT),
    TYPESCRIPT(jp.miyavi.android.sumahoclaw.R.string.lang_typescript, SyntaxLanguage.TYPESCRIPT),
    PYTHON(jp.miyavi.android.sumahoclaw.R.string.lang_python, SyntaxLanguage.PYTHON),
    RUBY(jp.miyavi.android.sumahoclaw.R.string.lang_ruby, SyntaxLanguage.RUBY),
    RUST(jp.miyavi.android.sumahoclaw.R.string.lang_rust, SyntaxLanguage.RUST),
    GO(jp.miyavi.android.sumahoclaw.R.string.lang_go, SyntaxLanguage.GO),
    SWIFT(jp.miyavi.android.sumahoclaw.R.string.lang_swift, SyntaxLanguage.SWIFT),
    C(jp.miyavi.android.sumahoclaw.R.string.lang_c, SyntaxLanguage.C),
    CPP(jp.miyavi.android.sumahoclaw.R.string.lang_cpp, SyntaxLanguage.CPP),
    CSHARP(jp.miyavi.android.sumahoclaw.R.string.lang_csharp, SyntaxLanguage.CSHARP),
    DART(jp.miyavi.android.sumahoclaw.R.string.lang_dart, SyntaxLanguage.DART),
    PHP(jp.miyavi.android.sumahoclaw.R.string.lang_php, SyntaxLanguage.PHP),
    PERL(jp.miyavi.android.sumahoclaw.R.string.lang_perl, SyntaxLanguage.PERL),
    SHELL(jp.miyavi.android.sumahoclaw.R.string.lang_shell, SyntaxLanguage.SHELL),
    COFFEESCRIPT(jp.miyavi.android.sumahoclaw.R.string.lang_coffeescript, SyntaxLanguage.COFFEESCRIPT),
    XML(jp.miyavi.android.sumahoclaw.R.string.lang_xml, markup = Markup.XML),
    YAML(jp.miyavi.android.sumahoclaw.R.string.lang_yaml, markup = Markup.YAML),
    CONFIG(jp.miyavi.android.sumahoclaw.R.string.lang_config, markup = Markup.CONFIG),
    CSS(jp.miyavi.android.sumahoclaw.R.string.lang_css, markup = Markup.CSS),
    MARKDOWN(jp.miyavi.android.sumahoclaw.R.string.lang_markdown, markup = Markup.MARKDOWN),
}

/** XML・YAML・設定ファイル・CSS・Markdown の簡単な色分け（正規表現で、重なる区間は先に見つけた方を残す）。 */
internal object MarkupHighlighter {
    private val rules: Map<Markup, List<Pair<Regex, TokenKind>>> = mapOf(
        Markup.XML to listOf(
            Regex("<!--[\\s\\S]*?-->") to TokenKind.COMMENT,
            Regex("<!\\[CDATA\\[[\\s\\S]*?]]>") to TokenKind.STRING,
            Regex("\"[^\"]*\"|'[^']*'") to TokenKind.STRING,
            Regex("</?[A-Za-z][\\w:.-]*") to TokenKind.KEYWORD,
            Regex("[A-Za-z_:][\\w:.-]*(?=\\s*=)") to TokenKind.METADATA,
            Regex("/?>|<\\?|\\?>") to TokenKind.PUNCTUATION,
            Regex("&\\w+;|&#\\w+;") to TokenKind.LITERAL,
        ),
        Markup.YAML to listOf(
            Regex("(?m)#.*$") to TokenKind.COMMENT,
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'") to TokenKind.STRING,
            Regex("(?m)^\\s*(?:- )?[\\w.\\-/\"' ]+?(?=\\s*:(?:\\s|$))") to TokenKind.KEYWORD,
            Regex("(?m)^(?:---|\\.\\.\\.)\\s*$|[&*][\\w-]+|!!?[\\w/]+") to TokenKind.METADATA,
            Regex("\\b(?:true|false|null|yes|no|on|off|~)\\b|(?<![\\w.])-?\\d+(?:\\.\\d+)?\\b") to TokenKind.LITERAL,
        ),
        Markup.CONFIG to listOf(
            Regex("(?m)^\\s*[#;!].*$") to TokenKind.COMMENT,
            Regex("(?m)^\\s*\\[[^\\]]+]") to TokenKind.METADATA,
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'") to TokenKind.STRING,
            Regex("(?m)^\\s*(?:export\\s+)?[\\w.\\-]+(?=\\s*[=:])") to TokenKind.KEYWORD,
            Regex("\\b(?:true|false|null)\\b|(?<![\\w.])-?\\d+(?:\\.\\d+)?\\b") to TokenKind.LITERAL,
        ),
        Markup.CSS to listOf(
            Regex("/\\*[\\s\\S]*?\\*/|(?m)//.*$") to TokenKind.COMMENT,
            Regex("\"[^\"]*\"|'[^']*'") to TokenKind.STRING,
            Regex("@[\\w-]+|\\$[\\w-]+") to TokenKind.METADATA,
            Regex("[\\w-]+(?=\\s*:[^:{]*[;}])") to TokenKind.KEYWORD,
            Regex("#[0-9a-fA-F]{3,8}\\b|(?<![\\w-])-?\\d*\\.?\\d+(?:px|em|rem|%|vh|vw|s|ms|deg|fr)?\\b") to TokenKind.LITERAL,
            Regex("[{}();:,]") to TokenKind.PUNCTUATION,
        ),
        Markup.MARKDOWN to listOf(
            Regex("(?m)^```[\\s\\S]*?^```") to TokenKind.STRING,
            Regex("`[^`\\n]+`") to TokenKind.STRING,
            Regex("<!--[\\s\\S]*?-->") to TokenKind.COMMENT,
            Regex("(?m)^#{1,6} .*$") to TokenKind.KEYWORD,
            Regex("\\[[^\\]\\n]*]\\([^)\\n]*\\)") to TokenKind.METADATA,
            Regex("(?m)^\\s*(?:[-*+]|\\d+\\.) |(?m)^>+") to TokenKind.PUNCTUATION,
            Regex("\\*\\*[^*\\n]+\\*\\*") to TokenKind.LITERAL,
        ),
    )

    fun spans(markup: Markup, text: String): List<TokenSpan> {
        val taken = java.util.BitSet(text.length)
        val result = mutableListOf<TokenSpan>()
        for ((regex, kind) in rules.getValue(markup)) {
            for (match in regex.findAll(text)) {
                val start = match.range.first
                val end = match.range.last + 1
                if (start >= end) continue
                val free = taken.nextSetBit(start).let { it == -1 || it >= end }
                if (!free) continue
                taken.set(start, end)
                result += TokenSpan(start, end, kind)
            }
        }
        return result.sortedBy { it.start }
    }
}
