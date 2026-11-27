package jp.miyavi.android.sumahoclaw.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoredDraftTest {
    @Test fun preservesExistingDraft() {
        assertEquals("pending", mergeRestoredDraft("pending", ""))
        assertEquals("pending\n\nexisting", mergeRestoredDraft("pending", "existing"))
    }
}
