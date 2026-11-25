package jp.miyavi.android.clawdroid.core.licenses

import jp.miyavi.android.clawdroid.feature.settings.privacyPolicyUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ビルドで作るライセンスの一覧（aboutlibraries.json）の読み取り（spec 003 FR-033・034）。 */
class OssLicensesTest {
    private val json = Json.parseToJsonElement(
        """
        {"libraries":[
          {"uniqueId":"androidx.activity:activity","artifactVersion":"1.13.0","name":"Activity","licenses":["Apache-2.0"]},
          {"uniqueId":"com.github.termux.termux-app:terminal-view","artifactVersion":"v0.118.3","name":"terminal-view","licenses":["c6dd"]},
          {"uniqueId":"x:dual","name":"Dual","licenses":["MIT","Apache-2.0"]}
        ],
        "licenses":{
          "Apache-2.0":{"name":"Apache License 2.0","url":"https://spdx.org/licenses/Apache-2.0.html","content":"Apache License\nVersion 2.0"},
          "MIT":{"name":"MIT License","content":"MIT text"},
          "c6dd":{"name":"Other","url":""}
        }}
        """.trimIndent(),
    ).jsonObject

    @Test fun readsNameVersionAndLicenseText() {
        val entries = OssLicenses.parse(json).associateBy { it.id }
        val activity = entries.getValue("androidx.activity:activity")
        assertEquals("Activity", activity.name)
        assertEquals("1.13.0", activity.version)
        assertEquals("Apache License 2.0", activity.licenseNames)
        assertTrue(activity.licenses.single().text!!.startsWith("Apache License"))
        assertEquals("MIT License / Apache License 2.0", entries.getValue("x:dual").licenseNames)
    }

    @Test fun termuxWithoutLicenseInPom_isApache() {
        val termux = OssLicenses.parse(json).single { it.id.endsWith("terminal-view") }
        assertEquals("Apache License 2.0", termux.licenseNames)
    }

    @Test fun privacyPolicy_opensTheSectionForTheLanguage() {
        assertTrue(privacyPolicyUrl("ja").endsWith("docs/privacy-policy.md#日本語"))
        assertTrue(privacyPolicyUrl("en").endsWith("#english"))
        assertTrue(privacyPolicyUrl("fr").endsWith("#english"))
    }
}
