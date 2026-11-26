package jp.miyavi.android.clawdroid.core.data

import android.content.ContentResolver
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 会話の添付ファイルを「ダウンロード」に保存した先を覚えておく（アプリを閉じても・会話を切り替えても「開く」を出すため）。
 * 鍵は添付の場所（`media://inbound/...` やホストのパス）。保存した先が消えていたら忘れる。
 */
class SavedFileStore(private val prefs: SharedPreferences) {
    fun get(source: String): Uri? = prefs.getString(source, null)?.let(Uri::parse)

    fun put(source: String, uri: Uri) = prefs.edit().putString(source, uri.toString()).apply()

    fun forget(source: String) = prefs.edit().remove(source).apply()

    /** 覚えている先がまだあればその Uri。端末から消されていたら忘れて null。 */
    suspend fun existing(resolver: ContentResolver, source: String): Uri? {
        val uri = get(source) ?: return null
        val alive = withContext(Dispatchers.IO) {
            runCatching {
                resolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns._ID), null, null, null)?.use { it.count > 0 } == true
            }.getOrDefault(false)
        }
        if (!alive) forget(source)
        return uri.takeIf { alive }
    }
}
