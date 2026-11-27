package jp.miyavi.android.sumahoclaw.core.data

import java.util.concurrent.TimeUnit
import jp.miyavi.android.sumahoclaw.core.gateway.ClientInfo
import jp.miyavi.android.sumahoclaw.core.gateway.DeviceIdentityRepository
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import jp.miyavi.android.sumahoclaw.core.gateway.bool
import jp.miyavi.android.sumahoclaw.core.gateway.string
import jp.miyavi.android.sumahoclaw.testing.FakeGateway
import jp.miyavi.android.sumahoclaw.testing.InMemorySecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 次々送る（steer）と、受け付け待ちの取り消し。 */
class ChatSessionQueueTest {
    private val gateway = FakeGateway()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var client: GatewayClient
    /** 履歴が返す受け付け待ちの runId。 */
    @Volatile private var queuedRunIds = emptyList<String>()
    /** 履歴が返す、差し込み済み・差し込みの順番待ちの runId（Gateway が `queued: true` を付けない送信）。 */
    @Volatile private var steeredRunIds = emptyList<String>()
    /** 履歴に記録済みのエージェントの文（最後の利用者のメッセージの後ろ）。 */
    @Volatile private var committedReply: String? = null

    @Before fun setUp() {
        gateway.handlers["chat.send"] = { buildJsonObject { put("status", "started") } }
        gateway.handlers["chat.abort"] = { params ->
            buildJsonObject {
                val withdrawn = params?.string("runId")?.takeIf { it == "idem-2" }
                put("ok", true)
                put("aborted", withdrawn != null)
                put("runIds", buildJsonArray { withdrawn?.let { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            }
        }
        gateway.handlers["sessions.messages.subscribe"] = { null }
        gateway.handlers["chat.history"] = {
            buildJsonObject {
                put("messages", buildJsonArray {
                    committedReply?.let { reply ->
                        add(buildJsonObject { put("role", "user"); put("content", "go") })
                        add(buildJsonObject { put("role", "assistant"); put("content", reply) })
                    }
                })
                put("inFlightRun", buildJsonObject { put("runId", "run-1"); put("text", "working") })
                put("pendingInputs", buildJsonObject {
                    put("total", queuedRunIds.size)
                    put("items", buildJsonArray {
                        queuedRunIds.forEach { id ->
                            add(buildJsonObject {
                                put("id", "in-$id"); put("runId", id); put("acceptedAt", 1); put("state", "queued"); put("queued", true)
                                put("message", buildJsonObject { put("role", "user"); put("content", "queued $id") })
                            })
                        }
                        steeredRunIds.forEach { id ->
                            add(buildJsonObject {
                                put("id", "in-$id"); put("runId", id); put("acceptedAt", 1); put("state", "queued")
                                put("message", buildJsonObject { put("role", "user"); put("content", "steered $id") })
                            })
                        }
                    })
                })
            }
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
    }

    @After fun tearDown() {
        scope.cancel()
        gateway.shutdown()
    }

    private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

    @Test fun liveText_alreadyInTranscript_isNotShownTwice() {
        await { client.connect(gateway.url, "t"); client.awaitConnected() }
        // 途中の一言（working）が記録に入ったあとも、生成中の文が同じ working を持っている
        committedReply = "working"
        val chat = ChatSession(client, scope, "agent:a:s1", "a")
        chat.open()
        await { chat.state.first { !it.loading && it.running } }
        assertEquals("", chat.state.value.live?.text)
        // 遅れて同じ文の delta が届いても出さない。続きの文は出す
        fun delta(text: String) = gateway.emit("chat", buildJsonObject {
            put("sessionKey", chat.sessionKey); put("runId", "run-1"); put("state", "delta"); put("deltaText", text); put("replace", true)
        })
        delta("working")
        // 生成中の文は実行全体をつないだもの。記録済みの一言を先頭から除き、続きだけを出す
        delta("working\n\nnext step")
        await { chat.state.first { it.live?.text == "next step" } }
        chat.close()
    }

    @Test fun completedRunStaysIdleDespiteStaleEventsAndHistory() {
        await { client.connect(gateway.url, "t"); client.awaitConnected() }
        val chat = ChatSession(client, scope, "agent:a:s1", "a")
        chat.open()
        await { chat.state.first { !it.loading && it.running } }

        fun emitChat(run: String, status: String) = gateway.emit("chat", buildJsonObject {
            put("sessionKey", chat.sessionKey)
            put("runId", run)
            put("state", status)
            put("phase", "thinking")
        })
        emitChat("run-1", "final")
        await { chat.state.first { !it.running } }
        emitChat("run-1", "status")
        gateway.emit("session.tool", buildJsonObject {
            put("sessionKey", chat.sessionKey)
            put("name", "finished-tool")
        })
        // The fixture continues returning run-1 as in flight, even after final.
        await { chat.refreshHistory() }
        assertEquals(false, chat.state.value.running)
        assertEquals(null, chat.state.value.activity)
        assertEquals(null, chat.state.value.live)

        emitChat("run-2", "status")
        await { chat.state.first { it.running && it.activeRunId == "run-2" } }
        chat.close()
    }

    @Test fun sendWhileRunning_isSteer_andShowsPending_thenCancelDiscards() {
        await { client.connect(gateway.url, "t"); client.awaitConnected() }
        val chat = ChatSession(client, scope, "agent:a:s1", "a")
        chat.open()
        await { chat.state.first { !it.loading && it.running } }

        await { chat.send("second", emptyList(), "idem-2") }
        val send = gateway.requests.last { it.first == "chat.send" }.second!!
        assertEquals("steer", send.string("queueMode"))
        assertEquals("idem-2", send.string("idempotencyKey"))
        // 実行中に送ったものは会話ではなく受け付け待ちに出る
        assertTrue(chat.state.value.pending.any { it.runId == "idem-2" })
        assertTrue(chat.state.value.messages.none { it.runId == "idem-2" })

        // Gateway が受け付け待ちとして返すようになっても、二重にならない
        queuedRunIds = listOf("idem-2")
        await { chat.refreshHistory() }
        assertEquals(1, chat.state.value.pending.count { it.runId == "idem-2" })

        assertEquals(false, await { chat.cancelPending("not-queued") })
        queuedRunIds = emptyList()
        assertEquals(true, await { chat.cancelPending("idem-2") })
        val abort = gateway.requests.last { it.first == "chat.abort" }.second!!
        assertEquals("idem-2", abort.string("runId"))
        assertEquals(true, abort.bool("discardPendingInput"))
        assertTrue(chat.state.value.pending.isEmpty())
        chat.close()
    }

    @Test fun onlyGatewayQueuedInputs_areCancellable_othersAreSentBubbles() {
        await { client.connect(gateway.url, "t"); client.awaitConnected() }
        val chat = ChatSession(client, scope, "agent:a:s1", "a")
        chat.open()
        await { chat.state.first { !it.loading && it.running } }

        // 受け付けの応答を待っている手元の送信は、取り消せない（送信済みの吹き出し）
        await { chat.send("local", emptyList(), "idem-local") }
        assertTrue(chat.state.value.sentInputs.any { it.runId == "idem-local" })
        assertTrue(chat.state.value.queuedInputs.none { it.runId == "idem-local" })

        // Gateway が queued: true を付けたものだけがピル、付けないもの（差し込み済み・順番待ち）は吹き出し
        queuedRunIds = listOf("idem-q")
        steeredRunIds = listOf("idem-s")
        await { chat.refreshHistory() }
        assertEquals(listOf("idem-q"), chat.state.value.queuedInputs.map { it.runId })
        assertTrue(chat.state.value.sentInputs.any { it.runId == "idem-s" && it.text == "steered idem-s" })
        assertTrue(chat.state.value.sentInputs.none { it.runId == "idem-q" })
        chat.close()
    }
}
