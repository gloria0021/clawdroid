package jp.miyavi.android.sumahoclaw.core.notify

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** 通知・ランチャーのショートカットから開く行き先（spec 006 R7・spec 007 R3）。 */
sealed interface OpenTarget {
    data class Session(val agentId: String, val sessionKey: String) : OpenTarget
    /** そのエージェントの新しい会話。[cwd] があればその作業フォルダで始める（セッションは最初の送信で作られる）。 */
    data class NewChat(val agentId: String, val cwd: String?) : OpenTarget
    data class Automation(val jobId: String) : OpenTarget
    /** 承認の表示はアプリ全体のシートに出るので、アプリを開くだけ。 */
    data object Approvals : OpenTarget
}

/** アプリが前にあるか・どの会話を開いているか、と、通知から開く行き先の受け渡し。 */
class ForegroundState {
    private val _inForeground = MutableStateFlow(false)
    val inForeground: StateFlow<Boolean> = _inForeground.asStateFlow()

    /** 画面で開いている会話（新しいチャット・ほかの画面なら null）。 */
    val openSessionKey = MutableStateFlow<String?>(null)

    private val _openRequests = MutableSharedFlow<OpenTarget>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val openRequests: SharedFlow<OpenTarget> = _openRequests.asSharedFlow()

    fun setForeground(value: Boolean) { _inForeground.value = value }
    fun open(target: OpenTarget) { _openRequests.tryEmit(target) }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consumed() = _openRequests.resetReplayCache()

    /** その会話を今まさに画面で見ているか（通知を出さない。FR-011）。 */
    fun isViewing(sessionKey: String): Boolean = inForeground.value && openSessionKey.value == sessionKey
}
