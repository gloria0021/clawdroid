package jp.miyavi.android.clawdroid.feature.chat.message

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.LocalInboundImages
import jp.miyavi.android.clawdroid.core.data.MessagePart
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.bodyContainerColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 添付ファイルの受け取りの状態（カードごと）。 */
private sealed interface FileState {
    data object Idle : FileState
    data class Saving(val read: Long, val total: Long, val openAfter: Boolean) : FileState
    data class Saved(val uri: Uri) : FileState
    data object Failed : FileState
}

/**
 * 会話の中の添付ファイル（Web 版と同じく、種類のアイコン・名前・種類と大きさ。マスター指定 2026-11-26）。
 * 押すと端末の「ダウンロード」に保存してから、そのファイルを開けるアプリで開く。右のボタンは保存だけ。
 * 受け取っているあいだは、ボタンの場所に割合の輪を出す（もう一度押すと中止）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FileAttachment(
    file: MessagePart.File,
    /** 添付の載ったメッセージの ID（保存先の記録の鍵。同じパスに作り直されたファイルを取り違えない）。 */
    messageId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val source = LocalInboundImages.current
    val container = remember(context) { (context.applicationContext as jp.miyavi.android.clawdroid.ClawdroidApp).container }
    val store = container.savedFiles
    val center = container.downloads
    // 保存先の記録の鍵: 接続先＋会話＋メッセージ＋添付の場所（CJ レビュー 2026-11-26）
    var storageKey by remember(file.url, messageId) { mutableStateOf<String?>(null) }
    var savedUri by remember(file.url, messageId) { mutableStateOf<Uri?>(null) }
    var failed by remember(file.url, messageId) { mutableStateOf(false) }
    // 保存したら開く（カードを押したとき）。保存はカードより長く生きるので、カードが残っているときだけ開く
    var openWhenSaved by remember(file.url, messageId) { mutableStateOf(false) }
    // 前に保存したことがあり、保存した先がまだあれば「開く」を出す（アプリを閉じても・会話を切り替えても残す）
    androidx.compose.runtime.LaunchedEffect(file.url, messageId, source) {
        val key = source?.storageKey(file.url, messageId) ?: return@LaunchedEffect
        storageKey = key
        store.existing(context.contentResolver, key)?.let { if (savedUri == null) savedUri = it }
    }
    // 保存は画面の外（ダウンロードの帯と同じ仕組み）で進む。カードはその進みと結果を見るだけ
    val tasks by center.tasks.collectAsStateWithLifecycle()
    val task = storageKey?.let { key -> tasks.firstOrNull { it.key == key } }
    androidx.compose.runtime.LaunchedEffect(storageKey) {
        val key = storageKey ?: return@LaunchedEffect
        center.events.collect { event ->
            if (event.key != key) return@collect
            when (event) {
                is jp.miyavi.android.clawdroid.core.data.DownloadEvent.Saved -> {
                    savedUri = event.result.uri
                    failed = false
                    if (openWhenSaved) open(context, file, event.result.uri)
                }
                is jp.miyavi.android.clawdroid.core.data.DownloadEvent.Failed -> failed = true
                is jp.miyavi.android.clawdroid.core.data.DownloadEvent.Cancelled -> Unit
            }
            openWhenSaved = false
        }
    }
    val state: FileState = when {
        task != null -> FileState.Saving(task.received, task.total ?: file.size ?: -1, openWhenSaved)
        savedUri != null -> FileState.Saved(savedUri!!)
        failed -> FileState.Failed
        else -> FileState.Idle
    }
    val colors = MaterialTheme.colorScheme

    fun open(uri: Uri) = open(context, file, uri)

    fun save(openAfter: Boolean) {
        val current = state
        if (current is FileState.Saved) {
            if (openAfter) open(current.uri)
            return
        }
        // 受け取り中にもう一度押したら中止
        task?.let { center.cancel(it.id); return }
        val key = storageKey ?: return
        val src = source ?: return
        val resolver = context.applicationContext.contentResolver
        failed = false
        openWhenSaved = openAfter
        center.startAttachment(file.name, key) { onProgress ->
            src.saveFile(resolver, file) { read, total -> onProgress(read, total.takeIf { it > 0 } ?: file.size) }.also { result ->
                // 記録はここで残す（カードが消えていても）
                if (result is jp.miyavi.android.clawdroid.core.data.DownloadResult.Saved) store.put(key, result.uri)
            }
        }
    }

    val meta = fileMeta(file, state)
    Row(
        modifier
            .widthIn(max = Dimens.messageImageMax)
            .clip(MaterialTheme.shapes.large)
            .background(bodyContainerColor())
            .clickable(onClickLabel = stringResource(R.string.chat_file_open)) { save(openAfter = true) }
            .padding(start = Spacing.m, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(painterResource(R.drawable.ic_huge_file), contentDescription = null, tint = colors.onSurfaceVariant)
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(
                meta,
                style = MaterialTheme.typography.labelMedium,
                color = if (state is FileState.Failed) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val saved = state as? FileState.Saved
        if (saved != null) {
            // 保存できたら、右のボタンは「開く」（Web 版と同じ。マスター指定 2026-11-26）
            androidx.compose.material3.TextButton(onClick = { open(saved.uri) }, modifier = Modifier.padding(end = Spacing.xs)) {
                Text(stringResource(R.string.chat_file_open))
            }
        } else Box(contentAlignment = Alignment.Center) {
            IconButton(onClick = { save(openAfter = false) }) {
                when (val s = state) {
                    is FileState.Saving -> {
                        val fraction = if (s.total > 0) (s.read.toFloat() / s.total).coerceIn(0f, 1f) else null
                        if (fraction != null) {
                            val shown by animateFloatAsState(fraction, MaterialTheme.motionScheme.fastSpatialSpec(), label = "fileProgress")
                            CircularWavyProgressIndicator(progress = { shown }, modifier = Modifier.size(Dimens.smallIcon + Spacing.xs))
                        } else {
                            androidx.compose.material3.LoadingIndicator(Modifier.size(Dimens.smallIcon + Spacing.xs))
                        }
                    }
                    else -> Icon(painterResource(R.drawable.ic_huge_download), contentDescription = stringResource(R.string.chat_file_save_cd))
                }
            }
        }
    }
}

/** 保存したファイルを開く。APK は端末の「ダウンロード」の画面から入れる（直接インストーラーを呼ぶには
 * REQUEST_INSTALL_PACKAGES が要り、Google Play の制限の対象になるため）。開けるアプリが無ければ同じ画面を開く。 */
private fun open(context: android.content.Context, file: MessagePart.File, uri: Uri) {
    val apk = file.mimeType == APK_MIME || file.name.endsWith(".apk", ignoreCase = true)
    if (apk) {
        runCatching { context.startActivity(jp.miyavi.android.clawdroid.core.data.downloadsFolderIntent()) }
        return
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, context.contentResolver.getType(uri) ?: file.mimeType ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, file.name))
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(jp.miyavi.android.clawdroid.core.data.downloadsFolderIntent()) }
    }
}

