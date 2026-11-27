package jp.miyavi.android.sumahoclaw.core.data

import jp.miyavi.android.sumahoclaw.core.gateway.ClientInfo
import jp.miyavi.android.sumahoclaw.core.gateway.ConnectionState
import jp.miyavi.android.sumahoclaw.core.gateway.DeviceIdentityRepository
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import jp.miyavi.android.sumahoclaw.testing.FakeGateway
import jp.miyavi.android.sumahoclaw.testing.InMemorySecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

/**
 * エージェントの一覧（顔の画像込みで数 MB）を、つなぎ直すたびに取り直さないこと（マスター指定 2026-11-27）。
 * 設定の版（appliedConfigHash）が変わったとき・つながっているあいだに config.changed が来たときだけ取り直す。
 */
class AgentRosterRefreshTest {
    @get:Rule val tmp = TemporaryFolder()
    private val gateway = FakeGateway()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun tearDown() {
        scope.cancel()
        gateway.shutdown()
    }

    private fun count(method: String) = gateway.requests.count { it.first == method }

    private suspend fun until(what: String, check: () -> Boolean) {
        runCatching { withTimeout(15_000) { while (!check()) delay(20) } }
            .onFailure { throw AssertionError("timed out: $what (agents.list=${count("agents.list")})") }
    }

    @Test fun refetchesOnlyWhenConfigVersionChanges() = runBlocking {
        gateway.handlers["agents.list"] = {
            buildJsonObject {
                put("defaultId", "a")
                put("agents", buildJsonArray { add(buildJsonObject { put("id", "a") }) })
            }
        }
        gateway.handlers["sessions.subscribe"] = { JsonObject(emptyMap()) }
        gateway.start()
        gateway.enqueue(FakeGateway.ConnectBehavior.Accept(configHash = "h1"))
        val secrets = InMemorySecretStore()
        val client = GatewayClient(
            scope = scope,
            httpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
            identities = DeviceIdentityRepository(secrets),
            secrets = secrets,
            clientInfo = ClientInfo(version = "test", modelIdentifier = null, displayName = null),
        )
        val repo = SessionsRepository(client, scope, AgentRosterCache(tmp.root))
        withTimeout(10_000) { client.connect(gateway.url, "t"); client.awaitConnected() }
        withTimeout(10_000) { repo.agents.first { it.agents.isNotEmpty() } }
        until("first subscribe") { count("sessions.subscribe") >= 1 }
        assertEquals(1, count("agents.list"))

        // 同じ版でつなぎ直す: 取り直さない
        gateway.enqueue(FakeGateway.ConnectBehavior.Accept(configHash = "h1"))
        gateway.closeAll()
        until("second connect") { count("sessions.subscribe") >= 2 }
        delay(300)
        assertEquals(1, count("agents.list"))

        // 版が変わっていた: 取り直す
        gateway.enqueue(FakeGateway.ConnectBehavior.Accept(configHash = "h2"))
        gateway.closeAll()
        until("refetch on new hash") { count("agents.list") == 2 }

        // つながっているあいだの設定の変更: 取り直す
        client.state.first { it is ConnectionState.Connected }
        gateway.emit("config.changed", buildJsonObject { put("hash", "x") })
        until("refetch on config.changed") { count("agents.list") == 3 }
    }
}
