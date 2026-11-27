package jp.miyavi.android.sumahoclaw.core.data

import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import jp.miyavi.android.sumahoclaw.core.gateway.array
import jp.miyavi.android.sumahoclaw.core.gateway.asObject
import jp.miyavi.android.sumahoclaw.core.gateway.bool
import jp.miyavi.android.sumahoclaw.core.gateway.obj
import jp.miyavi.android.sumahoclaw.core.gateway.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** スキルの状態（spec 004 FR-030）。並びもこの順。 */
enum class SkillState { ENABLED, UNMET, DISABLED }

/**
 * スキルの出どころ（`skills.status` の `source`。資料 tools/skills.md の読み込み元）。[agentOnly] はこのエージェントだけに見えるもの。
 * 並びもこの順（このエージェントのもの → 共有 → 同梱）。
 */
enum class SkillOrigin(val agentOnly: Boolean) {
    /** `<workspace>/skills`。 */
    WORKSPACE(true),
    /** `<workspace>/.agents/skills`。 */
    PROJECT(true),
    /** エージェントが Skill Workshop で作ったもの（`workshop-skills`）。 */
    WORKSHOP(true),
    /** `<state-dir>/skills`。全エージェントで共有。 */
    MANAGED(false),
    /** 個人・チームのスキルライブラリ。 */
    LIBRARY(false),
    /** `~/.agents/skills`（AgentSkills の共通の場所。全エージェントとほかの AI ツールで共通）。 */
    PERSONAL(false),
    /** `skills.load.extraDirs`。 */
    EXTRA(false),
    PLUGIN(false),
    BUNDLED(false),
    UNKNOWN(false),
    ;

    companion object {
        fun of(source: String?, bundled: Boolean): SkillOrigin = when (source) {
            "openclaw-workspace" -> WORKSPACE
            "agents-skills-project" -> PROJECT
            "openclaw-workshop" -> WORKSHOP
            "openclaw-managed" -> MANAGED
            "openclaw-library" -> LIBRARY
            "agents-skills-personal" -> PERSONAL
            "openclaw-extra" -> EXTRA
            "openclaw-plugin" -> PLUGIN
            "openclaw-bundled" -> BUNDLED
            else -> if (bundled) BUNDLED else UNKNOWN
        }
    }
}

/** 足りない要件（`skills.status` の `missing`）。 */
data class SkillMissing(
    val bins: List<String> = emptyList(),
    val anyBins: List<String> = emptyList(),
    val env: List<String> = emptyList(),
    val config: List<String> = emptyList(),
    val os: List<String> = emptyList(),
)

/** `skills.status` の 1 件。ホストのパスは [filePath]（SKILL.md を読むためだけ。表示しない）以外持たない。 */
data class SkillStatus(
    val skillKey: String,
    val name: String,
    val description: String?,
    val emoji: String?,
    val filePath: String?,
    val source: String?,
    val origin: SkillOrigin = SkillOrigin.of(source, bundled = false),
    /** null = Gateway が状態を返さなかった（切り替えを出さない）。 */
    val state: SkillState?,
    val missing: SkillMissing,
    val blockedByAllowlist: Boolean,
    val blockedByAgentFilter: Boolean,
) {
    val enabled: Boolean get() = state != null && state != SkillState.DISABLED
}

internal object SkillParse {
    /** `skills.status` の結果。形はスキーマで決まっていないので、知らない項目は無視する（004 research R4）。 */
    fun status(result: JsonObject?): List<SkillStatus> = sortSkills(
        result?.array("skills").orEmpty().mapNotNull { element -> element.asObject()?.let(::skill) }.distinctBy { it.skillKey },
    )

    private fun skill(obj: JsonObject): SkillStatus? {
        val name = obj.string("name")?.takeIf(String::isNotBlank) ?: return null
        val eligible = obj.bool("eligible")
        val disabled = obj.bool("disabled")
        val state = when {
            disabled == true -> SkillState.DISABLED
            eligible == true -> SkillState.ENABLED
            eligible == false -> SkillState.UNMET
            else -> null
        }
        val missing = obj.obj("missing")
        return SkillStatus(
            skillKey = obj.string("skillKey") ?: name,
            name = name,
            description = obj.string("description")?.takeIf(String::isNotBlank),
            emoji = obj.string("emoji")?.takeIf(String::isNotBlank),
            filePath = obj.string("filePath"),
            source = obj.string("source"),
            origin = SkillOrigin.of(obj.string("source"), obj.bool("bundled") == true),
            state = state,
            missing = SkillMissing(
                bins = missing.strings("bins"),
                anyBins = missing.strings("anyBins"),
                env = missing.strings("env"),
                config = missing.strings("config"),
                os = missing.strings("os"),
            ),
            blockedByAllowlist = obj.bool("blockedByAllowlist") == true,
            blockedByAgentFilter = obj.bool("blockedByAgentFilter") == true,
        )
    }

    private fun JsonObject?.strings(key: String): List<String> =
        (this?.get(key) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
}

/** 使用中 → 未対応 → 無効（状態の分からないものは最後）、その中は出どころの順（このエージェントのもの → 共有 → 同梱）、名前順。 */
fun sortSkills(skills: List<SkillStatus>): List<SkillStatus> =
    skills.sortedWith(
        compareBy<SkillStatus> { it.state?.ordinal ?: Int.MAX_VALUE }
            .thenBy { it.origin.ordinal }
            .thenComparator { a, b -> naturalCompare(a.name, b.name) },
    )

/** スキルの窓口（ViewModel のテストで偽物に差し替える）。 */
interface SkillSource {
    suspend fun status(agentId: String): List<SkillStatus>
    suspend fun readSkillFile(agentId: String, filePath: String): String
    suspend fun setEnabled(skillKey: String, enabled: Boolean)
}

/** `skills.status` / `skills.files.get` / `skills.update`（spec 004、contracts/gateway-rpc.md）。 */
class SkillsRepository(private val gateway: GatewayClient) : SkillSource {
    override suspend fun status(agentId: String): List<SkillStatus> =
        SkillParse.status(gateway.request("skills.status", buildJsonObject { put("agentId", agentId) }).asObject())

    override suspend fun readSkillFile(agentId: String, filePath: String): String =
        gateway.request("skills.files.get", buildJsonObject {
            put("agentId", agentId)
            put("path", filePath)
        }).asObject()?.string("content") ?: error("no content")

    /** 有効・無効は Gateway 全体の設定（`skills.entries.<key>.enabled`）。`apiKey`・`env` は送らない。 */
    override suspend fun setEnabled(skillKey: String, enabled: Boolean) {
        gateway.request("skills.update", buildJsonObject {
            put("skillKey", skillKey)
            put("enabled", enabled)
        })
    }
}
