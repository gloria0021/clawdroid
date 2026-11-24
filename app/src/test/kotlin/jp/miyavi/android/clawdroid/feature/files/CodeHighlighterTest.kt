package jp.miyavi.android.clawdroid.feature.files

import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlighterTest {
    @Test
    fun kotlin_hasKeywordsStringsComments() {
        val code = "package a\n// note\nfun main() { val s = \"hi\"; return 42 }\n"
        val spans = CodeHighlighter.spans("Main.kt", code)
        println(spans.map { it.kind to code.substring(it.start, it.end) })
        assertTrue(spans.any { it.kind == TokenKind.KEYWORD })
        assertTrue(spans.any { it.kind == TokenKind.STRING })
        assertTrue(spans.any { it.kind == TokenKind.COMMENT })
    }
}

class MarkupHighlighterTest {
    private fun kinds(name: String, text: String) =
        CodeHighlighter.spans(name, text).map { it.kind to text.substring(it.start, it.end) }

    @Test
    fun xml() {
        val k = kinds("a.xml", "<!-- c --><string name=\"app\">Hi &amp; you</string>")
        assertTrue(k.contains(TokenKind.COMMENT to "<!-- c -->"))
        assertTrue(k.contains(TokenKind.KEYWORD to "<string"))
        assertTrue(k.contains(TokenKind.METADATA to "name"))
        assertTrue(k.contains(TokenKind.STRING to "\"app\""))
        assertTrue(k.contains(TokenKind.LITERAL to "&amp;"))
    }

    @Test
    fun yamlAndConfigAndMarkdown() {
        val y = kinds("ci.yml", "# c\nname: build\non: true\nsteps:\n  - run: echo 'x'\n")
        assertTrue(y.contains(TokenKind.COMMENT to "# c"))
        assertTrue(y.contains(TokenKind.KEYWORD to "name"))
        assertTrue(y.contains(TokenKind.STRING to "'x'"))
        val e = kinds(".env", "# key\nAPI_KEY=\"abc\"\nPORT=8080\n")
        assertTrue(e.contains(TokenKind.KEYWORD to "API_KEY"))
        assertTrue(e.contains(TokenKind.LITERAL to "8080"))
        val m = kinds("README.md", "# Title\nuse `code` and [a](b)\n")
        assertTrue(m.contains(TokenKind.KEYWORD to "# Title"))
        assertTrue(m.contains(TokenKind.STRING to "`code`"))
        assertTrue(m.contains(TokenKind.METADATA to "[a](b)"))
    }
}

class IdentifierSpansTest {
    @Test
    fun typesAndCalls_outsideStrings() {
        val code = "val db = ClawdroidDatabase.create(app) // Foo()\nval s = \"Bar()\"\n"
        val k = CodeHighlighter.spans("A.kt", code).map { it.kind to code.substring(it.start, it.end) }
        assertTrue(k.contains(TokenKind.TYPE to "ClawdroidDatabase"))
        assertTrue(k.contains(TokenKind.FUNCTION to "create"))
        assertTrue(k.none { it.second == "Foo" || it.second == "Bar" })
    }
}

class HtmlAssetsTest {
    @Test
    fun refsAndInline() {
        val html = """<link rel="stylesheet" href="site.css"><link href="https://f.com/a.css"><img src='shots/a.png'><a href="#top">x</a><script src="site.js" defer></script>"""
        val refs = htmlAssetRefs(html)
        org.junit.Assert.assertEquals(listOf("site.css", "site.js", "shots/a.png"), refs)
        val out = inlineHtmlAssets(html, mapOf("site.css" to "data:text/css;base64,AA=="))
        assertTrue(out.contains("""href="data:text/css;base64,AA==""""))
        assertTrue(out.contains("""src="site.js""""))
    }
}
