package jp.miyavi.android.clawdroid.feature.chat.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemainingPartsTest {
    @Test
    fun splitsAndRoundsUpMinutes() {
        val minute = 60_000L
        assertNull(remainingParts(0))
        assertEquals(Triple(0L, 0L, 1L), remainingParts(1))
        assertEquals(Triple(1L, 16L, 42L), remainingParts((24 * 60 + 16 * 60 + 42) * minute))
        assertEquals(Triple(0L, 1L, 0L), remainingParts(60 * minute - 1))
    }
}
