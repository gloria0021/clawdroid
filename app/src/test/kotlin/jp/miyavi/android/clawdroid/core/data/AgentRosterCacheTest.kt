package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.obj
import jp.miyavi.android.clawdroid.core.gateway.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI
import java.util.Base64

class AgentRosterCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)

    private fun roster(avatar: String?) = buildJsonObject {
        put("defaultId", "a")
        put("agents", buildJsonArray {
            add(buildJsonObject {
                put("id", "a")
                put("identity", buildJsonObject {
                    put("name", "A")
                    avatar?.let { put("avatarUrl", it) }
                })
            })
            add(buildJsonObject { put("id", "b") })
        })
    }

    private fun avatarOf(root: JsonObject, index: Int = 0): String? =
        (root["agents"] as kotlinx.serialization.json.JsonArray)[index].let { it as JsonObject }.obj("identity")?.string("avatarUrl")

    @Test fun store_writesAvatarFile_andKeepsOnlyItsLocation() {
        val cache = AgentRosterCache(tmp.root)
        val dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png)
        val localized = cache.store("wss://gw", 100L, "h1", roster(dataUri))

        val url = avatarOf(localized)!!
        assertTrue(url.startsWith("file:"))
        assertArrayEquals(png, File(URI(url)).readBytes())
        assertEquals("b", ((localized["agents"] as kotlinx.serialization.json.JsonArray)[1] as JsonObject).string("id"))

        val loaded = assertNotNullAndGet(cache.load())
        assertEquals("wss://gw", loaded.endpoint)
        assertEquals(100L, loaded.fetchedAt)
        assertEquals("h1", loaded.configHash)
        assertEquals(url, avatarOf(loaded.root))
        assertTrue(loaded.root.toString().length < dataUri.length + 200)
    }

    @Test fun changedAvatar_replacesFile_andRemovesOld() {
        val cache = AgentRosterCache(tmp.root)
        val first = avatarOf(cache.store("wss://gw", 1L, "h1", roster("data:image/png;base64," + Base64.getEncoder().encodeToString(png))))!!
        val second = avatarOf(cache.store("wss://gw", 2L, "h1", roster("data:image/webp;base64," + Base64.getEncoder().encodeToString(byteArrayOf(9, 9)))))!!

        assertTrue(first != second)
        assertTrue(second.endsWith(".webp"))
        assertTrue(!File(URI(first)).exists())
        assertTrue(File(URI(second)).isFile)
    }

    @Test fun missingAvatarFile_invalidatesSnapshot() {
        val cache = AgentRosterCache(tmp.root)
        val url = avatarOf(cache.store("wss://gw", 1L, "h1", roster("data:image/png;base64," + Base64.getEncoder().encodeToString(png))))!!
        File(URI(url)).delete()
        assertNull(cache.load())
    }

    @Test fun nonDataAvatar_isKeptAsIs() {
        val cache = AgentRosterCache(tmp.root)
        val localized = cache.store("wss://gw", 1L, "h1", roster("https://example.com/a.png"))
        assertEquals("https://example.com/a.png", avatarOf(localized))
        assertEquals("https://example.com/a.png", avatarOf(cache.load()!!.root))
    }

    @Test fun noFile_loadsNothing() {
        assertNull(AgentRosterCache(tmp.root).load())
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
