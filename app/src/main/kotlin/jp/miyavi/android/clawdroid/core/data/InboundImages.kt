package jp.miyavi.android.clawdroid.core.data

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 会話に添付された画像（`media://inbound/...`）を取り、縮めて復号し、手元に置いておく。 */
class InboundImageLoader(private val gateway: GatewayClient) {
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    suspend fun load(source: String, sessionKey: String, agentId: String?): ImageBitmap? {
        cache.get(source)?.let { return it }
        val bytes = gateway.loadInboundMedia(source, sessionKey, agentId) ?: return null
        val bitmap = withContext(Dispatchers.Default) { decodeScaled(bytes) } ?: return null
        cache.put(source, bitmap)
        return bitmap
    }

    /** 元のままのバイト列（保存用。縮めない）。 */
    suspend fun loadBytes(source: String, sessionKey: String, agentId: String?): ByteArray? =
        gateway.loadInboundMedia(source, sessionKey, agentId)

    private fun decodeScaled(bytes: ByteArray): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()

    private companion object {
        const val CACHE_BYTES = 48 * 1024 * 1024
        /** 一覧と全画面で使う最大の辺。これより大きい画像は 2 の累乗で縮める。 */
        const val MAX_SIDE_PX = 2048
    }
}

/** 今開いている会話で、添付画像を取る口。 */
fun interface InboundImageSource {
    suspend fun load(source: String): ImageBitmap?

    /** 保存用の元のバイト列。取れなければ null。 */
    suspend fun bytes(source: String): ByteArray? = null
}

val LocalInboundImages = staticCompositionLocalOf<InboundImageSource?> { null }

fun isInboundMediaUrl(url: String?): Boolean = url?.startsWith("media://inbound/", ignoreCase = true) == true

/** `media://inbound/...` の画像を読み込む（読み込み中・失敗は null）。 */
@Composable
fun rememberInboundImage(url: String?): ImageBitmap? {
    val loader = LocalInboundImages.current
    val image by produceState<ImageBitmap?>(null, url, loader) {
        value = if (url != null && loader != null && isInboundMediaUrl(url)) loader.load(url) else null
    }
    return image
}
