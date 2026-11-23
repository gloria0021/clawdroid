package jp.miyavi.android.clawdroid.core.designsystem

import jp.miyavi.android.clawdroid.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 会話の高速スクロールバー（Gerbil の ThreadFastScroller を元にした、AGENTS.md 2.7 で許可された自作の部品）。
 * つまみの位置はピクセルではなく項目の番号に対応させる（中央 ≒ 全体の半分の項目）。
 * ふだんは隠し、スクロール中は細く、つかむと太くして、移動先の日時を吹き出しで出す。
 * 本文の操作と干渉しないよう、右端のつまみの周りを掴んだときだけ反応する。
 * [reverseLayout] は、新しい順に下から積むリスト（会話）。つまみの上端が古い側になる。
 * [topInset] / [bottomInset] はタイトルバーと入力欄の高さで、つまみをその裏に隠さない。
 */
@Composable
fun FastScroller(
    listState: LazyListState,
    itemCount: Int,
    label: (index: Int) -> String?,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var heightPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableIntStateOf(0) }
    /** つかんだときの項目（ここから動かなければスクロールしない）。 */
    var grabIndex by remember { mutableIntStateOf(-1) }
    var visible by remember { mutableStateOf(false) }

    val scrolling = listState.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
            visible = true
        } else {
            delay(HIDE_DELAY_MS)
            visible = false
        }
    }

    // ドラッグ中は最新の位置だけを反映する。
    LaunchedEffect(listState) {
        // つかんだだけでは動かさない（いまの位置に scrollToItem すると、項目の途中の位置が失われて飛ぶ）。
        // 指を動かして別の項目に移ったときだけ移動する
        snapshotFlow { if (dragging && dragIndex != grabIndex) dragIndex else -1 }
            .filter { it >= 0 }
            .collectLatest { listState.scrollToItem(it) }
    }

    val listIndex by remember(listState, itemCount) {
        derivedStateOf {
            if (!listState.canScrollForward && itemCount > 0) itemCount - 1 else listState.firstVisibleItemIndex
        }
    }
    val currentIndex = if (dragging) dragIndex else listIndex
    // 下から積むリストでは、新しい項目（番号 0）がいちばん下
    fun fractionOf(index: Int) = FastScrollerMath.fractionForIndex(index, itemCount).let { if (reverseLayout) 1f - it else it }
    val fraction = fractionOf(currentIndex)

    val thumbLengthPx = with(density) { Dimens.scrollbarThumbLength.toPx() }
    val paddingPx = with(density) { Spacing.xs.toPx() }
    val topPx = with(density) { topInset.toPx() } + paddingPx
    val bottomPx = with(density) { bottomInset.toPx() } + paddingPx
    val trackPx = (heightPx - thumbLengthPx - topPx - bottomPx).coerceAtLeast(0f)
    val thumbTopPx = topPx + fraction * trackPx

    val shown = (visible || dragging) && itemCount > 1 && trackPx > 0f
    val latestShown by rememberUpdatedState(shown)
    val latestThumbTop by rememberUpdatedState(thumbTopPx)
    val latestTrack by rememberUpdatedState(trackPx)
    val latestTrackTop by rememberUpdatedState(topPx)
    val latestCount by rememberUpdatedState(itemCount)
    val latestListIndex by rememberUpdatedState(listIndex)
    val latestReverse by rememberUpdatedState(reverseLayout)

    // 出入りは不透明度（effects）、つかんだときの太さは大きさ（spatial）のトークンで動かす
    val motion = MaterialTheme.motionScheme
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (shown) motion.fastEffectsSpec() else motion.slowEffectsSpec(),
        label = "scrollbarAlpha",
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (dragging) Dimens.scrollbarThick else Dimens.scrollbarThin,
        animationSpec = motion.fastSpatialSpec(),
        label = "scrollbarWidth",
    )
    val alignment = Alignment.TopEnd

    Box(
        modifier = modifier
            .onSizeChanged { heightPx = it.height }
            .pointerInput(Unit) {
                val touchWidth = Dimens.minTouchTarget.toPx()
                val grab = Dimens.minTouchTarget.toPx() / 2
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val inStrip = down.position.x >= size.width - touchWidth
                    val top = latestThumbTop
                    val nearThumb = down.position.y in (top - grab)..(top + thumbLengthPx + grab)
                    if (!latestShown || !inStrip || !nearThumb) return@awaitEachGesture

                    down.consume()
                    val grabOffset = down.position.y - top
                    dragIndex = latestListIndex
                    grabIndex = latestListIndex
                    dragging = true
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val f = FastScrollerMath.fractionForOffset(
                                change.position.y - grabOffset - latestTrackTop,
                                latestTrack,
                            )
                            // 意味のない触覚フィードバックは付けない（AGENTS.md 2.8）
                            dragIndex = FastScrollerMath.indexForFraction(if (latestReverse) 1f - f else f, latestCount)
                            change.consume()
                        }
                    } finally {
                        dragging = false
                    }
                }
            },
    ) {
        content()

        // 目印とつまみ
        // TalkBack: 古い方から数えた位置を読み上げ、値を変えると移動する（AGENTS.md 2.11）
        val position = if (reverseLayout) itemCount - currentIndex else currentIndex + 1
        val positionDescription = stringResource(R.string.chat_scroller_cd)
        val positionState = stringResource(R.string.chat_scroller_state, position, itemCount)
        val moveLabel = stringResource(R.string.chat_scroller_move)
        Box(
            modifier = Modifier
                .align(alignment)
                .fillMaxHeight()
                .width(Dimens.minTouchTarget)
                .alpha(alpha)
                .semantics {
                    contentDescription = positionDescription
                    stateDescription = positionState
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = position.toFloat(),
                        range = 1f..itemCount.coerceAtLeast(1).toFloat(),
                    )
                    setProgress(label = moveLabel) { value ->
                        val chosen = value.roundToInt().coerceIn(1, itemCount.coerceAtLeast(1))
                        val target = if (reverseLayout) itemCount - chosen else chosen - 1
                        scope.launch { listState.scrollToItem(target) }
                        true
                    }
                },
        ) {
            val edge = Alignment.TopEnd
            Box(
                Modifier
                    .align(edge)
                    .offset { IntOffset(0, thumbTopPx.roundToInt()) }
                    .padding(horizontal = Spacing.xs)
                    .size(thumbWidth, Dimens.scrollbarThumbLength)
                    .background(
                        color = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = MaterialTheme.shapes.extraLarge,
                    ),
            )
        }

        if (dragging) {
            val bubbleOffset = with(density) { Dimens.minTouchTarget.roundToPx() }
            val bubbleModifier = Modifier
                .align(alignment)
                .offset {
                    IntOffset(-bubbleOffset, (thumbTopPx + thumbLengthPx / 2).roundToInt() - bubbleOffset / 2)
                }
                .height(Dimens.minTouchTarget)
            val text = label(dragIndex)
            if (text != null) {
                ProvideGlassEnvironment {
                    Box(
                        modifier = bubbleModifier
                            .hazeSurface(hazeState, CircleShape, fixedOpacity = SURFACE_OPACITY)
                            .clip(CircleShape)
                            .padding(horizontal = Spacing.l),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private const val HIDE_DELAY_MS = 1_500L
