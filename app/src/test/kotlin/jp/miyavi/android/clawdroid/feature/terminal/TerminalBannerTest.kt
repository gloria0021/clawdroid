package jp.miyavi.android.clawdroid.feature.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalBannerTest {
    @Test
    fun shape_followsColumns() {
        assertEquals(BannerShape.TEXT_ONLY, bannerShape(19))
        assertEquals(BannerShape.MASCOT_ONLY, bannerShape(20))
        assertEquals(BannerShape.MASCOT_ONLY, bannerShape(46))
        assertEquals(BannerShape.STACKED, bannerShape(47))
        assertEquals(BannerShape.STACKED, bannerShape(69))
        assertEquals(BannerShape.SIDE_BY_SIDE, bannerShape(70))
        assertEquals(BannerShape.SIDE_BY_SIDE, bannerShape(200))
    }

    @Test
    fun noLineExceedsColumns() {
        for (columns in listOf(8, 19, 20, 33, 46, 47, 60, 69, 70, 71, 200)) {
            bannerLines(columns).forEach { line ->
                val width = line.text.codePointCount(0, line.text.length)
                assertTrue("columns=$columns width=$width '${line.text}'", width <= columns)
            }
        }
    }

    @Test
    fun ansi_colorsMascotAndEndsWithReset() {
        val ansi = bannerAnsi(80, 0xFF102030.toInt())
        assertTrue(ansi.contains("\u001b[38;2;16;32;48m"))
        assertTrue(ansi.contains("\u001b[0m"))
        assertTrue(ansi.endsWith("\r\n\r\n"))
        // 文字だけの形は色を付けない
        assertFalse(bannerAnsi(10, 0xFF102030.toInt()).contains("\u001b["))
    }
}

class ClearScreenEndTest {
    @Test
    fun findsEndOfClear() {
        assertEquals(-1, clearScreenEnd("hello"))
        assertEquals(7, clearScreenEnd("\u001b[H\u001b[2J$ "))
        assertEquals(11, clearScreenEnd("\u001b[H\u001b[2J\u001b[3J$ "))
    }
}
