package jp.miyavi.android.sumahoclaw.feature.editor

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import jp.miyavi.android.sumahoclaw.feature.files.CodeHighlighter
import jp.miyavi.android.sumahoclaw.feature.files.CodeLanguage
import jp.miyavi.android.sumahoclaw.feature.files.TokenKind
import jp.miyavi.android.sumahoclaw.feature.files.TokenSpan
import org.junit.Assert.assertEquals
import org.junit.Test

/** 色分けの区間（文字の位置）→ Sora の行・列の切り替わり（spec 008 research R2）。 */
class HighlightBridgeTest {
    private val normal = EditorColorScheme.TEXT_NORMAL

    @Test fun tokensBecomeLineColumnChanges() {
        val text = "val a = 1\nfun b()"
        val changes = styleChanges(text, listOf(TokenSpan(0, 3, TokenKind.KEYWORD), TokenSpan(10, 13, TokenKind.KEYWORD)))
        assertEquals(
            listOf(
                StyleChange(0, 0, EditorColorScheme.KEYWORD), StyleChange(0, 3, normal),
                StyleChange(1, 0, EditorColorScheme.KEYWORD), StyleChange(1, 3, normal),
            ),
            changes,
        )
    }

    @Test fun multilineCommentEndsOnItsLastLine() {
        val text = "/* a\nb */ x"
        val changes = styleChanges(text, listOf(TokenSpan(0, 9, TokenKind.COMMENT)))
        assertEquals(listOf(StyleChange(0, 0, EditorColorScheme.COMMENT), StyleChange(1, 4, normal)), changes)
    }

    @Test fun adjacentTokensDoNotInsertNormalBetween() {
        val changes = styleChanges("ab", listOf(TokenSpan(0, 1, TokenKind.KEYWORD), TokenSpan(1, 2, TokenKind.STRING)))
        assertEquals(listOf(StyleChange(0, 0, EditorColorScheme.KEYWORD), StyleChange(0, 1, COLOR_STRING), StyleChange(0, 2, normal)), changes)
    }

    @Test fun crlfAndCrSplitLinesLikeSora() {
        val text = "a\r\nb\rc"
        assertEquals(3, lineCountOf(text))
        val changes = styleChanges(text, listOf(TokenSpan(5, 6, TokenKind.LITERAL)))
        assertEquals(StyleChange(2, 0, EditorColorScheme.LITERAL), changes.first())
    }

    @Test fun overlappingTokensKeepTheFirst() {
        val changes = styleChanges("abcdef", listOf(TokenSpan(0, 4, TokenKind.STRING), TokenSpan(2, 6, TokenKind.KEYWORD)))
        assertEquals(listOf(StyleChange(0, 0, COLOR_STRING), StyleChange(0, 4, normal)), changes)
    }

    @Test fun spansCoverEveryLine() {
        val text = "fun a() {}\n\n// c\n"
        val spans = spansOf(text, CodeHighlighter.spans(CodeLanguage.KOTLIN, text))
        assertEquals(lineCountOf(text), spans.lineCount)
    }

    @Test fun languageFromFileName() {
        assertEquals(CodeLanguage.KOTLIN, CodeHighlighter.languageFor("Main.kt"))
        assertEquals(CodeLanguage.JAVASCRIPT, CodeHighlighter.languageFor("app.js"))
        assertEquals(CodeLanguage.MARKDOWN, CodeHighlighter.languageFor("README.md"))
        assertEquals(CodeLanguage.YAML, CodeHighlighter.languageFor("ci.yml"))
        assertEquals(null, CodeHighlighter.languageFor("notes.txt"))
    }
}
