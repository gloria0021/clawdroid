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
        withContext(Dispatchers.IO) {
            val web = image.url?.takeUnless(::isInboundMediaUrl)?.takeIf { image.base64 == null }?.let(::webUri)
            if (web != null) {
                if (web.scheme !in setOf("http", "https")) return@withContext false
                val client = (context.applicationContext as ClawdroidApp).container.httpClient
                return@withContext client.newCall(Request.Builder().url(web.toString()).build()).execute().use { res ->
                    res.isSuccessful && res.body.contentLength() <= MAX_SAVE_BYTES &&
                        saveStream(context, res.body.byteStream(), image.mimeType)
                }
            }
            val bytes = imageBytes(image, inbound) ?: return@withContext false
            saveStream(context, bytes.inputStream(), image.mimeType)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}

private const val MAX_SAVE_BYTES = 32 * 1024 * 1024

private fun saveStream(context: Context, source: java.io.InputStream, hint: String?): Boolean = source.buffered().use { input ->
        input.mark(16)
        val header = input.readNBytes(12)
        if (header.isEmpty()) return false
        input.reset()
        val mime = sniffMime(header) ?: hint?.takeIf { it.startsWith("image/") } ?: "image/png"
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "png"
        val name = "clawdroid-${System.currentTimeMillis()}.$extension"
        run {
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }) ?: return false
            val ok = runCatching { resolver.openOutputStream(uri, "wt")?.use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_SAVE_BYTES) throw java.io.IOException("Image too large")
                    output.write(buffer, 0, n)
                }
             } != null }.getOrDefault(false)
            if (ok) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } else {
                resolver.delete(uri, null, null)
            }
            ok
        }
}

private suspend fun imageBytes(image: MessagePart.Image, inbound: InboundImageSource?): ByteArray? {
    image.base64?.let { data ->
        if (data.length > MAX_SAVE_BYTES * 4 / 3 + 256) return null
        return withContext(Dispatchers.Default) { runCatching { Base64.decode(data.substringAfter("base64,"), Base64.DEFAULT) }.getOrNull() }
    }
    image.url?.let { url -> if (isInboundMediaUrl(url)) return inbound?.bytes(url) }
    return null
}

/** 先頭のバイトから画像の種類を見分ける（mimeType が無いとき）。 */
private fun sniffMime(bytes: ByteArray): String? = when {
    bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
    bytes.size >= 6 && String(bytes, 0, 6) in setOf("GIF87a", "GIF89a") -> "image/gif"
    bytes.size >= 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP" -> "image/webp"
    else -> null
}
