package jp.miyavi.android.clawdroid.core.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.TypefaceSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.ApprovalDecision
import jp.miyavi.android.clawdroid.core.data.ApprovalKey
import jp.miyavi.android.clawdroid.core.data.PendingApproval
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState

/**
 * 通知のチャンネルと、各通知の組み立て・出し消し（spec 006 R6、contracts/notifications.md）。
 * 応答は会話ごと・承認は承認ごとに id を固定して書き換える。
 */
internal class NotificationCenter(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        val system = context.getSystemService(NotificationManager::class.java)
        // チャンネルの重要度は作ったあと変えられないので、ポップアップ・振動を既定にした版で作り直す（古い版は消す。マスター指定 2026-11-24）
        system.notificationChannels.filter { it.id in OLD_CHANNELS || it.id.startsWith(OLD_REPLIES_PREFIX) && !it.id.startsWith(CHANNEL_REPLIES_PREFIX) }
            .forEach { system.deleteNotificationChannel(it.id) }
        system.createNotificationChannelGroup(NotificationChannelGroup(GROUP_REPLIES, context.getString(R.string.notify_group_replies)))
        system.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_CONNECTION, context.getString(R.string.notify_channel_connection), NotificationManager.IMPORTANCE_MIN).apply {
                    description = context.getString(R.string.notify_channel_connection_desc)
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_APPROVALS, context.getString(R.string.notify_channel_approvals), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.notify_channel_approvals_desc)
                    enableVibration(true)
                },
                NotificationChannel(CHANNEL_AUTOMATION, context.getString(R.string.notify_channel_automation), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.notify_channel_automation_desc)
                    enableVibration(true)
                },
            ),
        )
    }

    /** 通知を出せるか（許可）。 */
    fun permitted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            manager.areNotificationsEnabled()

    /** エージェントごとの応答のチャンネル（音・オン・オフは端末の設定で変える。FR-031）。 */
    private fun replyChannel(agentId: String, agentName: String): String {
        val id = "$CHANNEL_REPLIES_PREFIX$agentId"
        val system = context.getSystemService(NotificationManager::class.java)
        val existing = system.getNotificationChannel(id)
        if (existing == null || existing.name != agentName) {
            system.createNotificationChannel(
                // ポップアップ（ヘッドアップ）と振動を既定でオン。音・オン・オフは端末の設定で変えられる
                NotificationChannel(id, agentName, NotificationManager.IMPORTANCE_HIGH).apply {
                    group = GROUP_REPLIES
                    description = context.getString(R.string.notify_channel_replies_desc, agentName)
                    enableVibration(true)
                },
            )
        }
        return id
    }

    fun connection(state: ConnectionState): Notification =
        NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_stat_clawdroid)
            .setContentTitle(context.getString(if (state is ConnectionState.Connected) R.string.notify_connection_connected else R.string.notify_connection_reconnecting))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent(null, REQUEST_CONNECTION))
            .build()

    fun showReply(agentId: String, agentName: String, sessionKey: String, title: String, text: String, hideOnLock: Boolean, avatar: android.graphics.Bitmap? = null) {
        val channel = replyChannel(agentId, agentName)
        val group = "$GROUP_REPLIES:$agentId"
        val notification = base(channel, hideOnLock, context.getString(R.string.notify_reply_hidden))
            .setContentTitle(title)
            .setSubText(agentName)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setGroup(group)
            .setLargeIcon(avatar)
            .setAutoCancel(true)
            .setContentIntent(openIntent(OpenTarget.Session(agentId, sessionKey), sessionKey.hashCode()))
            .build()
        post(replyId(sessionKey), notification)
        // エージェントごとにまとめる要約（FR-012）
        post(
            group.hashCode(),
            base(channel, hideOnLock, agentName).setContentTitle(agentName).setGroup(group).setGroupSummary(true).setAutoCancel(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN).build(),
        )
    }

    fun showApproval(item: PendingApproval, agentName: String?, sessionTitle: String?, hideOnLock: Boolean, avatar: android.graphics.Bitmap? = null) {
        val body = approvalBody(item, sessionTitle)
        val builder = base(CHANNEL_APPROVALS, hideOnLock, context.getString(R.string.notify_approval_hidden))
            .setContentTitle(context.getString(R.string.notify_approval_title, agentName ?: context.getString(R.string.notify_agent_fallback)))
            .setContentText(item.detail.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setLargeIcon(avatar)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter((item.expiresAt - System.currentTimeMillis()).coerceAtLeast(1))
            .setContentIntent(openIntent(OpenTarget.Approvals, approvalId(item.key)))
        // 許される決定だけ、許可 → 常に → 拒否 の順。ロック中は解除を求める（FR-024）
        listOf(ApprovalDecision.ONCE, ApprovalDecision.ALWAYS, ApprovalDecision.DENY).filter { it in item.decisions }.forEach { decision ->
            val label = when (decision) {
                ApprovalDecision.ONCE -> R.string.notify_approval_once
                ApprovalDecision.ALWAYS -> R.string.notify_approval_always
                ApprovalDecision.DENY -> R.string.notify_approval_deny
            }
            builder.addAction(
                NotificationCompat.Action.Builder(0, context.getString(label), ApprovalActionReceiver.intent(context, item.key, decision))
                    .setAuthenticationRequired(true)
                    .build(),
            )
        }
        post(approvalId(item.key), builder.build())
    }

    /** 承認の決定の結果を示す（少しあとに消える）。 */
    fun approvalResult(key: ApprovalKey, text: Int, openOnTap: Boolean) {
        val builder = NotificationCompat.Builder(context, CHANNEL_APPROVALS)
            .setSmallIcon(R.drawable.ic_stat_clawdroid)
            .setContentTitle(context.getString(text))
            .setSilent(true)
            .setAutoCancel(true)
        if (openOnTap) builder.setContentIntent(openIntent(OpenTarget.Approvals, approvalId(key))) else builder.setTimeoutAfter(RESULT_TIMEOUT_MS)
        post(approvalId(key), builder.build())
    }

    fun cancelApproval(key: ApprovalKey) = manager.cancel(approvalId(key))

    fun cancel(id: Int) = manager.cancel(id)

    fun showAutomationFailure(failure: AutomationFailure, hideOnLock: Boolean) {
        val text = failure.error ?: context.getString(R.string.notify_automation_failed)
        post(
            failure.jobId.hashCode(),
            base(CHANNEL_AUTOMATION, hideOnLock, context.getString(R.string.notify_automation_failed))
                .setContentTitle(failure.name ?: context.getString(R.string.notify_automation_failed))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openIntent(OpenTarget.Automation(failure.jobId), failure.jobId.hashCode()))
                .build(),
        )
    }

    fun showTest() {
        post(
            TEST_ID,
            NotificationCompat.Builder(context, replyChannel(TEST_AGENT, context.getString(R.string.notify_test_title)))
                .setSmallIcon(R.drawable.ic_stat_clawdroid)
                .setContentTitle(context.getString(R.string.notify_test_title))
                .setContentText(context.getString(R.string.notify_test_body))
                .setAutoCancel(true)
                .setContentIntent(openIntent(null, TEST_ID))
                .build(),
        )
    }

    /** 承認の全文。コマンドは等幅、続けて対象・警告・会話（長すぎる分は「続きはアプリで」）。 */
    private fun approvalBody(item: PendingApproval, sessionTitle: String?): CharSequence {
        val detail = if (item.detail.length > APPROVAL_TEXT_LIMIT) item.detail.take(APPROVAL_TEXT_LIMIT) + context.getString(R.string.notify_approval_more) else item.detail
        return SpannableStringBuilder().apply {
            append(detail)
            setSpan(TypefaceSpan(Typeface.MONOSPACE), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            item.target.forEach { append("\n").append(context.getString(R.string.notify_approval_target, it)) }
            item.warning?.takeIf(String::isNotBlank)?.let { append("\n").append(it) }
            sessionTitle?.let { append("\n").append(context.getString(R.string.notify_approval_session, it)) }
        }
    }

    private fun base(channel: String, hideOnLock: Boolean, publicTitle: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_clawdroid)
            .setWhen(System.currentTimeMillis())
            .apply {
                // ロック画面で伏せるときは、題だけの公開版を付ける（FR-032）。端末の設定に従うときは何も変えない
                if (hideOnLock) {
                    setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    setPublicVersion(
                        NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_stat_clawdroid).setContentTitle(publicTitle).build(),
                    )
                }
            }

    private fun openIntent(target: OpenTarget?, requestCode: Int): PendingIntent {
        val intent = OpenIntents.intent(context, target).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }


    @android.annotation.SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        if (permitted()) runCatching { manager.notify(id, notification) }
    }

    companion object {
        const val CONNECTION_ID = 6_001
        private const val TEST_ID = 6_002
        private const val REQUEST_CONNECTION = 6_003
        private const val CHANNEL_CONNECTION = "connection"
        private const val CHANNEL_APPROVALS = "approvals-v2"
        private const val CHANNEL_AUTOMATION = "automation-failures-v2"
        private const val CHANNEL_REPLIES_PREFIX = "replies-v2-"
        /** 重要度を変える前の版（消す）。 */
        private val OLD_CHANNELS = setOf("approvals", "automation-failures")
        private const val OLD_REPLIES_PREFIX = "replies-"
        private const val GROUP_REPLIES = "replies"
        private const val TEST_AGENT = "test"
        /** 承認の全文の上限（通知の表示が切れる目安。R4-1）。 */
        private const val APPROVAL_TEXT_LIMIT = 5_000
        private const val RESULT_TIMEOUT_MS = 4_000L

        fun replyId(sessionKey: String): Int = "reply:$sessionKey".hashCode()
        fun approvalId(key: ApprovalKey): Int = "approval:${key.kind}:${key.id}".hashCode()
    }
}
