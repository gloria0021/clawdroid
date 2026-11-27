package jp.miyavi.android.sumahoclaw.core.data

import java.util.concurrent.TimeUnit
import jp.miyavi.android.sumahoclaw.core.gateway.ClientInfo
import jp.miyavi.android.sumahoclaw.core.gateway.DeviceIdentityRepository
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import jp.miyavi.android.sumahoclaw.core.gateway.long
import jp.miyavi.android.sumahoclaw.core.gateway.string
import jp.miyavi.android.sumahoclaw.testing.FakeGateway
import jp.miyavi.android.sumahoclaw.testing.InMemorySecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** ファイルの一覧・開く・保存（spec 002、contracts/gateway-rpc.md）。 */
class FilesRepositoryTest {
    private val gateway = FakeGateway()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var client: GatewayClient
    private lateinit var repo: FilesRepository
    private val session = FileRoot.Session("agent:a:s1", "a")
    private val hash = "a".repeat(64)

    @Before fun setUp() {
        gateway.handlers["sessions.files.list"] = {
            buildJsonObject {
                put("sessionKey", "agent:a:s1")
                put("browser", buildJsonObject {
                    put("path", "src")
                    put("parentPath", "")
                    put("entries", buildJsonArray {
                        add(buildJsonObject { put("path", "src/file10.kt"); put("name", "file10.kt"); put("kind", "file"); put("size", 10) })
                        add(buildJsonObject { put("path", "src/file2.kt"); put("name", "file2.kt"); put("kind", "file") })
                        add(buildJsonObject { put("path", "src/lib"); put("name", "lib"); put("kind", "directory") })
                    })
                })
            }
        }
        gateway.handlers["sessions.files.get"] = { params ->
            when (params?.string("path")) {
                "big.bin" -> throw FakeGateway.RpcError("INVALID_REQUEST", buildJsonObject { put("type", "session_file_too_large"); put("size", 999_999) })
                "gone.txt" -> throw FakeGateway.RpcError("INVALID_REQUEST", buildJsonObject { put("type", "session_file_not_found") })
                else -> buildJsonObject {
                    put("sessionKey", "agent:a:s1")
                    put("source", buildJsonObject { put("workspaceId", "w1"); put("root", "/r"); put("fileRoot", "/r") })
                    put("file", buildJsonObject {
                        put("path", "src/a.kt"); put("name", "a.kt"); put("kind", "read"); put("missing", false)
                        put("content", "val x = 1"); put("contentEncoding", "utf8"); put("previewKind", "text"); put("hash", hash)
                    })
                }
            }
        }
        gateway.handlers["sessions.files.set"] = { params ->
            if (params?.string("expectedHash") != hash) {
                throw FakeGateway.RpcError("INVALID_REQUEST", buildJsonObject { put("type", "session_file_conflict"); put("currentHash", "b".repeat(64)) })
            }
            buildJsonObject {
                put("sessionKey", "agent:a:s1")
                put("file", buildJsonObject {
                    put("path", "src/a.kt"); put("name", "a.kt"); put("kind", "modified"); put("missing", false)
                    put("content", params.string("content")); put("contentEncoding", "utf8"); put("previewKind", "text"); put("hash", "c".repeat(64))
                })
            }
        }
        gateway.handlers["agents.workspace.list"] = { params ->
            val offset = params?.long("offset") ?: 0
            buildJsonObject {
                put("agentId", "a"); put("path", ""); put("totalEntries", 3); put("offset", offset)
                put("entries", buildJsonArray {
                    if (offset == 0L) {
                        add(buildJsonObject { put("path", "b.md"); put("name", "b.md"); put("kind", "file") })
                        add(buildJsonObject { put("path", "A.md"); put("name", "A.md"); put("kind", "file") })
                    } else if (offset == 2L) {
                        add(buildJsonObject { put("path", "docs"); put("name", "docs"); put("kind", "directory") })
                    }
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
        repo = FilesRepository(client)
        await { client.connect(gateway.url, "t"); client.awaitConnected() }
    }

    @After fun tearDown() {
        scope.cancel()
        gateway.shutdown()
    }

    private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

    @Test fun sessionList_foldersFirst_naturalOrder() {
        val listing = await { repo.list(session, "src") }
        assertEquals(listOf("lib", "file2.kt", "file10.kt"), listing.entries.map { it.name })
        assertTrue(listing.entries.first().isDirectory)
        assertEquals("", listing.parentPath)
    }

    @Test fun workspaceList_readsAllPages() {
        val listing = await { repo.list(FileRoot.Workspace("a"), "") }
        assertEquals(listOf("docs", "A.md", "b.md"), listing.entries.map { it.name })
    }

    @Test fun open_textIsEditable_tooLargeAndMissing() {
        val opened = (await { repo.open(session, "src/a.kt") } as OpenResult.Opened).file
        assertEquals("val x = 1", opened.text)
        assertTrue(opened.editable)
        val big = (await { repo.open(session, "big.bin") } as OpenResult.Opened).file
        assertEquals(PreviewKind.UNSUPPORTED, big.kind)
        assertEquals(999_999L, big.size)
        assertFalse(big.editable)
        assertEquals(OpenResult.Missing, await { repo.open(session, "gone.txt") })
    }

    @Test fun save_sendsExpectedHashAndSource_conflictIsReported() {
        val opened = (await { repo.open(session, "src/a.kt") } as OpenResult.Opened).file
        val saved = await { repo.save(opened, "val x = 2") } as SaveResult.Saved
        assertEquals("c".repeat(64), saved.file.hash)
        val sent = gateway.requests.last { it.first == "sessions.files.set" }.second!!
        assertEquals(hash, sent.string("expectedHash"))
        assertEquals("w1", (sent["expectedSource"] as kotlinx.serialization.json.JsonObject).string("workspaceId"))
        assertEquals(SaveResult.Conflict, await { repo.save(opened, "val x = 3", expectedHash = "d".repeat(64)) })
    }

    @Test fun naturalCompare_numbersAsNumbers() {
        assertTrue(naturalCompare("file2", "file10") < 0)
        assertTrue(naturalCompare("B", "a") > 0)
        assertNull(null)
    }
}
