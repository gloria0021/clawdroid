package jp.miyavi.android.sumahoclaw.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRowKeysTest {
    @Test fun repeatedIdsKeepBothRowsAndAppendDoesNotChangeKeys() {
        val original = chatRowKeys(listOf("same", "thinking", "same", "1:same"))
        assertEquals(4, original.toSet().size)
        assertEquals(original, chatRowKeys(listOf("same", "thinking", "same", "1:same", "same")).take(4))
    }
}
