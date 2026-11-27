package jp.miyavi.android.sumahoclaw.core.data

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import jp.miyavi.android.sumahoclaw.core.gateway.ConnectionState
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** ダウンロードの結果。 */
sealed interface DownloadResult {
    /** 保存できた。`uri` から開ける。 */
    data class Saved(val uri: Uri, val displayName: String, val mimeType: String) : DownloadResult
    data class Failed(val reason: DownloadSource.Reason) : DownloadResult
}

/**
 * ファイルを端末の Download に保存する（spec 002 FR-013〜015、research R4）。
 * 同じ名前があるときは MediaStore が別名にする（上書きしない）。書き終わるまで IS_PENDING で隠す。
 *
 * 作業フォルダのファイルは、Gateway の接続に紐付く短命のリンクから受け取る。画面を消して接続が切れる・
 * リンクが失効することがあるので、切れたら接続が戻るのを待ち、新しいリンクを取り直して続きから（Range）受け取る。
 */
class FileDownloader(
    private val files: FilesRepository,
    private val gateway: GatewayClient,
    private val resolver: ContentResolver,
) {
    /** [onProgress] は（受け取った量, 全体の量（分からなければ null））、[onWaiting] は接続を待っているか。 */
    suspend fun download(
        root: FileRoot,
        path: String,
        onProgress: (Long, Long?) -> Unit,
        onWaiting: (Boolean) -> Unit = {},
    ): DownloadResult {
        val source = files.downloadSource(root, path)
        val (name, mime) = when (val first = source) {
            is DownloadSource.Link -> first.filename to first.mimeType
            is DownloadSource.Inline -> first.filename to first.mimeType
            is DownloadSource.Unavailable -> return DownloadResult.Failed(first.reason)
        }
        val uri = DownloadsStore.create(resolver, name, mime) ?: return DownloadResult.Failed(DownloadSource.Reason.FAILED)
        val ok = try {
            when (val s = source) {
                is DownloadSource.Inline -> withContext(Dispatchers.IO) {
                    runCatching { resolver.openOutputStream(uri, "wt")?.use { it.write(s.bytes) } != null }.getOrDefault(false)
                }
                is DownloadSource.Link -> {
                    var link: DownloadSource.Link = s
                    var written = 0L
                    var attempts = 0
                    var done = false
                    while (!done) {
                        val result = gateway.downloadLink(link.url, from = written) { input, resumed, total ->
                            // 続きを受け取れなかった（最初から返ってきた）ときは、書いた分を捨てて最初から
                            if (!resumed) written = 0
                            resolver.openOutputStream(uri, if (resumed) "wa" else "wt")?.use { output ->
                                val buffer = ByteArray(COPY_BUFFER)
                                var reported = written
                                onProgress(written, total)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                    written += n
                                    // 知らせは 256KB ごと（画面と通知の更新を詰まらせない）
                                    if (written - reported >= PROGRESS_STEP) {
                                        reported = written
                                        onProgress(written, total)
                                    }
                                }
                                onProgress(written, total)
                            } ?: error("no output")
                        }
                        when (result) {
                            GatewayClient.LinkAttempt.COMPLETE -> done = true
                            GatewayClient.LinkAttempt.FAIL -> break
                            GatewayClient.LinkAttempt.RETRY -> {
                                if (++attempts > MAX_RESUMES) break
                                // 接続が戻るのを待ち（画面を消している間も待つ）、新しいリンクを取り直す
                                onWaiting(true)
                                val connected = withTimeoutOrNull(RECONNECT_WAIT_MS) {
                                    gateway.state.first { it is ConnectionState.Connected }
                                }
                                onWaiting(false)
                                if (connected == null) break
                                delay(RESUME_BACKOFF_MS * attempts.coerceAtMost(4))
                                link = files.downloadSource(root, path) as? DownloadSource.Link ?: break
                            }
                        }
                    }
                    done
                }
                is DownloadSource.Unavailable -> false
            }
        } catch (e: CancellationException) {
            // 中止したら、書きかけのファイルを消す
            DownloadsStore.discard(resolver, uri)
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "download failed", e)
            false
        }
        val saved = DownloadsStore.finish(resolver, uri, ok, name, mime)
        if (saved == null) android.util.Log.w(TAG, "download failed: $path")
        return saved ?: DownloadResult.Failed(if (source is DownloadSource.Link) DownloadSource.Reason.NO_HTTP else DownloadSource.Reason.FAILED)
    }
}

private const val TAG = "SumahoClawDownload"
private const val COPY_BUFFER = 64 * 1024
private const val PROGRESS_STEP = 256 * 1024L
/** 切れたときに続きから受け取り直す回数の上限。 */
private const val MAX_RESUMES = 20
/** 接続が戻るのを待つ上限（画面を消しているあいだも含む）。 */
private const val RECONNECT_WAIT_MS = 5 * 60_000L
private const val RESUME_BACKOFF_MS = 500L
