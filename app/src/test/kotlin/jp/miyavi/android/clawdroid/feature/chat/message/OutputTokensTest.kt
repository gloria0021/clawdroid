package jp.miyavi.android.clawdroid.feature.chat.message

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputTokensTest {
    @Test fun updatesEstimateFromTextInJapaneseAndEnglish() {
        assertEquals(0, estimateOutputTokens(""))
        assertEquals(2, estimateOutputTokens("abcd日"))
        assertEquals(3, estimateOutputTokens("abcd日本"))
        assertEquals(1, estimateOutputTokens("𠀀"))
    }

    @Test fun abbreviatesAtFourDigits() {
        assertEquals("999", formatOutputTokens(999))
        assertEquals("1k", formatOutputTokens(1000))
        assertEquals("1.4k", formatOutputTokens(1400))
    }
}
