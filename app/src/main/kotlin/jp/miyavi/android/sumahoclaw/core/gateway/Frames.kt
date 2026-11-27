package jp.miyavi.android.sumahoclaw.core.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Gateway のプロトコルのフレーム（`ref-schema/frames.ts`）。 */
sealed interface Frame {
    data class Response(val id: String, val ok: Boolean, val payload: JsonElement?, val error: GatewayError?) : Frame
    data class Event(val event: String, val payload: JsonElement?, val seq: Long?) : Frame
}

/** `ErrorShapeSchema`。`details.code` に細かい理由が入る。 */
data class GatewayError(
    val code: String,
    val message: String,
    val details: JsonObject?,
    val retryable: Boolean?,
    val retryAfterMs: Long?,
) {
    val detailCode: String? get() = details?.string("code")
}

object Frames {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    fun request(id: String, method: String, params: JsonElement?): String = buildJsonObject {
        put("type", "req")
        put("id", id)
        put("method", method)
        if (params != null && params !is JsonNull) put("params", params)
    }.toString()

    /** 受け取った文字列を解析する。要求（`req`）や壊れたフレームは null。 */
    fun parse(text: String): Frame? {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        return when (obj.string("type")) {
            "res" -> Frame.Response(
                id = obj.string("id") ?: return null,
                ok = (obj["ok"] as? JsonPrimitive)?.booleanOrNull ?: false,
                payload = obj["payload"],
                error = (obj["error"] as? JsonObject)?.let(::parseError),
            )
            "event" -> Frame.Event(
                event = obj.string("event") ?: return null,
                payload = obj["payload"],
                seq = (obj["seq"] as? JsonPrimitive)?.longOrNull,
            )
            else -> null
        }
    }

    fun parseError(obj: JsonObject): GatewayError = GatewayError(
        code = obj.string("code") ?: "UNKNOWN",
        message = obj.string("message") ?: "",
        details = obj["details"] as? JsonObject,
        retryable = (obj["retryable"] as? JsonPrimitive)?.booleanOrNull,
        retryAfterMs = (obj["retryAfterMs"] as? JsonPrimitive)?.longOrNull,
    )
}

// JSON を読む小さな補助。各 Repository でも使う。
fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? kotlinx.serialization.json.JsonArray) ?: emptyList()
fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
