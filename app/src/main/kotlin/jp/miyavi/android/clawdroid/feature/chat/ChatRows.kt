package jp.miyavi.android.clawdroid.feature.chat

import androidx.compose.runtime.Immutable
import jp.miyavi.android.clawdroid.core.data.ChatMessage
import jp.miyavi.android.clawdroid.core.data.MessagePart

/**
 * 会話の一覧の 1 行。続いたツールの実行は 1 行にまとめ、次にエージェントの応答が来たらその応答の見出しに入れる。
 * 変わっていない行は前と同じインスタンスを使い回す（履歴を取り直しても、表示済みの行を組み立て直さない）。
 */
@Immutable
data class ChatRow(
    /** LazyColumn の key（`chatRowKeys`）。 */
    val key: String,
    val items: List<MessageUi>,
    /** ツールだけの行ならその全部、応答の行なら応答の前に続いたツールの呼び出し。 */
    val tools: List<MessagePart.ToolCall>,
) {
    val last: MessageUi get() = items.last()

    /** 行の形ごとに LazyColumn の部品を使い回す。 */
    val contentType: Int get() {
        val message = last.message
        return when {
            message.notice != null -> CONTENT_NOTICE
            message.parts.isEmpty() -> CONTENT_MODEL_CHANGE
            message.parts.all { it is MessagePart.ToolCall } -> CONTENT_TOOLS
            message.role == ChatMessage.Role.USER -> CONTENT_USER
            else -> CONTENT_REPLY
        }
    }

    private companion object {
        const val CONTENT_NOTICE = 0
        const val CONTENT_MODEL_CHANGE = 1
        const val CONTENT_TOOLS = 2
        const val CONTENT_USER = 3
        const val CONTENT_REPLY = 4
    }
}

/**
 * 記録から一覧の行を作る。前の行と中身が同じメッセージは前の [MessageUi] を、メッセージが全部同じ行は前の [ChatRow] をそのまま使う。
 * Markdown の解析は作り直したメッセージだけ。メインスレッドの外で呼ぶ。
 */
internal fun buildChatRows(
    messages: List<ChatMessage>,
    previous: List<ChatRow>,
    timeFormatter: (Long) -> String,
    parseMarkdown: (ChatMessage) -> List<com.mikepenz.markdown.model.State>,
): List<ChatRow> {
    val oldItems = previous.flatMap { it.items }
    val oldByKey = chatRowKeys(oldItems.map { it.message.id }).zip(oldItems).toMap()
    val keys = chatRowKeys(messages.map { it.id })
    val items = formatMessages(messages, timeFormatter).mapIndexed { i, fresh ->
        val old = oldByKey[keys[i]]
        if (old != null && old.message == fresh.message && old.timeLabel == fresh.timeLabel &&
            old.elapsedMs == fresh.elapsedMs && old.modelChange == fresh.modelChange
        ) old else fresh.copy(markdown = parseMarkdown(fresh.message))
    }
    val groups = groupChatRows(items)
    val rowKeys = chatRowKeys(groups.map { it.first().message.id })
    val oldRows = previous.associateBy { it.key }
    return groups.mapIndexed { i, group ->
        val old = oldRows[rowKeys[i]]
        if (old != null && old.items.size == group.size && old.items.indices.all { old.items[it] === group[it] }) old
        else ChatRow(rowKeys[i], group, rowTools(group))
    }
}

/** 続いたツールの実行を 1 つにまとめ、次に来たエージェントの応答と同じ行にする。 */
private fun groupChatRows(items: List<MessageUi>): List<List<MessageUi>> = buildList {
    var tools = mutableListOf<MessageUi>()
    for (item in items) {
        val toolOnly = item.message.parts.isNotEmpty() && item.message.parts.all { it is MessagePart.ToolCall }
        when {
            toolOnly -> tools += item
            item.message.role == ChatMessage.Role.ASSISTANT && tools.isNotEmpty() -> {
                add(tools + item)
                tools = mutableListOf()
            }
            else -> {
                if (tools.isNotEmpty()) {
                    add(tools)
                    tools = mutableListOf()
                }
                add(listOf(item))
            }
        }
    }
    if (tools.isNotEmpty()) add(tools)
}

private fun rowTools(group: List<MessageUi>): List<MessagePart.ToolCall> {
    val last = group.last().message
    val toolOnly = last.parts.isNotEmpty() && last.parts.all { it is MessagePart.ToolCall }
    return (if (toolOnly) group else group.dropLast(1)).flatMap { it.message.parts.filterIsInstance<MessagePart.ToolCall>() }
}
