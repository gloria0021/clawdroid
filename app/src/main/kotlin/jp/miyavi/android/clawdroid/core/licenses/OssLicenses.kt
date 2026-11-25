package jp.miyavi.android.clawdroid.core.licenses

import android.content.Context
import androidx.compose.runtime.Immutable
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.gateway.array
import jp.miyavi.android.clawdroid.core.gateway.asObject
import jp.miyavi.android.clawdroid.core.gateway.obj
import jp.miyavi.android.clawdroid.core.gateway.string
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** 1 つの許諾（種類の名前と全文）。 */
@Immutable
data class OssLicense(val name: String, val text: String?, val url: String?)

/** ライセンスの一覧の 1 行（ライブラリ・フォント・アイコンなど）。 */
@Immutable
data class OssEntry(
    val id: String,
    val name: String,
    val version: String?,
    val licenses: List<OssLicense>,
) {
    val licenseNames: String get() = licenses.joinToString(" / ") { it.name }
}

/**
 * 同梱したもののライセンス（spec 003 FR-033・034）。
 * ライブラリの分はビルドで依存関係から作る `res/raw/aboutlibraries.json`、依存関係に出ないもの（フォント・アイコン・取り込んだコード）は
 * `assets/licenses/` から足す。どちらも端末内にあり、ネットワークに頼らない。
 */
object OssLicenses {
    /** 依存関係に出ないもの。ファイルは assets/licenses/ に置く。 */
    private val bundled = listOf(
        Bundled("hugeicons", "Hugeicons (Stroke Rounded)", "MIT License", "hugeicons-MIT.md"),
        Bundled("line-seed-jp", "LINE Seed JP", "SIL Open Font License 1.1", "line-seed-jp-OFL.md"),
        Bundled("openclaw", "OpenClaw", "MIT License", "openclaw-MIT.md"),
    )

    /**
     * 依存関係の情報（POM）に許諾が書かれていないもの。Termux の terminal-view と terminal-emulator は、
     * termux-app の LICENSE.md で Apache License 2.0 と明記されている（アプリ本体の GPLv3 とは別）。
     */
    private val overrides = mapOf(
        "com.github.termux.termux-app:terminal-emulator" to "Apache-2.0",
        "com.github.termux.termux-app:terminal-view" to "Apache-2.0",
    )

    /** 名前の順に並べた一覧。メインスレッドの外で読む。 */
    suspend fun load(context: Context): List<OssEntry> = withContext(Dispatchers.IO) {
        val json = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
        val fromDependencies = parse(Json.parseToJsonElement(json).jsonObject)
        val fromAssets = bundled.mapNotNull { b ->
            val text = runCatching { context.assets.open("licenses/${b.file}").bufferedReader().use { it.readText() } }.getOrNull()
                ?: return@mapNotNull null
            OssEntry(b.id, b.name, null, listOf(OssLicense(b.license, text, null)))
        }
        (fromDependencies + fromAssets).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** `aboutlibraries.json`（`libraries` と、許諾の id ごとの `licenses`）を読む。 */
    internal fun parse(root: JsonObject): List<OssEntry> {
        val licenses = root.obj("licenses").orEmpty()
        fun license(id: String): OssLicense? {
            val obj = licenses[id]?.asObject() ?: return null
            return OssLicense(
                name = obj.string("name")?.takeIf(String::isNotBlank) ?: id,
                text = obj.string("content")?.takeIf(String::isNotBlank),
                url = obj.string("url")?.takeIf(String::isNotBlank),
            )
        }
        return root.array("libraries").mapNotNull { element ->
            val lib = element.asObject() ?: return@mapNotNull null
            val id = lib.string("uniqueId") ?: return@mapNotNull null
            val ids = overrides[id]?.let(::listOf)
                ?: lib.array("licenses").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            OssEntry(
                id = id,
                name = lib.string("name")?.takeIf(String::isNotBlank) ?: id,
                version = lib.string("artifactVersion"),
                licenses = ids.mapNotNull(::license),
            )
        }
    }

    private data class Bundled(val id: String, val name: String, val license: String, val file: String)
}
