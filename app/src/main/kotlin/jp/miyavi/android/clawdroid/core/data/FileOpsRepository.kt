package jp.miyavi.android.clawdroid.core.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ファイルの作成・削除・名前の変更（spec 005 US6）。ターミナルと同じ `operator.admin` の範囲でだけ使える。
 * 1 つずつ行う（同じ場所への操作が重ならないように）。
 */
class FileOpsRepository(private val terminal: TerminalRepository) {
    private val lock = Mutex()

    /** 使えるか（ターミナルを開ける接続か）。 */
    fun available(): Boolean = terminal.availability() == null

    suspend fun run(agentId: String?, root: String, op: FileOp): FileOpResult = lock.withLock {
        val nonce = (1..NONCE_LENGTH).map { NONCE_CHARS.random() }.joinToString("")
        terminal.runOnce(agentId, fileOpCommand(op, root, nonce), TIMEOUT_MS) { parseFileOpOutput(it, nonce) }
            ?: FileOpResult.Unknown
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val NONCE_LENGTH = 16
        val NONCE_CHARS = ('a'..'z') + ('0'..'9')
    }
}
