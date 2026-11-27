package jp.miyavi.android.sumahoclaw.feature.terminal

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.terminal.KeyHandler
import com.termux.terminal.RemoteTerminalBridge
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import jp.miyavi.android.sumahoclaw.core.data.ExitReason
import jp.miyavi.android.sumahoclaw.core.data.TerminalDenial
import jp.miyavi.android.sumahoclaw.core.data.TerminalDeniedException
import jp.miyavi.android.sumahoclaw.core.data.TerminalEvent
import jp.miyavi.android.sumahoclaw.core.data.TerminalRepository
import jp.miyavi.android.sumahoclaw.core.gateway.ConnectionState
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

/** ターミナルの状態（data-model の TerminalState）。 */
sealed interface TerminalStatus {
    data object Opening : TerminalStatus
    data object Reattaching : TerminalStatus
    data object Connected : TerminalStatus
    data class Exited(val exitCode: Long?) : TerminalStatus
    /** Gateway の保持時間を過ぎて消えた。 */
    data object Lost : TerminalStatus
    /** ほかの接続（PC の Control UI など）がこの PTY を取った。 */
    data object TakenElsewhere : TerminalStatus
    data class Denied(val reason: TerminalDenial) : TerminalStatus
}

/** 特殊キーの行から送るキー（FR-022）。 */
enum class SpecialKey { ESC, TAB, UP, DOWN, LEFT, RIGHT, INTERRUPT, ENTER }

/**
 * ターミナル（spec 002 US4）。Termux のエミュレータ（[bridge]）を持ち、構成の変更（回転）を越えて画面の中身を保つ。
 * 入力はエミュレータのキューから順に Gateway へ、出力は `terminal.data` を seq の順に流す。
 */
