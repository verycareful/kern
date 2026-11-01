package dev.kern.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** How long the bar stays on screen after the finger lifts. */
const val FAST_SCROLL_HIDE_DELAY_MS = 1_500L

/** Width of the strip along the right edge where a vertical swipe grabs the bar. */
private val EdgeStripWidth = 24.dp
private val TrackInset = 6.dp
private val ThumbWidth = 4.dp
private val ThumbWidthDragging = 6.dp
private val ThumbMinLength = 40.dp
private val ThumbEndPadding = 4.dp
private const val TRACK_ALPHA = 0.12f

/**
 * Something that scrolls vertically, seen as a fraction of its scrollable range.
 * Every viewer adapts its own scroller to this so one bar serves them all.
 */
interface FastScrollTarget {
    /** Current position, 0 at the top and 1 at the bottom. */
    val fraction: Float

    /** Share of the content on screen, 0 to 1; sets the thumb's length. */
    val visibleFraction: Float

    /** False when the content fits on screen, so there is nothing to scroll. */
    val canScroll: Boolean

    /** Jumps to [fraction] of the scrollable range, clamped to 0..1. */
    fun scrollToFraction(fraction: Float)
}

/**
 * Pure geometry behind the bar: converting between scroll positions, pixel
 * offsets and item indices. Kept free of Compose so it can be unit tested.
 */
object FastScrollMath {

    /** A pixel-addressed scroller: [offsetPx] of [maxOffsetPx], showing [viewportPx]. */
    data class PixelMetrics(val offsetPx: Int, val maxOffsetPx: Int, val viewportPx: Int) {
        val canScroll: Boolean get() = maxOffsetPx > 0 && viewportPx > 0

        val fraction: Float
            get() = if (maxOffsetPx <= 0) 0f else (offsetPx.toFloat() / maxOffsetPx).coerceIn(0f, 1f)

        val visibleFraction: Float
            get() {
                val content = viewportPx + maxOffsetPx
                return if (content <= 0) 1f else (viewportPx.toFloat() / content).coerceIn(0f, 1f)
            }

        fun offsetFor(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * max(maxOffsetPx, 0)).roundToInt()
    }

    /** Where a lazy list should land: item [index], scrolled [offsetPx] into it. */
    data class ItemPosition(val index: Int, val offsetPx: Int)

    /**
     * A lazy list, whose total height is unknown, estimated from the items on
     * screen: [averageItemPx] includes the spacing between items.
     */
    data class ListMetrics(
        val totalItems: Int,
        val firstIndex: Int,
        val firstOffsetPx: Int,
        val averageItemPx: Float,
        val viewportPx: Int,
    ) {
        private val itemsInView: Float
            get() = if (averageItemPx <= 0f) totalItems.toFloat() else viewportPx / averageItemPx

        private val scrollableItems: Float get() = max(totalItems - itemsInView, 0f)

        val canScroll: Boolean get() = totalItems > 0 && scrollableItems > 0f

        val fraction: Float
            get() {
                if (!canScroll) return 0f
                val position = firstIndex + firstOffsetPx / averageItemPx
                return (position / scrollableItems).coerceIn(0f, 1f)
            }

        val visibleFraction: Float
            get() = if (totalItems <= 0) 1f else (itemsInView / totalItems).coerceIn(0f, 1f)

        fun positionFor(fraction: Float): ItemPosition {
            if (totalItems <= 0) return ItemPosition(0, 0)
            val position = fraction.coerceIn(0f, 1f) * scrollableItems
            val index = floor(position).toInt().coerceIn(0, totalItems - 1)
            val offset = ((position - index) * max(averageItemPx, 0f)).roundToInt()
            return ItemPosition(index, offset)
        }
    }

    /** Thumb length in a track of [trackPx], never shorter than [minThumbPx]. */
    fun thumbLength(trackPx: Float, visibleFraction: Float, minThumbPx: Float): Float {
        if (trackPx <= 0f) return 0f
        return max(trackPx * visibleFraction.coerceIn(0f, 1f), minThumbPx).coerceAtMost(trackPx)
    }

    /** Distance from the top of the track to the top of the thumb. */
    fun thumbOffset(trackPx: Float, thumbPx: Float, fraction: Float): Float =
        max(trackPx - thumbPx, 0f) * fraction.coerceIn(0f, 1f)

    /** The position after the thumb moves [dragPx] from where it sat at [startFraction]. */
    fun draggedFraction(startFraction: Float, dragPx: Float, trackPx: Float, thumbPx: Float): Float {
        val travel = trackPx - thumbPx
        if (travel <= 0f) return startFraction.coerceIn(0f, 1f)
        return (startFraction + dragPx / travel).coerceIn(0f, 1f)
    }
}

/** A [ScrollState] (a `verticalScroll` column or a text field) as a [FastScrollTarget]. */
@Composable
fun rememberFastScrollTarget(state: ScrollState): FastScrollTarget = remember(state) {
    object : FastScrollTarget {
        private val metrics get() = FastScrollMath.PixelMetrics(state.value, state.maxValue, state.viewportSize)
        override val fraction get() = metrics.fraction
        override val visibleFraction get() = metrics.visibleFraction
        override val canScroll get() = metrics.canScroll
        override fun scrollToFraction(fraction: Float) {
            state.dispatchRawDelta((metrics.offsetFor(fraction) - state.value).toFloat())
        }
    }
}

