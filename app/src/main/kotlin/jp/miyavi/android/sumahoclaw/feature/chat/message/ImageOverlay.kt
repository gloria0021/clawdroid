package jp.miyavi.android.sumahoclaw.feature.chat.message

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.LocalInboundImages
import jp.miyavi.android.sumahoclaw.core.data.MessagePart
import jp.miyavi.android.sumahoclaw.core.data.isInboundMediaUrl
import jp.miyavi.android.sumahoclaw.core.data.rememberInboundImage
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassPill
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.sumahoclaw.core.designsystem.IMAGE_OPEN_START_SCALE
import jp.miyavi.android.sumahoclaw.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 全画面で見る画像の並びと、最初に出す位置（同じメッセージの画像を横にめくれる）。 */
/** [scope] は画像の載ったメッセージの ID（会話の画像の鍵。メッセージの外から開くときは空）。 */
data class ImageGallery(val images: List<MessagePart.Image>, val initialIndex: Int = 0, val scope: String = "")

/** 1 枚だけを開く（ファイルビューアなど、並びの無いところ）。 */
@Composable
fun ImageOverlay(image: MessagePart.Image?, onSave: (() -> Unit)? = null, onDismiss: () -> Unit) {
    ImageOverlay(gallery = image?.let { ImageGallery(listOf(it)) }, onSave = onSave, onDismiss = onDismiss)
}

/**
 * 画像の全画面オーバーレイ（AGENTS.md 2.7。Gerbil の画像のオーバーレイに倣う。マスター指定 2026-11-24）。
 * 横にめくる（HorizontalPager）、ピンチとダブルタップで拡大、下に浮くガラスのバーに 閉じる・何枚目か・ダウンロード。
 * 背景はフェード、画像は少し小さい状態からばねで広がって出る。消える動きを終えてから閉じる（AGENTS.md 2.8）。
 */
@Composable
fun ImageOverlay(
    gallery: ImageGallery?,
    /** 保存のボタンで、表示中の画像ではなく元のファイルを保存する（ファイルビューア。CJ レビュー 2026-11-26）。 */
    onSave: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var shown by remember { mutableStateOf<ImageGallery?>(null) }
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(gallery) {
        if (gallery != null && gallery.images.isNotEmpty()) {
            shown = gallery
            visible.targetState = true
        } else {
            visible.targetState = false
        }
    }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState && shown != null) shown = null
    }
    val current = shown ?: return
    // 開くたびに最初の位置からめくれるよう、並びごとに状態を作り直す
    androidx.compose.runtime.key(current) { GalleryContent(current, visible, onSave, onDismiss) }
}

@Composable
private fun GalleryContent(current: ImageGallery, visible: MutableTransitionState<Boolean>, onSave: (() -> Unit)?, onDismiss: () -> Unit) {
    val images = current.images
    BackHandler(enabled = visible.targetState) { onDismiss() }
    val motion = MaterialTheme.motionScheme
    val pagerState = rememberPagerState(initialPage = current.initialIndex.coerceIn(0, images.lastIndex)) { images.size }
    val hazeState = rememberHazeState()
    var currentScaled by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) { currentScaled = false }

    val context = LocalContext.current
    val resources = LocalResources.current
    val inbound = LocalInboundImages.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var saving by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec()),
    ) {
        ProvideScreenHaze(hazeState) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim)) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !currentScaled,
                    // 隣の画像を先に組み立てておき、めくったときに空白を見せない
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .fillMaxSize()
                        .animateEnterExit(
                            enter = scaleIn(motion.defaultSpatialSpec(), initialScale = IMAGE_OPEN_START_SCALE),
                            exit = scaleOut(motion.fastSpatialSpec(), targetScale = IMAGE_OPEN_START_SCALE),
                        )
                        .hazeSource(hazeState),
                ) { page ->
                    // 会話の画像は、載っていたメッセージの鍵で取る（一覧で取ったものをそのまま使う）
                    androidx.compose.runtime.CompositionLocalProvider(jp.miyavi.android.sumahoclaw.core.data.LocalMessageScope provides current.scope) {
                        ZoomableImagePage(
                            image = images[page],
                            isCurrentPage = page == pagerState.currentPage,
                            onScaledChange = { scaled -> if (page == pagerState.currentPage) currentScaled = scaled },
                        )
                    }
                }
                // 下に浮くガラスのバー: 閉じる・何枚目か・ダウンロード
                GlassPill(
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Spacing.l),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.ic_huge_close), contentDescription = stringResource(R.string.chat_image_close_cd))
                        }
                        if (images.size > 1) {
                            Text(
                                stringResource(R.string.chat_image_position, pagerState.currentPage + 1, images.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = Spacing.s),
                            )
                        }
                        IconButton(
                            enabled = !saving,
                            onClick = {
                                if (onSave != null) {
                                    onSave()
                                    return@IconButton
                                }
                                val target = images[pagerState.currentPage]
                                saving = true
                                scope.launch {
                                    val ok = saveImageToDownloads(context, target, inbound)
                                    saving = false
                                    snackbar.showSnackbar(resources.getString(if (ok) R.string.chat_image_saved else R.string.chat_image_save_failed))
                                }
                            },
                        ) {
                            Icon(painterResource(R.drawable.ic_huge_download), contentDescription = stringResource(R.string.chat_image_save_cd))
                        }
                    }
                }
                GlassSnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = SNACKBAR_LIFT),
                )
            }
        }
    }
}

