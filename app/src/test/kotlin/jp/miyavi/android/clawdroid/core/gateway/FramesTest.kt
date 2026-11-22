package jp.miyavi.android.clawdroid.core.gateway

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FramesTest {
    @Test fun request_hasEnvelopeFields() {
        val text = Frames.request("r1", "sessions.list", buildJsonObject { put("limit", 5) })
        val obj = Frames.json.parseToJsonElement(text).jsonObject
        assertEquals("req", obj.string("type"))
        assertEquals("r1", obj.string("id"))
        assertEquals("sessions.list", obj.string("method"))
        assertEquals(5, obj.obj("params")?.int("limit"))
    }

    @Test fun parse_okResponse() {
        val frame = Frames.parse("""{"type":"res","id":"r1","ok":true,"payload":{"a":1}}""") as Frame.Response
        assertTrue(frame.ok)
        assertEquals(1, frame.payload.asObject()?.int("a"))
    }

    @Test fun parse_errorResponse_withDetailCode() {
        val frame = Frames.parse(
            """{"type":"res","id":"c","ok":false,"error":{"code":"NOT_PAIRED","message":"pairing required","details":{"code":"PAIRING_REQUIRED","requestId":"q1","waitForResolution":true}}}""",
        ) as Frame.Response
        assertFalse(frame.ok)
        val error = frame.error!!
        assertEquals("PAIRING_REQUIRED", error.detailCode)
        assertTrue(GatewayErrorClassifier.isPairingRequired(error))
        assertTrue(GatewayErrorClassifier.waitsForResolution(error))
    }

    @Test fun parse_event() {
        val frame = Frames.parse("""{"type":"event","event":"tick","payload":{"ts":1},"seq":3}""") as Frame.Event
        assertEquals("tick", frame.event)
        assertEquals(3L, frame.seq)
    }

    @Test fun parse_garbage_isNull() {
        assertNull(Frames.parse("not json"))
        assertNull(Frames.parse("""{"type":"req","id":"x","method":"m"}"""))
    }

    @Test fun classify_authAndPairing() {
        fun err(code: String, detail: String? = null) = GatewayError(code, "m", detail?.let { d -> buildJsonObject { put("code", d) } }, null, null)
        assertEquals(FailureReason.AUTH_REJECTED, GatewayErrorClassifier.classify(err("INVALID_REQUEST", "AUTH_TOKEN_MISMATCH")))
        assertEquals(FailureReason.PAIRING_REJECTED, GatewayErrorClassifier.classify(err("NOT_PAIRED", "PAIRING_REJECTED")))
        assertEquals(FailureReason.PROTOCOL_MISMATCH, GatewayErrorClassifier.classify(err("INVALID_REQUEST", "PROTOCOL_MISMATCH")))
    }
}
