package jp.miyavi.android.sumahoclaw.core.data

import jp.miyavi.android.sumahoclaw.core.gateway.ConnectionState
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 再起動の結果。 */
enum class RestartResult { RECONNECTED, TIMED_OUT, FAILED, ALREADY_RUNNING }

/**
 * Gateway の再起動（US7）。対象を指定しない要求は、Gateway 側で実行中の作業を考慮した「安全な再起動」になる。
 * 指示は 1 回だけ送る（FR-073）。
 */
class GatewayControlRepository(private val gateway: GatewayClient) {
    private val mutex = Mutex()

    suspend fun restart(timeoutMs: Long = RECONNECT_TIMEOUT_MS): RestartResult {
        if (!mutex.tryLock()) return RestartResult.ALREADY_RUNNING
        try {
            runCatching {
                gateway.request("gateway.restart.request", buildJsonObject { put("reason", "SumahoClaw のワンボタン再起動") })
            }.onFailure { return RestartResult.FAILED }
            gateway.markRestarting()
            // 切断されるまで待ってから、戻るのを待つ
            val outcome = withTimeoutOrNull(timeoutMs) {
                gateway.state.first { it !is ConnectionState.Connected }
                gateway.state.first { it is ConnectionState.Connected }
            }
            return if (outcome != null) RestartResult.RECONNECTED else RestartResult.TIMED_OUT
        } finally {
            mutex.unlock()
        }
    }

    private companion object {
        const val RECONNECT_TIMEOUT_MS = 90_000L
    }
}
