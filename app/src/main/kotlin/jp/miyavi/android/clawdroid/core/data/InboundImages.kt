package jp.miyavi.android.clawdroid.core.data

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 画像の受け取りの進み（Gerbil の画像の進捗に倣う）。[total] は分からなければ 0 以下。
 * [failed] は取れなかった（Gateway が許さない場所・つながらない・大きすぎる）。
 */
data class MediaProgress(val read: Long, val total: Long, val failed: Boolean = false) {
    val fraction: Float? get() = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else null
}

/**
 * 会話の画像を Gateway から取り、縮めて復号し、手元に置いておく。利用者の添付（`media://inbound/...`）と、
 * エージェントが `MEDIA:` で示したホストのパスの両方。受け取りの進みを [progress] で知らせる。
 */
class InboundImageLoader(private val gateway: GatewayClient) {
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    /** 同じ画像を一覧と全画面で同時に開いても、受け取りは 1 回にする。 */
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val _progress = MutableStateFlow<Map<String, MediaProgress>>(emptyMap())
    val progress: StateFlow<Map<String, MediaProgress>> = _progress.asStateFlow()

    suspend fun load(source: String, sessionKey: String, agentId: String?): ImageBitmap? {
        // 同じパスでも会話が違えば別のもの（ホストのパスは作り直されることがある）
        val key = "$sessionKey|$source"
        cache.get(key)?.let { return it }
        return locks.getOrPut(key) { Mutex() }.withLock {
            cache.get(key)?.let { return@withLock it }
            val bytes = gateway.loadInboundMedia(source, sessionKey, agentId) { read, total -> report(source, read, total) }
            val bitmap = bytes?.let { withContext(Dispatchers.Default) { decodeScaled(it) } }
            if (bitmap == null) {
                _progress.update { it + (source to (it[source] ?: MediaProgress(0, 0)).copy(failed = true)) }
                return@withLock null
            }
            cache.put(key, bitmap)
            _progress.update { it - source }
            bitmap
        }
    }

    /** 今つないでいる Gateway の住所（保存先の記録の鍵に使う）。 */
    suspend fun endpoint(): String? = gateway.endpoint()

    /** 元のままのバイト列（保存用。縮めない）。 */
    suspend fun loadBytes(source: String, sessionKey: String, agentId: String?): ByteArray? =
        gateway.loadInboundMedia(source, sessionKey, agentId)

    /**
     * 画像でない添付ファイルを端末の「ダウンロード」に保存する（メモリに載せずに書き出す）。
     * 同じ名前があれば MediaStore が別名にする。書き終えるまで IS_PENDING で隠し、失敗・中止したら消す。保存できたらその Uri。
     */
    suspend fun saveFile(
        resolver: android.content.ContentResolver,
        file: MessagePart.File,
        sessionKey: String,
        agentId: String?,
        onProgress: (read: Long, total: Long) -> Unit,
    ): DownloadResult {
        val mime = file.mimeType?.takeIf(String::isNotBlank)
            ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val uri = DownloadsStore.create(resolver, file.name, mime) ?: return DownloadResult.Failed(DownloadSource.Reason.FAILED)
        // 受け取りの繰り返しは中断できない読み出しなので、中止されたら塊ごとに確かめて抜ける
        val caller = kotlin.coroutines.coroutineContext
        val ok = try {
            gateway.streamAssistantMedia(file.url, sessionKey, agentId) { input, total ->
                resolver.openOutputStream(uri, "wt")?.use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    var read = 0L
                    var reported = -1L
                    onProgress(0, total)
                    while (true) {
                        if (!caller[kotlinx.coroutines.Job]!!.isActive) return@use false
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (read - reported >= UNKNOWN_SIZE_STEP) {
                            reported = read
                            onProgress(read, total)
                        }
                    }
                    onProgress(read, total)
                    true
                } ?: false
            } == true
        } catch (e: kotlinx.coroutines.CancellationException) {
            DownloadsStore.discard(resolver, uri)
            throw e
        }
        // 途中で止められたときは、書きかけを消して中止として返す
        if (caller[kotlinx.coroutines.Job]?.isActive == false) {
            DownloadsStore.discard(resolver, uri)
            throw kotlinx.coroutines.CancellationException("cancelled")
        }
        return DownloadsStore.finish(resolver, uri, ok, file.name, mime) ?: DownloadResult.Failed(DownloadSource.Reason.FAILED)
    }

    /** 表示が変わる単位（1%、全体が分からなければ 64 KB）ごとにだけ知らせる。 */
    private fun report(source: String, read: Long, total: Long) {
        _progress.update { current ->
            val prev = current[source]
            val next = MediaProgress(read, total)
            val changed = prev == null || prev.failed || prev.total != total || if (total > 0) {
                (prev.read * 100 / total) != (read * 100 / total)
            } else read - prev.read >= UNKNOWN_SIZE_STEP
            if (changed) current + (source to next) else current
        }
    }

    private fun decodeScaled(bytes: ByteArray): ImageBitmap? = decodeImageScaled(bytes)

    private companion object {
        const val CACHE_BYTES = 48 * 1024 * 1024
        const val UNKNOWN_SIZE_STEP = 64 * 1024L
    }
}

