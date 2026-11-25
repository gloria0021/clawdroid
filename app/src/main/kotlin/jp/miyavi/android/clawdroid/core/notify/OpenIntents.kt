package jp.miyavi.android.clawdroid.core.notify

import android.content.Context
import android.content.Intent
import jp.miyavi.android.clawdroid.MainActivity

/**
 * 通知とランチャーのショートカットから開く行き先を、`MainActivity` 宛ての Intent の付加情報にする・読み戻す（spec 006 R7・spec 007 R2）。
 * ショートカットの Intent は端末に保存されるので、付加情報は文字列だけにする。
 */
object OpenIntents {
    const val EXTRA_OPEN = "jp.miyavi.android.clawdroid.OPEN"
    const val EXTRA_AGENT = "jp.miyavi.android.clawdroid.AGENT"
    const val EXTRA_SESSION = "jp.miyavi.android.clawdroid.SESSION"
    const val EXTRA_CWD = "jp.miyavi.android.clawdroid.CWD"
    const val EXTRA_JOB = "jp.miyavi.android.clawdroid.JOB"
    const val EXTRA_SHORTCUT_ID = "jp.miyavi.android.clawdroid.SHORTCUT_ID"
    const val OPEN_SESSION = "session"
    const val OPEN_NEW_CHAT = "new_chat"
    const val OPEN_AUTOMATION = "automation"
    const val OPEN_APPROVALS = "approvals"

    /** `MainActivity` を開く Intent（行き先が null ならアプリを開くだけ）。 */
    fun intent(context: Context, target: OpenTarget?): Intent =
        Intent(context, MainActivity::class.java).also { put(it, target) }

    /** 行き先を付加情報に書く。 */
    fun put(intent: Intent, target: OpenTarget?) {
        when (target) {
            is OpenTarget.Session -> intent.putExtra(EXTRA_OPEN, OPEN_SESSION).putExtra(EXTRA_AGENT, target.agentId).putExtra(EXTRA_SESSION, target.sessionKey)
            is OpenTarget.NewChat -> {
                intent.putExtra(EXTRA_OPEN, OPEN_NEW_CHAT).putExtra(EXTRA_AGENT, target.agentId)
                target.cwd?.let { intent.putExtra(EXTRA_CWD, it) }
            }
            is OpenTarget.Automation -> intent.putExtra(EXTRA_OPEN, OPEN_AUTOMATION).putExtra(EXTRA_JOB, target.jobId)
            OpenTarget.Approvals -> intent.putExtra(EXTRA_OPEN, OPEN_APPROVALS)
            null -> Unit
        }
    }

    /** 付加情報から行き先を読む。足りなければ null（アプリを開くだけ）。 */
    fun targetOf(intent: Intent?): OpenTarget? = when (intent?.getStringExtra(EXTRA_OPEN)) {
        OPEN_SESSION -> {
            val agent = intent.getStringExtra(EXTRA_AGENT)
            val session = intent.getStringExtra(EXTRA_SESSION)
            if (agent != null && session != null) OpenTarget.Session(agent, session) else null
        }
        OPEN_NEW_CHAT -> intent.getStringExtra(EXTRA_AGENT)?.let { OpenTarget.NewChat(it, intent.getStringExtra(EXTRA_CWD)) }
        OPEN_AUTOMATION -> intent.getStringExtra(EXTRA_JOB)?.let(OpenTarget::Automation)
        OPEN_APPROVALS -> OpenTarget.Approvals
        else -> null
    }

    /** ランチャーのショートカットから開いたときの、そのショートカットの ID。 */
    fun shortcutIdOf(intent: Intent?): String? = intent?.getStringExtra(EXTRA_SHORTCUT_ID)
}
