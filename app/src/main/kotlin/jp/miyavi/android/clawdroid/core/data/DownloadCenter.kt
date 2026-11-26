package jp.miyavi.android.clawdroid.core.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 進んでいるダウンロード 1 件。 */
data class DownloadTask(
    val id: Long,
    val name: String,
    val received: Long = 0,
    val total: Long? = null,
    /** 接続が切れて、戻るのを待っている。 */
    val waiting: Boolean = false,
    /** 会話の添付の保存なら、その添付の鍵（カードが自分の進みを見分ける）。 */
    val key: String? = null,
) {
    val fraction: Float? get() = total?.takeIf { it > 0 }?.let { (received.toFloat() / it).coerceIn(0f, 1f) }
}

/** 終わったダウンロード（アプリ全体の表示と通知で使う）。 */
sealed interface DownloadEvent {
    val id: Long
    /** 会話の添付の保存なら、その添付の鍵。 */
    val key: String?
    data class Saved(override val id: Long, val result: DownloadResult.Saved, override val key: String? = null) : DownloadEvent
    data class Failed(override val id: Long, val name: String, val reason: DownloadSource.Reason, override val key: String? = null) : DownloadEvent
    data class Cancelled(override val id: Long, val name: String, override val key: String? = null) : DownloadEvent
}

/** ファイルビューアからダウンロードを頼む窓口（ViewModel のテストで偽物に差し替える）。 */
fun interface FileDownloads {
    fun start(root: FileRoot, path: String)
}

/**
 * アプリ全体のダウンロード（画面より長く生きる）。ファイルビューアを閉じても、画面を消しても続く。
 * 進んでいるあいだはフォアグラウンドサービスで動かし（通知に進み具合）、アプリのどの画面でも上部に進み具合を出す。
 */
class DownloadCenter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val downloader: FileDownloader,
) : FileDownloads {
    private val ids = AtomicLong()
    private val jobs = mutableMapOf<Long, Job>()
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()
    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    override fun start(root: FileRoot, path: String) {
        run(path.substringAfterLast('/'), key = null) { progress, waiting ->
            downloader.download(root, path, onProgress = progress, onWaiting = waiting)
        }
    }

    /**
     * 会話の添付を「ダウンロード」に保存する（一覧の行より長く生きる。スクロールで行が消えても、画面を移っても続く。
     * CJ レビュー 2026-11-26）。[key] は添付の鍵で、カードが自分の進みと結果を見分けるのに使う。
     */
    fun startAttachment(name: String, key: String, save: suspend (onProgress: (Long, Long?) -> Unit) -> DownloadResult): Long =
        run(name, key) { progress, _ -> save(progress) }

    private fun run(
        name: String,
        key: String?,
        block: suspend (onProgress: (Long, Long?) -> Unit, onWaiting: (Boolean) -> Unit) -> DownloadResult,
    ): Long {
        val id = ids.incrementAndGet()
        _tasks.update { it + DownloadTask(id, name, key = key) }
        startService()
        jobs[id] = scope.launch {
            val event = try {
                val result = block(
                    { received, total -> update(id) { it.copy(received = received, total = total, waiting = false) } },
                    { waiting -> update(id) { it.copy(waiting = waiting) } },
                )
                when (result) {
                    is DownloadResult.Saved -> DownloadEvent.Saved(id, result, key)
                    is DownloadResult.Failed -> DownloadEvent.Failed(id, name, result.reason, key)
                }
            } catch (e: CancellationException) {
                DownloadEvent.Cancelled(id, name, key)
            } catch (e: Exception) {
                DownloadEvent.Failed(id, name, DownloadSource.Reason.FAILED, key)
            } finally {
                jobs.remove(id)
                _tasks.update { list -> list.filterNot { it.id == id } }
            }
            _events.tryEmit(event)
        }
        return id
    }

    fun cancel(id: Long) {
        jobs[id]?.cancel()
    }

    private fun update(id: Long, block: (DownloadTask) -> DownloadTask) =
        _tasks.update { list -> list.map { if (it.id == id) block(it) else it } }

    private fun startService() {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
            .onFailure { android.util.Log.w("ClawdroidDownload", "could not start the download service", it) }
    }
}
