package jp.miyavi.android.sumahoclaw.core.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ファイルの作成・削除・名前の変更（spec 005 US6）。ターミナルと同じ `operator.admin` の範囲でだけ使える。
 * 1 つずつ行う（同じ場所への操作が重ならないように）。
 */
/**
 * アップロードする 1 件。[name] は端末での名前、[target] は置く名前（同じ名前があって別名にしたときは違う）。
 * [read] で中身を読む（読めなければ null）。
 */
class UploadFile(
    val name: String,
    val target: String,
    val size: Long?,
    val overwrite: Boolean,
    val read: suspend () -> ByteArray?,
)

/** アップロードの結果のうち、大きすぎて送らなかったもの（[FileOpResult.Failed] の理由）。 */
const val UPLOAD_TOO_LARGE = "too-large"

/** 1 ファイルの上限。Gateway の上限は 16 MiB だが、base64 にした 1 通が OkHttp の送信の上限（16 MiB）に収まる大きさにする。 */
const val MAX_UPLOAD_BYTES = 12 * 1000 * 1000

class FileOpsRepository(private val terminal: TerminalRepository) {
    private val lock = Mutex()

    /** 使えるか（ターミナルを開ける接続か）。 */
    fun available(): Boolean = terminal.availability() == null

    suspend fun run(agentId: String?, root: String, op: FileOp): FileOpResult = lock.withLock {
        val nonce = (1..NONCE_LENGTH).map { NONCE_CHARS.random() }.joinToString("")
        terminal.runOnce(agentId, fileOpCommand(op, root, nonce), TIMEOUT_MS) { parseFileOpOutput(it, nonce) }
            ?: FileOpResult.Unknown
    }

    /**
     * 端末のファイルを作業フォルダの [parent] にアップロードする（Gateway の `terminal.upload` でホストの一時置き場に置き、
     * シェルで目的のフォルダへ移す）。1 つずつ送り、送る前に [onProgress] で何件目かを知らせる。
     * 結果は送った順。開けない・切れたときは、残りを [FileOpResult.Unknown] にする。
     */
    suspend fun upload(
        agentId: String?,
        root: String,
        parent: String,
        files: List<UploadFile>,
        onProgress: (index: Int, file: UploadFile) -> Unit,
    ): List<FileOpResult> = lock.withLock {
        val results = mutableListOf<FileOpResult>()
        terminal.withOneShot(agentId, files.size * PER_FILE_TIMEOUT_MS) {
            files.forEachIndexed { index, file ->
                onProgress(index, file)
                // 大きすぎるものは読まずに断る（大きさの分からないものは読んでから確かめる）
                val bytes = if ((file.size ?: 0) > MAX_UPLOAD_BYTES) null else file.read()
                val result = when {
                    (file.size ?: 0) > MAX_UPLOAD_BYTES || (bytes != null && bytes.size > MAX_UPLOAD_BYTES) -> FileOpResult.Failed(UPLOAD_TOO_LARGE)
                    bytes == null -> FileOpResult.Failed(null)
                    else -> {
                        // 最大 12 MB を文字にするので、画面のスレッドの外で行う（CJ レビュー 2026-11-26）
                        val encoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            java.util.Base64.getEncoder().encodeToString(bytes)
                        }
                        val staged = upload(file.name, encoded)
                        if (staged == null) FileOpResult.Unknown
                        else {
                            val nonce = (1..NONCE_LENGTH).map { NONCE_CHARS.random() }.joinToString("")
                            val op = FileOp.Place(parent, file.target, staged, file.overwrite)
                            run(fileOpCommand(op, root, nonce)) { parseFileOpOutput(it, nonce) } ?: FileOpResult.Unknown
                        }
                    }
                }
                results += result
            }
        }
        results + List(files.size - results.size) { FileOpResult.Unknown }
    }

    private companion object {
        const val PER_FILE_TIMEOUT_MS = 200_000L
        const val TIMEOUT_MS = 15_000L
        const val NONCE_LENGTH = 16
        val NONCE_CHARS = ('a'..'z') + ('0'..'9')
    }
}
