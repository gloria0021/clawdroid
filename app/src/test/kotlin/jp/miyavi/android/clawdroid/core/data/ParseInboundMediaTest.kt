package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.Frames
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
        assertEquals("見て", m.text)
        assertTrue(isInboundMediaUrl(images.single().url))
    }
}
