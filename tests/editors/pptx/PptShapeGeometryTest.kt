package dev.kern.editors.pptx

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The preset outlines, checked by their bounds and by points that must fall inside or
 * outside them. A Compose Path on Android is an android.graphics.Path, so this runs
 * under Robolectric's native graphics rather than on the plain JVM.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PptShapeGeometryTest {

    private val tolerance = 0.01f

    private fun path(preset: String, width: Float, height: Float, adjustments: Map<String, Int> = emptyMap()): Path =
        requireNotNull(PptShapeGeometry.path(preset, Size(width, height), adjustments)) { "no path for $preset" }

    /** Whether the filled outline covers the point, probed with a half-point square. */
    private fun Path.covers(x: Float, y: Float): Boolean {
        val probe = Path().apply { addRect(Rect(x - 0.25f, y - 0.25f, x + 0.25f, y + 0.25f)) }
        return !Path.combine(PathOperation.Intersect, this, probe).isEmpty
    }

    private fun assertBounds(expected: Rect, path: Path) {
        val actual = path.getBounds()
        assertEquals("left", expected.left, actual.left, tolerance)
        assertEquals("top", expected.top, actual.top, tolerance)
        assertEquals("right", expected.right, actual.right, tolerance)
        assertEquals("bottom", expected.bottom, actual.bottom, tolerance)
    }

    @Test
    fun everySupportedPreset_hasAPath_fillingItsBox() {
        for (preset in PptShapeGeometry.supported) {
            val path = PptShapeGeometry.path(preset, Size(120f, 80f), emptyMap())
            assertNotNull(preset, path)
            assertBounds(Rect(0f, 0f, 120f, 80f), path!!)
        }
    }

    @Test
    fun unsupportedPreset_hasNoPath() {
        assertNull(PptShapeGeometry.path("star5", Size(100f, 100f), emptyMap()))
        assertNull(PptShapeGeometry.path("chevron", Size(100f, 100f), emptyMap()))
        assertFalse(PptShapeGeometry.supported.contains("star5"))
    }

    @Test
    fun rect_coversItsCorners() {
        val rect = path("rect", 100f, 50f)
        assertTrue(rect.covers(1f, 1f))
        assertTrue(rect.covers(99f, 49f))
        assertFalse(rect.covers(101f, 25f))
    }

    @Test
    fun roundRect_defaultRadiusIsASixthOfTheShortSide() {
        // 16667 of 60 is a 10pt radius: the corner point is cut, a point 10pt in is not.
        val shape = path("roundRect", 120f, 60f)
        assertFalse(shape.covers(1f, 1f))
        assertTrue(shape.covers(10f, 10f))
        assertTrue(shape.covers(60f, 1f))
    }

    @Test
    fun roundRect_adjustmentSetsTheRadius() {
        // 50000 of 100 is a 50pt radius: a full circle.
        val circle = path("roundRect", 100f, 100f, mapOf("adj" to 50000))
        assertFalse(circle.covers(10f, 10f))
        assertTrue(circle.covers(50f, 50f))
        val square = path("roundRect", 100f, 100f, mapOf("adj" to 0))
        assertTrue(square.covers(1f, 1f))
    }

    @Test
    fun ellipse_excludesCornersIncludesCentreAndEdgeMidpoints() {
        val ellipse = path("ellipse", 200f, 100f)
        assertFalse(ellipse.covers(5f, 5f))
        assertTrue(ellipse.covers(100f, 50f))
        assertTrue(ellipse.covers(100f, 1f))
        assertTrue(ellipse.covers(1f, 50f))
    }

    @Test
    fun diamond_hasItsVerticesAtEdgeMidpoints() {
        val diamond = path("diamond", 100f, 100f)
        assertFalse(diamond.covers(10f, 10f))
        assertTrue(diamond.covers(50f, 2f))
        assertTrue(diamond.covers(98f, 50f))
        assertTrue(diamond.covers(50f, 50f))
    }

    @Test
    fun triangle_defaultApexIsCentred() {
        val triangle = path("triangle", 100f, 100f)
        assertTrue(triangle.covers(50f, 2f))
        assertFalse(triangle.covers(10f, 2f))
        assertFalse(triangle.covers(90f, 2f))
        assertTrue(triangle.covers(2f, 99f))
    }

    @Test
    fun triangle_adjustmentMovesTheApex() {
        val triangle = path("triangle", 100f, 100f, mapOf("adj" to 25000))
        assertTrue(triangle.covers(25f, 2f))
        assertFalse(triangle.covers(50f, 2f))
    }

    @Test
    fun line_runsCornerToCorner() {
        assertBounds(Rect(0f, 0f, 100f, 50f), path("line", 100f, 50f))
    }

    // A 200 by 100 horizontal arrow with default guides: the shaft is half the height
    // (y 25 to 75) and the head is half the short side long (50pt).

    @Test
    fun rightArrow_shaftAndHead() {
        val arrow = path("rightArrow", 200f, 100f)
        assertTrue("shaft", arrow.covers(10f, 50f))
        assertFalse("beside the shaft", arrow.covers(10f, 10f))
        assertTrue("head, beside the shaft", arrow.covers(155f, 10f))
        assertTrue("near the tip", arrow.covers(195f, 50f))
        assertFalse("beside the tip", arrow.covers(195f, 10f))
    }

    @Test
    fun leftArrow_isTheMirrorImage() {
        val arrow = path("leftArrow", 200f, 100f)
        assertTrue(arrow.covers(190f, 50f))
        assertFalse(arrow.covers(190f, 10f))
        assertTrue(arrow.covers(45f, 10f))
        assertTrue(arrow.covers(5f, 50f))
        assertFalse(arrow.covers(5f, 10f))
    }

    @Test
    fun downArrow_pointsDown() {
        val arrow = path("downArrow", 100f, 200f)
        assertTrue(arrow.covers(50f, 10f))
        assertFalse(arrow.covers(10f, 10f))
        assertTrue(arrow.covers(10f, 155f))
        assertTrue(arrow.covers(50f, 195f))
    }

    @Test
    fun upArrow_pointsUp() {
        val arrow = path("upArrow", 100f, 200f)
        assertTrue(arrow.covers(50f, 190f))
        assertFalse(arrow.covers(10f, 190f))
        assertTrue(arrow.covers(10f, 45f))
        assertTrue(arrow.covers(50f, 5f))
    }

    @Test
    fun arrow_adjustmentsSetShaftThicknessAndHeadLength() {
        // adj1 20000: shaft 20pt of 100 (y 40 to 60). adj2 100000: head as long as the short side.
        val arrow = path("rightArrow", 200f, 100f, mapOf("adj1" to 20000, "adj2" to 100000))
        assertTrue(arrow.covers(10f, 50f))
        assertFalse(arrow.covers(10f, 35f))
        assertTrue("head starts at 100", arrow.covers(105f, 10f))
        assertFalse(arrow.covers(95f, 10f))
    }
}
