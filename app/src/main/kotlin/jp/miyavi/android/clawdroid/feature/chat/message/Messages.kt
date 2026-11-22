package jp.miyavi.android.clawdroid.feature.chat.message

import androidx.compose.ui.draw.shadow
import jp.miyavi.android.clawdroid.core.designsystem.accentContainer
import jp.miyavi.android.clawdroid.core.data.rememberInboundImage
import jp.miyavi.android.clawdroid.core.data.isInboundMediaUrl
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.ChatMessage
import jp.miyavi.android.clawdroid.core.data.MessagePart
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.bodyContainerColor
import jp.miyavi.android.clawdroid.core.designsystem.bodyTextStyle
import jp.miyavi.android.clawdroid.core.designsystem.codeTextStyle
import jp.miyavi.android.clawdroid.core.designsystem.expandEnter
import jp.miyavi.android.clawdroid.core.designsystem.shrinkExit
import jp.miyavi.android.clawdroid.feature.chat.AgentAvatar

/** 利用者のメッセージ。右寄せの角丸の面（本文の下地の色。影なし。AGENTS.md 2.5）。 */
@Composable
fun UserMessage(
    message: ChatMessage,
    timeLabel: String?,
    onOpenImage: (MessagePart.Image) -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        message.parts.filterIsInstance<MessagePart.Image>().forEach { image ->
            // 画像と本文のあいだは 12dp あける
            MessageImage(image, onOpenImage, Modifier.padding(bottom = Spacing.m))
        }
        message.parts.filterIsInstance<MessagePart.Video>().forEach { MessageVideo(it.url, onOpenVideo) }
        val text = message.text
        if (text.isNotBlank()) {
            // 利用者のメッセージの面はテーマの濃い色（OpenClaw なら赤み）。背景のオフホワイトと見分けやすくする
            val accent = accentContainer()
            // 右寄せで、幅は中身に合わせる。長くても画面（本文の幅）の 8 割まで
            Box(Modifier.fillMaxWidth(USER_BUBBLE_MAX_FRACTION), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    color = accent.container,
                    contentColor = accent.content,
                    shape = MaterialTheme.shapes.large,
                ) {
                    // Markdown の部品は横幅いっぱいに広がるので、利用者の文は素の文字で描く
                    SelectionContainer {
                        Text(text, style = bodyTextStyle(), modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s))
                    }
                }
            }
        }
        if (timeLabel != null) {
            Text(
                timeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/** エージェントの応答。全幅の素の文字（Markdown）。下に操作行。 */
@Composable
fun AssistantMessage(
    message: ChatMessage,
    agent: Agent?,
    timeLabel: String?,
    elapsedMs: Long?,
    onCopy: (String) -> Unit,
    onReply: ((String) -> Unit)?,
    onOpenImage: (MessagePart.Image) -> Unit,
    onOpenVideo: (String) -> Unit,
    onShowFull: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ReplyLayout(
        agent,
        showAvatar = message.role == ChatMessage.Role.ASSISTANT,
        modifier,
        header = elapsedMs?.let { ms -> { ElapsedLabel(ms) } },
    ) {
        message.parts.forEach { part ->
            when (part) {
                is MessagePart.Text -> MarkdownText(part.markdown)
                is MessagePart.Image -> MessageImage(part, onOpenImage)
                is MessagePart.Video -> MessageVideo(part.url, onOpenVideo)
                is MessagePart.ToolCall -> Unit
            }
        }
        val tools = remember(message.parts) { message.parts.filterIsInstance<MessagePart.ToolCall>() }
        if (tools.isNotEmpty()) ToolGroup(tools)
        if (message.truncated && onShowFull != null) {
            Text(
                stringResource(R.string.chat_show_full),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.heightIn(min = Dimens.compactTouchTarget).clickable(onClick = onShowFull).padding(vertical = Spacing.xs),
            )
        }
        val text = message.text
        if (timeLabel != null) {
            Text(timeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (text.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (onReply != null && message.role == ChatMessage.Role.ASSISTANT) {
                    IconButton(onClick = { onReply(text) }) {
                        Icon(painterResource(R.drawable.ic_huge_ai_chat), contentDescription = stringResource(R.string.chat_reply_cd), modifier = Modifier.size(Dimens.smallIcon))
                    }
                }
                IconButton(onClick = { onCopy(text) }) {
                    Icon(painterResource(R.drawable.ic_huge_copy), contentDescription = stringResource(R.string.action_copy), modifier = Modifier.size(Dimens.smallIcon))
                }
            }
        }
    }
}

/** 生成中の応答（逐次表示）。 */
@Composable
fun LiveAssistantMessage(text: String, agent: Agent?, modifier: Modifier = Modifier) {
    ReplyLayout(agent, showAvatar = true, modifier) { MarkdownText(text) }
}

/**
 * エージェントの応答の外枠。いちばん上に回答にかかった時間、その下にキャラクターのアイコンと本文。
 */
@Composable
private fun ReplyLayout(
    agent: Agent?,
    showAvatar: Boolean,
    modifier: Modifier,
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        header?.invoke()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (showAvatar) AgentAvatar(agent, label = agent?.name, size = Dimens.avatarLarge)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                content()
            }
        }
    }
}

