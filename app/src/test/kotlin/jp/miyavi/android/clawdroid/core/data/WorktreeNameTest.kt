package jp.miyavi.android.clawdroid.core.data

import java.util.concurrent.TimeUnit
import jp.miyavi.android.clawdroid.core.gateway.ClientInfo
import jp.miyavi.android.clawdroid.core.gateway.DeviceIdentityRepository
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
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
import org.junit.Before
import org.junit.Test

/** 並行開発で同じ名前を何度も使えること（予約済みなら -2, -3…。マスター指定 2026-11-24）。 */
class WorktreeNameTest {
    private val gateway = FakeGateway()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: SessionsRepository
    private val parent = SessionSummary(
        key = "agent:a:main", agentId = "a", sessionId = null, title = "main", cwd = "/w/app", model = null, thinkingLevel = null,
        running = false, unread = false, archived = false, isAutomation = false, updatedAt = 1,
        contextTokens = null, totalTokens = null, preview = null,
    )
    /** sessions.create を断る名前。 */
    private val refuse = mutableSetOf<String>()

    @Before fun setUp() {
        gateway.handlers["worktrees.list"] = {
            buildJsonObject {
                put("worktrees", buildJsonArray {
                    // 片付け済みでも復元用のスナップショットが残っていれば予約済み
                    add(buildJsonObject { put("name", "refactor"); put("repoRoot", "/w/app"); put("removedAt", 5); put("snapshotRef", "refs/x") })
                    // スナップショットも無く片付け済みなら空いている
                    add(buildJsonObject { put("name", "old"); put("repoRoot", "/w/app"); put("removedAt", 5) })
                    // ほかのリポジトリは関係ない
                    add(buildJsonObject { put("name", "fix"); put("repoRoot", "/w/other") })
                })
            }
        }
        gateway.handlers["sessions.create"] = { params ->
            val name = params?.string("worktreeName")
            if (name != null && name in refuse) {
                throw FakeGateway.RpcError("UNAVAILABLE", null, "worktree name is already in use by session x: $name; choose an unused name")
            }
            buildJsonObject { put("key", "agent:a:new") }
        }
        gateway.handlers["sessions.list"] = { buildJsonObject { put("sessions", buildJsonArray { }) } }
        gateway.start()
        gateway.enqueue(FakeGateway.ConnectBehavior.Accept())
        val secrets = InMemorySecretStore()
        val client = GatewayClient(
            scope = scope,
            httpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
            identities = DeviceIdentityRepository(secrets),
            secrets = secrets,
            clientInfo = ClientInfo(version = "test", modelIdentifier = null, displayName = null),
        )
        repo = SessionsRepository(client, scope)
        runBlocking { withTimeout(10_000) { client.connect(gateway.url, "t"); client.awaitConnected() } }
    }

    @After fun tearDown() {
        scope.cancel()
        gateway.shutdown()
    }

    private fun lastCreate() = gateway.requests.last { it.first == "sessions.create" }.second

    @Test fun reservedName_getsNumber_displayNameKeepsWanted() {
        runBlocking { withTimeout(10_000) { repo.createWorktree(parent, "a", "refactor", null, fork = false) } }
        assertEquals("refactor-2", lastCreate()?.string("worktreeName"))
        assertEquals("refactor", lastCreate()?.string("displayName"))
    }

    @Test fun freeName_isUsedAsIs() {
        runBlocking { withTimeout(10_000) { repo.createWorktree(parent, "a", "old", null, fork = false) } }
        assertEquals("old", lastCreate()?.string("worktreeName"))
        runBlocking { withTimeout(10_000) { repo.createWorktree(parent, "a", "fix", null, fork = false) } }
        assertEquals("fix", lastCreate()?.string("worktreeName"))
    }

    @Test fun refusedByGateway_retriesWithNextNumber() {
        refuse += "refactor-2"
        runBlocking { withTimeout(10_000) { repo.createWorktree(parent, "a", "refactor", null, fork = false) } }
        assertEquals("refactor-3", lastCreate()?.string("worktreeName"))
    }

    @Test fun freeName_skipsTakenNumbers() {
        assertEquals("fix", freeWorktreeName("fix", setOf("refactor")))
        assertEquals("refactor-3", freeWorktreeName("refactor", setOf("refactor", "refactor-2")))
    }
}
