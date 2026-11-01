package dev.kern.shared.ui

import dev.kern.shared.ui.FastScrollMath.ItemPosition
import dev.kern.shared.ui.FastScrollMath.ListMetrics
import dev.kern.shared.ui.FastScrollMath.PixelMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Geometry behind the right-edge fast scroll bar. */
class FastScrollMathTest {

    private val eps = 1e-4f

    @Test
    fun pixelMetrics_fractionTracksOffset() {
        assertEquals(0f, PixelMetrics(0, 1000, 500).fraction, eps)
        assertEquals(0.25f, PixelMetrics(250, 1000, 500).fraction, eps)
        assertEquals(1f, PixelMetrics(1000, 1000, 500).fraction, eps)
    }

    @Test
    fun pixelMetrics_visibleFractionIsViewportOverContent() {
        assertEquals(500f / 1500f, PixelMetrics(0, 1000, 500).visibleFraction, eps)
    }

    @Test
    fun pixelMetrics_offsetForRoundTripsAndClamps() {
        val m = PixelMetrics(0, 1000, 500)
        assertEquals(400, m.offsetFor(0.4f))
        assertEquals(0, m.offsetFor(-1f))
        assertEquals(1000, m.offsetFor(2f))
        assertEquals(0.4f, m.copy(offsetPx = m.offsetFor(0.4f)).fraction, eps)
    }

    @Test
    fun pixelMetrics_contentThatFitsCannotScroll() {
        val m = PixelMetrics(0, 0, 500)
        assertFalse(m.canScroll)
        assertEquals(0f, m.fraction, eps)
        assertEquals(1f, m.visibleFraction, eps)
        assertEquals(0, m.offsetFor(0.7f))
    }

    @Test
    fun pixelMetrics_unmeasuredViewportCannotScroll() {
        assertFalse(PixelMetrics(0, 1000, 0).canScroll)
        assertTrue(PixelMetrics(0, 1000, 1).canScroll)
    }

    @Test
    fun listMetrics_fractionCountsPartlyScrolledItem() {
        // 100 items of 100px in a 1000px viewport: 90 items of travel.
        val m = ListMetrics(100, firstIndex = 45, firstOffsetPx = 0, averageItemPx = 100f, viewportPx = 1000)
        assertEquals(0.5f, m.fraction, eps)
        assertEquals(0.1f, m.visibleFraction, eps)
        val half = m.copy(firstIndex = 9, firstOffsetPx = 0)
        assertEquals(0.1f, half.fraction, eps)
        assertEquals(9.5f / 90f, m.copy(firstIndex = 9, firstOffsetPx = 50).fraction, eps)
    }

    @Test
    fun listMetrics_positionForInvertsFraction() {
        val m = ListMetrics(100, 0, 0, averageItemPx = 100f, viewportPx = 1000)
        assertEquals(ItemPosition(0, 0), m.positionFor(0f))
        assertEquals(ItemPosition(45, 0), m.positionFor(0.5f))
        assertEquals(ItemPosition(90, 0), m.positionFor(1f))
        assertEquals(ItemPosition(9, 50), m.positionFor(9.5f / 90f))
    }

    @Test
    fun listMetrics_positionForClampsOutOfRange() {
        val m = ListMetrics(100, 0, 0, averageItemPx = 100f, viewportPx = 1000)
        assertEquals(ItemPosition(0, 0), m.positionFor(-0.5f))
        assertEquals(ItemPosition(90, 0), m.positionFor(3f))
    }

    @Test
    fun listMetrics_shortListCannotScroll() {
        val m = ListMetrics(3, 0, 0, averageItemPx = 100f, viewportPx = 1000)
        assertFalse(m.canScroll)
        assertEquals(0f, m.fraction, eps)
        assertEquals(1f, m.visibleFraction, eps)
        assertEquals(ItemPosition(0, 0), m.positionFor(0.8f))
    }

    @Test
    fun listMetrics_emptyListIsSafe() {
        val m = ListMetrics(0, 0, 0, averageItemPx = 0f, viewportPx = 1000)
        assertFalse(m.canScroll)
        assertEquals(0f, m.fraction, eps)
        assertEquals(ItemPosition(0, 0), m.positionFor(0.5f))
    }

    @Test
    fun listMetrics_singleTallItemScrollsWithinIt() {
        // One PDF page taller than the screen.
        val m = ListMetrics(1, 0, 0, averageItemPx = 3000f, viewportPx = 1000)
        assertTrue(m.canScroll)
        assertEquals(ItemPosition(0, 1000), m.positionFor(0.5f))
    }

    @Test
    fun thumbLength_followsVisibleShareWithAFloor() {
        assertEquals(250f, FastScrollMath.thumbLength(1000f, 0.25f, 40f), eps)
        assertEquals(40f, FastScrollMath.thumbLength(1000f, 0.001f, 40f), eps)
        assertEquals(1000f, FastScrollMath.thumbLength(1000f, 1.5f, 40f), eps)
        assertEquals(30f, FastScrollMath.thumbLength(30f, 0.1f, 40f), eps)
        assertEquals(0f, FastScrollMath.thumbLength(0f, 0.5f, 40f), eps)
    }

    @Test
    fun thumbOffset_spansTheTravel() {
        assertEquals(0f, FastScrollMath.thumbOffset(1000f, 200f, 0f), eps)
        assertEquals(400f, FastScrollMath.thumbOffset(1000f, 200f, 0.5f), eps)
        assertEquals(800f, FastScrollMath.thumbOffset(1000f, 200f, 1f), eps)
        assertEquals(0f, FastScrollMath.thumbOffset(100f, 200f, 0.5f), eps)
    }

    @Test
    fun draggedFraction_movesRelativeToStart() {
        assertEquals(0.5f, FastScrollMath.draggedFraction(0.3f, 160f, 1000f, 200f), eps)
        assertEquals(0.1f, FastScrollMath.draggedFraction(0.3f, -160f, 1000f, 200f), eps)
    }

    @Test
    fun draggedFraction_zeroDragKeepsPosition() {
        assertEquals(0.37f, FastScrollMath.draggedFraction(0.37f, 0f, 1000f, 200f), eps)
    }

    @Test
    fun draggedFraction_clampsAndSurvivesNoTravel() {
        assertEquals(1f, FastScrollMath.draggedFraction(0.9f, 5000f, 1000f, 200f), eps)
        assertEquals(0f, FastScrollMath.draggedFraction(0.1f, -5000f, 1000f, 200f), eps)
        assertEquals(0.4f, FastScrollMath.draggedFraction(0.4f, 50f, 200f, 200f), eps)
    }
}