/** 表示に使う最大の辺。これより大きい画像は 2 の累乗で縮めて復号する（原寸のまま持たない。CJ レビュー 2026-11-26）。 */
const val MAX_IMAGE_SIDE_PX = 2048

/** 画像のバイト列を、表示に足りる大きさまで縮めて復号する（画面のスレッドの外で呼ぶ）。 */
fun decodeImageScaled(bytes: ByteArray, maxSide: Int = MAX_IMAGE_SIDE_PX): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

/** 今開いている会話で、会話の画像を取る口。 */
interface InboundImageSource {
    suspend fun load(source: String): ImageBitmap?

    /** 保存用の元のバイト列。取れなければ null。 */
    suspend fun bytes(source: String): ByteArray? = null

    /** 画像ごとの受け取りの進み。 */
    val progress: StateFlow<Map<String, MediaProgress>> get() = EMPTY_PROGRESS

    /** 添付ファイルの保存先の記録の鍵（接続先・会話・メッセージ・場所で区別する）。 */
    suspend fun storageKey(source: String, messageId: String): String = "$messageId|$source"

    /** 添付ファイルを「ダウンロード」に保存する。 */
    suspend fun saveFile(resolver: android.content.ContentResolver, file: MessagePart.File, onProgress: (Long, Long) -> Unit): DownloadResult =
        DownloadResult.Failed(DownloadSource.Reason.FAILED)
}

private val EMPTY_PROGRESS: StateFlow<Map<String, MediaProgress>> = MutableStateFlow(emptyMap())

val LocalInboundImages = staticCompositionLocalOf<InboundImageSource?> { null }

/** Gateway から取る画像か（利用者の添付 `media://inbound/...`、エージェントが `MEDIA:` で示したホストのパス）。 */
fun isInboundMediaUrl(url: String?): Boolean = GatewayClient.isGatewayMediaSource(url)

/** Gateway の画像を読み込む（読み込み中・失敗は null）。 */
@Composable
fun rememberInboundImage(url: String?): ImageBitmap? {
    val loader = LocalInboundImages.current
    val image by produceState<ImageBitmap?>(null, url, loader) {
        value = if (url != null && loader != null && isInboundMediaUrl(url)) loader.load(url) else null
    }
    return image
}

/** [url] の受け取りの進みだけを見る（ほかの画像の進みが変わっても再構成しない）。 */
@Composable
fun rememberMediaProgress(url: String?): MediaProgress? {
    val loader = LocalInboundImages.current ?: return null
    val flow = remember(loader, url) { loader.progress.map { it[url] }.distinctUntilChanged() }
    return flow.collectAsState(initial = null).value
}
