package jp.miyavi.android.sumahoclaw.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusCounterTest {

    @Test fun asciiAndJapanese() {
        assertEquals(TextStats(chars = 7, lines = 2), StatusCounter.stats("abc\nあいうえ"))
    }

    @Test fun newlinesAreNotCounted() {
        assertEquals(0, StatusCounter.graphemes("\n\n\n", 0, 3))
    }

    @Test fun emojiAndCombiningCountAsOne() {
        // 家族の絵文字（ZWJ）・肌の色・国旗・結合の濁点・異体字セレクタ
        assertEquals(2, StatusCounter.graphemes("👨‍👩‍👧が", 0, "👨‍👩‍👧が".length))
        assertEquals(1, StatusCounter.graphemes("👍🏽", 0, "👍🏽".length))
        assertEquals(1, StatusCounter.graphemes("🇯🇵", 0, "🇯🇵".length))
        assertEquals(1, StatusCounter.graphemes("が", 0, 2))
        assertEquals(1, StatusCounter.graphemes("❤️", 0, "❤️".length))
    }

    @Test fun lines() {
        assertEquals(1, StatusCounter.lines(""))
        assertEquals(1, StatusCounter.lines("a"))
        assertEquals(1, StatusCounter.lines("a\n"))
        assertEquals(2, StatusCounter.lines("a\nb"))
        assertEquals(3, StatusCounter.lines("a\n\nb\n"))
    }
}
