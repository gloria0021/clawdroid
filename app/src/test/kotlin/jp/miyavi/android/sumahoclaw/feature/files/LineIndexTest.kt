package jp.miyavi.android.sumahoclaw.feature.files

import org.junit.Assert.assertEquals
import org.junit.Test

class LineIndexTest {
    @Test
    fun separators() {
        for (text in listOf("a\nbb\nccc", "a\r\nbb\r\nccc", "a\rbb\rccc")) {
            val index = LineIndex.of(text)
            assertEquals(3, index.lineCount)
            assertEquals("bb", text.substring(index.start(1), index.end(1, text)))
            assertEquals("ccc", text.substring(index.start(2), index.end(2, text)))
            assertEquals(1, index.lineOf(index.start(1)))
            assertEquals(2, index.lineOf(text.length))
        }
    }

    @Test
    fun emptyAndTrailingNewline() {
        assertEquals(1, LineIndex.of("").lineCount)
        val text = "a\n"
        val index = LineIndex.of(text)
        assertEquals(2, index.lineCount)
        assertEquals("", text.substring(index.start(1), index.end(1, text)))
        assertEquals(1, LineIndex.of("x".repeat(9)).gutterDigits)
        assertEquals(2, LineIndex.of("\n".repeat(9)).gutterDigits)
    }

    @Test
    fun displayColumns_countsWide() {
        assertEquals(5, displayColumns("aあbc", 0, 4))
    }
}
