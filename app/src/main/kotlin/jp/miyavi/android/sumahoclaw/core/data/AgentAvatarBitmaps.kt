package jp.miyavi.android.sumahoclaw.core.data

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.toBitmap
import coil3.transform.CircleCropTransformation
import java.util.concurrent.ConcurrentHashMap

/**
 * エージェントの顔の画像を、画面の外（通知の大きなアイコン・ランチャーのショートカット）で使う Bitmap にする。
 * 一度読んだものは URL・大きさ・形ごとに使い回す。読めなければ null。
 */
class AgentAvatarBitmaps(private val context: Context) {
    private val cache = ConcurrentHashMap<String, Bitmap>()

    /** [circle] なら丸く切る（通知）。切らなければ正方形のまま（ショートカット。形はランチャーが切る）。 */
    suspend fun load(agent: Agent?, sizePx: Int, circle: Boolean): Bitmap? {
        val url = usableUrl(agent?.avatarUrl) ?: return null
        val key = "$url|$sizePx|$circle"
        cache[key]?.let { return it }
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(sizePx)
            .apply { if (circle) transformations(CircleCropTransformation()) }
            .allowHardware(false)
            .build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull() as? SuccessResult ?: return null
        return runCatching { result.image.toBitmap() }.getOrNull()?.also { cache[key] = it }
    }

    companion object {
        /** 読める URL（`data:image/`・http(s)・手元の控えの `file:`。[AgentRosterCache]）だけを返す。 */
        fun usableUrl(raw: String?): String? = raw?.trim()?.takeIf {
            it.startsWith("data:image/", ignoreCase = true) || it.startsWith("https://") || it.startsWith("http://") ||
                it.startsWith("file:/")
        }
    }
}
