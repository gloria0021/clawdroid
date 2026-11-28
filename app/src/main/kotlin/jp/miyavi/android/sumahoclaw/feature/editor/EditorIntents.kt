package jp.miyavi.android.sumahoclaw.feature.editor

import android.content.Context
import android.content.Intent
import jp.miyavi.android.sumahoclaw.core.data.FileRoot

/** エディターの開き方（spec 008 contracts/editor-intents.md）。 */
data class EditorLaunch(val target: EditTarget, val handoff: String? = null, val line: Int = 0)

object EditorIntents {
    private const val KIND = "editor.kind"
    private const val AGENT = "editor.agentId"
    private const val SESSION = "editor.sessionKey"
    private const val PATH = "editor.path"
    private const val CHAT = "editor.chatKey"
    private const val TEXT = "editor.textId"
    private const val HANDOFF = "editor.handoff"
    private const val LINE = "editor.line"

    fun intent(context: Context, launch: EditorLaunch): Intent =
        write(Intent(context, EditorActivity::class.java), launch)

    /** アプリの中からの Intent に詰める。 */
    fun write(intent: Intent, launch: EditorLaunch): Intent = intent.apply {
        when (val t = launch.target) {
            is EditTarget.GatewayFile -> when (val root = t.root) {
                is FileRoot.Session -> { putExtra(KIND, "session"); putExtra(AGENT, root.agentId); putExtra(SESSION, root.sessionKey) }
                is FileRoot.AgentFile -> { putExtra(KIND, "agent"); putExtra(AGENT, root.agentId) }
                is FileRoot.Workspace -> error("ワークスペースは読むだけ")
            }.also { putExtra(PATH, t.path) }
            is EditTarget.PastedText -> { putExtra(KIND, "paste"); putExtra(CHAT, t.chatKey); putExtra(TEXT, t.textId) }
            is EditTarget.LocalDocument -> error("端末のファイルはほかのアプリから開く")
        }
        launch.handoff?.let { putExtra(HANDOFF, it) }
        putExtra(LINE, launch.line)
    }

    /**
     * Intent から読む。ほかのアプリからは、外向きの `ExternalEditorActivity` に来た `content://` の VIEW / EDIT だけを
     * 端末のファイルとして受け、アプリの中の extras は読まない（ほかのアプリに Gateway のファイルを開かせない。plan 憲章 IV）。
     * アプリの中の `EditorActivity` は外に出していない（exported="false"）ので、extras はアプリからしか来ない。
     */
    fun read(intent: Intent?, fromOutside: Boolean): EditorLaunch? {
        intent ?: return null
        if (fromOutside) {
            if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_EDIT) return null
            val data = intent.data ?: return null
            if (data.scheme != "content") return null
            return EditorLaunch(EditTarget.LocalDocument(data))
        }
        val line = intent.getIntExtra(LINE, 0)
        val handoff = intent.getStringExtra(HANDOFF)
        val target = when (intent.getStringExtra(KIND)) {
            "session" -> EditTarget.GatewayFile(
                FileRoot.Session(intent.getStringExtra(SESSION) ?: return null, intent.getStringExtra(AGENT) ?: return null),
                intent.getStringExtra(PATH) ?: return null,
            )
            "agent" -> EditTarget.GatewayFile(FileRoot.AgentFile(intent.getStringExtra(AGENT) ?: return null), intent.getStringExtra(PATH) ?: return null)
            "paste" -> EditTarget.PastedText(intent.getStringExtra(CHAT) ?: return null, intent.getStringExtra(TEXT) ?: return null)
            else -> return null
        }
        return EditorLaunch(target, handoff, line)
    }
}
