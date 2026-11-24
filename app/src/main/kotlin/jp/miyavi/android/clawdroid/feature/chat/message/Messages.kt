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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
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
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.ChatMessage
import jp.miyavi.android.clawdroid.core.data.MessagePart
import jp.miyavi.android.clawdroid.core.data.ModelChange
import jp.miyavi.android.clawdroid.core.data.SystemNotice
import jp.miyavi.android.clawdroid.core.data.shortModelName
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.appMarkdownPadding
import jp.miyavi.android.clawdroid.core.designsystem.appMarkdownTypography
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
    onOpenImage: (ImageGallery) -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCopy: ((String) -> Unit)? = null,
    /** 文を入力欄へ写す（送り直し・手直し用）。送れないときは null。 */
    onReuse: ((String) -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        val images = remember(message.parts) { message.parts.filterIsInstance<MessagePart.Image>() }
        // 画像は 1 行にまとめる（複数なら横にスクロール。マスター指定 2026-11-24）。画像と本文のあいだは 12dp
        if (images.isNotEmpty()) MessageImages(images, onOpenImage, alignEnd = true, modifier = Modifier.padding(bottom = Spacing.m))
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
        // 下に時刻と操作（再利用・コピー）。並びはエージェントの応答の下と同じ（マスター指定 2026-11-24）
        val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant
        val actionable = text.isNotBlank()
        if (timeLabel != null || actionable) Row(verticalAlignment = Alignment.CenterVertically) {
            if (timeLabel != null) {
                Text(
                    timeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = metadataColor,
                    modifier = Modifier.padding(top = if (actionable) 0.dp else Spacing.xs, end = if (actionable) Spacing.xs else 0.dp),
                )
            }
            if (actionable) {
                onReuse?.let { reuse ->
                    IconButton(onClick = { reuse(text) }) {
                        Icon(painterResource(R.drawable.ic_huge_edit), contentDescription = stringResource(R.string.chat_reuse_cd), tint = metadataColor, modifier = Modifier.offset(x = Spacing.xs).size(Dimens.smallIcon))
                    }
                }
                onCopy?.let { copy ->
                    IconButton(onClick = { copy(text) }) {
                        Icon(painterResource(R.drawable.ic_huge_copy), contentDescription = stringResource(R.string.action_copy), tint = metadataColor, modifier = Modifier.offset(x = if (onReuse != null) -Spacing.xs else 0.dp).size(Dimens.smallIcon))
                    }
                }
            }
        }
    }
}

