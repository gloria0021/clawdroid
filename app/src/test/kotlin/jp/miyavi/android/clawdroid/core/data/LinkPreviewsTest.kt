package jp.miyavi.android.clawdroid.core.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun internalHosts_areNotFetched() {
        listOf("localhost", "printer.local", "nas.lan", "server.tail1234.ts.net", "127.0.0.1", "192.168.68.1", "10.0.0.2",
            "172.16.5.5", "169.254.1.1", "100.101.102.103", "[::1]".trim('[', ']'), "fd12::1", "0.0.0.0").forEach {
            assertFalse(it, isPublicHost(it))
        }
        listOf("example.com", "news.example.co.jp", "8.8.8.8", "2001:4860:4860::8888").forEach { assertTrue(it, isPublicHost(it)) }
    }
}
