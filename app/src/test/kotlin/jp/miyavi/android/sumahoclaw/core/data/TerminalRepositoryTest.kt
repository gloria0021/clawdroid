package jp.miyavi.android.sumahoclaw.core.data

import java.util.concurrent.TimeUnit
import jp.miyavi.android.sumahoclaw.core.gateway.ClientInfo
import jp.miyavi.android.sumahoclaw.core.gateway.DeviceIdentityRepository
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import jp.miyavi.android.sumahoclaw.core.gateway.string
import jp.miyavi.android.sumahoclaw.testing.FakeGateway
import jp.miyavi.android.sumahoclaw.testing.InMemorySecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** ターミナル（spec 002 US4、research R5）。 */
class TerminalRepositoryTest {
    private val gateway = FakeGateway()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var client: GatewayClient
    private val ids = object : TerminalIdStore {
        val map = mutableMapOf<String, String>()
        override fun get(conversationKey: String) = map[conversationKey]
        override fun put(conversationKey: String, sessionId: String?) {
            if (sessionId == null) map.remove(conversationKey) else map[conversationKey] = sessionId
        }
    }
    @Volatile private var alive = listOf("pty-1")

    @Before fun setUp() {
        gateway.handlers["terminal.open"] = {
            buildJsonObject { put("sessionId", "pty-1"); put("agentId", "a"); put("shell", "/bin/bash"); put("cwd", "/home/ws"); put("confined", false) }
        }
        gateway.handlers["terminal.input"] = { null }
        gateway.handlers["terminal.resize"] = { null }
        gateway.handlers["terminal.close"] = { null }
        gateway.handlers["terminal.list"] = {
            buildJsonObject {
                put("sessions", buildJsonArray {
                    alive.forEach { id -> add(buildJsonObject { put("sessionId", id); put("agentId", "a"); put("shell", "bash"); put("cwd", "/"); put("confined", false); put("attached", false); put("createdAtMs", 1) }) }
                })
            }
        }
        gateway.handlers["terminal.attach"] = {
            buildJsonObject { put("sessionId", "pty-1"); put("agentId", "a"); put("shell", "bash"); put("cwd", "/w"); put("confined", false); put("buffer", "hello\r\n"); put("seq", 7) }
        }
        gateway.start()
        gateway.enqueue(FakeGateway.ConnectBehavior.Accept())
        val secrets = InMemorySecretStore()
        client = GatewayClient(
            scope = scope,
            httpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
            identities = DeviceIdentityRepository(secrets),
            secrets = secrets,
            clientInfo = ClientInfo(version = "test", modelIdentifier = null, displayName = null),
        )
        await { client.connect(gateway.url, "t"); client.awaitConnected() }
    }

    @After fun tearDown() {
        scope.cancel()
        gateway.shutdown()
    }

    private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

    @Test fun open_isConnectionOwned_andMovesToWorkingFolder() {
        val repo = TerminalRepository(client, ids)
        val info = await { repo.open("agent:a:s1", "a", "/home/it's here", 100, 30) }
        val open = gateway.requests.first { it.first == "terminal.open" }.second!!
        // この接続だけのもの（会話に紐付けない。FR-028）
        assertNull(open["sessionKey"])
        assertEquals("100", open["cols"].toString())
        assertEquals("pty-1", ids.get("agent:a:s1"))
        assertEquals("/home/it's here", info.cwd)
        val cd = gateway.requests.first { it.first == "terminal.input" }.second!!.string("data")
        assertEquals("cd -- '/home/it'\\''s here' && clear\r", cd)
    }

    @Test fun reattach_returnsReplay_orForgetsDeadPty() {
        val repo = TerminalRepository(client, ids)
        ids.put("k", "pty-1")
        val replay = await { repo.reattach("k") }!!
        assertEquals("hello\r\n", replay.buffer)
        assertEquals(7L, replay.seq)
        alive = emptyList()
        assertNull(await { repo.reattach("k") })
        assertNull(ids.get("k"))
    }

    @Test fun events_filterBySession_andMapExitReasons() {
        val repo = TerminalRepository(client, ids)
        val collected = scope.async { repo.events("pty-1").take(2).toList() }
        runBlocking { kotlinx.coroutines.delay(200) }
        gateway.emit("terminal.data", buildJsonObject { put("sessionId", "other"); put("seq", 1); put("data", "x") })
        gateway.emit("terminal.data", buildJsonObject { put("sessionId", "pty-1"); put("seq", 3); put("data", "ls\r\n") })
        gateway.emit("terminal.exit", buildJsonObject { put("sessionId", "pty-1"); put("reason", "detached") })
        val events = await { collected.await() }
        assertEquals(TerminalEvent.Data("pty-1", 3, "ls\r\n"), events[0])
        assertEquals(ExitReason.DETACHED, (events[1] as TerminalEvent.Exit).reason)
    }

    @Test fun availability_requiresAdminAndMethod() {
        val repo = TerminalRepository(client, ids)
        assertNull(repo.availability())
        assertFalse(TerminalRepository.exitReason("process_exit") == ExitReason.CLOSED)
    }
}
