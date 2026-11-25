package jp.miyavi.android.clawdroid.core.data

/**
 * Claude CLI の会話では、Gateway の履歴に同じ送信が 2 つ並ぶことがある。OpenClaw が受け付けたときの記録（送信 ID 付き）と、
 * Claude 自身の会話の記録から取り込んだ写し（送信 ID なし）で、受け付けから実行までが長く空くと重ならずに残る。
 * 送信 ID の付いた利用者のメッセージのすぐ後ろに、送信 ID の無い同じ文面の利用者のメッセージが来たら、写しとして外す。
 * 送信 ID はどちらの送信にも付くので、利用者が同じ文を続けて送った場合は外さない。
 */
internal fun withoutTranscriptEchoes(messages: List<ChatMessage>): List<ChatMessage> {
    if (messages.size < 2) return messages
    val out = ArrayList<ChatMessage>(messages.size)
    for (message in messages) {
        val previous = out.lastOrNull()
        val echo = previous != null &&
            message.role == ChatMessage.Role.USER && previous.role == ChatMessage.Role.USER &&
            message.runId == null && previous.runId != null &&
            message.text.trim().isNotEmpty() && message.text.trim() == previous.text.trim() &&
            message.parts.count { it is MessagePart.Image } <= previous.parts.count { it is MessagePart.Image }
        if (!echo) out += message
    }
    return if (out.size == messages.size) messages else out
}

/**
 * 1 回のリセットで、Gateway の区切り（種類が分からない）と、利用者が送った `/reset` などのコマンドの両方が残ることがある。
 * リセットのお知らせが隣り合ったら、種類の分からない区切りを外し、コマンドの方（送信 ID を持つ）を残す。
 */
internal fun withoutDuplicateResets(messages: List<ChatMessage>): List<ChatMessage> {
    fun ChatMessage?.reset() = this?.notice as? SystemNotice.Reset
    val out = messages.filterIndexed { i, message ->
        message.reset()?.kind != ResetKind.UNKNOWN ||
            listOf(messages.getOrNull(i - 1), messages.getOrNull(i + 1)).none { it.reset()?.let { r -> r.kind != ResetKind.UNKNOWN } == true }
    }
    return if (out.size == messages.size) messages else out
}
