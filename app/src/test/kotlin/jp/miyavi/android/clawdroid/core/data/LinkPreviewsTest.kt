package jp.miyavi.android.clawdroid.core.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkPreviewsTest {
    @Test fun title_prefersOgTitle_andUnescapes() {
        val html = """<html><head><title>Fallback</title>
            <meta property="og:title" content="【AED問題】救助者を &amp; どう守る &#x3042;"></head></html>"""
        assertEquals("【AED問題】救助者を & どう守る あ", pageTitle(html))
        assertEquals("Only title", pageTitle("<title>\n Only  \n title </title>"))
        assertNull(pageTitle("<html></html>"))
    }

    @Test fun icon_resolvesRelativeHref() {
        val base = "https://news.example.com/a/b".toHttpUrl()
        assertEquals("https://news.example.com/static/fav.png", pageIcon("""<link rel="shortcut icon" href="/static/fav.png">""", base))
        assertNull(pageIcon("""<link rel="stylesheet" href="/x.css">""", base))
    }

    @Test fun findsLinks_withoutTrailingPunctuation() {
        val text = "見て https://example.com/x?a=1。あと(https://b.example/)"
        val links = findWebLinks(text).map { text.substring(it) }
        assertEquals(listOf("https://example.com/x?a=1", "https://b.example/"), links)
    }
}
