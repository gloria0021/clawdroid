package jp.miyavi.android.clawdroid.core.data

import android.content.SharedPreferences
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.gateway.GatewayRpcException
import jp.miyavi.android.clawdroid.core.gateway.array
import jp.miyavi.android.clawdroid.core.gateway.asObject
import jp.miyavi.android.clawdroid.core.gateway.long
import jp.miyavi.android.clawdroid.core.gateway.string
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Gateway の PTY（open / attach の結果）。 */
data class TerminalInfo(
    val sessionId: String,
    val cwd: String,
    val shell: String,
    val confined: Boolean,
    /** 開いた直後に作業フォルダへ移った（そのあとシェルが画面を消す）。 */
    val changedDirectory: Boolean = false,
)

/** つなぎ直したときの続き（離れていたあいだの出力を含む）。 */
data class TerminalReplay(val info: TerminalInfo, val buffer: String, val seq: Long?)

/** ターミナルの出来事。 */
sealed interface TerminalEvent {
    val sessionId: String
    data class Data(override val sessionId: String, val seq: Long, val data: String) : TerminalEvent
    data class Exit(override val sessionId: String, val reason: ExitReason, val exitCode: Long?) : TerminalEvent
}

/** `terminal.exit` の理由（research R5）。 */
enum class ExitReason { PROCESS_EXIT, CLOSED, DISCONNECTED, DETACHED, ERROR }

/** ターミナルを開けない理由（FR-026）。 */
enum class TerminalDenial { NOT_CONNECTED, NO_PERMISSION, DISABLED, FAILED }

class TerminalDeniedException(val reason: TerminalDenial) : Exception(reason.name)

/** 会話ごとのターミナルの sessionId を覚えておく所（アプリを離れて戻ったときにつなぎ直す）。 */
interface TerminalIdStore {
    fun get(conversationKey: String): String?
    fun put(conversationKey: String, sessionId: String?)
}

class SharedPreferencesTerminalIdStore(private val prefs: SharedPreferences) : TerminalIdStore {
    override fun get(conversationKey: String): String? = prefs.getString(conversationKey, null)
    override fun put(conversationKey: String, sessionId: String?) {
        prefs.edit().apply { if (sessionId == null) remove(conversationKey) else putString(conversationKey, sessionId) }.apply()
    }
}

/**
 * ターミナル（spec 002 US4、contracts/gateway-rpc.md）。どの RPC も `operator.admin` が要る。
 * 開くのは「この接続だけのもの」（`sessionKey` を送らない。FR-028）。作業フォルダへは開いた直後に cd で移る（FR-021）。
 */
class TerminalRepository(private val gateway: GatewayClient, private val ids: TerminalIdStore) {

    /** 開けるか。開けなければ理由。 */
    fun availability(): TerminalDenial? {
        val connected = gateway.state.value as? ConnectionState.Connected ?: return TerminalDenial.NOT_CONNECTED
        if ("terminal.open" !in connected.methods) return TerminalDenial.DISABLED
        if ("operator.admin" !in connected.scopes) return TerminalDenial.NO_PERMISSION
        return null
    }

    /** この PTY のイベントだけ。 */
    fun events(sessionId: String): Flow<TerminalEvent> = gateway.events.mapNotNull { event ->
        val payload = event.payload as? JsonObject ?: return@mapNotNull null
        if (payload.string("sessionId") != sessionId) return@mapNotNull null
        when (event.name) {
            "terminal.data" -> TerminalEvent.Data(sessionId, payload.long("seq") ?: return@mapNotNull null, payload.string("data") ?: return@mapNotNull null)
            "terminal.exit" -> TerminalEvent.Exit(sessionId, exitReason(payload.string("reason")), payload.long("exitCode"))
            else -> null
        }
    }

    /** 新しく開く。作業フォルダがあればそこへ移る。 */
    suspend fun open(conversationKey: String, agentId: String?, cwd: String?, cols: Int, rows: Int): TerminalInfo {
        availability()?.let { throw TerminalDeniedException(it) }
        val info = guarded {
            parseInfo(gateway.request("terminal.open", buildJsonObject {
                agentId?.let { put("agentId", it) }
                put("cols", cols.coerceAtLeast(1))
                put("rows", rows.coerceAtLeast(1))
            }).asObject())
        } ?: throw TerminalDeniedException(TerminalDenial.FAILED)
        ids.put(conversationKey, info.sessionId)
        val moved = !cwd.isNullOrBlank() && cwd != info.cwd
        if (moved) input(info.sessionId, changeDirectoryCommand(cwd!!))
        return info.copy(cwd = cwd?.takeIf(String::isNotBlank) ?: info.cwd, changedDirectory = moved)
    }

    /** 前に開いたものが残っていれば、続きにつなぎ直す。無ければ null。 */
    suspend fun reattach(conversationKey: String): TerminalReplay? {
        availability()?.let { throw TerminalDeniedException(it) }
        val sessionId = ids.get(conversationKey) ?: return null
        val alive = guarded {
            gateway.request("terminal.list", buildJsonObject {}).asObject()?.array("sessions").orEmpty()
                .any { it.asObject()?.string("sessionId") == sessionId }
        } ?: false
        if (!alive) {
            ids.put(conversationKey, null)
            return null
        }
        val result = guarded { gateway.request("terminal.attach", buildJsonObject { put("sessionId", sessionId) }).asObject() }
            ?: run { ids.put(conversationKey, null); return null }
        val info = parseInfo(result) ?: return null
        return TerminalReplay(info, result.string("buffer").orEmpty(), result.long("seq"))
    }

    suspend fun input(sessionId: String, data: String) {
        gateway.request("terminal.input", buildJsonObject {
            put("sessionId", sessionId)
            put("data", data)
        })
    }

