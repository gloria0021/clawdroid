package jp.miyavi.android.sumahoclaw.core.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.miyavi.android.sumahoclaw.SumahoClawApp
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.ApprovalDecision
import jp.miyavi.android.sumahoclaw.core.data.ApprovalKey
import jp.miyavi.android.sumahoclaw.core.data.ApprovalKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 承認の通知のボタン（spec 006 FR-023）。アプリを開かずに決定を送り、結果を通知に示す。 */
class ApprovalActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SumahoClawApp).container
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { k -> ApprovalKind.entries.firstOrNull { it.name == k } } ?: return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val decision = intent.getStringExtra(EXTRA_DECISION)?.let { d -> ApprovalDecision.entries.firstOrNull { it.name == d } } ?: return
        val key = ApprovalKey(kind, id)
        val notifier = container.notifier
        val pending = goAsync()
        container.appScope.launch(Dispatchers.Main) {
            try {
                val item = container.approvals.requests.value.firstOrNull { it.key == key }
                if (item == null) {
                    notifier.center.approvalResult(key, R.string.notify_approval_gone, openOnTap = false)
                    return@launch
                }
                notifier.decidedApprovals += key
                val sent = runCatching { container.approvals.resolve(item, decision) }.isSuccess
                when {
                    !sent -> {
                        notifier.decidedApprovals -= key
                        notifier.center.approvalResult(key, R.string.notify_approval_failed, openOnTap = true)
                    }
                    decision == ApprovalDecision.DENY -> notifier.center.approvalResult(key, R.string.notify_approval_denied, openOnTap = false)
                    else -> notifier.center.approvalResult(key, R.string.notify_approval_allowed, openOnTap = false)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_ID = "id"
        private const val EXTRA_DECISION = "decision"

        internal fun intent(context: Context, key: ApprovalKey, decision: ApprovalDecision): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                "${key.kind}:${key.id}:${decision.name}".hashCode(),
                Intent(context, ApprovalActionReceiver::class.java)
                    .putExtra(EXTRA_KIND, key.kind.name)
                    .putExtra(EXTRA_ID, key.id)
                    .putExtra(EXTRA_DECISION, decision.name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
