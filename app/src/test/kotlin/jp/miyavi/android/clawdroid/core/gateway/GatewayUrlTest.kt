package jp.miyavi.android.clawdroid.core.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayUrlTest {
    @Test fun wss_isAlwaysOk() {
        assertEquals(UrlCheck.Ok("wss://server.example.ts.net"), GatewayUrl.check("wss://server.example.ts.net"))
    }

    @Test fun https_isReadAsWss() {
        assertEquals(UrlCheck.Ok("wss://host.example.com/"), GatewayUrl.check(" https://host.example.com/ "))
    }

    @Test fun ws_onlyForPrivateHosts() {
        assertTrue(GatewayUrl.check("ws://192.168.1.10:18789") is UrlCheck.Ok)
        assertTrue(GatewayUrl.check("ws://100.101.102.103:18789") is UrlCheck.Ok) // Tailscale
        assertTrue(GatewayUrl.check("ws://10.0.0.2:18789") is UrlCheck.Ok)
        assertTrue(GatewayUrl.check("ws://server.tail1234.ts.net") is UrlCheck.Ok)
        assertTrue(GatewayUrl.check("ws://homeserver:18789") is UrlCheck.Ok)
        assertEquals(UrlCheck.CleartextPublic, GatewayUrl.check("ws://8.8.8.8:18789"))
        assertEquals(UrlCheck.CleartextPublic, GatewayUrl.check("ws://example.com"))
    }

    @Test fun invalid_inputs() {
        assertEquals(UrlCheck.Invalid, GatewayUrl.check(""))
        assertEquals(UrlCheck.Invalid, GatewayUrl.check("ftp://x"))
        assertEquals(UrlCheck.Invalid, GatewayUrl.check("not a url"))
    }
}
