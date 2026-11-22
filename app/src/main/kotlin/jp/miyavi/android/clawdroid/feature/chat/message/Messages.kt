package jp.miyavi.android.clawdroid.feature.chat.message

import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.ChatMessage
import jp.miyavi.android.clawdroid.core.data.MessagePart
import jp.miyavi.android.clawdroid.core.data.ModelChange
import jp.miyavi.android.clawdroid.core.data.shortModelName
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
    /** この応答の前に続いたツールの実行（応答の見出しの行にまとめて出す）。 */
    precedingTools: List<MessagePart.ToolCall> = emptyList(),
    timeLabel: String?,
    elapsedMs: Long?,
    onCopy: (String) -> Unit,
    onReply: ((String) -> Unit)?,
    onOpenImage: (MessagePart.Image) -> Unit,
    onOpenVideo: (String) -> Unit,
    onShowFull: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tools = remember(precedingTools, message.parts) { precedingTools + message.parts.filterIsInstance<MessagePart.ToolCall>() }
    var toolsOpen by rememberSaveable(message.id) { mutableStateOf(false) }
    ReplyLayout(
        agent,
        showAvatar = message.role == ChatMessage.Role.ASSISTANT,
        modifier,
        // アイコンの横: 回答にかかった時間とツールの実行の開閉
        header = if (elapsedMs != null || tools.isNotEmpty()) ({
            elapsedMs?.let { ElapsedLabel(it) }
            if (tools.isNotEmpty()) ToolToggle(tools, toolsOpen) { toolsOpen = !toolsOpen }
        }) else null,
    ) {
        if (tools.isNotEmpty()) ToolDetails(tools, toolsOpen)
        message.parts.forEach { part ->
            when (part) {
                is MessagePart.Text -> MarkdownText(part.markdown)
                is MessagePart.Image -> MessageImage(part, onOpenImage)
                is MessagePart.Video -> MessageVideo(part.url, onOpenVideo)
                is MessagePart.ToolCall -> Unit
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
        if (timeLabel != null || text.isNotBlank()) {
            val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (timeLabel != null) {
                    Text(
                        timeLabel,
                        modifier = Modifier.weight(1f, fill = false).padding(end = Spacing.xs),
                        style = MaterialTheme.typography.labelMedium,
                        color = metadataColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (text.isNotBlank()) {
                    val reply = onReply?.takeIf { message.role == ChatMessage.Role.ASSISTANT }
                    if (reply != null) {
                        IconButton(onClick = { reply(text) }) {
                            Icon(painterResource(R.drawable.ic_huge_ai_chat), contentDescription = stringResource(R.string.chat_reply_cd), tint = metadataColor, modifier = Modifier.offset(x = Spacing.xs).size(Dimens.smallIcon))
                        }
                    }
                    IconButton(onClick = { onCopy(text) }) {
                        Icon(painterResource(R.drawable.ic_huge_copy), contentDescription = stringResource(R.string.action_copy), tint = metadataColor, modifier = Modifier.offset(x = if (reply != null) -Spacing.xs else 0.dp).size(Dimens.smallIcon))
                    }
                }
            }
        }
    }
}

/**
 * ここから別のモデルが答えることを示す区切り。線のあいだに「前 → 後」のモデル名。
 * 短い名前が同じになるとき（プロバイダだけの切り替え）は、プロバイダ付きの名前を出す。
 */
@Composable
fun ModelChangeDivider(change: ModelChange, modifier: Modifier = Modifier) {
    val (from, to) = remember(change) {
        val from = shortModelName(change.from)
        val to = shortModelName(change.to)
        if (from == to) change.from to change.to else from to to
    }
    val description = stringResource(R.string.chat_model_changed_cd, from, to)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_huge_model), contentDescription = null, tint = color, modifier = Modifier.size(Spacing.l))
        Text(
            stringResource(R.string.chat_model_changed, from, to),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            textAlign = TextAlign.Center,
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

/** 推論・ツール実行・本文生成を同じ応答ヘッダーで表示する。 */
@Composable
fun LiveAssistantMessage(
    text: String,
    agent: Agent?,
    running: Boolean,
    startedAt: Long?,
    reportedOutputTokens: Long?,
    modifier: Modifier = Modifier,
) {
    ReplyLayout(
        agent,
        showAvatar = true,
        modifier,
        avatarBusy = running,
        header = {
            if (running) ThinkingIndicator(startedAt, outputText = text, reportedOutputTokens = reportedOutputTokens)
        },
    ) {
        // 未完の Markdown をチャンクごとに再解析せず、確定後に Markdown として描画する。
        if (text.isNotBlank()) Text(text, style = bodyTextStyle())
    }
}

/**
 * エージェントの応答の外枠。1 行目にキャラクターのアイコンと、その横の見出し（回答時間・ツールの開閉）。
 * 本文はアイコンの下の行から横幅いっぱいに出す。
 */
@Composable
private fun ReplyLayout(
    agent: Agent?,
    showAvatar: Boolean,
    modifier: Modifier,
    avatarBusy: Boolean = false,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (showAvatar || header != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (showAvatar) AgentAvatar(agent, label = agent?.name, size = Dimens.avatarLarge, busy = avatarBusy)
                if (header != null) Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs / 2)) { header() }
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) { content() }
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
        ToolToggle(parts, expanded) { expanded = !expanded }
        ToolDetails(parts, expanded)
    }
}

/** ツールの実行の件数と開閉の矢印（押すと開閉）。 */
@Composable
fun ToolToggle(parts: List<MessagePart.ToolCall>, expanded: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Row(
        modifier.clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onToggle)
            .heightIn(min = Dimens.compactTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (parts.any { it.isError }) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(painterResource(R.drawable.ic_huge_tool), null, Modifier.size(Spacing.l), tint = tint)
        Text(pluralStringResource(R.plurals.chat_tools_count, parts.size, parts.size), style = MaterialTheme.typography.labelLarge, color = tint)
        Icon(painterResource(R.drawable.ic_huge_arrow_down), contentDescription = null, modifier = Modifier.size(Spacing.l).rotate(if (expanded) 180f else 0f), tint = tint)
    }
}

/** 開いたときのツールの実行の一覧（横幅いっぱい）。 */
@Composable
fun ToolDetails(parts: List<MessagePart.ToolCall>, expanded: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = expanded, enter = expandEnter(), exit = shrinkExit(), modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            parts.forEach { ToolCallRow(it) }
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
