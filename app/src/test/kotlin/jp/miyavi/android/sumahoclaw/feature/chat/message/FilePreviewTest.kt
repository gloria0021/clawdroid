package jp.miyavi.android.sumahoclaw.feature.chat.message

import jp.miyavi.android.sumahoclaw.core.data.MessagePart
import jp.miyavi.android.sumahoclaw.core.data.decodeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilePreviewTest {
    private fun file(name: String, mime: String? = null, size: Long? = null) = MessagePart.File("media://inbound/$name", name, mime, size)

    @Test fun picksPreviewByExtensionAndMime() {
        assertEquals(TextPreview.MARKDOWN, textPreviewOf(file("README.md")))
        assertEquals(TextPreview.MARKDOWN, textPreviewOf(file("notes", "text/markdown; charset=utf-8")))
        assertEquals(TextPreview.PLAIN, textPreviewOf(file("pasted-text-1.txt")))
        assertEquals(TextPreview.PLAIN, textPreviewOf(file("server.LOG")))
        assertEquals(TextPreview.PLAIN, textPreviewOf(file("blob", "text/plain")))
        assertNull(textPreviewOf(file("app.apk")))
        assertNull(textPreviewOf(file("doc.pdf", "application/pdf")))
    }

    @Test fun tooLargeFallsBackToDownload() {
        assertNull(textPreviewOf(file("big.txt", size = TEXT_PREVIEW_MAX_BYTES + 1)))
        assertEquals(TextPreview.PLAIN, textPreviewOf(file("ok.txt", size = TEXT_PREVIEW_MAX_BYTES)))
    }

    @Test fun chunksLines() {
        val text = (1..85).joinToString("\n") { "l$it" } + "\n"
        val chunks = plainChunks(text, linesPerChunk = 40)
        assertEquals(3, chunks.size)
        assertEquals("l1", chunks[0].lineSequence().first())
        assertEquals("l85", chunks[2].lineSequence().last())
    }

    @Test fun decodesUtf8AndRejectsBinary() {
        assertEquals("あいう", decodeText(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "あいう".toByteArray()))
        assertNull(decodeText(byteArrayOf(0x50, 0x4B, 0x00, 0x03)))
    }
}
