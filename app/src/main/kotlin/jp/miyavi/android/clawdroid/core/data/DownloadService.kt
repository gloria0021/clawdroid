package jp.miyavi.android.clawdroid.core.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import jp.miyavi.android.clawdroid.ClawdroidApp
import jp.miyavi.android.clawdroid.MainActivity
import jp.miyavi.android.clawdroid.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * ダウンロードのあいだだけ動くフォアグラウンドサービス。画面を消してもアプリを止めさせず、通知に進み具合と中止を出す。
 * 終わったら通知（押すと開く）を出して止まる。
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val center get() = (application as ClawdroidApp).container.downloads

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
        ServiceCompat.startForeground(this, PROGRESS_ID, progressNotification(emptyList()), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        scope.launch {
            // 通知の更新は 0.5 秒ごとまで（多すぎると通知が間引かれる）
            center.tasks.sample(NOTIFY_INTERVAL_MS).collect { tasks ->
                if (tasks.isEmpty()) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notify(PROGRESS_ID, progressNotification(tasks))
                }
            }
        }
        scope.launch {
            center.events.collect { event ->
                when (event) {
                    is DownloadEvent.Saved -> notify(event.id.toInt() + RESULT_ID_BASE, savedNotification(event.result))
                    is DownloadEvent.Failed -> notify(event.id.toInt() + RESULT_ID_BASE, failedNotification(event.name))
                    is DownloadEvent.Cancelled -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getLongExtra(EXTRA_CANCEL, -1L)?.takeIf { it >= 0 }?.let(center::cancel)
        return START_NOT_STICKY
    }

    /** Android 15 以降の dataSync の時間切れ。ダウンロードを止めてサービスを終える。 */
    override fun onTimeout(startId: Int, fgsType: Int) {
        center.tasks.value.forEach { center.cancel(it.id) }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(id: Int, notification: Notification) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(this).notify(id, notification) }
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(tasks: List<DownloadTask>): Notification {
        val first = tasks.firstOrNull()
        val builder = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_huge_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (first == null) return builder.setContentTitle(getString(R.string.download_notify_preparing)).setProgress(0, 0, true).build()
        val size = listOfNotNull(
            Formatter.formatShortFileSize(this, first.received),
            first.total?.let { Formatter.formatShortFileSize(this, it) },
        ).joinToString(" / ")
        val title = if (tasks.size > 1) getString(R.string.download_notify_many, first.name, tasks.size - 1) else first.name
        val cancel = PendingIntent.getService(
            this, first.id.toInt(), Intent(this, DownloadService::class.java).putExtra(EXTRA_CANCEL, first.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val fraction = first.fraction
        return builder
            .setContentTitle(title)
            .setContentText(if (first.waiting) getString(R.string.download_waiting) else size)
            .setProgress(PROGRESS_MAX, ((fraction ?: 0f) * PROGRESS_MAX).toInt(), fraction == null || first.waiting)
            .addAction(0, getString(R.string.action_cancel), cancel)
            .build()
    }

    private fun savedNotification(result: DownloadResult.Saved): Notification {
        val open = PendingIntent.getActivity(
            this, result.uri.hashCode(),
            Intent(Intent.ACTION_VIEW).setDataAndType(result.uri, result.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_huge_download)
            .setContentTitle(getString(R.string.download_notify_saved))
            .setContentText(result.displayName)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    private fun failedNotification(name: String): Notification = NotificationCompat.Builder(this, CHANNEL_RESULT)
        .setSmallIcon(R.drawable.ic_huge_download)
        .setContentTitle(getString(R.string.download_notify_failed))
        .setContentText(name)
        .setContentIntent(openApp())
        .setAutoCancel(true)
        .build()

    companion object {
        private const val CHANNEL_PROGRESS = "downloads_progress"
        private const val CHANNEL_RESULT = "downloads_result"
        private const val PROGRESS_ID = 4100
        private const val RESULT_ID_BASE = 4200
        private const val PROGRESS_MAX = 1000
        private const val NOTIFY_INTERVAL_MS = 500L
        private const val EXTRA_CANCEL = "cancel"

        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.download_channel_progress), NotificationManager.IMPORTANCE_LOW),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RESULT, context.getString(R.string.download_channel_result), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}
