package jp.miyavi.android.sumahoclaw.core.data

import jp.miyavi.android.sumahoclaw.core.gateway.Frames
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseInboundMediaTest {
    @Test fun userMessage_imagesFromOpenclawMedia() {
        val m = Parse.message(
            Frames.json.parseToJsonElement(
                """{"role":"user","content":"見て","__openclaw":{"id":"e1","media":[
                  {"url":"media://inbound/a---1.png","contentType":"image/png","kind":"image"},
                  {"url":"media://inbound/b.pdf","contentType":"application/pdf","kind":"document"}]}}""",
            ).jsonObject,
            0,
        )!!
        val images = m.parts.filterIsInstance<MessagePart.Image>()
        assertEquals(listOf("media://inbound/a---1.png"), images.map { it.url })
        // 画像でない添付はファイルとして出す（名前は保存名の ---<uuid> を除いたもの）
        val file = m.parts.filterIsInstance<MessagePart.File>().single()
        assertEquals("b.pdf", file.name)
        assertEquals("見て", m.text)
        assertTrue(isInboundMediaUrl(images.single().url))
    }

    @Test fun assistantMediaLines_becomeImages_andOtherFilesStayInText() {
        val m = Parse.message(
            Frames.json.parseToJsonElement(
                """{"role":"assistant","content":[{"type":"text","text":"できた\n\nMEDIA:/home/a/out/icon.png\nMEDIA:/home/a/out/app.apk\n```\nMEDIA:/x/in-code.png\n```"}]}""",
            ).jsonObject,
            0,
        )!!
        val images = m.parts.filterIsInstance<MessagePart.Image>()
        assertEquals(listOf("/home/a/out/icon.png"), images.map { it.url })
        assertEquals("image/png", images.single().mimeType)
        assertTrue(isInboundMediaUrl(images.single().url))
        // 画像でないファイルは添付ファイルに、コードの中の行は本文に残る
        val file = m.parts.filterIsInstance<MessagePart.File>().single()
        assertEquals("/home/a/out/app.apk", file.url)
        assertEquals("app.apk", file.name)
        assertTrue("app.apk" !in m.text)
        assertTrue("MEDIA:/x/in-code.png" in m.text)
        assertTrue("icon.png" !in m.text)
    }

    @Test fun gatewayMediaSources() {
        assertTrue(isInboundMediaUrl("media://inbound/a.png"))
        assertTrue(isInboundMediaUrl("/home/mi/a.png"))
        assertTrue(isInboundMediaUrl("~/a.png"))
        assertTrue(isInboundMediaUrl("file:///home/mi/a.png"))
        assertTrue(!isInboundMediaUrl("https://example.com/a.png"))
        assertTrue(!isInboundMediaUrl("//example.com/a.png"))
    }

    @Test fun gatewayAttachmentParts() {
        val m = Parse.message(
            Frames.json.parseToJsonElement(
                """{"role":"assistant","content":[{"type":"text","text":"まとめたよ"},
                  {"type":"attachment","attachment":{"url":"/api/chat/media/outgoing/a/1/full","kind":"document","label":"jcb.xlsx","mimeType":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","sizeBytes":3756}},
                  {"type":"attachment_error","attachment":{"code":"unsupported-format","kind":"document","label":"app.apk"}}]}""",
            ).jsonObject,
            0,
        )!!
        val file = m.parts.filterIsInstance<MessagePart.File>().single()
        assertEquals("jcb.xlsx", file.name)
        assertEquals(3756L, file.size)
        assertTrue(isInboundMediaUrl(file.url))
        assertEquals(MessagePart.AttachmentError("app.apk", "unsupported-format"), m.parts.filterIsInstance<MessagePart.AttachmentError>().single())
    }
}