/** 回答にかかった時間。 */
@Composable
private fun ElapsedLabel(elapsedMs: Long) {
    val seconds = (elapsedMs / 1_000).coerceAtLeast(1)
    val duration = if (seconds < 60) {
        stringResource(R.string.chat_elapsed_seconds, seconds)
    } else {
        stringResource(R.string.chat_elapsed_minutes_seconds, seconds / 60, seconds % 60)
    }
    Text(
        stringResource(R.string.chat_elapsed, duration),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 連続したツール実行をひとつの行にまとめる。 */
@Composable
fun ToolGroup(parts: List<MessagePart.ToolCall>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                .clickable { expanded = !expanded }
                .heightIn(min = Dimens.minTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (parts.any { it.isError }) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(painterResource(R.drawable.ic_huge_tool), null, Modifier.size(Spacing.l), tint = tint)
            Text(
                pluralStringResource(R.plurals.chat_tools_count, parts.size, parts.size),
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(R.drawable.ic_huge_arrow_down),
                contentDescription = null,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
                tint = tint,
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandEnter(), exit = shrinkExit()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                parts.forEach { ToolCallRow(it) }
            }
        }
    }
}

@Composable
private fun MarkdownText(markdown: String) {
    SelectionContainer {
        Markdown(
            content = markdown,
            colors = markdownColor(),
            typography = markdownTypography(
                text = bodyTextStyle(),
                paragraph = bodyTextStyle(),
                code = codeTextStyle(),
            ),
            padding = markdownPadding(listItemTop = Spacing.xs, listItemBottom = Spacing.xs),
        )
    }
}

/** ツールの実行。折りたたみの行で、展開すると引数と出力を見られる（FR-024）。 */
@Composable
private fun ToolCallRow(part: MessagePart.ToolCall, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(part.callId, part.isResult) { mutableStateOf(false) }
    val label = stringResource(R.string.chat_tool_expand_label)
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = label) { expanded = !expanded }
            .heightIn(min = Dimens.compactTouchTarget)
            .padding(vertical = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(
                painterResource(R.drawable.ic_huge_tool),
                contentDescription = null,
                tint = if (part.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Spacing.l),
            )
            Text(
                listOfNotNull(part.name, part.detail).joinToString("  "),
                style = MaterialTheme.typography.labelMedium,
                color = if (part.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandEnter(), exit = shrinkExit()) {
            Surface(color = bodyContainerColor(), shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(top = Spacing.xs)) {
                SelectionContainer {
                    Text(
                        listOfNotNull(part.detail, part.result).joinToString("\n\n"),
                        style = codeTextStyle(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MessageImage(image: MessagePart.Image, onOpen: (MessagePart.Image) -> Unit, modifier: Modifier = Modifier) {
    val cd = stringResource(R.string.chat_image_cd)
    val shape = MaterialTheme.shapes.large
    val imageModifier = modifier
        .widthIn(max = Dimens.messageImageMax)
        .heightIn(max = Dimens.messageImageMax)
        // 背景と同化しないよう、少し浮かせる（入力欄と同じく影の例外。マスター指定）
        .shadow(Dimens.imageElevation, shape)
        .clip(shape)
        .background(bodyContainerColor())
        .clickable { onOpen(image) }
    val url = remember(image.url) { image.url?.let(::webUri) }
    val bitmap = rememberBase64Bitmap(image.base64) ?: rememberInboundImage(image.url)
    when {
        bitmap != null -> Image(bitmap, contentDescription = cd, contentScale = ContentScale.Fit, modifier = imageModifier)
        isInboundMediaUrl(image.url) -> Box(imageModifier.size(Dimens.imagePlaceholder))
        url != null -> AsyncImage(model = url, contentDescription = cd, contentScale = ContentScale.Fit, modifier = imageModifier)
    }
}

/** Base64 の画像を一度だけ復号する。 */
@Composable
fun rememberBase64Bitmap(base64: String?): ImageBitmap? = remember(base64) {
    base64?.let {
        runCatching {
            val bytes = Base64.decode(it.substringAfter("base64,"), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
}

/** 利用者の面の最大幅（本文の最大幅に対する割合）。 */
private const val USER_BUBBLE_MAX_FRACTION = 0.8f
