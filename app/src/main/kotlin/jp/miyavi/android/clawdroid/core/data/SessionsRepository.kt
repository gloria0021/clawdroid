package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.long
import jp.miyavi.android.clawdroid.core.gateway.bool
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.gateway.array
import jp.miyavi.android.clawdroid.core.gateway.asObject
import jp.miyavi.android.clawdroid.core.gateway.obj
import jp.miyavi.android.clawdroid.core.gateway.string
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** エージェントの一覧（US1）。 */
data class AgentsState(
    val agents: List<Agent> = emptyList(),
    val defaultId: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/** 1 エージェントぶんのセッション一覧。 */
data class SessionListState(
    /** アクティブなセッション（ホームを除く）。 */
    val sessions: List<SessionSummary> = emptyList(),
    /** アーカイブ済みのセッション。 */
    val archived: List<SessionSummary> = emptyList(),
    /** エージェントのホーム（main）。 */
    val home: SessionSummary? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * エージェントとセッションの一覧を Gateway から取り、変更イベント（`sessions.changed`）で更新する。
 * 一覧はエージェントごとに保持し、タブの切り替えで作り直さない（FR-011）。
 */
class SessionsRepository(
    private val gateway: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val _agents = MutableStateFlow(AgentsState())
    val agents: StateFlow<AgentsState> = _agents.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, SessionListState>>(emptyMap())
    val sessions: StateFlow<Map<String, SessionListState>> = _sessions.asStateFlow()

    init {
        // 接続（再接続を含む）のたびに取り直す
        scope.launch {
            var wasConnected = false
            gateway.state.collect { state ->
                val connected = state is ConnectionState.Connected
                if (connected && !wasConnected) onConnected()
                wasConnected = connected
            }
        }
        scope.launch {
            gateway.events.collect { event ->
                when (event.name) {
                    "sessions.changed" -> onSessionsChanged(event.payload.asObject())
                    "config.changed" -> refreshAgents()
                }
            }
        }
    }

    private suspend fun onConnected() {
        refreshAgents()
        // 変更イベントの購読（空の params は購読の確認だけを返す）
        runCatching { gateway.request("sessions.subscribe", JsonObject(emptyMap())) }
        _sessions.value.keys.forEach { refresh(it) }
    }

    suspend fun refreshAgents() {
        _agents.update { it.copy(loading = true, error = null) }
        runCatching { gateway.request("agents.list", JsonObject(emptyMap())).asObject() }
            .onSuccess { root ->
                val agents = root?.array("agents")?.mapNotNull { Parse.agent(it as? JsonObject ?: return@mapNotNull null) }
                    ?.filterNot { it.isSystem }.orEmpty()
                _agents.value = AgentsState(agents, root?.string("defaultId"), loading = false)
            }
            .onFailure { e -> _agents.update { it.copy(loading = false, error = e.message) } }
    }

    /** そのエージェントの一覧を（まだなら）読み込む。 */
    fun ensureLoaded(agentId: String) {
        if (_sessions.value[agentId]?.loading == false) return
        scope.launch { refresh(agentId) }
    }

    suspend fun refresh(agentId: String) {
        _sessions.update { it + (agentId to (it[agentId] ?: SessionListState()).copy(loading = true, error = null)) }
        runCatching {
            // アクティブとアーカイブ済みは Gateway 側で別の一覧（archived の true / false）。どちらもページを最後まで読む
            val active = fetchAll(agentId, archived = false)
            val archived = fetchAll(agentId, archived = true)
            SessionListState(
                sessions = active.filterNot { it.isMain },
                archived = archived.filterNot { it.isMain },
                home = active.firstOrNull { it.isMain } ?: archived.firstOrNull { it.isMain },
                loading = false,
            )
        }.onSuccess { state -> _sessions.update { it + (agentId to state) } }
            .onFailure { e ->
                _sessions.update { it + (agentId to (it[agentId] ?: SessionListState()).copy(loading = false, error = e.message)) }
            }
    }

    private suspend fun fetchAll(agentId: String, archived: Boolean): List<SessionSummary> {
        val rows = mutableListOf<SessionSummary>()
        var offset = 0
        repeat(MAX_PAGES) {
            val root = gateway.request("sessions.list", listParams(agentId, archived, offset)).asObject()
            val page = Parse.sessionsArray(root)
            rows += page
            val more = root?.bool("hasMore") == true && page.isNotEmpty()
            if (!more) return rows
            offset = root?.long("nextOffset")?.toInt() ?: (offset + page.size)
        }
        return rows
    }

    private fun listParams(agentId: String, archived: Boolean, offset: Int) = buildJsonObject {
        put("agentId", agentId)
        if (archived) put("archived", true)
        if (offset > 0) put("offset", offset)
        put("includeGlobal", false)
        put("includeUnknown", false)
        put("excludeSubagents", true)
        // 自動化の会話は通常の一覧から分ける（FR-117）
        put("excludeCron", true)
        put("excludeDock", true)
        put("includeDerivedTitles", true)
        put("includeLastMessage", true)
        put("sortBy", "activity")
        put("limit", LIST_LIMIT)
    }

    private fun onSessionsChanged(payload: JsonObject?) {
        val row = Parse.session(payload?.obj("session"))
        if (row == null) {
            // 行の無い通知は、読み込み済みの一覧を取り直す
            scope.launch { _sessions.value.keys.forEach { refresh(it) } }
            return
        }
        val agentId = row.agentId ?: payload?.string("agentId") ?: return
        if (row.isAutomation) return
        _sessions.update { all ->
            val current = all[agentId] ?: return@update all
            if (row.isMain) return@update all + (agentId to current.copy(home = row))
            val active = current.sessions.filterNot { it.key == row.key }
            val archived = current.archived.filterNot { it.key == row.key }
            val next = if (row.archived) {
                current.copy(sessions = active, archived = (listOf(row) + archived).sortedByDescending { it.updatedAt ?: 0 })
            } else {
                current.copy(sessions = (listOf(row) + active).sortedByDescending { it.updatedAt ?: 0 }, archived = archived)
            }
            all + (agentId to next)
        }
    }

    /** 一覧の中から探す（会話の画面のヘッダ用）。 */
    fun find(sessionKey: String): SessionSummary? =
        _sessions.value.values.firstNotNullOfOrNull { list ->
            list.home?.takeIf { it.key == sessionKey } ?: (list.sessions + list.archived).firstOrNull { it.key == sessionKey }
        }

    suspend fun rename(session: SessionSummary, label: String) = patch(session) { put("label", label) }
    /** アーカイブ・解除は「セッションの寿命」の変更で、Gateway は expectedSessionId を必須にしている（別の実体を誤って動かさないため）。 */
    suspend fun setArchived(session: SessionSummary, archived: Boolean) = patch(session) {
        put("archived", archived)
        session.sessionId?.let { put("expectedSessionId", it) }
    }
    suspend fun markRead(session: SessionSummary) = patch(session) { put("unread", false) }

    suspend fun setOption(key: String, agentId: String, setting: SessionSetting, value: String?) =
        patch(key, agentId) {
            if (value == null) put(setting.wireKey, JsonNull) else put(setting.wireKey, value)
        }

    private suspend fun patch(session: SessionSummary, fields: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
        patch(session.key, session.agentId, fields)

    private suspend fun patch(key: String, agentId: String?, fields: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        gateway.request("sessions.patch", buildJsonObject {
            put("key", key)
            agentId?.let { put("agentId", it) }
            fields()
        })
        agentId?.let { refresh(it) }
    }

    /** 新しいセッションを作る（US3）。最初のメッセージを一緒に送れる。作られた key を返す。 */
    suspend fun create(
        agentId: String,
        cwd: String?,
        message: String?,
        idempotencyKey: String,
        options: Map<SessionSetting, String> = emptyMap(),
    ): String? {
        val result = gateway.request("sessions.create", buildJsonObject {
            put("agentId", agentId)
            put("idempotencyKey", idempotencyKey)
            cwd?.let { put("cwd", it) }
            message?.let { put("message", it) }
            options.forEach { (setting, value) -> put(setting.wireKey, value) }
        }).asObject()
        refresh(agentId)
        return result?.string("key") ?: result?.string("sessionKey") ?: result?.obj("session")?.string("key")
    }

    private companion object {
        const val LIST_LIMIT = 100
        /** 1 つの一覧で読むページの上限（100 件 × 20）。 */
        const val MAX_PAGES = 20
    }
}
