package jp.miyavi.android.sumahoclaw.feature.editor

import android.net.Uri
import jp.miyavi.android.sumahoclaw.core.data.FileRoot
import jp.miyavi.android.sumahoclaw.core.data.OpenedFile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 編集の対象（spec 008 data-model）。 */
sealed interface EditTarget {
    /** 下書きの鍵（既存の作業フォルダ・エージェントのファイルの鍵と同じ形）。 */
    val draftKey: String

    /** 表示と保存の名前。 */
    val name: String

    data class GatewayFile(val root: FileRoot, val path: String) : EditTarget {
        override val draftKey: String get() = when (root) {
            is FileRoot.Session -> "session:${root.sessionKey}:$path"
            is FileRoot.AgentFile -> "agent:${root.agentId}:$path"
            is FileRoot.Workspace -> "workspace:${root.agentId}:$path"
        }
        override val name: String get() = path.substringAfterLast('/')
    }

    /** 入力欄の「コピーしたテキスト」のタイル。 */
    data class PastedText(val chatKey: String, val textId: String) : EditTarget {
        override val draftKey: String get() = "paste:$chatKey:$textId"
        override val name: String get() = "pasted-text.txt"
    }

    /** ほかのアプリから渡されたファイル。名前は開いてから分かる。 */
    data class LocalDocument(val uri: Uri, override val name: String = uri.lastPathSegment?.substringAfterLast('/').orEmpty()) : EditTarget {
        override val draftKey: String get() = "uri:$uri"
    }
}

/** 呼び出し元が読み込み済みの中身（Intent に本文を載せないため。research R3）。 */
sealed interface HandoffContent {
    data class Gateway(val file: OpenedFile) : HandoffContent
    data class Text(val text: String) : HandoffContent
}

/** プロセスの中だけの受け渡し口。取り出したら消す。 */
class EditorHandoff {
    private val items = ConcurrentHashMap<String, HandoffContent>()

    fun put(content: HandoffContent): String = UUID.randomUUID().toString().also { items[it] = content }

    fun take(id: String?): HandoffContent? = id?.let(items::remove)
}

/** エディターで保存した結果（呼び出し元が受けて表示を差し替える）。 */
sealed interface EditorResult {
    data class Saved(val target: EditTarget.GatewayFile, val file: OpenedFile) : EditorResult
    data class PasteEdited(val chatKey: String, val textId: String, val text: String) : EditorResult
}

class EditorResults {
    private val _results = MutableSharedFlow<EditorResult>(extraBufferCapacity = 8)
    val results: SharedFlow<EditorResult> = _results.asSharedFlow()

    fun emit(result: EditorResult) {
        _results.tryEmit(result)
    }
}

/**
 * 開いているエディター（FR-007）。同じ対象が新しく開かれたら、前の画面に下書きを書かせて閉じる。
 */
class OpenEditors {
    private val open = ConcurrentHashMap<String, Pair<Any, suspend () -> Unit>>()

    /** 登録する。前に同じ対象を開いていた画面があれば、その「下書きを書いて閉じる」を返す。 */
    fun register(key: String, owner: Any, closeKeepingDraft: suspend () -> Unit): (suspend () -> Unit)? =
        open.put(key, owner to closeKeepingDraft)?.takeIf { it.first !== owner }?.second

    fun unregister(key: String, owner: Any) {
        open.computeIfPresent(key) { _, current -> if (current.first === owner) null else current }
    }
}
