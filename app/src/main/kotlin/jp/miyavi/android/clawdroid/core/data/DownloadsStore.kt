package jp.miyavi.android.clawdroid.core.data

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 端末の「ダウンロード」への保存の共通部分（ファイルビューアのダウンロードと会話の添付で共有。CJ レビュー 2026-11-26）。
 * 書き終わるまで IS_PENDING で隠し、成功なら確定、失敗・中止なら消す。同じ名前があれば MediaStore が別名にする。
 */
internal object DownloadsStore {
    /** 書き込み先を作る（隠したまま）。作れなければ null。 */
    suspend fun create(resolver: ContentResolver, name: String, mime: String): Uri? = withContext(Dispatchers.IO) {
        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        })
    }

    /** 書き終えたら確定して、実際の名前（別名になったとき）で結果を返す。[ok] でなければ消して null。中止されても必ず片付ける。 */
    suspend fun finish(resolver: ContentResolver, uri: Uri, ok: Boolean, name: String, mime: String): DownloadResult.Saved? =
        withContext(NonCancellable + Dispatchers.IO) {
            if (!ok) {
                resolver.delete(uri, null, null)
                return@withContext null
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            val actual = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: name
            DownloadResult.Saved(uri, actual, mime)
        }

    /** 中止・失敗のときに、書きかけを消す。 */
    suspend fun discard(resolver: ContentResolver, uri: Uri) = withContext(NonCancellable + Dispatchers.IO) {
        resolver.delete(uri, null, null)
    }
}