/** 2 行目: 種類と大きさ。受け取り中は量、保存できたら「保存しました」、失敗したら「保存できませんでした」。 */
@Composable
private fun fileMeta(file: MessagePart.File, state: FileState): String {
    val context = LocalContext.current
    return when (state) {
        is FileState.Saving -> if (state.total > 0) {
            stringResource(R.string.chat_image_progress, Formatter.formatShortFileSize(context, state.read), Formatter.formatShortFileSize(context, state.total))
        } else Formatter.formatShortFileSize(context, state.read)
        is FileState.Saved -> stringResource(R.string.chat_file_saved)
        FileState.Failed -> stringResource(R.string.chat_file_failed)
        FileState.Idle -> listOfNotNull(
            file.name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= MAX_EXTENSION }?.uppercase(),
            file.size?.let { Formatter.formatShortFileSize(context, it) },
        ).joinToString(" · ").ifEmpty { stringResource(R.string.chat_file_kind) }
    }
}

private const val MAX_EXTENSION = 8
private const val APK_MIME = "application/vnd.android.package-archive"

/** Gateway が受け付けなかった添付。理由とファイル名だけを出す（保存はできない）。 */
@Composable
internal fun AttachmentErrorRow(error: MessagePart.AttachmentError, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val reason = stringResource(
        when (error.code) {
            "unsupported-format" -> R.string.chat_attachment_unsupported
            "file-too-large" -> R.string.chat_attachment_too_large
            "file-not-found" -> R.string.chat_attachment_missing
            else -> R.string.chat_attachment_failed
        },
    )
    Row(
        modifier
            .widthIn(max = Dimens.messageImageMax)
            .clip(MaterialTheme.shapes.large)
            .background(bodyContainerColor())
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(painterResource(R.drawable.ic_huge_alert_circle), contentDescription = null, tint = colors.error)
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (error.name.isNotEmpty()) Text(error.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(reason, style = MaterialTheme.typography.labelMedium, color = colors.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
