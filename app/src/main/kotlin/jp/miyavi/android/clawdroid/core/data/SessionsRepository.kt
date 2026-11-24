package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.int
import jp.miyavi.android.clawdroid.core.gateway.long
import jp.miyavi.android.clawdroid.core.gateway.GatewayRpcException
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

    /**
     * 作業フォルダごとの今のブランチ（`worktrees.branches` の headBranch。Git でないフォルダは入らない）。
     * セッションの一覧には通常のセッションのブランチが無いので、フォルダごとに 1 回だけ問い合わせる（マスター指定 2026-11-24）。
     */
    private val _headBranches = MutableStateFlow<Map<String, String>>(emptyMap())
    val headBranches: StateFlow<Map<String, String>> = _headBranches.asStateFlow()

    /** 作業フォルダごとの未コミットの変更量（`sessions.diff` の合計だけ。変更が無いフォルダは入らない）。 */
    private val _folderDiffs = MutableStateFlow<Map<String, DiffTotals>>(emptyMap())
    val folderDiffs: StateFlow<Map<String, DiffTotals>> = _folderDiffs.asStateFlow()
    /** 最後に問い合わせた時刻（Git でないフォルダも含む。間を空けて問い直す）。 */
    private val branchCheckedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

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
        }.onSuccess { state ->
            _sessions.update { it + (agentId to state) }
            refreshHeadBranches(state.sessions + state.archived + listOfNotNull(state.home))
        }
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
        refreshHeadBranches(listOf(row))
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
    suspend fun setArchived(session: SessionSummary, archived: Boolean) {
        // 先に手元の一覧で動かす（行がもう一方の区切りへ移る動きをすぐ見せる）。失敗したら取り直して戻す
        moveLocally(session, archived)
        try {
            patch(session) {
                put("archived", archived)
                session.sessionId?.let { put("expectedSessionId", it) }
            }
        } catch (e: Exception) {
            session.agentId?.let { refresh(it) }
            throw e
        }
    }

    /** 手元の一覧で、行をアクティブ ⇄ アーカイブ済みへ移す（更新時刻の順に差し込む）。 */
    private fun moveLocally(session: SessionSummary, archived: Boolean) {
        val agentId = session.agentId ?: return
        val moved = session.copy(archived = archived)
        _sessions.update { all ->
            val current = all[agentId] ?: return@update all
            val active = current.sessions.filterNot { it.key == session.key }
            val archivedRows = current.archived.filterNot { it.key == session.key }
            all + (agentId to if (archived) {
                current.copy(sessions = active, archived = (archivedRows + moved).sortedByDescending { it.updatedAt ?: 0 })
            } else {
                current.copy(sessions = (active + moved).sortedByDescending { it.updatedAt ?: 0 }, archived = archivedRows)
            })
        }
    }

    /** 手元の一覧から行を外す。 */
    private fun removeLocally(session: SessionSummary) {
        val agentId = session.agentId ?: return
        _sessions.update { all ->
            val current = all[agentId] ?: return@update all
            all + (agentId to current.copy(
                sessions = current.sessions.filterNot { it.key == session.key },
                archived = current.archived.filterNot { it.key == session.key },
            ))
        }
    }

    /**
     * セッションを削除する（会話の記録も消す）。アーカイブ済みならそのまま、アクティブならアーカイブしてから消す
     * （Gateway は archivedOnly の削除を operator.write に許し、それ以外は operator.admin を求めるため、公式アプリと同じ手順にする）。
     */
    /**
     * セッションを消す。ワークツリーのセッションは、Gateway が未コミットの変更をスナップショットに保存してから
     * ワークツリーを片付ける（ブランチはリポジトリに残る。docs/concepts/managed-worktrees.md）。使用中などで片付けられず
     * 残したときは、その情報を返す（`worktreePreserved`）。
     */
    suspend fun delete(session: SessionSummary): PreservedWorktree? {
        // 先に一覧から外す（行が消える動きをすぐ見せる）。失敗したら取り直して戻す
        removeLocally(session)
        try {
            if (!session.archived) {
                gateway.request("sessions.patch", buildJsonObject {
                    put("key", session.key)
                    session.agentId?.let { put("agentId", it) }
                    put("archived", true)
                    session.sessionId?.let { put("expectedSessionId", it) }
                })
            }
            val result = gateway.request("sessions.delete", buildJsonObject {
                put("key", session.key)
                session.agentId?.let { put("agentId", it) }
                put("deleteTranscript", true)
                put("archivedOnly", true)
                session.sessionId?.let { put("expectedSessionId", it) }
            }).asObject()
            return result?.obj("worktreePreserved")?.let { PreservedWorktree(it.string("branch"), it.string("reason")) }
        } finally {
            session.agentId?.let { refresh(it) }
        }
    }

    suspend fun markRead(session: SessionSummary) = patch(session) { put("unread", false) }

    /** ピン留めする・外す。先に手元の一覧で変え、失敗したら取り直す。 */
    suspend fun setPinned(session: SessionSummary, pinned: Boolean) {
        val agentId = session.agentId
        if (agentId != null) _sessions.update { all ->
            val current = all[agentId] ?: return@update all
            all + (agentId to current.copy(sessions = current.sessions.map { if (it.key == session.key) it.copy(pinned = pinned) else it }))
        }
        try {
            patch(session) {
                put("pinned", pinned)
                session.sessionId?.let { put("expectedSessionId", it) }
            }
        } catch (e: Exception) {
            agentId?.let { refresh(it) }
            throw e
        }
    }

    /** セッションの作業フォルダの変更（Git の差分。ブランチの分と未コミットの分）。集計中なら少し待って取り直す。 */
    suspend fun diff(session: SessionSummary, summaryOnly: Boolean = false): SessionDiff? {
        repeat(DIFF_POLLS) { attempt ->
            val result = gateway.request("sessions.diff", buildJsonObject {
                put("sessionKey", session.key)
                session.agentId?.let { put("agentId", it) }
                put("scope", "all")
                put("poll", true)
                // メニューに合計だけ出すときは、差分の中身を取らない（軽くする）
                if (summaryOnly) put("summaryOnly", true)
            }, timeoutMs = WORKTREE_TIMEOUT_MS).asObject() ?: return null
            if (result.bool("computing") == true && attempt < DIFF_POLLS - 1) {
                kotlinx.coroutines.delay(DIFF_POLL_MS)
                return@repeat
            }
            return SessionDiff(
                branch = result.string("branch"),
                baseRef = result.string("baseRef"),
                aheadCount = result.int("aheadCount"),
                additions = result.int("additions") ?: 0,
                deletions = result.int("deletions") ?: 0,
                files = result.array("files").mapNotNull { it as? JsonObject }.map { f ->
                    DiffFile(
                        path = f.string("path").orEmpty(),
                        oldPath = f.string("oldPath"),
                        status = f.string("status").orEmpty(),
                        additions = f.int("additions") ?: 0,
                        deletions = f.int("deletions") ?: 0,
                        binary = f.bool("binary") == true,
                        patch = f.string("patch"),
                    )
                },
                unavailable = result.string("unavailableReason"),
                truncated = result.bool("truncated") == true,
            )
        }
        return null
    }

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

    /**
     * 既存のセッションの作業フォルダから、ワークツリーのセッションを分けて作る。ブランチは `openclaw/<name>`
     * （名前を省くと Gateway が会話のタイトルから付ける）。[fork] なら会話の中身も引き継ぐ。作られた key を返す。
     */
    suspend fun createWorktree(parent: SessionSummary, agentId: String, name: String?, baseRef: String?, fork: Boolean): String? {
        val cwd = parent.cwd ?: return null
        val wanted = name?.trim()?.takeIf(String::isNotBlank)
        // 同じ名前を何度も使えるよう、予約済み（使用中・復元用のスナップショットが残っている）なら -2, -3… を付ける。
        // 古いスナップショットは消さない（前回の作業を復元できるまま。マスター指定 2026-11-24）
        var candidate = wanted?.let { w -> takenWorktreeNames(parent)?.let { freeWorktreeName(w, it) } ?: w }
        repeat(WORKTREE_NAME_RETRIES) {
            try {
                val result = gateway.request("sessions.create", buildJsonObject {
                    put("agentId", agentId)
                    put("parentSessionKey", parent.key)
                    put("cwd", cwd)
                    put("worktree", true)
                    candidate?.let { put("worktreeName", it) }
                    // 表示名は入れた名前のまま（ブランチが refactor-2 でも、セッションは「refactor」）
                    wanted?.let { put("displayName", it) }
                    baseRef?.takeIf(String::isNotBlank)?.let { put("worktreeBaseRef", it) }
                    if (fork) put("fork", true)
                    // 名前（label）は付けない。Gateway は同じ名前のセッションを断るため、元と同じ名前だと作れない
                }, timeoutMs = WORKTREE_TIMEOUT_MS).asObject()
                refresh(agentId)
                return result?.string("key") ?: result?.string("sessionKey")
            } catch (e: GatewayRpcException) {
                // 台帳を読めなかった・読んだ後に取られたときは、番号を進めて作り直す
                val base = wanted ?: throw e
                if (!e.error.message.contains(WORKTREE_NAME_IN_USE, ignoreCase = true)) throw e
                candidate = nextWorktreeName(base, candidate)
            }
        }
        throw IllegalStateException("no free worktree name for $wanted")
    }

    /**
     * 同じリポジトリで使えないワークツリー名（Gateway の台帳で予約中のもの＋ローカルの `openclaw/<名前>` ブランチ）。
     * 台帳を読めなければ null。
     */
    suspend fun takenWorktreeNames(parent: SessionSummary): Set<String>? {
        val cwd = parent.cwd ?: return null
        val repoRoot = parent.projectFolder ?: cwd.trimEnd('/')
        val reserved = runCatching {
            gateway.request("worktrees.list", JsonObject(emptyMap())).asObject()?.array("worktrees").orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { w -> w.string("repoRoot")?.trimEnd('/') == repoRoot && (w.long("removedAt") == null || w.string("snapshotRef") != null) }
                .mapNotNull { it.string("name") }
                .toSet()
        }.getOrNull() ?: return null
        val branchNames = runCatching { branches(cwd) }.getOrNull()?.branches.orEmpty()
            .filterNot { it.remote }
            .mapNotNull { b -> b.name.removePrefix(WORKTREE_BRANCH_PREFIX).takeIf { it != b.name } }
        return reserved + branchNames
    }

    private fun nextWorktreeName(base: String, current: String?): String {
        val n = current?.removePrefix("$base-")?.toIntOrNull() ?: 1
        return "$base-${n + 1}"
    }

    /**
     * 新しいセッションで選べる作業フォルダの候補（新しく使った順）。これまでのセッションの作業フォルダと、
     * Gateway が見つけたリポジトリのチェックアウトを合わせる。
     */
    suspend fun folderCandidates(agentId: String): List<String> {
        val fromSessions = (_sessions.value[agentId]?.let { it.sessions + it.archived }.orEmpty())
            .sortedByDescending { it.updatedAt ?: 0 }
            .mapNotNull { it.cwd }
        val observed = runCatching {
            gateway.request("projects.list", buildJsonObject { put("includeObserved", true) }).asObject()
        }.getOrNull()
        val fromProjects = observed?.array("observedProjects").orEmpty()
            .mapNotNull { it as? JsonObject }
            .sortedByDescending { it.long("lastUsedAt") ?: 0 }
            .flatMap { project -> project.array("checkouts").mapNotNull { (it as? JsonObject)?.string("path") } }
        val recents = observed?.array("recents").orEmpty().mapNotNull { (it as? JsonObject)?.takeIf { r -> r.string("kind") == "folder" }?.string("folder") }
        // 直近の 5 つまで（マスター指定 2026-11-23）
        return (recents + fromSessions + fromProjects).map { it.trimEnd('/') }.filter(String::isNotBlank).distinct().take(MAX_FOLDER_CANDIDATES)
    }

    /** Gateway のマシンのフォルダの中のフォルダ（新しいセッションのフォルダ選択。`fs.listDir`、operator.admin）。 */
    suspend fun listDir(path: String?): DirListing? {
        val result = gateway.request("fs.listDir", buildJsonObject { path?.let { put("path", it) } }).asObject() ?: return null
        return DirListing(
            path = result.string("path") ?: return null,
            parent = result.string("parent"),
            home = result.string("home"),
            entries = result.array("entries").mapNotNull { it as? JsonObject }.mapNotNull { e ->
                DirEntry(e.string("name") ?: return@mapNotNull null, e.string("path") ?: return@mapNotNull null, e.bool("hidden") == true)
            }.sortedWith(compareBy<DirEntry> { it.hidden }.thenBy { it.name.lowercase() }),
        )
    }

    /**
     * 作業フォルダの今のブランチ（ワークツリーでないもの）と未コミットの変更量を読む。同じフォルダは間を空けて 1 回だけ。
     * 変更量は作業フォルダで決まるので、そのフォルダのセッションを 1 つ選んで問い合わせる。
     */
    fun refreshHeadBranches(rows: List<SessionSummary>) {
        val now = System.currentTimeMillis()
        val byFolder = rows.asSequence()
            .filter { !it.archived }
            .mapNotNull { row -> row.cwd?.trimEnd('/')?.takeIf(String::isNotBlank)?.let { it to row } }
            .distinctBy { it.first }
            .filter { (folder, _) -> now - (branchCheckedAt[folder] ?: 0L) > BRANCH_RECHECK_MS }
            .toList()
        if (byFolder.isEmpty()) return
        byFolder.forEach { (folder, _) -> branchCheckedAt[folder] = now }
        val canBranch = gateway.supports("worktrees.branches")
        val canDiff = gateway.supports("sessions.diff")
        scope.launch {
            byFolder.forEach { (folder, row) ->
                if (canBranch && row.worktreeBranch == null) {
                    val head = runCatching { branches(folder)?.head }.getOrNull()?.takeIf(String::isNotBlank)
                    _headBranches.update { if (head != null) it + (folder to head) else it - folder }
                }
                if (canDiff) {
                    val d = runCatching { diff(row, summaryOnly = true) }.getOrNull()?.takeIf { it.unavailable == null }
                    val totals = d?.let { DiffTotals(it.additions, it.deletions) }?.takeIf { it.additions > 0 || it.deletions > 0 }
                    _folderDiffs.update { if (totals != null) it + (folder to totals) else it - folder }
                }
            }
        }
    }

    /** リポジトリのブランチ（ローカルとリモート）と、今のブランチ・既定のブランチ。[cwd] はリポジトリの中のフォルダ。 */
    suspend fun branches(cwd: String): RepoBranches? {
        val result = gateway.request("worktrees.branches", buildJsonObject { put("repoRoot", cwd) }).asObject() ?: return null
        val branches = result.array("branches").mapNotNull { it as? JsonObject }.mapNotNull { b ->
            RepoBranch(b.string("name") ?: return@mapNotNull null, remote = b.string("kind") == "remote")
        }
        return RepoBranches(branches, head = result.string("headBranch"), default = result.string("defaultBranch"))
    }

    private companion object {
        const val MAX_FOLDER_CANDIDATES = 5
        /** ワークツリーの名前が使用中だったときに番号を進めて作り直す回数。 */
        const val WORKTREE_NAME_RETRIES = 5
        /** Gateway の「名前は使用中」のエラーの文言（details に種類が無いため文言で見分ける）。 */
        const val WORKTREE_NAME_IN_USE = "worktree name is already in use"
        /** 同じフォルダのブランチを問い直すまでの間隔。 */
        const val BRANCH_RECHECK_MS = 60_000L
        const val DIFF_POLLS = 10
        const val DIFF_POLL_MS = 1_000L
        /** ワークツリーを作るのは時間がかかることがある（リポジトリのコピー）。 */
        const val WORKTREE_TIMEOUT_MS = 120_000L
        const val LIST_LIMIT = 100
        /** 1 つの一覧で読むページの上限（100 件 × 20）。 */
        const val MAX_PAGES = 20
    }
}