class TerminalViewModel(
    private val repository: TerminalRepository,
    gateway: GatewayClient,
    private val conversationKey: String,
    private val agentId: String?,
    private val cwd: String?,
) : ViewModel() {
    private val _status = MutableStateFlow<TerminalStatus>(TerminalStatus.Opening)
    val status: StateFlow<TerminalStatus> = _status.asStateFlow()

    /** 画面（TerminalView）へ描き直しを知らせる先。回転で作り直されるので差し替える。 */
    @Volatile var onScreenChanged: (() -> Unit)? = null
    var onCopy: ((String) -> Unit)? = null
    var onPasteRequest: (() -> String?)? = null
    /** 画面で選んでいる文字をコピーする（画面が持つ。選んでいなければ何もしない）。 */
    var copySelection: (() -> Unit)? = null
    /** 文字の大きさの倍率（ピンチ。画面を開き直しても続きから）。 */
    var textScale: Float = 1f

    /** 看板のマスコットの色（画面のテーマから。spec 005 R3）。 */
    @Volatile var bannerArgb: Int = 0xFFE0E0E0.toInt()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionClient: TerminalSessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) { onScreenChanged?.invoke() }
        override fun onTitleChanged(changedSession: TerminalSession) = Unit
        override fun onSessionFinished(finishedSession: TerminalSession) = Unit
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) { text?.let { onCopy?.invoke(it) } }
        override fun onPasteTextFromClipboard(session: TerminalSession?) = paste()
        // 意味のない触覚・音は出さない（AGENTS.md 2.8）
        override fun onBell(session: TerminalSession) = Unit
        override fun onColorsChanged(session: TerminalSession) { onScreenChanged?.invoke() }
        override fun onTerminalCursorStateChange(state: Boolean) = Unit
        override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
        override fun logError(tag: String?, message: String?) = Unit
        override fun logWarn(tag: String?, message: String?) = Unit
        override fun logInfo(tag: String?, message: String?) = Unit
        override fun logDebug(tag: String?, message: String?) = Unit
        override fun logVerbose(tag: String?, message: String?) = Unit
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
        override fun logStackTrace(tag: String?, e: Exception?) = Unit
    }

    val bridge: RemoteTerminalBridge = RemoteTerminalBridge(sessionClient, INITIAL_COLUMNS, INITIAL_ROWS, TRANSCRIPT_ROWS)

    private var sessionId: String? = null
    private var lastSeq = -1L
    private var started = false
    private var size = INITIAL_COLUMNS to INITIAL_ROWS
    private var eventsJob: Job? = null
    /** 看板を、シェルが画面を消したあとに描く待ち。 */
    @Volatile private var bannerPending = false
    private var resizeJob: Job? = null
    private val inputs = Channel<String>(Channel.UNLIMITED)

    init {
        // 入力のキューを別のスレッドで待ち、順番どおりに Gateway へ送る
        thread(name = "terminal-input", isDaemon = true) {
            val buffer = ByteArray(INPUT_BUFFER)
            while (true) {
                val n = bridge.readInput(buffer) ?: break
                if (n > 0) inputs.trySend(String(buffer, 0, n, Charsets.UTF_8))
            }
        }
        viewModelScope.launch {
            for (data in inputs) {
                val id = sessionId ?: continue
                if (_status.value != TerminalStatus.Connected) continue
                runCatching { repository.input(id, data) }
            }
        }
        // 切れてつながり直したら、続きにつなぎ直す
        viewModelScope.launch {
            gateway.state.map { it is ConnectionState.Connected }.distinctUntilChanged().collect { connected ->
                if (connected && started && _status.value in RESUMABLE) start()
            }
        }
    }

    /** 画面の大きさ（行と桁）が決まった・変わった（FR-023）。最初の 1 回で開く。 */
    fun onSize(columns: Int, rows: Int) {
        if (columns <= 0 || rows <= 0) return
        size = columns to rows
        if (!started) {
            started = true
            start()
            return
        }
        val id = sessionId ?: return
        resizeJob?.cancel()
        resizeJob = viewModelScope.launch {
            delay(RESIZE_DEBOUNCE_MS)
            runCatching { repository.resize(id, columns, rows) }
        }
    }

    /** 開く・つなぎ直す（保存済みの PTY が残っていればつなぎ直し、無ければ新しく開く）。 */
    fun start() {
        eventsJob?.cancel()
        viewModelScope.launch {
            _status.value = if (sessionId != null) TerminalStatus.Reattaching else TerminalStatus.Opening
            try {
                val replay = repository.reattach(conversationKey)
                if (replay != null) {
                    sessionId = replay.info.sessionId
                    // 手元に出ている分は描き直さず、離れていたあいだの分だけを足す
                    if (lastSeq < 0 || replay.seq == null || replay.seq > lastSeq) {
                        if (lastSeq >= 0) bridge.emulator.reset()
                        // 画面が空のところへつなぎ直す（アプリを開き直した・別の会話から戻った）ときも看板を出す。
                        // 続きの出力に画面を消す並びがあれば、その後ろに入れる（消されないように）
                        appendOnMain(if (lastSeq < 0) withBanner(replay.buffer) else replay.buffer)
                    }
                    lastSeq = replay.seq ?: lastSeq
                    listen(replay.info.sessionId)
                    runCatching { repository.resize(replay.info.sessionId, size.first, size.second) }
                } else {
                    if (sessionId != null) {
                        // 前の PTY が消えていた
                        sessionId = null
                        _status.value = TerminalStatus.Lost
                        return@launch
                    }
                    // 作業フォルダへの移動は、出力を受け始めてから送る（移動と画面の消去の出力を取りこぼさない）
                    val info = repository.open(conversationKey, agentId, null, size.first, size.second)
                    sessionId = info.sessionId
                    lastSeq = -1
                    val moveTo = cwd?.takeIf { it.isNotBlank() && it != info.cwd }
                    // 看板は手元で描く（spec 005 FR-020）。作業フォルダへ移るときはシェルが画面を消すので、消したあとに描く
                    if (moveTo != null) {
                        bannerPending = true
                        viewModelScope.launch {
                            delay(BANNER_FALLBACK_MS)
                            if (bannerPending) { bannerPending = false; appendOnMain(bannerAnsi(size.first, bannerArgb)) }
                        }
                    } else {
                        appendOnMain(bannerAnsi(size.first, bannerArgb))
                    }
                    listen(info.sessionId)
                    if (moveTo != null) runCatching { repository.input(info.sessionId, TerminalRepository.changeDirectoryCommand(moveTo)) }
                }
                _status.value = TerminalStatus.Connected
            } catch (e: TerminalDeniedException) {
                _status.value = TerminalStatus.Denied(e.reason)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _status.value = TerminalStatus.Denied(TerminalDenial.FAILED)
            }
        }
    }

    /** 消えた・終わったあと、新しく開き直す。 */
    fun reopen() {
        sessionId?.let { repository.forget(conversationKey) }
        sessionId = null
        lastSeq = -1
        bridge.emulator.reset()
        onScreenChanged?.invoke()
        start()
    }

    /** 終える（PTY ごと閉じる）。終わったら [then]。 */
    fun end(then: () -> Unit) {
        val id = sessionId ?: return then()
        viewModelScope.launch {
            repository.close(conversationKey, id)
            sessionId = null
            then()
        }
    }

    /** ほかで開かれた PTY を取り戻す。 */
    fun takeBack() = start()

    private fun listen(id: String) {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            repository.events(id).collect { event ->
                when (event) {
                    is TerminalEvent.Data -> {
                        // attach の buffer と重なった分は捨てる
                        if (event.seq <= lastSeq) return@collect
                        lastSeq = event.seq
                        val clearEnd = if (bannerPending) clearScreenEnd(event.data) else -1
                        if (clearEnd >= 0) {
                            bannerPending = false
                            appendOnMain(event.data.substring(0, clearEnd) + bannerAnsi(size.first, bannerArgb) + event.data.substring(clearEnd))
                        } else {
                            appendOnMain(event.data)
                        }
                    }
                    is TerminalEvent.Exit -> _status.value = when (event.reason) {
                        ExitReason.PROCESS_EXIT, ExitReason.CLOSED -> {
                            repository.forget(conversationKey)
                            TerminalStatus.Exited(event.exitCode)
                        }
                        ExitReason.DETACHED -> TerminalStatus.TakenElsewhere
                        // 接続が戻れば自動でつなぎ直す（init の監視）
                        ExitReason.DISCONNECTED, ExitReason.ERROR -> TerminalStatus.Reattaching
                    }
                }
            }
        }
    }

    private var shownBefore = false

    /**
     * ターミナルの画面を開いた（spec 005 US3。開くたびに看板を出す。マスター指定 2026-11-24）。
     * 最初の 1 回は開くときに描くので何もしない。2 回目からは、シェルに Ctrl+L（画面を消してプロンプトと入力中の文字を描き直す）を
     * 送り、消したあとに看板を入れる。vim などが画面を使っているあいだ（代替の画面）は邪魔をしない。
     */
    fun onScreenShown() {
        if (!shownBefore) { shownBefore = true; return }
        val id = sessionId ?: return
        if (_status.value != TerminalStatus.Connected || bridge.emulator.isAlternateBufferActive) return
        bannerPending = true
        viewModelScope.launch {
            runCatching { repository.input(id, CLEAR_SCREEN) }
            // 画面が消えなかった（シェルが Ctrl+L を扱わない）ときは、途中に割り込まないよう看板をあきらめる
            delay(REDRAW_WAIT_MS)
            bannerPending = false
        }
    }

    /** 看板を、続きの出力の最後の画面の消去の後ろ（無ければ先頭）に入れる。 */
    private fun withBanner(data: String): String {
        val banner = bannerAnsi(size.first, bannerArgb)
        val at = clearScreenEnd(data)
        return if (at < 0) banner + data else data.substring(0, at) + banner + data.substring(at)
    }

    private fun appendOnMain(data: String) {
        if (data.isEmpty()) return
        val bytes = data.toByteArray(Charsets.UTF_8)
        mainHandler.post {
            bridge.append(bytes)
            onScreenChanged?.invoke()
        }
    }

    /** 特殊キーの行（FR-022）。矢印は端末のモード（アプリケーションモード）に合わせた列で送る。 */
    fun sendKey(key: SpecialKey) {
        val emulator = bridge.emulator
        val code = when (key) {
            SpecialKey.ESC -> "\u001b"
            // 割り込み（Ctrl+C）。Ctrl の一時押しを使わずに 1 回で送る（spec 005 FR-001）
            SpecialKey.INTERRUPT -> "\u0003"
            SpecialKey.ENTER -> "\r"
            SpecialKey.TAB -> "\t"
            SpecialKey.UP -> KeyHandler.getCode(android.view.KeyEvent.KEYCODE_DPAD_UP, 0, emulator.isCursorKeysApplicationMode, emulator.isKeypadApplicationMode)
            SpecialKey.DOWN -> KeyHandler.getCode(android.view.KeyEvent.KEYCODE_DPAD_DOWN, 0, emulator.isCursorKeysApplicationMode, emulator.isKeypadApplicationMode)
            SpecialKey.LEFT -> KeyHandler.getCode(android.view.KeyEvent.KEYCODE_DPAD_LEFT, 0, emulator.isCursorKeysApplicationMode, emulator.isKeypadApplicationMode)
            SpecialKey.RIGHT -> KeyHandler.getCode(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, 0, emulator.isCursorKeysApplicationMode, emulator.isKeypadApplicationMode)
        } ?: return
        bridge.write(code)
    }

    /** クリップボードの文字を貼り付ける。bracketed paste と制御文字の除去はエミュレータに任せる（spec 005 FR-011〜013）。 */
    fun paste() {
        val text = onPasteRequest?.invoke()?.takeIf(String::isNotEmpty) ?: return
        if (_status.value == TerminalStatus.Connected) bridge.emulator.paste(text)
    }

    override fun onCleared() {
        // 画面を離れても PTY は残す（戻ってきたら続きから。FR-024）
        bridge.close()
        inputs.close()
    }

    private companion object {
        const val INITIAL_COLUMNS = 80
        const val INITIAL_ROWS = 24
        const val TRANSCRIPT_ROWS = 2_000
        const val INPUT_BUFFER = 4_096
        const val RESIZE_DEBOUNCE_MS = 150L
        /** 画面を消す出力が来なくても、この時間が過ぎたら看板を描く。 */
        const val BANNER_FALLBACK_MS = 3_000L
        /** Ctrl+L（多くのシェルで画面を消して描き直す）。 */
        const val CLEAR_SCREEN = "\u000c"
        const val REDRAW_WAIT_MS = 1_500L
        val RESUMABLE = setOf<TerminalStatus>(TerminalStatus.Reattaching, TerminalStatus.Connected)
    }
}

/** 画面を消す制御の並び（`clear` の出力）の終わりの位置。無ければ -1。 */
internal fun clearScreenEnd(data: String): Int {
    var end = -1
    for (seq in listOf("\u001b[2J", "\u001b[3J")) {
        val at = data.lastIndexOf(seq)
        if (at >= 0) end = maxOf(end, at + seq.length)
    }
    return end
}
