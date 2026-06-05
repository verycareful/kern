package dev.kern.shared.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Holds a clamped zoom factor that editors multiply into their cell sizes / fonts. */
class ZoomState(val min: Float, val max: Float) {
    var scale by mutableFloatStateOf(1f)
        private set

    fun zoomBy(factor: Float) {
        scale = (scale * factor).coerceIn(min, max)
    }
}

@Composable
fun rememberZoomState(min: Float = 0.5f, max: Float = 3f): ZoomState =
    remember(min, max) { ZoomState(min, max) }

/**
 * Pinch-to-zoom. Only two-finger gestures drive the zoom (and are consumed);
 * single-finger gestures are left alone so nested scrolling keeps working. Reused
 * by every editor (grids, document page, slide canvas).
 */
fun Modifier.pinchZoom(state: ZoomState): Modifier = pointerInput(state) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    state.zoomBy(zoom)
                    event.changes.forEach { if (it.pressed) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
