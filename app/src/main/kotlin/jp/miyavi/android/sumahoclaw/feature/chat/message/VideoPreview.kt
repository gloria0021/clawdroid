package jp.miyavi.android.sumahoclaw.feature.chat.message

import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.designsystem.AppBottomSheet
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.designsystem.sheetFieldColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val videoFrames = LruCache<String, ImageBitmap>(32)
private const val FRAME_MAX_PX = 480
private const val VIDEO_ASPECT_RATIO = 16f / 9f

/** Gerbil と同様にサムネイルは別スレッドで取得し、読み込み済みのフレームを再利用する。 */
@Composable
internal fun MessageVideo(url: String, onOpen: (String) -> Unit) {
    val uri = remember(url) { webUri(url) } ?: return
    val frame by produceState<ImageBitmap?>(null, uri) {
        value = videoFrames.get(url) ?: withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(url, emptyMap())
                    retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FRAME_MAX_PX, FRAME_MAX_PX)
                        ?.asImageBitmap()?.also { videoFrames.put(url, it) }
                }
            }.getOrNull()
        }
    }
    val label = stringResource(R.string.chat_video_preview)
    val preview = frame
    if (preview == null) {
        TextButton(onClick = { onOpen(url) }) { Text(label) }
    } else {
        Image(
            preview,
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.widthIn(max = Dimens.maxReadingWidth / 2)
                .fillMaxWidth()
                .aspectRatio(VIDEO_ASPECT_RATIO)
                .clip(MaterialTheme.shapes.large)
                .clickable(onClickLabel = label) { onOpen(url) },
        )
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPreviewSheet(url: String, hazeState: HazeState, onDismiss: () -> Unit) {
    val uri = remember(url) { webUri(url) } ?: return
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var failed by remember(url) { mutableStateOf(false) }
    val player = remember(context, uri) {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { failed = true }
            })
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(lifecycleState, player) {
        if (!lifecycleState.isAtLeast(Lifecycle.State.STARTED)) player.pause()
    }
    AppBottomSheet(onDismissRequest = onDismiss, hazeState = hazeState) {
        Surface(
            color = sheetFieldColor(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT_RATIO), contentAlignment = Alignment.Center) {
                if (failed) {
                    Text(stringResource(R.string.chat_video_failed), modifier = Modifier.padding(Spacing.l))
                } else {
                    AndroidView(
                        factory = { PlayerView(it).apply { this.player = player } },
                        onRelease = { it.player = null },
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }
}
