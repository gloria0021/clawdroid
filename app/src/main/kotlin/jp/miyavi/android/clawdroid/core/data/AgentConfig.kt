package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.gateway.asObject
import jp.miyavi.android.clawdroid.core.gateway.bool
import jp.miyavi.android.clawdroid.core.gateway.obj
import jp.miyavi.android.clawdroid.core.gateway.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 設定の値と、エージェントの定義に無く既定（`agents.defaults` / `tools`）から引き継いだか。 */
data class Configured<T>(val value: T, val inherited: Boolean)

/**
 * openclaw.json のエージェントの定義（`agents.entries.<id>`）を、既定と重ねて読んだもの（spec 004 FR-010）。
 * 読むだけ。秘密の値は Gateway が伏せて返す（`config.get` は redacted）。ホストのパスはワークスペースの名前だけ持つ。
 */
data class AgentConfig(
    val model: Configured<String>?,
    val thinking: Configured<String>?,
    val toolProfile: Configured<String>?,
    /** `tools.allow` と `tools.alsoAllow` を合わせたもの（エージェントの定義のもの）。 */
    val allow: List<String>,
    val deny: List<String>,
    val execMode: Configured<String>?,
    val elevated: Configured<Boolean>?,
    val workspaceOnly: Configured<Boolean>?,
    /** Web 検索（Gateway 全体の `tools.web.search`）。 */
    val webSearch: Boolean?,
    val webSearchProvider: String?,
    /** ワークスペースのフォルダの名前（パスの最後だけ）。 */
    val workspaceName: String?,
    /** 全体の既定（`agents.defaults`）。「既定に戻す」の説明に使う。 */
    val defaultModel: String? = null,
    val defaultThinking: String? = null,
    /** 読んだときの設定の版（`config.patch` の baseHash。他で変わっていれば Gateway が拒む）。 */
    val baseHash: String? = null,
)

internal object AgentConfigParse {
    /** `config.get` の結果から、[agentId] の定義を取り出す。見つからなければ null。 */
    fun parse(result: JsonObject?, agentId: String): AgentConfig? {
        val config = result?.obj("config") ?: result?.obj("resolved") ?: return null
        val agents = config.obj("agents")
        val defaults = agents?.obj("defaults")
        val entry = entryOf(agents, agentId) ?: return null
        val tools = entry.obj("tools")
        val globalTools = config.obj("tools")

        fun <T> pick(own: T?, fallback: T?): Configured<T>? = when {
            own != null -> Configured(own, inherited = false)
            fallback != null -> Configured(fallback, inherited = true)
            else -> null
        }

        val search = globalTools?.obj("web")?.obj("search")
        return AgentConfig(
            model = pick(modelOf(entry), modelOf(defaults)),
            thinking = pick(entry.string("thinkingDefault"), defaults?.string("thinkingDefault")),
            toolProfile = pick(tools?.string("profile"), globalTools?.string("profile")),
            allow = (tools.strings("allow") + tools.strings("alsoAllow")).distinct(),
            deny = tools.strings("deny"),
            execMode = pick(tools?.obj("exec")?.string("mode"), globalTools?.obj("exec")?.string("mode")),
            elevated = pick(tools?.obj("elevated")?.bool("enabled"), globalTools?.obj("elevated")?.bool("enabled")),
            workspaceOnly = pick(tools?.obj("fs")?.bool("workspaceOnly"), globalTools?.obj("fs")?.bool("workspaceOnly")),
            webSearch = search?.bool("enabled"),
            webSearchProvider = search?.string("provider")?.takeIf(String::isNotBlank),
            workspaceName = (entry.string("workspace") ?: defaults?.string("workspace"))
                ?.trimEnd('/')?.substringAfterLast('/')?.takeIf(String::isNotBlank),
            defaultModel = modelOf(defaults),
            defaultThinking = defaults?.string("thinkingDefault"),
            baseHash = result?.string("hash"),
        )
    }

    /** `agents.entries` は id をキーにしたオブジェクト。古い形の配列（`id` を持つ）にも対応する。 */
    private fun entryOf(agents: JsonObject?, agentId: String): JsonObject? {
        val entries = agents?.get("entries") ?: agents?.get("list")
        return when (entries) {
            is JsonObject -> entries.obj(agentId)
            is JsonArray -> entries.firstNotNullOfOrNull { (it as? JsonObject)?.takeIf { e -> e.string("id") == agentId } }
            else -> null
        }
    }