/** A [LazyListState] as a [FastScrollTarget], its length estimated from the items on screen. */
@Composable
fun rememberFastScrollTarget(state: LazyListState): FastScrollTarget = remember(state) {
    object : FastScrollTarget {
        private val metrics: FastScrollMath.ListMetrics
            get() {
                val info = state.layoutInfo
                val visible = info.visibleItemsInfo
                val average = if (visible.isEmpty()) 0f else visible.sumOf { it.size }.toFloat() / visible.size + info.mainAxisItemSpacing
                return FastScrollMath.ListMetrics(
                    totalItems = info.totalItemsCount,
                    firstIndex = state.firstVisibleItemIndex,
                    firstOffsetPx = state.firstVisibleItemScrollOffset,
                    averageItemPx = average,
                    viewportPx = info.viewportEndOffset - info.viewportStartOffset,
                )
            }
        override val fraction get() = metrics.fraction
        override val visibleFraction get() = metrics.visibleFraction
        override val canScroll get() = metrics.canScroll || state.canScrollForward || state.canScrollBackward
        override fun scrollToFraction(fraction: Float) {
            val target = metrics.positionFor(fraction)
            state.requestScrollToItem(target.index, target.offsetPx)
        }
    }
}

/**
 * A box whose right edge carries a fast scroll bar for [target].
 *
 * A vertical swipe that starts in the strip along the right edge shows the bar and
 * moves its thumb with the finger, from wherever the thumb already is, so showing
 * the bar never jumps the position. The bar fades out [FAST_SCROLL_HIDE_DELAY_MS]
 * after the finger lifts. Taps, long presses and horizontal swipes in the strip are
 * left to the content, as is any gesture with a second finger (pinch zoom).
 */
@Composable
fun FastScrollBox(
    target: FastScrollTarget,
    hue: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val bar = rememberAutoHide(FAST_SCROLL_HIDE_DELAY_MS, initiallyVisible = false)
    var heightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val insetPx = with(density) { TrackInset.toPx() }
    val minThumbPx = with(density) { ThumbMinLength.toPx() }
    val trackPx = (heightPx - 2 * insetPx).coerceAtLeast(0f)

    Box(
        modifier
            .onSizeChanged { heightPx = it.height }
            .edgeDrag(
                edgePx = with(density) { EdgeStripWidth.toPx() },
                enabled = { target.canScroll },
                onStart = { bar.hold() },
                onDrag = { startFraction, dragPx ->
                    val thumbPx = FastScrollMath.thumbLength(trackPx, target.visibleFraction, minThumbPx)
                    target.scrollToFraction(FastScrollMath.draggedFraction(startFraction, dragPx, trackPx, thumbPx))
                },
                onEnd = { bar.release() },
                currentFraction = { target.fraction },
            ),
    ) {
        content()
        AnimatedVisibility(
            visible = bar.visible && target.canScroll,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight(),
        ) {
            Box(Modifier.fillMaxHeight().padding(vertical = TrackInset, horizontal = ThumbEndPadding)) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(ThumbWidthDragging)
                        .clip(RoundedCornerShape(ThumbWidthDragging))
                        .background(hue.copy(alpha = TRACK_ALPHA)),
                )
                val thumbPx = FastScrollMath.thumbLength(trackPx, target.visibleFraction, minThumbPx)
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            IntOffset(0, FastScrollMath.thumbOffset(trackPx, thumbPx, target.fraction).roundToInt())
                        }
                        .width(if (bar.held) ThumbWidthDragging else ThumbWidth)
                        .height(with(density) { thumbPx.toDp() })
                        .clip(RoundedCornerShape(ThumbWidthDragging))
                        .background(hue),
                )
            }
        }
    }
}

/**
 * Watches every gesture before the content does. One that starts within [edgePx]
 * of the right edge and moves vertically past the touch slop, before the long
 * press timeout and before moving sideways, is taken from the content and reported
 * as a drag from the position [currentFraction] had at the start.
 */
private fun Modifier.edgeDrag(
    edgePx: Float,
    enabled: () -> Boolean,
    onStart: () -> Unit,
    onDrag: (startFraction: Float, dragPx: Float) -> Unit,
    onEnd: () -> Unit,
    currentFraction: () -> Float,
): Modifier = pointerInput(edgePx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.x < size.width - edgePx || !enabled()) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop

        var grabbed: Boolean? = null
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (grabbed == null) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                val dx = change?.let { it.position.x - down.position.x } ?: 0f
                val dy = change?.let { it.position.y - down.position.y } ?: 0f
                grabbed = when {
                    event.changes.size > 1 || change == null -> false
                    !change.pressed || change.isConsumed -> false
                    abs(dx) > slop && abs(dx) > abs(dy) -> false
                    abs(dy) > slop -> true.also { change.consume() }
                    else -> null
                }
            }
        }
        if (grabbed != true) return@awaitEachGesture

        val startFraction = currentFraction()
        onStart()
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    change.consume()
                    break
                }
                onDrag(startFraction, change.position.y - down.position.y)
                change.consume()
            }
        } finally {
            onEnd()
        }
    }
}
