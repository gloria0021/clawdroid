package jp.miyavi.android.sumahoclaw.core.data

import jp.miyavi.android.sumahoclaw.core.gateway.ConnectionState
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import jp.miyavi.android.sumahoclaw.core.gateway.long
import jp.miyavi.android.sumahoclaw.core.gateway.string
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal enum class ApprovalKind(val rpc: String) { EXEC("exec.approval"), PLUGIN("plugin.approval") }
internal enum class ApprovalDecision(val wire: String) { DENY("deny"), ALWAYS("allow-always"), ONCE("allow-once") }
internal data class ApprovalKey(val kind: ApprovalKind, val id: String)
internal data class PendingApproval(
    val key: ApprovalKey,
    val detail: String,
    val warning: String?,
    val agentId: String?,
    val sessionKey: String?,
    val target: List<String>,
    val createdAt: Long,
    val expiresAt: Long,
    val decisions: Set<ApprovalDecision>,
)

internal fun parsePendingApproval(kind: ApprovalKind, payload: JsonElement?): PendingApproval? {
    val row = payload as? JsonObject ?: return null
    val request = row["request"] as? JsonObject ?: return null
    val id = row.string("id")?.takeIf(String::isNotBlank) ?: return null
    val detail = request.string(if (kind == ApprovalKind.EXEC) "command" else "title")
        ?.takeIf(String::isNotBlank) ?: return null
    val created = row.long("createdAtMs") ?: return null
    val expires = row.long("expiresAtMs")?.takeIf { it > created } ?: return null
    val decisions = when (val advertised = request["allowedDecisions"]) {
        null -> setOf(ApprovalDecision.DENY, ApprovalDecision.ONCE)
        is JsonArray -> ApprovalDecision.entries.filterTo(mutableSetOf()) { JsonPrimitive(it.wire) in advertised }
        else -> emptySet()
    }
    return PendingApproval(
        ApprovalKey(kind, id), detail,
        request.string(if (kind == ApprovalKind.EXEC) "warningText" else "description"),
        request.string("agentId"), request.string("sessionKey"),
        listOfNotNull(request.string("host"), request.string("nodeId"), request.string("cwd"), request.string("resolvedPath"), request.string("pluginId")).distinct(),
        created, expires, decisions,
    )
}

/** App-wide, so an approval cannot be hidden by a drawer, settings, or an unrelated chat. */
internal class ApprovalsRepository(private val gateway: GatewayClient, scope: CoroutineScope) {
    private val pending = MutableStateFlow<List<PendingApproval>>(emptyList())
    val requests = pending.asStateFlow()
    private var connection: ConnectionState.Connected? = null
    // These counters and the queue are owned by Main. A broadcast wins over stale replay.
    private val revision = mutableMapOf<ApprovalKind, Int>()

    init {
        scope.launch(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
            gateway.events.collect { event ->
                if (connection == null || gateway.state.value !== connection) return@collect
                val kind = ApprovalKind.entries.firstOrNull { event.name == "${it.rpc}.requested" || event.name == "${it.rpc}.resolved" }
                    ?: return@collect
                revision[kind] = (revision[kind] ?: 0) + 1
                val row = event.payload as? JsonObject ?: return@collect
                if (event.name.endsWith(".resolved")) {
                    val id = row.string("id") ?: return@collect
                    replace(pending.value.filterNot { it.key == ApprovalKey(kind, id) })
                } else {
                    val item = parsePendingApproval(kind, row) ?: return@collect
                    replace(pending.value.filterNot { it.key == item.key } + item)
                }
            }
        }
        scope.launch(Dispatchers.Main.immediate) {
            gateway.state.collectLatest { state ->
                connection = (state as? ConnectionState.Connected)?.takeIf {
                    "operator.approvals" in it.scopes || "operator.admin" in it.scopes
                }
                replace(emptyList())
                val active = connection ?: return@collectLatest
                for (kind in ApprovalKind.entries) replay(active, kind)
            }
        }
        scope.launch(Dispatchers.Main.immediate) {
            pending.collectLatest { list ->
                val expires = list.minOfOrNull { it.expiresAt } ?: return@collectLatest
                delay((expires - System.currentTimeMillis()).coerceAtLeast(0))
                replace(pending.value)
            }
        }
    }

    private fun replace(items: List<PendingApproval>) {
        val now = System.currentTimeMillis()
        pending.value = items.filter { it.expiresAt > now }.sortedBy { it.createdAt }
    }

    private suspend fun replay(active: ConnectionState.Connected, kind: ApprovalKind) {
        val method = "${kind.rpc}.list"
        if (method !in active.methods) return
        repeat(3) {
            val snapshot = revision[kind]
            val rows = try {
                gateway.request(method, buildJsonObject {}, timeoutMs = 5_000) as? JsonArray ?: return
            } catch (_: TimeoutCancellationException) {
                return
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                return
            }
            if (gateway.state.value !== active) return
            if (snapshot == revision[kind]) {
                replace(pending.value.filterNot { it.key.kind == kind } + rows.mapNotNull { parsePendingApproval(kind, it) }.distinctBy { it.key })
                return
            }
        }
    }

    /** The only write is the operator's explicit decision on this still-pending approval. */
    suspend fun resolve(item: PendingApproval, decision: ApprovalDecision) {
        val active = checkNotNull(connection)
        val method = "${item.key.kind.rpc}.resolve"
        check(gateway.state.value === active && method in active.methods)
        check(item in pending.value && item.expiresAt > System.currentTimeMillis() && decision in item.decisions)
        gateway.request(method, buildJsonObject {
            put("id", item.key.id)
            put("decision", decision.wire)
        })
        if (gateway.state.value === active) {
            revision[item.key.kind] = (revision[item.key.kind] ?: 0) + 1
            replace(pending.value.filterNot { it.key == item.key })
        }
    }
}
