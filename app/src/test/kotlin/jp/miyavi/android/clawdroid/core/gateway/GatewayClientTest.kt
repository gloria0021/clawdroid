package jp.miyavi.android.clawdroid.core.gateway

import java.util.concurrent.TimeUnit
import jp.miyavi.android.clawdroid.core.security.GatewayConnection
import jp.miyavi.android.clawdroid.testing.FakeGateway
import jp.miyavi.android.clawdroid.testing.FakeGateway.ConnectBehavior
import jp.miyavi.android.clawdroid.testing.InMemorySecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GatewayClientTest {
    private val gateway = FakeGateway()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var secrets: InMemorySecretStore
    private lateinit var client: GatewayClient

    @Before fun setUp() {
        gateway.handlers["echo"] = { params -> params }
        gateway.start()
        secrets = InMemorySecretStore()
        client = GatewayClient(
            scope = scope,
            httpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
            identities = DeviceIdentityRepository(secrets),
            secrets = secrets,
            clientInfo = ClientInfo(version = "0.1.0-test", modelIdentifier = "Test", displayName = "Test"),
            handshakeTimeoutMs = 5_000,
            foregroundProbeTimeoutMs = 1_000,
            probeSkipRecentMs = 0,
        )
    }

    @After fun tearDown() {
        scope.cancel()
        gateway.shutdown()
    }

    private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

    @Test fun connect_succeeds_savesDeviceToken_andRequestsWork() {
        gateway.enqueue(ConnectBehavior.Accept(deviceToken = "dt-1"))
        await { client.connect(gateway.url, "shared-token") }
        val connected = await { client.awaitConnected() }
        assertEquals("2026.11.6", connected.serverVersion)
        assertEquals("dt-1", secrets.connection?.deviceToken)

        // connect の中身: role・scopes・token・端末の署名
        val params = gateway.connectParams.single()
        assertEquals("operator", params.string("role"))
        assertEquals("shared-token", gateway.lastConnectAuthToken())
        val device = params.obj("device")!!
        assertEquals("nonce-1", device.string("nonce"))
        assertEquals(1_700_000_000_000L, device.long("signedAt"))
        assertNotNull(device.string("signature"))
        assertEquals("openclaw-android", params.obj("client")?.string("id"))

        val echoed = await { client.request("echo", buildJsonObject { put("x", 1) }) }
        assertEquals(1, echoed.asObject()?.int("x"))
    }

    @Test fun pause_closesTheSocket_andResumeReconnects() {
        gateway.enqueue(ConnectBehavior.Accept())
        gateway.enqueue(ConnectBehavior.Accept())
        await { client.connect(gateway.url, "t") }
        await { client.awaitConnected() }

        client.pause()
        assertEquals(ConnectionState.Paused, client.state.value)
        // 休んでいるあいだは自分からつなぎ直さない
        runBlocking { kotlinx.coroutines.delay(1_500) }
        assertEquals(ConnectionState.Paused, client.state.value)
        assertEquals(1, gateway.connectParams.size)

        assertTrue(client.resumeFromPause())
        await { client.awaitConnected() }
        assertEquals(2, gateway.connectParams.size)
        // つながっていれば何もしない
        assertEquals(false, client.resumeFromPause())
    }

    @Test fun startPaused_doesNotConnect_untilResumed() {
        gateway.enqueue(ConnectBehavior.Accept())
        secrets.connection = GatewayConnection(url = gateway.url, gatewayToken = "t")
        client.startPaused()
        await { client.state.first { it == ConnectionState.Paused } }
        runBlocking { kotlinx.coroutines.delay(500) }
        assertEquals(0, gateway.connectParams.size)
        assertTrue(client.resumeFromPause())
        await { client.awaitConnected() }
    }

    @Test fun pause_isIgnoredAfterUserDisconnect() {
        gateway.enqueue(ConnectBehavior.Accept())
        await { client.connect(gateway.url, "t") }
        await { client.awaitConnected() }
        client.disconnect()
        client.pause()
        assertEquals(ConnectionState.Disconnected, client.state.value)
    }

    @Test fun pairingRequired_thenApproved_connects() {
        gateway.enqueue(ConnectBehavior.PairingRequired(resolveWith = "approved"))
        gateway.enqueue(ConnectBehavior.Accept())
        await { client.connect(gateway.url, "t") }
        await { client.awaitConnected() }
        assertEquals(2, gateway.connectParams.size)
    }

    @Test fun pairingRequired_showsAwaitingApproval() {
        gateway.enqueue(ConnectBehavior.PairingRequired(requestId = "req-9"))
        await { client.connect(gateway.url, "t") }
        val state = await { client.state.first { it is ConnectionState.AwaitingApproval } } as ConnectionState.AwaitingApproval
        assertEquals("req-9", state.requestId)
    }

    @Test fun pairingRequired_reconnectsWhenResolutionIsMissing() {
        gateway.enqueue(ConnectBehavior.PairingRequired())
        gateway.enqueue(ConnectBehavior.Accept())
        await { client.connect(gateway.url, "t") }
        await { client.state.first { it is ConnectionState.AwaitingApproval } }
        await { client.awaitConnected() }
        assertEquals(2, gateway.connectParams.size)
    }

    @Test fun pairingRequired_reconnectsAfterGatewayClosesTheSocket() {
        gateway.enqueue(ConnectBehavior.PairingRequired(waitForResolution = false))
        gateway.enqueue(ConnectBehavior.Accept())
        await { client.connect(gateway.url, "t") }
        await { client.state.first { it is ConnectionState.AwaitingApproval } }
        await { client.awaitConnected() }
        assertEquals(2, gateway.connectParams.size)
    }

    @Test fun pairingRejected_failsWithoutRetry() {
        gateway.enqueue(ConnectBehavior.PairingRequired(resolveWith = "rejected"))
        await { client.connect(gateway.url, "t") }
        val state = await { client.state.first { it is ConnectionState.Failed } } as ConnectionState.Failed
        assertEquals(FailureReason.PAIRING_REJECTED, state.reason)
    }

    @Test fun authRejected_failsAndStopsReconnecting() {
        gateway.enqueue(ConnectBehavior.Reject("INVALID_REQUEST", "AUTH_TOKEN_MISMATCH"))
        await { client.connect(gateway.url, "wrong") }
        val state = await { client.state.first { it is ConnectionState.Failed } } as ConnectionState.Failed
        assertEquals(FailureReason.AUTH_REJECTED, state.reason)
        Thread.sleep(1_500)
        assertEquals(1, gateway.connectParams.size)
    }

    @Test fun unreachable_reconnectsWithBackoff() {
        val deadUrl = gateway.url
        gateway.shutdown()
        await { client.connect(deadUrl, "t") }
        val state = await { client.state.first { it is ConnectionState.Reconnecting } } as ConnectionState.Reconnecting
        assertEquals(FailureReason.UNREACHABLE, state.reason)
    }

    @Test fun tickSilence_closesAndReconnects() {
        gateway.enqueue(ConnectBehavior.Accept(tickIntervalMs = 200))
        gateway.enqueue(ConnectBehavior.Accept(tickIntervalMs = 30_000))
        await { client.connect(gateway.url, "t") }
        await { client.awaitConnected() }
        // tick が届かないので 400ms で閉じ、2 本目の接続に入る
        await { client.state.first { it is ConnectionState.Reconnecting } }
        await { client.awaitConnected() }
        assertTrue(gateway.connectParams.size >= 2)
    }

    @Test fun foreground_reconnectsWhenAnOpenSocketNoLongerResponds() {
        gateway.ignoredMethods += "health"
        gateway.enqueue(ConnectBehavior.Accept(deviceToken = "before-lock"))
        gateway.enqueue(ConnectBehavior.Accept(deviceToken = "after-resume"))
        await { client.connect(gateway.url, "t") }
        await { client.awaitConnected() }

        client.onForeground()
        await { client.state.first { it is ConnectionState.Connected && secrets.connection?.deviceToken == "after-resume" } }

        assertEquals("after-resume", secrets.connection?.deviceToken)
        assertEquals(2, gateway.connectParams.size)
    }

    @Test fun foreground_recentFrame_skipsTheProbe() {
        // 直前に Gateway から届いていれば生きているので確かめず、つなぎ直さない（遅い回線で生きている接続を捨てない）
        val recent = GatewayClient(
            scope = scope,
            httpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
            identities = DeviceIdentityRepository(secrets),
            secrets = secrets,
            clientInfo = ClientInfo(version = "0.1.0-test", modelIdentifier = "Test", displayName = "Test"),
            handshakeTimeoutMs = 5_000,
            foregroundProbeTimeoutMs = 500,
            probeSkipRecentMs = 60_000,
        )
        gateway.ignoredMethods += "health"
        gateway.enqueue(ConnectBehavior.Accept(deviceToken = "only"))
        await { recent.connect(gateway.url, "t") }
        await { recent.awaitConnected() }

        recent.onForeground()
        Thread.sleep(1_200)

        assertTrue(recent.state.value is ConnectionState.Connected)
        assertEquals(1, gateway.connectParams.size)
    }

    @Test fun reconnectNow_abandonsAStalledAttempt() {
        gateway.enqueueStalled(delayMs = 4_000)
        gateway.enqueue(ConnectBehavior.Accept(deviceToken = "fresh"))
        await { client.connect(gateway.url, "t") }
        Thread.sleep(300)

        val started = System.nanoTime()
        client.reconnectNow()
        await { client.awaitConnected() }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        // 固まった試行（4 秒）の終わりを待たず、新しいソケットでつながる
        assertTrue("elapsed=${elapsedMs}ms", elapsedMs < 2_000)
        assertEquals("fresh", secrets.connection?.deviceToken)
    }

    @Test fun foreground_whileReconnecting_startsAFreshAttempt() {
        gateway.enqueue(ConnectBehavior.Accept(deviceToken = "first"))
        gateway.enqueueStalled(delayMs = 4_000)
        gateway.enqueue(ConnectBehavior.Accept(deviceToken = "fresh"))
        await { client.connect(gateway.url, "t") }
        await { client.awaitConnected() }

        gateway.closeAll()
        await { client.state.first { it is ConnectionState.Reconnecting } }
        Thread.sleep(1_300) // 再接続の 1 本目（固まる試行）に入るまで待つ

        val started = System.nanoTime()
        client.onForeground()
        await { client.state.first { it is ConnectionState.Connected && secrets.connection?.deviceToken == "fresh" } }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("elapsed=${elapsedMs}ms", elapsedMs < 2_000)
    }

    @Test fun events_areDelivered_exceptTick() {
        gateway.enqueue(ConnectBehavior.Accept())
        await { client.connect(gateway.url, "t") }
        await { client.awaitConnected() }
        val received = scope.async { client.events.first() }
        Thread.sleep(200)
        gateway.emit("tick", buildJsonObject { put("ts", 1) })
        gateway.emit("sessions.changed", buildJsonObject { put("key", "s1") })
        val event = await { received.await() }
        assertEquals("sessions.changed", event.name)
    }

    @Test fun backoff_isCapped() {
        assertEquals(1_000L, GatewayClient.backoffMs(1))
        assertEquals(2_000L, GatewayClient.backoffMs(2))
        assertEquals(30_000L, GatewayClient.backoffMs(20))
    }

    @Test fun reconnect_keepsDeviceTokenForSameUrl() {
        secrets.connection = GatewayConnection(gateway.url, "old", deviceToken = "keep-me")
        gateway.enqueue(ConnectBehavior.Accept(deviceToken = null))
        await { client.connect(gateway.url, "new") }
        await { client.awaitConnected() }
        assertEquals("keep-me", gateway.connectParams.single().obj("auth")?.string("deviceToken"))
    }
}