    /** モデルは文字列か `{ primary }`。 */
    private fun modelOf(obj: JsonObject?): String? = when (val m = obj?.get("model")) {
        is JsonPrimitive -> m.contentOrNull?.takeIf(String::isNotBlank)
        is JsonObject -> m.string("primary")
        else -> null
    }

    private fun JsonObject?.strings(key: String): List<String> =
        (this?.get(key) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
}

/** エージェントの定義を読む窓口（ViewModel のテストで偽物に差し替える）。 */
fun interface AgentConfigSource {
    suspend fun load(agentId: String): AgentConfig?

    /**
     * 名前と絵文字を変える（`agents.update`）。Gateway が openclaw.json の `name`・`identity` を書き換え、
     * IDENTITY.md も書き直す（Control UI の「アイデンティティ」と同じ。spec 004 FR-013 改訂）。
     */
    suspend fun updateIdentity(agentId: String, name: String, emoji: String): Unit = error("unsupported")

    /** 選べるモデル（`models.list`）。 */
    suspend fun modelCatalog(agentId: String): ModelCatalog? = null

    /** 既定のモデルを変える（`agents.update` の model。null なら個別の設定を消して全体の既定に戻す）。 */
    suspend fun setModel(agentId: String, model: String?): Unit = error("unsupported")

    /**
     * 思考の既定を変える（`config.patch` で `agents.entries.<id>.thinkingDefault`。null なら消して全体の既定に戻す）。
     * [baseHash] は読んだときの設定の版。他で変わっていれば Gateway が拒む。
     */
    suspend fun setThinking(agentId: String, level: String?, baseHash: String?): Unit = error("unsupported")
}

/** `config.get`（伏せた設定の写し）からエージェントの定義を読む（spec 004 FR-010、research R11）。 */
class AgentConfigRepository(private val gateway: GatewayClient) : AgentConfigSource {
    override suspend fun load(agentId: String): AgentConfig? =
        AgentConfigParse.parse(gateway.request("config.get", JsonObject(emptyMap())).asObject(), agentId)

    override suspend fun modelCatalog(agentId: String): ModelCatalog? =
        gateway.request("models.list", kotlinx.serialization.json.buildJsonObject {
            put("agentId", kotlinx.serialization.json.JsonPrimitive(agentId))
            put("includeDetails", kotlinx.serialization.json.JsonPrimitive(true))
        }).asObject()?.let(Parse::modelCatalog)

    override suspend fun setModel(agentId: String, model: String?) {
        gateway.request("agents.update", kotlinx.serialization.json.buildJsonObject {
            put("agentId", kotlinx.serialization.json.JsonPrimitive(agentId))
            put("model", model?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
        })
    }

    override suspend fun setThinking(agentId: String, level: String?, baseHash: String?) {
        // JSON の merge patch。null はその項目を消す（全体の既定に戻る）
        val patch = kotlinx.serialization.json.buildJsonObject {
            put("agents", kotlinx.serialization.json.buildJsonObject {
                put("entries", kotlinx.serialization.json.buildJsonObject {
                    put(agentId, kotlinx.serialization.json.buildJsonObject {
                        put("thinkingDefault", level?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
                    })
                })
            })
        }
        gateway.request("config.patch", kotlinx.serialization.json.buildJsonObject {
            put("raw", kotlinx.serialization.json.JsonPrimitive(patch.toString()))
            baseHash?.let { put("baseHash", kotlinx.serialization.json.JsonPrimitive(it)) }
            put("note", kotlinx.serialization.json.JsonPrimitive("Clawdroid: thinkingDefault"))
        })
    }

    override suspend fun updateIdentity(agentId: String, name: String, emoji: String) {
        gateway.request("agents.update", kotlinx.serialization.json.buildJsonObject {
            put("agentId", kotlinx.serialization.json.JsonPrimitive(agentId))
            put("name", kotlinx.serialization.json.JsonPrimitive(name))
            put("emoji", kotlinx.serialization.json.JsonPrimitive(emoji))
        })
    }
}
