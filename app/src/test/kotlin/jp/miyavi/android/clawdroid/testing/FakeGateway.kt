package jp.miyavi.android.clawdroid.testing

import java.util.concurrent.CopyOnWriteArrayList
import jp.miyavi.android.clawdroid.core.gateway.Frames
import jp.miyavi.android.clawdroid.core.gateway.obj
import jp.miyavi.android.clawdroid.core.gateway.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * テスト用の偽の Gateway（MockWebServer の WebSocket）。
 * 接続ごとに [ConnectBehavior] を 1 つ消費し、challenge → connect の応答を再現する。
 */
class FakeGateway {
    sealed interface ConnectBehavior {
        data class Accept(val deviceToken: String? = "device-token-1", val tickIntervalMs: Long = 30_000) : ConnectBehavior
        data class Reject(val code: String, val detailCode: String) : ConnectBehavior
        data class PairingRequired(
            val requestId: String = "pair-1",
            val resolveWith: String? = null,
            val waitForResolution: Boolean = true,
        ) : ConnectBehavior
    }

    val server = MockWebServer()
    val connectParams = CopyOnWriteArrayList<JsonObject>()
    val requests = CopyOnWriteArrayList<Pair<String, JsonObject?>>()
    private val sockets = CopyOnWriteArrayList<WebSocket>()

    /** 接続済みのときの RPC の応答。method → payload（null なら空のオブジェクト）。 */
    val handlers = mutableMapOf<String, (JsonObject?) -> JsonElement?>()
    val ignoredMethods = mutableSetOf<String>()

    val url: String get() = server.url("/").toString().replace("http://", "ws://")

    fun start() = server.start()
    fun shutdown() = server.close()

    fun enqueue(behavior: ConnectBehavior) {
        server.enqueue(MockResponse.Builder().webSocketUpgrade(listenerFor(behavior)).build())
    }

    /** 接続中のすべてのクライアントにイベントを送る。 */
    fun emit(event: String, payload: JsonObject) {
        val text = buildJsonObject {
            put("type", "event")
            put("event", event)
            put("payload", payload)
        }.toString()
        sockets.forEach { it.send(text) }
    }

    fun closeAll() = sockets.forEach { it.close(1001, "going away") }

    private fun listenerFor(behavior: ConnectBehavior) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            sockets += webSocket
            webSocket.send(
                buildJsonObject {
                    put("type", "event")
                    put("event", "connect.challenge")
                    put("payload", buildJsonObject { put("nonce", "nonce-1"); put("ts", 1_700_000_000_000L) })
                }.toString(),
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = Frames.json.parseToJsonElement(text).jsonObject
            val id = obj.string("id") ?: return
            val method = obj.string("method") ?: return
            val params = obj["params"] as? JsonObject
            if (method == "connect") {
                connectParams += params ?: JsonObject(emptyMap())
                respondToConnect(webSocket, id, behavior)
            } else {
                requests += method to params
                if (method in ignoredMethods) return
                val handler = handlers[method]
                if (handler == null) {
                    webSocket.send(error(id, "UNKNOWN_METHOD", null))
                } else {
                    webSocket.send(
                        buildJsonObject {
                            put("type", "res"); put("id", id); put("ok", true)
                            put("payload", handler(params) ?: JsonObject(emptyMap()))
                        }.toString(),
                    )
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            sockets -= webSocket
            webSocket.close(code, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            sockets -= webSocket
        }
    }

    private fun respondToConnect(ws: WebSocket, id: String, behavior: ConnectBehavior) {
        when (behavior) {
            is ConnectBehavior.Accept -> ws.send(
                buildJsonObject {
                    put("type", "res"); put("id", id); put("ok", true)
                    put("payload", buildJsonObject {
                        put("type", "hello-ok")
                        put("protocol", 4)
                        put("server", buildJsonObject { put("version", "2026.11.6"); put("connId", "c1") })
                        put("features", buildJsonObject {
                            put("methods", buildJsonArray { handlers.keys.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                            put("events", buildJsonArray { })
                        })
                        put("snapshot", buildJsonObject { })
                        put("auth", buildJsonObject {
                            put("role", "operator")
                            put("scopes", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("operator.admin")) })
                            behavior.deviceToken?.let { put("deviceToken", it) }
                        })
                        put("policy", buildJsonObject { put("tickIntervalMs", behavior.tickIntervalMs) })
                    })
                }.toString(),
            )
            is ConnectBehavior.Reject -> {
                ws.send(error(id, behavior.code, buildJsonObject { put("code", behavior.detailCode) }))
                ws.close(1008, "auth")
            }
            is ConnectBehavior.PairingRequired -> {
                ws.send(
                    error(id, "NOT_PAIRED", buildJsonObject {
                        put("code", "PAIRING_REQUIRED")
                        put("requestId", behavior.requestId)
                        if (behavior.waitForResolution) put("waitForResolution", true)
                    }),
                )
                if (!behavior.waitForResolution) ws.close(1008, "pairing required")
                behavior.resolveWith?.let { decision ->
                    ws.send(
                        buildJsonObject {
                            put("type", "event"); put("event", "device.pair.resolved")
                            put("payload", buildJsonObject {
                                put("requestId", behavior.requestId); put("deviceId", "d"); put("decision", decision); put("ts", 1)
                            })
                        }.toString(),
                    )
                    ws.close(1000, "resolved")
                }
            }
        }
    }

    private fun error(id: String, code: String, details: JsonObject?) = buildJsonObject {
        put("type", "res"); put("id", id); put("ok", false)
        put("error", buildJsonObject {
            put("code", code); put("message", code.lowercase())
            details?.let { put("details", it) }
        })
    }.toString()

    fun lastConnectAuthToken(): String? = connectParams.lastOrNull()?.obj("auth")?.string("token")
}