/** 1 枚分。ピンチで拡大・移動、ダブルタップで 2 倍 ⇄ 等倍（ばね）。等倍で 1 本指の横スワイプはめくりへ譲る。 */
@Composable
private fun ZoomableImagePage(image: MessagePart.Image, isCurrentPage: Boolean, onScaledChange: (Boolean) -> Unit) {
    val key = image.base64 ?: image.url
    var scale by remember(key) { mutableFloatStateOf(1f) }
    var offset by remember(key) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val zoomSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // ダブルタップの拡大の途中で指が触れたら止め、今の倍率・位置から指の操作に引き継ぐ
    var zoomJob by remember(key) { mutableStateOf<Job?>(null) }
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            zoomJob?.cancel()
            scale = 1f
            offset = Offset.Zero
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(key) {
                detectTapGestures(
                    onDoubleTap = {
                        val fromScale = scale
                        val toScale = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                        val fromOffset = offset
                        onScaledChange(toScale > 1f)
                        zoomJob?.cancel()
                        zoomJob = scope.launch {
                            animate(0f, 1f, animationSpec = zoomSpec) { t, _ ->
                                scale = fromScale + (toScale - fromScale) * t
                                offset = fromOffset * (1f - t)
                            }
                        }
                    },
                )
            }
            .pointerInput(key) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    zoomJob?.cancel()
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount > 1 || scale > 1f) {
                            val nextScale = (scale * event.calculateZoom()).coerceIn(1f, MAX_SCALE)
                            scale = nextScale
                            offset = if (nextScale > 1f) offset + event.calculatePan() else Offset.Zero
                            onScaledChange(nextScale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val cd = stringResource(R.string.chat_image_cd)
        // 倍率・位置は描画の段階で読み、拡大中に再構成を起こさない（AGENTS.md 2.8）
        val imageModifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = offset.x
            translationY = offset.y
        }
        val bitmap = image.preloaded ?: rememberBase64Bitmap(image.base64) ?: rememberInboundImage(image.url)
        when {
            bitmap != null -> Image(bitmap, contentDescription = cd, contentScale = ContentScale.Fit, modifier = imageModifier)
            image.url != null && !isInboundMediaUrl(image.url) ->
                AsyncImage(model = webUri(image.url) ?: image.url, contentDescription = cd, contentScale = ContentScale.Fit, modifier = imageModifier)
            // 受け取っているあいだは進みを出す
            image.base64 == null && image.url != null -> MediaLoadingBadge(image.url)
        }
    }
}

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2f
/** Snackbar を下のバーの上に出す高さ（バー 48dp ＋ 余白）。 */
private val SNACKBAR_LIFT = jp.miyavi.android.sumahoclaw.core.designsystem.Dimens.minTouchTarget + Spacing.l * 2
