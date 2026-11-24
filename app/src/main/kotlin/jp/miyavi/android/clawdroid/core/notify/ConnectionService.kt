package jp.miyavi.android.clawdroid.core.notify

import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import jp.miyavi.android.clawdroid.ClawdroidApp
import kotlinx.coroutines.launch

/**
 * 常駐（spec 006 R1）。Gateway との接続（アプリの GatewayClient）が裏でも生きていられるようにプロセスを保つだけで、
 * 接続そのものは持たない。常駐の通知に接続の状態だけを出す。
 */
class ConnectionService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        val container = (application as ClawdroidApp).container
        val center = container.notifier.center
        ServiceCompat.startForeground(
            this, NotificationCenter.CONNECTION_ID, center.connection(container.gateway.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        lifecycleScope.launch {
            container.gateway.state.collect { state ->
                if (center.permitted()) {
                    @Suppress("MissingPermission")
                    NotificationManagerCompat.from(this@ConnectionService).notify(NotificationCenter.CONNECTION_ID, center.connection(state))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }
}
