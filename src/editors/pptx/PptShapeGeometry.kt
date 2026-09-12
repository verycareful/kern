package dev.kern.editors.pptx

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import kotlin.math.min

/**
 * Outlines for the DrawingML preset shapes the editor draws, built the way the
 * presetShapeDefinitions in the OOXML spec build them: from the shape's width, height,
 * and its adjust guides in 1/100000 units, with the spec's default for each guide the
 * file leaves unset.
 */
internal object PptShapeGeometry {

    /** The presets this object can outline. Anything else gets a labelled placeholder. */
    val supported: Set<String> = setOf(
        "rect", "roundRect", "ellipse", "diamond", "triangle", "line",
        "rightArrow", "leftArrow", "upArrow", "downArrow",
    )

    /** A closed (or, for `line`, open) path for [preset] filling [size], or null. */
    fun path(preset: String, size: Size, adjustments: Map<String, Int>): Path? {
        val w = size.width
        val h = size.height
        // The spec's "shortest side", which corner radii and arrow heads scale against.
        val ss = min(w, h)
        fun adj(name: String, default: Int): Float = (adjustments[name] ?: default) / 100000f
        return when (preset) {
            "rect" -> Path().apply { addRect(Rect(0f, 0f, w, h)) }
            "roundRect" -> {
                val r = ss * adj("adj", 16667)
                Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, r, r)) }
            }
            "ellipse" -> Path().apply { addOval(Rect(0f, 0f, w, h)) }
            "diamond" -> polygon(w / 2, 0f, w, h / 2, w / 2, h, 0f, h / 2)
            "triangle" -> {
                // adj places the apex along the top edge; 50000 is an isosceles triangle.
                val apex = w * adj("adj", 50000)
                polygon(0f, h, apex, 0f, w, h)
            }
            "line" -> Path().apply { moveTo(0f, 0f); lineTo(w, h) }
            "rightArrow", "leftArrow", "upArrow", "downArrow" -> arrow(preset, w, h, ss, adj("adj1", 50000), adj("adj2", 50000))
            else -> null
        }
    }

    /**
     * A block arrow. adj1 is the shaft thickness as a fraction of the shape's cross
     * dimension and adj2 the head length as a fraction of the shortest side, exactly as
     * PowerPoint's two yellow handles set them.
     */
    private fun arrow(preset: String, w: Float, h: Float, ss: Float, adj1: Float, adj2: Float): Path {
        val horizontal = preset == "rightArrow" || preset == "leftArrow"
        val length = if (horizontal) w else h
        val cross = if (horizontal) h else w
        val head = ss * adj2
        val half = cross * adj1 / 2
        val mid = cross / 2
        // Built pointing right along the length axis, then mapped into place.
        val pts = floatArrayOf(
            0f, mid - half,
            length - head, mid - half,
            length - head, 0f,
            length, mid,
            length - head, cross,
            length - head, mid + half,
            0f, mid + half,
        )
        val mapped = FloatArray(pts.size)
        for (i in pts.indices step 2) {
            val along = pts[i]
            val across = pts[i + 1]
            when (preset) {
                "rightArrow" -> { mapped[i] = along; mapped[i + 1] = across }
                "leftArrow" -> { mapped[i] = w - along; mapped[i + 1] = across }
                "downArrow" -> { mapped[i] = across; mapped[i + 1] = along }
                else -> { mapped[i] = across; mapped[i + 1] = h - along }
            }
        }
        return polygon(*mapped)
    }

    private fun polygon(vararg xy: Float): Path = Path().apply {
        moveTo(xy[0], xy[1])
        for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
        close()
    }
}
