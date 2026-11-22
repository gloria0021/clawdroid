package jp.miyavi.android.clawdroid.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitRecommendedTest {
    @Test fun recommendedMarkBecomesBadge() {
        assertEquals("push する" to true, splitRecommended("push する (Recommended)"))
        assertEquals("B" to true, splitRecommended("B（推奨）"))
        assertEquals("まだしない" to false, splitRecommended("まだしない"))
    }
}
