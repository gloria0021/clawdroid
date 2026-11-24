package jp.miyavi.android.clawdroid.feature.chat.message

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import jp.miyavi.android.clawdroid.ClawdroidApp
import jp.miyavi.android.clawdroid.core.data.InboundImageSource
import jp.miyavi.android.clawdroid.core.data.MessagePart
import jp.miyavi.android.clawdroid.core.data.isInboundMediaUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 会話の画像を端末の「ダウンロード」に保存する（Gerbil の画像の保存に倣う。ファイルのダウンロードと同じ置き場所）。
 * 元のバイト列を取る: base64 はそのまま、Gateway の添付（media://inbound/…）は開いている会話の権限で、Web の画像は OkHttp で。
 * 書き終わるまで IS_PENDING で隠し、失敗したら消す。許可は不要（MediaStore.Downloads）。
 */
internal suspend fun saveImageToDownloads(context: Context, image: MessagePart.Image, inbound: InboundImageSource?): Boolean {
    return try {
        val bytes = imageBytes(context, image, inbound) ?: return false
        val mime = image.mimeType?.takeIf { it.startsWith("image/") } ?: sniffMime(bytes) ?: "image/png"
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "png"
        val name = "clawdroid-${System.currentTimeMillis()}.$extension"
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }) ?: return@withContext false
            val ok = runCatching { resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null }.getOrDefault(false)
            if (ok) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } else {
                resolver.delete(uri, null, null)
            }
            ok
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}

private suspend fun imageBytes(context: Context, image: MessagePart.Image, inbound: InboundImageSource?): ByteArray? {
    image.base64?.let { data ->
        return withContext(Dispatchers.Default) { runCatching { Base64.decode(data.substringAfter("base64,"), Base64.DEFAULT) }.getOrNull() }
    }
    val url = image.url ?: return null
    if (isInboundMediaUrl(url)) return inbound?.bytes(url)
    val web = webUri(url)?.toString() ?: return null
    if (!web.startsWith("http://") && !web.startsWith("https://")) return null
    val client = (context.applicationContext as ClawdroidApp).container.httpClient
    return withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(web).build()).execute().use { response ->
            if (!response.isSuccessful) null else response.body.bytes().takeIf { it.isNotEmpty() }
        }
    }
}

/** 先頭のバイトから画像の種類を見分ける（mimeType が無いとき）。 */
private fun sniffMime(bytes: ByteArray): String? = when {
    bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
    bytes.size >= 6 && String(bytes, 0, 6) in setOf("GIF87a", "GIF89a") -> "image/gif"
    bytes.size >= 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP" -> "image/webp"
    else -> null
}
