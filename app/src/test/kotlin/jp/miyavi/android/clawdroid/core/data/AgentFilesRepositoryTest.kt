package jp.miyavi.android.clawdroid.core.data

import java.util.concurrent.TimeUnit
import jp.miyavi.android.clawdroid.core.gateway.ClientInfo
import jp.miyavi.android.clawdroid.core.gateway.DeviceIdentityRepository
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.gateway.bool
import jp.miyavi.android.clawdroid.core.gateway.string
import jp.miyavi.android.clawdroid.testing.FakeGateway
import jp.miyavi.android.clawdroid.testing.InMemorySecretStore
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

/** エージェントのファイルの一覧・開く・保存・作成・衝突（spec 004 US1、contracts/gateway-rpc.md）。 */
class AgentFilesRepositoryTest {
    private val gateway = FakeGateway()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var client: GatewayClient
    private lateinit var repo: FilesRepository
    private val root = FileRoot.AgentFile("a")
    private val hash = "a".repeat(64)

    @Before fun setUp() {
        gateway.handlers["agents.files.list"] = {
            buildJsonObject {
                put("agentId", "a"); put("workspace", "/secret/ws")
                put("files", buildJsonArray {
                    add(buildJsonObject { put("name", "USER.md"); put("path", "/secret/ws/USER.md"); put("missing", true); put("expectedAbsent", true) })
                    add(buildJsonObject { put("name", "NOTES.md"); put("path", "/secret/ws/NOTES.md"); put("missing", false); put("size", 3) })
                    add(buildJsonObject { put("name", "SOUL.md"); put("path", "/secret/ws/SOUL.md"); put("missing", false); put("size", 10); put("updatedAtMs", 5) })
                    add(buildJsonObject { put("name", "AGENTS.md"); put("path", "/secret/ws/AGENTS.md"); put("missing", false); put("size", 20) })
                })
            }
        }
        gateway.handlers["agents.files.get"] = { params ->
            val name = params?.string("name")
            buildJsonObject {
                put("agentId", "a"); put("workspace", "/secret/ws")
                put("file", buildJsonObject {
                    put("name", name); put("path", "/secret/ws/$name")
                    if (name == "USER.md") {
                        put("missing", true)
                    } else {
                        put("missing", false); put("size", 9); put("hash", hash); put("content", "# Agents")
                    }
                })
            }
        }
        gateway.handlers["agents.files.set"] = { params ->
            if (params?.string("expectedHash") == "stale") {
                throw FakeGateway.RpcError("INVALID_REQUEST", buildJsonObject { put("type", "agent_file_conflict"); put("currentHash", hash) })
            }
            buildJsonObject {
                put("ok", true); put("agentId", "a"); put("workspace", "/secret/ws")
                put("file", buildJsonObject {
                    put("name", params?.string("name")); put("path", "x"); put("missing", false)
                    put("hash", "c".repeat(64)); put("content", params?.string("content"))
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

    @Test fun list_existingFirst_inRoleOrder_unknownLast() {
        val files = await { repo.agentFiles("a") }
        assertEquals(listOf("AGENTS.md", "SOUL.md", "NOTES.md", "USER.md"), files.map { it.name })
        assertTrue(files.last().missing && files.last().expectedAbsent)
        assertEquals(AgentFileRole.INSTRUCTIONS, files.first().role)
        assertNull(files[2].role)
    }

    @Test fun open_existingIsEditable_missingOpensForCreating() {
        val existing = (await { repo.open(root, "AGENTS.md") } as OpenResult.Opened).file
        assertEquals("# Agents", existing.text)
        assertEquals("/secret/ws", existing.workspace)
        assertTrue(existing.editable)
        assertFalse(existing.creating)
        val missing = (await { repo.open(root, "USER.md") } as OpenResult.Opened).file
        assertTrue(missing.creating)
        assertEquals("", missing.text)
        assertNull(missing.hash)
        assertTrue(missing.editable)
    }

    @Test fun save_existingSendsHashAndWorkspace_creatingSendsExpectedMissing() {
        val existing = (await { repo.open(root, "AGENTS.md") } as OpenResult.Opened).file
        val saved = await { repo.save(existing, "# New", hash) } as SaveResult.Saved
        assertEquals("c".repeat(64), saved.file.hash)
        val params = gateway.requests.last { it.first == "agents.files.set" }.second
        assertEquals(hash, params?.string("expectedHash"))
        assertEquals("/secret/ws", params?.string("expectedWorkspace"))
        assertNull(params?.get("expectedMissing"))

        val missing = (await { repo.open(root, "USER.md") } as OpenResult.Opened).file
        await { repo.save(missing, "# Me", "") } as SaveResult.Saved
        val create = gateway.requests.last { it.first == "agents.files.set" }.second
        assertEquals(true, create?.bool("expectedMissing"))
        assertNull(create?.get("expectedHash"))
    }

    @Test fun save_conflictIsReported() {
        val existing = (await { repo.open(root, "AGENTS.md") } as OpenResult.Opened).file
        assertEquals(SaveResult.Conflict, await { repo.save(existing, "# New", "stale") })
    }

    @Test fun tooLarge_isReadOnly() {
        val big = OpenedFile(root, "AGENTS.md", "AGENTS.md", AGENT_FILE_MAX_BYTES + 1, null, PreviewKind.TEXT, "x", null, hash, null)
        assertFalse(big.editable)
        assertTrue(big.tooLargeToEdit)
    }
}
