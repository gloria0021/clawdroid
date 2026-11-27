package jp.miyavi.android.sumahoclaw.feature.chat.message

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.MediaProgress
import jp.miyavi.android.sumahoclaw.core.data.rememberMediaProgress
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing

/**
 * 画像を受け取っているあいだの中身（Gerbil の画像の進捗に倣う）。全体の大きさが分かれば割合の輪と「1.2 MB / 3.4 MB」、
 * 分からなければ Expressive の読み込み表示と受け取った量。取れなかったら印と「表示できません」。
 * 画像の面（プレースホルダー）の中央に置く。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaLoadingBadge(url: String?, modifier: Modifier = Modifier) {
    val progress = rememberMediaProgress(url)
    val colors = MaterialTheme.colorScheme
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(Spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            val fraction = progress?.fraction
            when {
                progress?.failed == true -> Icon(
                    painterResource(R.drawable.ic_huge_alert_circle),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.smallIcon),
                )
                fraction != null -> {
                    val shown by animateFloatAsState(fraction, MaterialTheme.motionScheme.fastSpatialSpec(), label = "imageProgress")
                    CircularWavyProgressIndicator(progress = { shown }, modifier = Modifier.size(Spacing.xl + Spacing.s))
                }
                else -> LoadingIndicator(Modifier.size(Spacing.xl + Spacing.s))
            }
            progressLabel(progress)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

@Composable
private fun progressLabel(progress: MediaProgress?): String? {
    val context = LocalContext.current
    return when {
        progress == null -> null
        progress.failed -> stringResource(R.string.chat_image_failed)
        progress.total > 0 -> stringResource(
            R.string.chat_image_progress,
            Formatter.formatShortFileSize(context, progress.read),
            Formatter.formatShortFileSize(context, progress.total),
        )
        progress.read > 0 -> Formatter.formatShortFileSize(context, progress.read)
        else -> null
    }
}
