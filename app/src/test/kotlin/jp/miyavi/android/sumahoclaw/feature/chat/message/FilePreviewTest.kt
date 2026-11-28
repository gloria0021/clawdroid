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

    @Test fun splitsLongSingleLine() {
        val json = "x".repeat(10_000)
        val chunks = plainChunks(json, linesPerChunk = 40, charsPerChunk = 4_000)
        assertEquals(listOf(4_000, 4_000, 2_000), chunks.map { it.length })
        assertEquals(json, chunks.joinToString(""))
    }

    @Test fun chunksStayUnderCharBudget() {
        val text = (1..10).joinToString("\n") { "y".repeat(1_500) }
        val chunks = plainChunks(text, linesPerChunk = 40, charsPerChunk = 4_000)
        assertEquals(text, chunks.joinToString("\n"))
        assert(chunks.all { it.length <= 4_000 })
    }

    @Test fun decodesShiftJisAndRejectsBroken() {
        val sjis = "名前,金額\n山田,100".toByteArray(charset("windows-31j"))
        assertEquals("名前,金額\n山田,100", decodeText(sjis))
        // どの文字コードでも読めないバイト列は、化けた文字にせず読めない扱い
        assertNull(decodeText(byteArrayOf(0x82.toByte(), 0xFF.toByte(), 0x8F.toByte(), 0xFF.toByte(), 0x81.toByte())))
    }

    @Test fun decodesUtf8AndRejectsBinary() {
        assertEquals("あいう", decodeText(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "あいう".toByteArray()))
        assertNull(decodeText(byteArrayOf(0x50, 0x4B, 0x00, 0x03)))
    }
}
