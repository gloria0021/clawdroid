package jp.miyavi.android.clawdroid.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAttachmentTest {
    @Test fun longPaste_needsLengthAndNewline() {
        assertFalse(isLongPaste("a".repeat(98) + "\n"))
        assertTrue(isLongPaste("a".repeat(99) + "\n"))
        assertFalse(isLongPaste("https://example.com/" + "a".repeat(200)))
        assertTrue(isLongPaste("a".repeat(60) + "\n" + "b".repeat(60)))
    }

    @Test fun quotes_areMarkdownBlockquotesBeforeTheText() {
        assertEquals("> one\n> two\n\nhello", withQuotes("hello", listOf("one\ntwo")))
        assertEquals("> a\n\n> b", withQuotes("", listOf("a", "b")))
        assertEquals("hello", withQuotes("hello", emptyList()))
    }
}