    suspend fun resize(sessionId: String, cols: Int, rows: Int) {
        gateway.request("terminal.resize", buildJsonObject {
            put("sessionId", sessionId)
            put("cols", cols.coerceAtLeast(1))
            put("rows", rows.coerceAtLeast(1))
        })
    }

    /** 終える（この接続だけのものは PTY ごと消える）。 */
    suspend fun close(conversationKey: String, sessionId: String) {
        ids.put(conversationKey, null)
        runCatching { gateway.request("terminal.close", buildJsonObject { put("sessionId", sessionId) }) }
    }

    fun forget(conversationKey: String) = ids.put(conversationKey, null)

    /**
     * 使い捨てのシェルを開いて [input] を送り、[done] が結果を返すまで出力を集める。閉じるのは必ず（spec 005 R7）。
     * 会話のターミナルの記録には触れない。時間切れ・切断・開けないときは null。
     */
    suspend fun <T : Any> runOnce(agentId: String?, input: String, timeoutMs: Long, done: (String) -> T?): T? {
        if (availability() != null) return null
        val info = guarded {
            parseInfo(gateway.request("terminal.open", buildJsonObject {
                agentId?.let { put("agentId", it) }
                put("cols", ONE_SHOT_COLUMNS)
                put("rows", ONE_SHOT_ROWS)
            }).asObject())
        } ?: return null
        return try {
            kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                kotlinx.coroutines.coroutineScope {
                    val output = StringBuilder()
                    val result = kotlinx.coroutines.CompletableDeferred<T?>()
                    // 出力を受け始めてから送る（最初の出力を取りこぼさない）
                    val collector = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                        events(info.sessionId).collect { event ->
                            when (event) {
                                is TerminalEvent.Data -> {
                                    output.append(event.data)
                                    done(output.toString())?.let { result.complete(it) }
                                }
                                is TerminalEvent.Exit -> result.complete(null)
                            }
                        }
                    }
                    input(info.sessionId, input)
                    result.await().also { collector.cancel() }
                }
            }
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                runCatching { gateway.request("terminal.close", buildJsonObject { put("sessionId", info.sessionId) }) }
            }
        }
    }

    /** 使い捨てのシェル 1 つの中で、ファイルの受け渡し（`terminal.upload`）とコマンドを続けて行う。 */
    inner class OneShot internal constructor(private val sessionId: String) {
        /**
         * ホストの一時置き場にファイルを置く（Control UI のターミナルへのドロップと同じ経路）。置いた場所の絶対パス。
         * 一時置き場は Gateway がしばらくして消す。
         */
        suspend fun upload(name: String, contentBase64: String): String? = guarded {
            gateway.request("terminal.upload", buildJsonObject {
                put("sessionId", sessionId)
                put("name", name)
                put("contentBase64", contentBase64)
            }, timeoutMs = UPLOAD_TIMEOUT_MS).asObject()?.string("path")
        }

        /** [input] を送り、[done] が結果を返すまで出力を集める。切れたら null。 */
        suspend fun <R : Any> run(input: String, done: (String) -> R?): R? = kotlinx.coroutines.coroutineScope {
            val output = StringBuilder()
            val result = kotlinx.coroutines.CompletableDeferred<R?>()
            val collector = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                events(sessionId).collect { event ->
                    when (event) {
                        is TerminalEvent.Data -> {
                            output.append(event.data)
                            done(output.toString())?.let { result.complete(it) }
                        }
                        is TerminalEvent.Exit -> result.complete(null)
                    }
                }
            }
            input(sessionId, input)
            result.await().also { collector.cancel() }
        }
    }

    /** 使い捨てのシェルを開いて [block] を行い、必ず閉じる。開けない・時間切れは null。 */
    suspend fun <T> withOneShot(agentId: String?, timeoutMs: Long, block: suspend OneShot.() -> T): T? {
        if (availability() != null) return null
        val info = guarded {
            parseInfo(gateway.request("terminal.open", buildJsonObject {
                agentId?.let { put("agentId", it) }
                put("cols", ONE_SHOT_COLUMNS)
                put("rows", ONE_SHOT_ROWS)
            }).asObject())
        } ?: return null
        return try {
            kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { OneShot(info.sessionId).block() }
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                runCatching { gateway.request("terminal.close", buildJsonObject { put("sessionId", info.sessionId) }) }
            }
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: GatewayRpcException) {
        if (e.missingScope) throw TerminalDeniedException(TerminalDenial.NO_PERMISSION)
        null
    }

    private fun parseInfo(obj: JsonObject?): TerminalInfo? {
        val id = obj?.string("sessionId") ?: return null
        return TerminalInfo(id, obj.string("cwd").orEmpty(), obj.string("shell").orEmpty(), obj["confined"]?.toString() == "true")
    }

    companion object {
        private const val ONE_SHOT_COLUMNS = 200
        private const val ONE_SHOT_ROWS = 24
        /** 1 ファイル（最大 12 MB）を送り終えるまで待つ時間。 */
        private const val UPLOAD_TIMEOUT_MS = 180_000L

        /** 作業フォルダへ移る入力。パスはシングルクォートで包み、中の ' は '\'' にする。 */
        fun changeDirectoryCommand(cwd: String): String = "cd -- '" + cwd.replace("'", "'\\''") + "' && clear\r"

        internal fun exitReason(reason: String?): ExitReason = when (reason) {
            "process_exit", null -> ExitReason.PROCESS_EXIT
            "closed" -> ExitReason.CLOSED
            "disconnected" -> ExitReason.DISCONNECTED
            "detached" -> ExitReason.DETACHED
            else -> ExitReason.ERROR
        }
    }
}
