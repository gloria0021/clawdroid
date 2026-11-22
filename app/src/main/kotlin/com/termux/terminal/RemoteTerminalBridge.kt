package com.termux.terminal

/**
 * Termux の TerminalSession を、端末内のプロセスではなく Gateway の PTY につなぐための橋（spec 002 research R6）。
 *
 * TerminalSession は final で、ふつうは最初の大きさが決まったときに端末内でシェルを起動する。ここでは
 * 起動の前にエミュレータを先に入れておき（mEmulator がある間は起動しない）、入力の行き先のキューを
 * Gateway へ流す。パッケージ内だけで見える項目に触るため、Termux と同じパッケージに置く。
 */
class RemoteTerminalBridge(
    client: TerminalSessionClient,
    columns: Int,
    rows: Int,
    transcriptRows: Int,
) {
    val session: TerminalSession = TerminalSession("", "", emptyArray(), emptyArray(), transcriptRows, client)

    init {
        // 入力（キー・貼り付け・端末からの応答）をキューに積ませる。プロセスは無いので、この番号は使われない
        session.mShellPid = Int.MAX_VALUE
        session.mEmulator = TerminalEmulator(session, columns, rows, CELL_SIZE, CELL_SIZE, transcriptRows, client)
    }

    val emulator: TerminalEmulator get() = session.mEmulator

    /** Gateway からの出力を画面に流す（メインスレッドで呼ぶ）。 */
    fun append(bytes: ByteArray) {
        session.mEmulator.append(bytes, bytes.size)
    }

    /**
     * 入力を 1 塊ずつ待って返す（別のスレッドで回す）。閉じたら null。
     * キー・IME・貼り付け・端末の応答（DSR など）がすべてここに来る。
     */
    fun readInput(buffer: ByteArray): Int? {
        val n = session.mTerminalToProcessIOQueue.read(buffer, true)
        return n.takeIf { it > 0 } ?: if (n == 0) 0 else null
    }

    /** 特殊キーの行などから、文字列をそのまま入力として送る。 */
    fun write(text: String) = session.write(text)

    fun close() {
        session.mTerminalToProcessIOQueue.close()
        session.mShellPid = -1
    }

    private companion object {
        /** 最初の文字の大きさ（すぐ TerminalView の実際の値で作り直される）。 */
        const val CELL_SIZE = 1
    }
}
