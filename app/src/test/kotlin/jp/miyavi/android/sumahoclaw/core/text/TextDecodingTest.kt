package jp.miyavi.android.sumahoclaw.core.text

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDecodingTest {

    private fun roundTrip(bytes: ByteArray, edit: (String) -> String = { it }): ByteArray {
        val decoded = TextDecoding.detect(bytes)!!
        val result = TextDecoding.encode(edit(decoded.text), decoded.format)
        return (result as EncodeResult.Ok).bytes
    }

    @Test fun utf8WithoutBom() {
        val d = TextDecoding.detect("こんにちは\nworld\n".toByteArray())!!
        assertEquals(TextCharset.UTF_8, d.format.charset)
        assertEquals(false, d.format.bom)
        assertEquals(LineSeparator.LF, d.format.lineSeparator)
        assertEquals("こんにちは\nworld\n", d.text)
    }

    @Test fun utf8BomIsKeptOnSave() {
        val bytes = TextCharset.UTF_8.bomBytes + "# 見出し\r\n本文\r\n".toByteArray()
        val d = TextDecoding.detect(bytes)!!
        assertTrue(d.format.bom)
        assertEquals(LineSeparator.CRLF, d.format.lineSeparator)
        assertEquals("# 見出し\n本文\n", d.text)
        assertArrayEquals(bytes, roundTrip(bytes))
    }

    @Test fun utf16WithBom() {
        val le = TextCharset.UTF_16LE.bomBytes + "abc あ".toByteArray(Charsets.UTF_16LE)
        assertEquals(TextCharset.UTF_16LE, TextDecoding.detect(le)!!.format.charset)
        assertArrayEquals(le, roundTrip(le))
        val be = TextCharset.UTF_16BE.bomBytes + "abc あ".toByteArray(Charsets.UTF_16BE)
        assertEquals(TextCharset.UTF_16BE, TextDecoding.detect(be)!!.format.charset)
        assertArrayEquals(be, roundTrip(be))
    }

    @Test fun shiftJisCrlfOnlyEditedBytesChange() {
        val cs = TextCharset.SHIFT_JIS.charset!!
        val bytes = "日本語①\r\n髙橋\r\n".toByteArray(cs)
        val d = TextDecoding.detect(bytes)!!
        assertEquals(TextCharset.SHIFT_JIS, d.format.charset)
        assertEquals(LineSeparator.CRLF, d.format.lineSeparator)
        assertEquals("日本語①\n髙橋\n", d.text)
        assertArrayEquals(bytes, roundTrip(bytes))
        assertArrayEquals("日本語②\r\n髙橋\r\n".toByteArray(cs), roundTrip(bytes) { it.replace('①', '②') })
    }

    @Test fun eucJp() {
        val cs = TextCharset.EUC_JP.charset!!
        val bytes = "これは日本語の文章です。カタカナも入る。\n".toByteArray(cs)
        val d = TextDecoding.detect(bytes)!!
        assertEquals(TextCharset.EUC_JP, d.format.charset)
        assertEquals("これは日本語の文章です。カタカナも入る。\n", d.text)
    }

    @Test fun binaryIsNotText() {
        assertNull(TextDecoding.detect(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x12)))
    }

    @Test fun undecodableIsNotText() {
        assertNull(TextDecoding.detect(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x80.toByte(), 0xFF.toByte())))
    }

    @Test fun lineSeparators() {
        assertEquals(LineSeparator.LF, TextDecoding.separatorOf("a\nb").first)
        assertEquals(LineSeparator.CRLF, TextDecoding.separatorOf("a\r\nb\r\n").first)
        assertEquals(LineSeparator.CR, TextDecoding.separatorOf("a\rb").first)
        assertEquals(LineSeparator.LF, TextDecoding.separatorOf("no newline").first)
        val (mixed, dominant) = TextDecoding.separatorOf("a\r\nb\r\nc\n")
        assertEquals(LineSeparator.MIXED, mixed)
        assertEquals(LineSeparator.CRLF, dominant)
    }

    @Test fun mixedSavesWithDominant() {
        val d = TextDecoding.detect("a\r\nb\r\nc\n".toByteArray())!!
        assertEquals(LineSeparator.MIXED, d.format.lineSeparator)
        assertArrayEquals("a\r\nb\r\nc\r\n".toByteArray(), (TextDecoding.encode(d.text, d.format) as EncodeResult.Ok).bytes)
    }

    @Test fun unmappableReportsOffset() {
        val format = DocumentFormat(TextCharset.SHIFT_JIS)
        val result = TextDecoding.encode("あいう\n😀え", format)
        assertEquals(EncodeResult.Unmappable(4), result)
    }

    @Test fun gatewayTextKeepsBomAndSeparator() {
        val content = "\uFEFFline1\r\nline2\r\n"
        val d = TextDecoding.fromGatewayText(content)
        assertTrue(d.format.bom)
        assertEquals("line1\nline2\n", d.text)
        assertEquals(content, TextDecoding.toGatewayText(d.text, d.format))
    }

    @Test fun decodeAsReplacesUnreadable() {
        val d = TextDecoding.decodeAs("日本".toByteArray(TextCharset.SHIFT_JIS.charset!!), TextCharset.UTF_8)
        assertEquals(TextCharset.UTF_8, d.format.charset)
        assertTrue(d.text.contains('�'))
    }
}
