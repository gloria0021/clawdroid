package jp.miyavi.android.sumahoclaw.feature.files

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.MAX_UPLOAD_BYTES
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassPanel
import jp.miyavi.android.sumahoclaw.core.designsystem.MENU_OPACITY
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import kotlinx.coroutines.delay

/**
 * アップロードの帯（ダウンロードの帯と同じ透過効果の面。マスター指定 2026-11-26）。上部に進み具合と中止、
 * 終わったら同じ帯で結果（置けた件数・容量の上限を超えたもの・置けなかった件数）を少しだけ出して消える。
 * ツリーのパネルを開いたままでも見えるよう、パネルより上に重ねる。
 */
@Composable
internal fun UploadBanner(
    status: UploadStatus?,
    result: FilesEvent.Uploaded?,
    hazeState: HazeState,
    onCancel: () -> Unit,
    onResultShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(result) {
        if (result != null) {
            delay(RESULT_VISIBLE_MS)
            onResultShown()
        }
    }
    val motion = MaterialTheme.motionScheme
    val shownResult = result?.takeIf { status == null }
    AnimatedVisibility(
        visible = status != null || shownResult != null,
        enter = slideInVertically(motion.defaultSpatialSpec()) { -it },
        exit = slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = Dimens.floatingButtonSize + Spacing.s),
    ) {
        // 消える動きのあいだは、最後の中身を出しておく
        val last = remember { arrayOfNulls<Any>(2) }
        status?.let { last[0] = it }
        shownResult?.let { last[1] = it }
        GlassPanel(
            hazeState = hazeState,
            fixedOpacity = MENU_OPACITY,
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s).widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            val progress = status ?: (last[0] as? UploadStatus)?.takeIf { shownResult == null && last[1] == null }
            if (progress != null) ProgressRow(progress, onCancel)
            else (shownResult ?: last[1] as? FilesEvent.Uploaded)?.let { ResultRow(it) }
        }
    }
}

@Composable
private fun ProgressRow(status: UploadStatus, onCancel: () -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_huge_upload), contentDescription = null, modifier = Modifier.padding(start = Spacing.l).padding(vertical = Spacing.m))
        Column(Modifier.weight(1f).padding(start = Spacing.m, top = Spacing.m, bottom = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Text(status.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f, fill = false))
                Text(
                    listOfNotNull(
                        stringResource(R.string.files_upload_count, status.index + 1, status.total),
                        status.size?.let { Formatter.formatShortFileSize(context, it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            // 1 件ごとに送るので、進みは件数で出す（1 件だけのときは動き続ける帯）
            if (status.total > 1) {
                val fraction by animateFloatAsState(status.index.toFloat() / status.total, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "uploadProgress")
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        IconButton(onClick = onCancel, modifier = Modifier.padding(horizontal = Spacing.xs)) {
            Icon(painterResource(R.drawable.ic_huge_close), stringResource(R.string.files_upload_cancel_cd))
        }
    }
}

@Composable
private fun ResultRow(result: FilesEvent.Uploaded) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val limit = Formatter.formatShortFileSize(context, MAX_UPLOAD_BYTES.toLong())
    val lines = buildList {
        when {
            result.cancelled -> add(stringResource(R.string.files_upload_cancelled, result.done) to colors.onSurfaceVariant)
            result.done > 0 -> add(pluralStringResource(R.plurals.files_uploaded, result.done, result.done) to colors.onSurface)
        }
        // 容量の上限を超えたものは、名前を挙げて知らせる（マスター指定 2026-11-26）
        when (result.tooLarge.size) {
            0 -> Unit
            1 -> add(stringResource(R.string.files_upload_too_large_one, result.tooLarge.first(), limit) to colors.error)
            else -> add(stringResource(R.string.files_upload_too_large, result.tooLarge.size, limit) to colors.error)
        }
        if (result.failed > 0 && !result.cancelled) add(stringResource(R.string.files_upload_failed, result.failed) to colors.error)
    }
    Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        lines.forEach { (text, color) ->
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 終わったあとの表示を出しておく時間（ダウンロードの帯と同じ）。 */
private const val RESULT_VISIBLE_MS = 6_000L
