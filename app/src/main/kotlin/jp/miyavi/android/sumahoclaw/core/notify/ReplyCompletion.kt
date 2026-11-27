package jp.miyavi.android.sumahoclaw.core.notify

import jp.miyavi.android.sumahoclaw.core.data.ChatMessage
import jp.miyavi.android.sumahoclaw.core.data.MessagePart

/** 実行中 → 止まった で完了。初めて見た行（前の状態が無い）では完了としない。 */
internal fun isRunCompleted(wasRunning: Boolean?, running: Boolean): Boolean = wasRunning == true && !running

/**
 * 通知にする応答。最後の利用者の文より後の、最後のエージェントの応答（エラーは除く）。無ければ null。
 */
internal fun replyToNotify(messages: List<ChatMessage>): ChatMessage? {
    val lastUser = messages.indexOfLast { it.role == ChatMessage.Role.USER }
    return messages.drop(lastUser + 1).lastOrNull { message ->
        message.role == ChatMessage.Role.ASSISTANT && !message.isError && message.notice == null &&
            message.parts.any { it is MessagePart.Text && it.markdown.isNotBlank() || it is MessagePart.Image }
    }
}

/** 通知の本文。Markdown の記号を除いた冒頭（[limit] 文字まで）。文が無く画像だけなら [imageLabel]。 */
internal fun notificationText(message: ChatMessage, imageLabel: String, limit: Int = REPLY_TEXT_LIMIT): String {
    val text = plainText(message.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.markdown })
    if (text.isEmpty()) return imageLabel
    return if (text.length <= limit) text else text.take(limit).trimEnd() + "…"
}

/** Markdown の記号を落として 1 段落の文にする（通知は装飾を描けないため）。 */
internal fun plainText(markdown: String): String = markdown
    .replace(Regex("```[^\\n]*\\n?"), "")
    .replace(Regex("`([^`]*)`"), "$1")
    .replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
    .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
    .replace(Regex("(?m)^\\s{0,3}(#{1,6}|>+|[-*+]|\\d+\\.)\\s+"), "")
    .replace(Regex("(\\*\\*|__|~~)(.+?)\\1"), "$2")
    .replace(Regex("(?<![\\w*])[*_](?!\\s)(.+?)(?<!\\s)[*_](?![\\w*])"), "$1")
    .replace(Regex("<[^>]+>"), "")
    .replace(Regex("\\s+"), " ")
    .trim()

private const val REPLY_TEXT_LIMIT = 300
