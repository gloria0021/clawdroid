package jp.miyavi.android.clawdroid.feature.chat.message

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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.ChatMessage
import jp.miyavi.android.clawdroid.core.data.MessagePart
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.bodyContainerColor
import jp.miyavi.android.clawdroid.core.designsystem.bodyTextStyle
import jp.miyavi.android.clawdroid.core.designsystem.codeTextStyle
import jp.miyavi.android.clawdroid.core.designsystem.expandEnter
import jp.miyavi.android.clawdroid.core.designsystem.shrinkExit

/** 利用者のメッセージ。右寄せの角丸の面（本文の下地の色。影なし。AGENTS.md 2.5）。 */
@Composable
fun UserMessage(message: ChatMessage, onOpenImage: (MessagePart.Image) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        message.parts.filterIsInstance<MessagePart.Image>().forEach { image ->
            MessageImage(image, onOpenImage, Modifier.padding(bottom = Spacing.xs))
        }
        val text = message.text
        if (text.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.widthIn(max = Dimens.maxReadingWidth * USER_BUBBLE_MAX_FRACTION),
            ) {
                SelectionContainer {
                    Text(text, style = bodyTextStyle(), modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s))
                }
            }
        }
    }
}

/** エージェントの応答。全幅の素の文字（Markdown）。下に操作行。 */
@Composable
fun AssistantMessage(
    message: ChatMessage,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onOpenImage: (MessagePart.Image) -> Unit,
    onShowFull: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        message.parts.forEach { part ->
            when (part) {
                is MessagePart.Text -> MarkdownText(part.markdown)
                is MessagePart.Image -> MessageImage(part, onOpenImage)
                is MessagePart.ToolCall -> ToolCallRow(part)
            }
        }
        if (message.truncated && onShowFull != null) {
            Text(
                stringResource(R.string.chat_show_full),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.heightIn(min = Dimens.compactTouchTarget).clickable(onClick = onShowFull).padding(vertical = Spacing.xs),
            )
        }
        val text = message.text
        if (text.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                IconButton(onClick = { onCopy(text) }) {
                    Icon(painterResource(R.drawable.ic_huge_copy), contentDescription = stringResource(R.string.action_copy))
                }
                IconButton(onClick = { onShare(text) }) {
                    Icon(painterResource(R.drawable.ic_huge_share), contentDescription = stringResource(R.string.action_share))
                }
            }
        }
    }
}

/** 生成中の応答（逐次表示）。 */
@Composable
fun LiveAssistantMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) { MarkdownText(text) }
}

/** ツールだけの結果のメッセージ（role = toolResult）。 */
@Composable
fun ToolResultMessage(message: ChatMessage, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        message.parts.filterIsInstance<MessagePart.ToolCall>().forEach { ToolCallRow(it) }
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
fun ToolCallRow(part: MessagePart.ToolCall, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(part.callId, part.isResult) { mutableStateOf(false) }
    val label = stringResource(R.string.chat_tool_expand_label)
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = label) { expanded = !expanded }
            .semantics { onClick(label) { expanded = !expanded; true } }
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
private fun MessageImage(image: MessagePart.Image, onOpen: (MessagePart.Image) -> Unit, modifier: Modifier = Modifier) {
    val cd = stringResource(R.string.chat_image_cd)
    val shape = MaterialTheme.shapes.large
    val imageModifier = modifier
        .widthIn(max = Dimens.maxReadingWidth / 2)
        .heightIn(max = Dimens.maxReadingWidth / 2)
        .clip(shape)
        .background(bodyContainerColor())
        .clickable { onOpen(image) }
    val bitmap = rememberBase64Bitmap(image.base64)
    when {
        bitmap != null -> Image(bitmap, contentDescription = cd, contentScale = ContentScale.Fit, modifier = imageModifier)
        image.url != null -> AsyncImage(model = image.url, contentDescription = cd, contentScale = ContentScale.Fit, modifier = imageModifier)
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