/** リポジトリのブランチ。 */
data class RepoBranch(val name: String, val remote: Boolean)

/** ブランチの一覧（[branches] が空なら、今のブランチだけ分かっている）。 */
/** 未コミットの変更の合計（サイドパネルの「+12 −3」）。 */
data class DiffTotals(val additions: Int, val deletions: Int)

/** 削除のときに Gateway が片付けずに残したワークツリー。 */
data class PreservedWorktree(val branch: String?, val reason: String?)

data class RepoBranches(val branches: List<RepoBranch>, val head: String?, val default: String?)

/** フォルダの一覧（[parent] はファイルシステムの根では null）。 */
data class DirListing(val path: String, val parent: String?, val home: String?, val entries: List<DirEntry>)
data class DirEntry(val name: String, val path: String, val hidden: Boolean)

/** セッションの変更（`sessions.diff`）。[unavailable] は Git でない・止まっているなどの理由。 */
data class SessionDiff(
    val branch: String?,
    val baseRef: String?,
    val aheadCount: Int?,
    val additions: Int,
    val deletions: Int,
    val files: List<DiffFile>,
    val unavailable: String?,
    val truncated: Boolean,
)

data class DiffFile(
    val path: String,
    val oldPath: String?,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val binary: Boolean,
    val patch: String?,
)

/** Gateway がワークツリーのブランチに付ける頭（固定の名前空間）。 */
internal const val WORKTREE_BRANCH_PREFIX = "openclaw/"

/** [taken] を避けた名前。[wanted] が空いていればそのまま、使われていれば -2, -3… と番号を付ける。 */
internal fun freeWorktreeName(wanted: String, taken: Set<String>): String {
    if (wanted !in taken) return wanted
    var n = 2
    while ("$wanted-$n" in taken) n++
    return "$wanted-$n"
}
