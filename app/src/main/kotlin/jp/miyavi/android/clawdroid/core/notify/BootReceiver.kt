package jp.miyavi.android.clawdroid.core.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.miyavi.android.clawdroid.ClawdroidApp

/** 端末の起動で常駐を再開する（spec 006 Edge Cases。connectedDevice は起動時に始められる種類）。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        (context.applicationContext as ClawdroidApp).container.notifier.ensureService()
    }
}