/** エージェントの応答。全幅の素の文字（Markdown）。下に操作行。 */
@Composable
fun AssistantMessage(
    message: ChatMessage,
    /** 本文の解析結果（Text の部分と同じ順）。無ければその場で解析する。 */
    markdown: List<com.mikepenz.markdown.model.State> = emptyList(),
    agent: Agent?,
    /** この応答の前に続いたツールの実行（応答の見出しの行にまとめて出す）。 */
    precedingTools: List<MessagePart.ToolCall> = emptyList(),
    timeLabel: String?,
    elapsedMs: Long?,
    onCopy: (String) -> Unit,
    onReply: ((String) -> Unit)?,
    onOpenImage: (ImageGallery) -> Unit,
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
        var textIndex = 0
        // 続いて並ぶ画像は 1 行にまとめる（複数なら横にスクロール）。全画面ではメッセージの画像すべてを横にめくれる
        val allImages = remember(message.parts) { message.parts.filterIsInstance<MessagePart.Image>() }
        val blocks = remember(message.parts) { groupImageRuns(message.parts) }
        blocks.forEach { block ->
            when (block) {
                is PartBlock.Images -> MessageImages(block.images, { gallery ->
                    // 押した画像の、メッセージ全体の中での位置から開く
                    val image = gallery.images[gallery.initialIndex]
                    onOpenImage(ImageGallery(allImages, allImages.indexOf(image).coerceAtLeast(0)))
                }, alignEnd = false)
                is PartBlock.Single -> when (val part = block.part) {
                    is MessagePart.Text -> MarkdownText(part.markdown, markdown.getOrNull(textIndex++))
                    is MessagePart.Video -> MessageVideo(part.url, onOpenVideo)
                    is MessagePart.Image, is MessagePart.ToolCall -> Unit
                }
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

/**
 * システムのお知らせの区切り（コンテキストの圧縮など）。線のあいだにアイコンと一言、押すと中身（要約など）を開く。
 * 利用者の吹き出しに見せない（Control UI の区切り・お知らせと同じ扱い）。
 */
@Composable
fun SystemNoticeDivider(
    notice: SystemNotice,
    body: String,
    parsed: com.mikepenz.markdown.model.State?,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = when (notice) {
        is SystemNotice.Compaction -> {
            val before = notice.tokensBefore
            val after = notice.tokensAfter
            if (before != null && after != null) {
                stringResource(R.string.chat_notice_compaction_tokens, formatReportedOutputTokens(before), formatReportedOutputTokens(after))
            } else stringResource(R.string.chat_notice_compaction)
        }
        SystemNotice.InjectedContext -> stringResource(R.string.chat_notice_injected_context)
        SystemNotice.TaskNotification -> stringResource(R.string.chat_notice_task)
    }
    val icon = when (notice) {
        is SystemNotice.Compaction -> R.drawable.ic_huge_layers
        SystemNotice.InjectedContext -> R.drawable.ic_huge_info
        SystemNotice.TaskNotification -> R.drawable.ic_huge_notification
    }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val hasBody = body.isNotBlank()
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.compactTouchTarget)
                .then(
                    if (hasBody) Modifier.clip(MaterialTheme.shapes.medium).clickable(
                        onClickLabel = stringResource(if (expanded) R.string.chat_notice_hide else R.string.chat_notice_show),
                    ) { expanded = !expanded } else Modifier,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            HorizontalDivider(Modifier.weight(1f))
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(Spacing.l))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, textAlign = TextAlign.Center)
            if (hasBody) {
                Icon(
                    painterResource(if (expanded) R.drawable.ic_huge_arrow_up else R.drawable.ic_huge_arrow_down),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(Spacing.l),
                )
            }
            HorizontalDivider(Modifier.weight(1f))
        }
        if (hasBody) {
            AnimatedVisibility(visible = expanded, enter = expandEnter(), exit = shrinkExit()) {
                Column(Modifier.padding(top = Spacing.s)) { MarkdownText(body, parsed) }
            }
        }
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
    /** コンテキストを圧縮しているあいだは、ネタの言い回しの代わりにそう出す。 */
    compacting: Boolean = false,
) {
    ReplyLayout(
        agent,
        showAvatar = true,
        modifier,
        avatarBusy = running,
        header = {
            if (running) ThinkingIndicator(
                startedAt,
                reportedOutputTokens = reportedOutputTokens,
                label = if (compacting) stringResource(R.string.chat_compacting) else null,
            )
        },
    ) {
        // 未完の Markdown をチャンクごとに再解析せず、確定後に Markdown として描画する。
        if (text.isNotBlank()) Text(text, style = bodyTextStyle())
    }
}

