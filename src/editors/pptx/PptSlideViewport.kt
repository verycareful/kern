package dev.kern.editors.pptx

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput

/** The furthest a slide can be magnified, as a multiple of fitting the viewport. */
private const val MaxSlideZoom = 6f

/**
 * Viewport transform for the slide canvas: how far the slide is magnified beyond
 * fitting the available area, and how far it has been panned within it.
 *
 * Deliberately separate from [dev.kern.shared.ui.ZoomState]. That one is a plain
 * multiplier the grid, document and page editors fold into their cell sizes and fonts,
 * and it carries no translation. A slide is a fixed canvas seen through a window, so it
 * needs an anchor and a pan offset, and its zoom is expressed relative to fit: 1f shows
 * the whole slide, and there is nothing useful below that.
 */
@Stable
class SlideViewport {

    /** Multiplier on top of the fit scale. Never below 1f, so the slide cannot shrink inside its frame. */
    var zoom by mutableFloatStateOf(1f)
        private set

    /** Pan in pixels from centred. Always within the bound implied by the current zoom. */
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isTransformed: Boolean get() = zoom != 1f

    fun reset() {
        zoom = 1f
        offset = Offset.Zero
    }

    /**
     * Applies one gesture step. [focus] is the gesture centroid measured from the centre
     * of the viewport, which is what holds the point under the fingers still instead of
     * dragging the slide toward a corner.
     */
    fun apply(zoomChange: Float, pan: Offset, focus: Offset, viewport: Size, slide: Size) {
        val next = (zoom * zoomChange).coerceIn(1f, MaxSlideZoom)
        val growth = next / zoom
        offset = (offset + pan - focus) * growth + focus
        zoom = next
        clamp(viewport, slide)
    }

    /**
     * Keeps the slide's edges from being dragged inside the viewport. An axis that fits
     * entirely has nothing to pan along, so its offset is pinned to centred.
     */
    fun clamp(viewport: Size, slide: Size) {
        val bx = panBound(slide.width * zoom, viewport.width)
        val by = panBound(slide.height * zoom, viewport.height)
        offset = Offset(offset.x.coerceIn(-bx, bx), offset.y.coerceIn(-by, by))
    }

    private fun panBound(scaled: Float, available: Float): Float =
        ((scaled - available) / 2f).coerceAtLeast(0f)
}

/**
 * Two-finger pinch and drag for the slide canvas.
 *
 * Single-finger gestures are deliberately left alone and never consumed: they have to
 * reach the text fields on the slide, which is how a shape is selected and typed into.
 */
fun Modifier.slideTransform(
    viewport: SlideViewport,
    viewportSize: () -> Size,
    slideSize: () -> Size,
): Modifier = pointerInput(viewport) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                val zoomChange = event.calculateZoom()
                val pan = event.calculatePan()
                if (zoomChange != 1f || pan != Offset.Zero) {
                    val view = viewportSize()
                    val focus = event.calculateCentroid() - Offset(view.width / 2f, view.height / 2f)
                    viewport.apply(zoomChange, pan, focus, view, slideSize())
                    event.changes.forEach { if (it.pressed) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
