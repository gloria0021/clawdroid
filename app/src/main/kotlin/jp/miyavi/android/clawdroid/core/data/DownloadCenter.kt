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
) {
    val fraction: Float? get() = total?.takeIf { it > 0 }?.let { (received.toFloat() / it).coerceIn(0f, 1f) }
}

/** 終わったダウンロード（アプリ全体の表示と通知で使う）。 */
sealed interface DownloadEvent {
    val id: Long
    data class Saved(override val id: Long, val result: DownloadResult.Saved) : DownloadEvent
    data class Failed(override val id: Long, val name: String, val reason: DownloadSource.Reason) : DownloadEvent
    data class Cancelled(override val id: Long, val name: String) : DownloadEvent
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
        val id = ids.incrementAndGet()
        val name = path.substringAfterLast('/')
        _tasks.update { it + DownloadTask(id, name) }
        startService()
        jobs[id] = scope.launch {
            val event = try {
                val result = downloader.download(
                    root, path,
                    onProgress = { received, total -> update(id) { it.copy(received = received, total = total, waiting = false) } },
                    onWaiting = { waiting -> update(id) { it.copy(waiting = waiting) } },
                )
                when (result) {
                    is DownloadResult.Saved -> DownloadEvent.Saved(id, result)
                    is DownloadResult.Failed -> DownloadEvent.Failed(id, name, result.reason)
                }
            } catch (e: CancellationException) {
                DownloadEvent.Cancelled(id, name)
            } catch (e: Exception) {
                DownloadEvent.Failed(id, name, DownloadSource.Reason.FAILED)
            } finally {
                jobs.remove(id)
                _tasks.update { list -> list.filterNot { it.id == id } }
            }
            _events.tryEmit(event)
        }
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
