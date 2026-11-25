package jp.miyavi.android.clawdroid.core.designsystem

import jp.miyavi.android.clawdroid.core.data.downloadsFolderIntent
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.DownloadCenter
import jp.miyavi.android.clawdroid.core.data.DownloadEvent
import jp.miyavi.android.clawdroid.core.data.DownloadSource
import jp.miyavi.android.clawdroid.core.data.DownloadTask
import kotlinx.coroutines.delay

/**
 * アプリ全体のダウンロードの表示。どの画面でも、上部（ステータスバーのすぐ下）に透過効果の帯で進み具合と中止を出す。
 * 終わったら同じ帯で「保存しました・開く」か失敗を少しだけ出して消える。
 */
@Composable
fun DownloadBanner(center: DownloadCenter, hazeState: HazeState, modifier: Modifier = Modifier) {
    val tasks by center.tasks.collectAsStateWithLifecycle()
    var finished by remember { mutableStateOf<DownloadEvent?>(null) }
    LaunchedEffect(center) { center.events.collect { finished = it } }
    LaunchedEffect(finished) {
        if (finished != null) {
            delay(RESULT_VISIBLE_MS)
            finished = null
        }
    }
    val first = tasks.firstOrNull()
    val result = finished?.takeIf { first == null }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = first != null || result != null,
        enter = slideInVertically(motion.defaultSpatialSpec()) { -it },
        exit = slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec()),
        // 画面の右上に浮くピル（会話の画面の「Clawdroid」など）の 1 段下に出し、ピルを押せるままにする（マスター指定 2026-11-25）
        modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = Dimens.floatingButtonSize + Spacing.s),
    ) {
        // 消える動きのあいだは、最後の中身を出しておく
        val lastTask = remember { LastValue<DownloadTask>() }
        val lastResult = remember { LastValue<DownloadEvent>() }
        first?.let { lastTask.value = it }
        result?.let { lastResult.value = it }
        GlassPanel(
            hazeState = hazeState,
            fixedOpacity = MENU_OPACITY,
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s).widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            val shownTask = first ?: lastTask.value.takeIf { result == null && lastResult.value == null }
            if (shownTask != null && (first != null || result == null)) {
                ProgressRow(shownTask, tasks.size, onCancel = { center.cancel(shownTask.id) })
            } else {
                (result ?: lastResult.value)?.let { ResultRow(it) { finished = null } }
            }
        }
    }
}

private class LastValue<T> {
    var value: T? = null
}

@Composable
private fun ProgressRow(task: DownloadTask, count: Int, onCancel: () -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_huge_download),
            contentDescription = null,
            modifier = Modifier.padding(start = Spacing.l).padding(vertical = Spacing.m),
        )
        Column(Modifier.weight(1f).padding(start = Spacing.m, top = Spacing.m, bottom = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val name = if (count > 1) stringResource(R.string.download_notify_many, task.name, count - 1) else task.name
            val size = listOfNotNull(
                Formatter.formatShortFileSize(context, task.received),
                task.total?.let { Formatter.formatShortFileSize(context, it) },
            ).joinToString(" / ")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(
                    if (task.waiting) stringResource(R.string.download_waiting) else size,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            val fraction = task.fraction
            if (fraction != null && !task.waiting) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        IconButton(onClick = onCancel, modifier = Modifier.padding(horizontal = Spacing.xs)) {
            Icon(painterResource(R.drawable.ic_huge_close), stringResource(R.string.files_download_cancel_cd))
        }
    }
}

@Composable
private fun ResultRow(event: DownloadEvent, onDone: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    // 開けるアプリが無かったときは、同じ帯で知らせる（Toast は使わない。AGENTS.md 2.7）
    var noApp by remember(event) { mutableStateOf(false) }
    Row(Modifier.padding(start = Spacing.l, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        val (text, color) = if (noApp) stringResource(R.string.files_no_app) to colors.error else when (event) {
            is DownloadEvent.Saved -> stringResource(R.string.files_downloaded, event.result.displayName) to colors.onSurface
            is DownloadEvent.Cancelled -> stringResource(R.string.files_download_cancelled) to colors.onSurfaceVariant
            is DownloadEvent.Failed -> stringResource(
                when (event.reason) {
                    DownloadSource.Reason.TOO_LARGE -> R.string.files_download_too_large
                    DownloadSource.Reason.NO_HTTP -> R.string.files_download_no_http
                    DownloadSource.Reason.MISSING -> R.string.files_download_missing
                    DownloadSource.Reason.FAILED -> R.string.files_download_failed
                },
            ) to colors.error
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = Spacing.m))
        if (event is DownloadEvent.Saved && !noApp) {
            TextButton(onClick = {
                // ファイルを直接開かず、端末の Download フォルダの画面を出す（種類の分からないファイルでテキストエディタなどが
                // 候補に並ぶのを避ける。マスター指定 2026-11-25）。その画面が無い端末だけ、ファイルを開く
                val opened = runCatching { context.startActivity(downloadsFolderIntent()) }.isSuccess ||
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW).setDataAndType(event.result.uri, event.result.mimeType)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        )
                    }.isSuccess
                if (opened) onDone() else noApp = true
            }) { Text(stringResource(R.string.files_open_download)) }
        }
    }
}

/** 終わったあとの表示を出しておく時間。 */
private const val RESULT_VISIBLE_MS = 6_000L