/** 応答の横のアバターを押したときに、そのエージェントの設定を開く（spec 004 FR-002）。会話の画面が渡す。 */
val LocalOpenAgentSettings = androidx.compose.runtime.staticCompositionLocalOf<((String) -> Unit)?> { null }

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
                if (showAvatar) {
                    val open = LocalOpenAgentSettings.current
                    val openLabel = androidx.compose.ui.res.stringResource(jp.miyavi.android.clawdroid.R.string.agent_settings_open_cd)
                    Box(
                        if (open != null && agent != null) {
                            Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable(onClickLabel = openLabel) { open(agent.id) }
                        } else Modifier,
                    ) {
                        AgentAvatar(agent, label = agent?.name, size = Dimens.avatarLarge, busy = avatarBusy)
                    }
                }
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
        // 件数だけを差分の数と同じ桁の回転で数え上げる（実行中に増えていく）。前後の文言は訳文の数字の位置で分ける
        val label = pluralStringResource(R.plurals.chat_tools_count, parts.size, parts.size).split(parts.size.toString(), limit = 2)
        jp.miyavi.android.clawdroid.core.designsystem.RollingCounter(
            parts.size, MaterialTheme.typography.labelLarge, tint,
            prefix = label.first(), suffix = label.getOrElse(1) { "" }, animateFirst = false,
        )
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
private fun MarkdownText(markdown: String, parsed: com.mikepenz.markdown.model.State?) {
    SelectionContainer {
        // 段落のリンクは長押しでメニュー（コピー・ダウンロード・ブラウザで開く）
        val components = remember { linkAwareComponents() }
        if (parsed != null) {
            // 解析済み（ViewModel で作った）。組み立てるだけ
            Markdown(state = parsed, colors = markdownColor(), typography = appMarkdownTypography(), padding = appMarkdownPadding(), components = components)
        } else {
            Markdown(content = markdown, colors = markdownColor(), typography = appMarkdownTypography(), padding = appMarkdownPadding(), components = components)
        }
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

/** メッセージの部品を、続いて並ぶ画像のまとまりとそれ以外に分ける。 */
private sealed interface PartBlock {
    data class Images(val images: List<MessagePart.Image>) : PartBlock
    data class Single(val part: MessagePart) : PartBlock
}

private fun groupImageRuns(parts: List<MessagePart>): List<PartBlock> {
    val blocks = mutableListOf<PartBlock>()
    for (part in parts) {
        if (part is MessagePart.Image) {
            val last = blocks.lastOrNull()
            if (last is PartBlock.Images) blocks[blocks.lastIndex] = PartBlock.Images(last.images + part)
            else blocks += PartBlock.Images(listOf(part))
        } else {
            blocks += PartBlock.Single(part)
        }
    }
    return blocks
}

/**
 * 画像の並び（Gerbil の画像の列に倣う。マスター指定 2026-11-24）。1 枚なら今までどおり大きく、
 * 複数なら同じ高さのサムネイルを 1 行に並べて横にスクロールする。押すと全画面で、その画像から横にめくれる。
 */
@Composable
internal fun MessageImages(images: List<MessagePart.Image>, onOpen: (ImageGallery) -> Unit, alignEnd: Boolean, modifier: Modifier = Modifier) {
    if (images.size == 1) {
        MessageImage(images.first(), { onOpen(ImageGallery(images, 0)) }, modifier)
        return
    }
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, if (alignEnd) Alignment.End else Alignment.Start),
        // 影（3dp）が切れないよう、上下に少しあける
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.xs),
    ) {
        items(images.size, key = { index -> "${index}:${images[index].url ?: images[index].base64?.hashCode()}" }) { index ->
            MessageThumbnail(images[index]) { onOpen(ImageGallery(images, index)) }
        }
    }
}

/** 並びの中の 1 枚。高さをそろえ、幅は画像の縦横比（分かるまでは正方形）。 */
@Composable
private fun MessageThumbnail(image: MessagePart.Image, onOpen: () -> Unit) {
    val cd = stringResource(R.string.chat_image_cd)
    val shape = MaterialTheme.shapes.large
    val bitmap = rememberBase64Bitmap(image.base64) ?: rememberInboundImage(image.url)
    val ratio = bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) }?.coerceIn(MIN_THUMB_RATIO, MAX_THUMB_RATIO) ?: 1f
    val thumbModifier = Modifier
        .height(Dimens.messageImageRowHeight)
        .aspectRatio(ratio)
        .shadow(Dimens.imageElevation, shape)
        .clip(shape)
        .background(bodyContainerColor())
        .clickable(onClick = onOpen)
    val url = remember(image.url) { image.url?.let(::webUri) }
    when {
        bitmap != null -> Image(bitmap, contentDescription = cd, contentScale = ContentScale.Crop, modifier = thumbModifier)
        isInboundMediaUrl(image.url) -> Box(thumbModifier)
        url != null -> AsyncImage(model = url, contentDescription = cd, contentScale = ContentScale.Crop, modifier = thumbModifier)
        else -> Box(thumbModifier)
    }
}

/** サムネイルの縦横比の範囲（極端に細長い画像で列が崩れないように）。 */
private const val MIN_THUMB_RATIO = 0.5f
private const val MAX_THUMB_RATIO = 2f

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
    // その場で展開する（後から届くと高さが変わり、スクロール位置が飛ぶため）。一度展開した画像は覚えておき、
    // スクロールで画面に入り直したときは展開し直さない
    base64?.let { data ->
        Base64BitmapCache.get(data) ?: runCatching {
            val bytes = Base64.decode(data.substringAfter("base64,"), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()?.also { Base64BitmapCache.put(data, it) }
    }
}

/** 展開した base64 の画像（数を限って覚えておく）。 */
private object Base64BitmapCache {
    private val cache = android.util.LruCache<String, ImageBitmap>(BASE64_BITMAP_CACHE_SIZE)
    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}

private const val BASE64_BITMAP_CACHE_SIZE = 32

/** 利用者の面の最大幅（本文の最大幅に対する割合）。 */
private const val USER_BUBBLE_MAX_FRACTION = 0.8f
