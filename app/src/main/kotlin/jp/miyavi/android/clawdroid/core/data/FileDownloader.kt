package jp.miyavi.android.clawdroid.core.data

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ダウンロードの結果。 */
sealed interface DownloadResult {
    /** 保存できた。`uri` から開ける。 */
    data class Saved(val uri: Uri, val displayName: String, val mimeType: String) : DownloadResult
    data class Failed(val reason: DownloadSource.Reason) : DownloadResult
}

/** ダウンロードの窓口（ViewModel のテストで偽物に差し替える）。 */
fun interface FileDownloads {
    suspend fun download(root: FileRoot, path: String): DownloadResult
}

/**
 * ファイルを端末の Download に保存する（spec 002 FR-013〜015、research R4）。
 * 同じ名前があるときは MediaStore が別名にする（上書きしない）。書き終わるまで IS_PENDING で隠す。
 */
class FileDownloader(
    private val files: FilesRepository,
    private val gateway: GatewayClient,
    private val resolver: ContentResolver,
) : FileDownloads {
    override suspend fun download(root: FileRoot, path: String): DownloadResult {
        val source = files.downloadSource(root, path)
        val (name, mime) = when (source) {
            is DownloadSource.Link -> source.filename to source.mimeType
            is DownloadSource.Inline -> source.filename to source.mimeType
            is DownloadSource.Unavailable -> return DownloadResult.Failed(source.reason)
        }
        val uri = withContext(Dispatchers.IO) {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            })
        } ?: return DownloadResult.Failed(DownloadSource.Reason.FAILED)
        val written = when (source) {
            is DownloadSource.Link -> gateway.downloadLink(source.url) { input ->
                resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) } ?: error("no output")
            }
            is DownloadSource.Inline -> withContext(Dispatchers.IO) {
                runCatching { resolver.openOutputStream(uri)?.use { it.write(source.bytes) } != null }.getOrDefault(false)
            }
        }
        return withContext(Dispatchers.IO) {
            if (!written) {
                resolver.delete(uri, null, null)
                DownloadResult.Failed(if (source is DownloadSource.Link) DownloadSource.Reason.NO_HTTP else DownloadSource.Reason.FAILED)
            } else {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                // 別名になったときは、実際の名前を示す
                val actual = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: name
                DownloadResult.Saved(uri, actual, mime)
            }
        }
    }
}
