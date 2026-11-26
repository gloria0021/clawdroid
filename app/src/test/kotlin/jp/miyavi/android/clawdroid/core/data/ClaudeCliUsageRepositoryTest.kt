package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.ClientInfo
import jp.miyavi.android.clawdroid.core.gateway.DeviceIdentityRepository
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.gateway.string
import jp.miyavi.android.clawdroid.core.security.GatewayConnection
import jp.miyavi.android.clawdroid.testing.FakeGateway
import jp.miyavi.android.clawdroid.testing.InMemorySecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ClaudeCliUsageRepositoryTest {
    private val gateway = FakeGateway()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val secrets = InMemorySecretStore()
    private lateinit var client: GatewayClient
    private lateinit var repository: ClaudeCliUsageRepository
    private var elapsed = 0L
    @Volatile private var exitCode = 0
    private val output = "Current session: 55% used · resets 6pm (Asia/Tokyo)\nCurrent week (all models): 51% used · resets Nov 30, 2am (Asia/Tokyo)"

    @Before fun setUp() {
        gateway.handlers["terminal.open"] = { buildJsonObject { put("sessionId", "usage-pty") } }
        gateway.handlers["terminal.close"] = { null }
        gateway.handlers["terminal.input"] = { params ->
            val input = params!!.string("data")!!
            val nonce = Regex("clawdroid-claude-usage '([^']+)'").find(input)!!.groupValues[1]
            gateway.emit("terminal.data", buildJsonObject {
                put("sessionId", "usage-pty"); put("seq", 1)
                put("data", input + "\u001e$nonce:start\u001e\r\n$output\r\n")
            })
            gateway.emit("terminal.data", buildJsonObject {
                put("sessionId", "usage-pty"); put("seq", 2)
                put("data", "\u001e$nonce:end:$exitCode\u001e\r\n")
            })
            null
        }
        gateway.start()
        gateway.enqueue(FakeGateway.ConnectBehavior.Accept())
        client = GatewayClient(
            scope, OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
            DeviceIdentityRepository(secrets), secrets, ClientInfo("test", null, null),
        )
        await { client.connect(gateway.url, "t"); client.awaitConnected() }
        val ids = object : TerminalIdStore {
            override fun get(conversationKey: String): String? = error("Must not touch conversation terminals")
            override fun put(conversationKey: String, sessionId: String?) = error("Must not touch conversation terminals")
        }
        repository = ClaudeCliUsageRepository(TerminalRepository(client, ids), secrets) { elapsed }
    }

    @After fun tearDown() {
        scope.cancel()
        gateway.shutdown()
    }

    private fun <T> await(block: suspend CoroutineScope.() -> T): T = runBlocking { withTimeout(10_000) { block() } }
    private fun executions() = gateway.requests.count { it.first == "terminal.input" }

    @Test fun sharesConcurrentRequestsAndRefreshesAfterFiveMinutes() = await {
        val first = async { repository.load("a") }
        val second = async { repository.load("a") }
        assertEquals(0.55f, first.await()!!.windows[0].usedFraction)
        assertEquals(first.await(), second.await())
        assertEquals(1, executions())
        elapsed = 299_999
        repository.load("a")
        assertEquals(1, executions())
        elapsed = 300_000
        repository.load("a")
        assertEquals(2, executions())
        assertEquals(2, gateway.requests.count { it.first == "terminal.close" })
        assertTrue(gateway.requests.all { it.first.startsWith("terminal.") })
    }

    @Test fun cachesFailuresWithoutFallingBackToGatewayUsage() = await {
        exitCode = 1
        assertNull(repository.load("a"))
        exitCode = 0
        assertNull(repository.load("a"))
        assertEquals(1, executions())
        elapsed = 300_000
        assertEquals(2, repository.load("a")!!.windows.size)
        assertTrue(gateway.requests.all { it.first.startsWith("terminal.") })
    }

    @Test fun doesNotReuseAnotherHostOrAgentCache() = await {
        repository.load("a")
        repository.load("b")
        assertEquals(2, executions())
        secrets.saveConnection(GatewayConnection("ws://another-host", "token"))
        repository.load("a")
        assertEquals(3, executions())
    }

    @Test fun cancellationClosesPtyAndDoesNotCacheFailure() = await {
        val respond = gateway.handlers.getValue("terminal.input")
        gateway.handlers["terminal.input"] = { null }
        val request = async { repository.load("a") }
        while (executions() == 0) delay(10)
        request.cancelAndJoin()
        assertEquals(1, gateway.requests.count { it.first == "terminal.close" })
        gateway.handlers["terminal.input"] = respond
        assertEquals(2, repository.load("a")!!.windows.size)
        assertEquals(2, executions())
    }
}
