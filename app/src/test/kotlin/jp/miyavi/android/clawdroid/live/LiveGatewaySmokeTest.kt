package jp.miyavi.android.clawdroid.live

import java.util.concurrent.TimeUnit
import jp.miyavi.android.clawdroid.core.data.Parse
import jp.miyavi.android.clawdroid.core.gateway.ClientInfo
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.DeviceIdentityRepository
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.gateway.array
import jp.miyavi.android.clawdroid.core.gateway.asObject
import jp.miyavi.android.clawdroid.testing.InMemorySecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 実際の Gateway への読み取りだけの接続確認。環境変数があるときだけ動く（通常のテストでは飛ばす）。
 *   CLAWDROID_LIVE_URL=ws://127.0.0.1:18789 CLAWDROID_LIVE_TOKEN=... ./gradlew testDebugUnitTest --tests '*LiveGatewaySmokeTest*'
 * メッセージの送信など、Gateway の状態を変える操作はしない。
 */
class LiveGatewaySmokeTest {
    @Test fun connect_and_readAgentsSessionsHistory() {
        val url = System.getenv("CLAWDROID_LIVE_URL")
        val token = System.getenv("CLAWDROID_LIVE_TOKEN")
        assumeTrue(!url.isNullOrBlank() && !token.isNullOrBlank())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val secrets = InMemorySecretStore()
        val client = GatewayClient(
            scope = scope,
            httpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
            identities = DeviceIdentityRepository(secrets),
            secrets = secrets,
            clientInfo = ClientInfo(version = "0.1.0-smoke", modelIdentifier = "Clawdroid smoke test", displayName = "Clawdroid smoke test"),
        )
        try {
            runBlocking {
                client.connect(url!!, token!!)
                val state = withTimeout(30_000) {
                    client.state.first { it is ConnectionState.Connected || it is ConnectionState.Failed || it is ConnectionState.AwaitingApproval }
                }
                println("LIVE state: $state".take(400).replace(Regex("methods=\\[[^]]*]"), "methods=[…]"))
                check(state is ConnectionState.Connected) { "not connected: $state" }
                println("LIVE scopes: ${state.scopes}  methods: ${state.methods.size}")
                listOf("agents.list", "sessions.list", "chat.history", "sessions.subscribe", "sessions.messages.subscribe", "chat.send", "gateway.restart.request")
                    .forEach { println("LIVE supports $it: ${it in state.methods}") }

                val agentsRoot = client.request("agents.list", JsonObject(emptyMap())).asObject()
                val agents = agentsRoot?.array("agents")?.mapNotNull { Parse.agent(it as JsonObject) }.orEmpty()
                println("LIVE agents: ${agents.map { "${it.id}${if (it.isSystem) "(system)" else ""}" }}")
                val agent = agents.firstOrNull { !it.isSystem } ?: error("no agents")

                val sessionsRoot = client.request("sessions.list", buildJsonObject {
                    put("agentId", agent.id); put("includeGlobal", false); put("includeUnknown", false)
                    put("excludeSubagents", true); put("excludeCron", true); put("includeDerivedTitles", true)
                    put("sortBy", "activity"); put("limit", 5)
                }).asObject()
                val sessions = Parse.sessionsArray(sessionsRoot)
                println("LIVE sessions(${agent.id}): ${sessions.size} first=${sessions.firstOrNull()?.let { "title=${it.title?.take(30)} cwd=${it.cwd} running=${it.running} updatedAt=${it.updatedAt}" }}")

                sessions.firstOrNull()?.let { s ->
                    val history = client.request("chat.history", buildJsonObject {
                        put("sessionKey", s.key); put("agentId", agent.id); put("limit", 20)
                    }).asObject()
                    val messages = history?.array("messages").orEmpty().mapIndexedNotNull { i, el -> Parse.message(el as JsonObject, i) }
                    println("LIVE history: raw=${history?.array("messages")?.size} parsed=${messages.size} roles=${messages.groupingBy { it.role }.eachCount()} parts=${messages.flatMap { it.parts }.groupingBy { it::class.simpleName }.eachCount()}")
                }
                runCatching { client.request("sessions.subscribe", JsonObject(emptyMap())) }
                    .onSuccess { println("LIVE sessions.subscribe ok") }
                    .onFailure { println("LIVE sessions.subscribe failed: ${it.message}") }
            }
        } finally {
            client.disconnect()
            scope.cancel()
        }
    }
}
