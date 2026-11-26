package jp.miyavi.android.clawdroid.core.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.miyavi.android.clawdroid.ClawdroidApp
import kotlinx.coroutines.launch

/**
 * 端末の起動で常駐を再開する（spec 006 Edge Cases。connectedDevice は起動の知らせから始められる種類）。
 * 起動直後は接続情報と設定の読み込みが終わっていないので、決まるのを待ってから始める（goAsync の上限 10 秒より短く待つ）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as ClawdroidApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.notifier.startServiceAfterBoot(BOOT_WAIT_MS)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val BOOT_WAIT_MS = 8_000L
    }
}
