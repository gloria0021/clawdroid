package jp.miyavi.android.sumahoclaw.core.data

import jp.miyavi.android.sumahoclaw.core.gateway.long
import jp.miyavi.android.sumahoclaw.core.gateway.obj
import jp.miyavi.android.sumahoclaw.core.gateway.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * エージェントの一覧（`agents.list` の応答）を手元に残す（マスター指定 2026-11-27）。
 *
 * Gateway は顔の画像を `identity.avatarUrl` に data URI で埋め込んで返すため、一覧はエージェント 4 体で約 3MB になる
 * （画像を外して取る引数は無い）。毎回つながるたびに取ると、遅い回線では読み込みが終わらない。そこで:
 * - 画像はファイルに書き出し、一覧には `file://` の場所だけを残す（画像が変われば名前が変わる）
 * - 一覧（画像を除いて数 KB）は接続先ごとに 1 つ保存し、起動したらまずそれを見せる
- 取ったときの Gateway の設定の版も残し、つなぎ直したときに版が同じなら取り直さない
 */
class AgentRosterCache(private val dir: File) {
    private val rosterFile = File(dir, ROSTER_FILE)
    private val avatarDir = File(dir, AVATAR_DIR)

    /** [configHash] は取ったときの Gateway の設定の版（分からなければ null）。 */
    data class Snapshot(val endpoint: String, val fetchedAt: Long, val configHash: String?, val root: JsonObject)

    /** 保存してある一覧。無い・壊れている・画像のファイルが消えているときは null。 */
    fun load(): Snapshot? = runCatching {
        if (!rosterFile.isFile) return null
        val saved = Json.parseToJsonElement(rosterFile.readText()) as? JsonObject ?: return null
        val endpoint = saved.string("endpoint") ?: return null
        val fetchedAt = saved.long("fetchedAt") ?: return null
        val root = saved.obj("root") ?: return null
        if (!avatarsPresent(root)) return null
        Snapshot(endpoint, fetchedAt, saved.string("configHash"), root)
    }.getOrNull()

    /**
     * Gateway の応答の画像をファイルに書き出し、`file://` の場所に置き換えた応答を返す。保存もする。
     * 書き出せなかった画像は data URI のまま残す（表示はできる）。
     */
    fun store(endpoint: String, fetchedAt: Long, configHash: String?, root: JsonObject): JsonObject {
        avatarDir.mkdirs()
        val used = HashSet<String>()
        val agents = (root["agents"] as? JsonArray)?.map { element ->
            val agent = element as? JsonObject ?: return@map element
            val identity = agent.obj("identity") ?: return@map element
            val dataUri = identity.string("avatarUrl")?.trim()?.takeIf { it.startsWith("data:image/", ignoreCase = true) }
                ?: return@map element
            val file = writeAvatar(agent.string("id") ?: return@map element, dataUri) ?: return@map element
            used += file.name
            JsonObject(agent + ("identity" to JsonObject(identity + ("avatarUrl" to JsonPrimitive(file.toURI().toString())))))
        }
        val localized = if (agents == null) root else JsonObject(root + ("agents" to JsonArray(agents)))
        runCatching {
            val saved = JsonObject(
                buildMap {
                    put("endpoint", JsonPrimitive(endpoint))
                    put("fetchedAt", JsonPrimitive(fetchedAt))
                    configHash?.let { put("configHash", JsonPrimitive(it)) }
                    put("root", localized)
                },
            )
            val tmp = File(dir, "$ROSTER_FILE.tmp")
            tmp.writeText(saved.toString())
            if (!tmp.renameTo(rosterFile)) tmp.delete()
            // 使わなくなった画像（変わる前の顔・消えたエージェント）を片付ける
            avatarDir.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
        }
        return localized
    }

    private fun writeAvatar(agentId: String, dataUri: String): File? = runCatching {
        val comma = dataUri.indexOf(',')
        if (comma < 0 || !dataUri.substring(0, comma).endsWith(";base64", ignoreCase = true)) return null
        val ext = dataUri.substring(DATA_PREFIX.length, comma).substringBefore(';').substringAfter('/').lowercase()
            .filter { it.isLetterOrDigit() }.ifEmpty { "img" }
        val name = "${safeName(agentId)}-${hash(dataUri)}.$ext"
        val file = File(avatarDir, name)
        if (!file.isFile) {
            val bytes = Base64.getMimeDecoder().decode(dataUri.substring(comma + 1))
            val tmp = File(avatarDir, "$name.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                tmp.delete()
                return null
            }
        }
        file
    }.getOrNull()

    private fun avatarsPresent(root: JsonObject): Boolean = (root["agents"] as? JsonArray).orEmpty().all { element ->
        val url = (element as? JsonObject)?.obj("identity")?.string("avatarUrl") ?: return@all true
        if (!url.startsWith("file:")) return@all true
        runCatching { File(java.net.URI(url)).isFile }.getOrDefault(false)
    }

    private fun safeName(id: String): String = id.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
        .joinToString("").take(MAX_ID_CHARS)

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(HASH_BYTES).joinToString("") { "%02x".format(it) }

    companion object {
        private const val ROSTER_FILE = "agents.json"
        private const val AVATAR_DIR = "avatars"
        private const val DATA_PREFIX = "data:image/"
        private const val HASH_BYTES = 8
        private const val MAX_ID_CHARS = 48
    }
}
