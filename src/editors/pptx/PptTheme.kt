package dev.kern.editors.pptx

import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.xmlbeans.XmlObject
import org.openxmlformats.schemas.drawingml.x2006.main.CTColor
import org.openxmlformats.schemas.drawingml.x2006.main.CTColorMapping
import org.openxmlformats.schemas.drawingml.x2006.main.CTColorScheme
import org.openxmlformats.schemas.drawingml.x2006.main.CTGradientFillProperties
import org.openxmlformats.schemas.drawingml.x2006.main.CTLineProperties
import org.openxmlformats.schemas.drawingml.x2006.main.CTNoFillProperties
import org.openxmlformats.schemas.drawingml.x2006.main.CTPercentage
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveFixedPercentage
import org.openxmlformats.schemas.drawingml.x2006.main.CTSRgbColor
import org.openxmlformats.schemas.drawingml.x2006.main.CTSchemeColor
import org.openxmlformats.schemas.drawingml.x2006.main.CTSolidColorFillProperties
import org.openxmlformats.schemas.drawingml.x2006.main.CTSystemColor
import kotlin.math.roundToInt

/**
 * The colour scheme and style matrix behind one slide, with the slide's colour map
 * applied, so that a `schemeClr`, a `fillRef` or a `bgRef` can be turned into the sRGB
 * value PowerPoint would paint.
 *
 * Everything here is read through the typed OOXML accessors. POI's own resolver
 * (`XSLFColor`, `getFontColor`, `getFillColor`) returns `java.awt.Color`, which does
 * not exist on Android; see the note on `getAnchor` in [PptDocument].
 */
internal class PptTheme private constructor(
    /** Scheme slot (dk1, lt1, accent1, ...) to six hex digits. */
    private val scheme: Map<String, String>,
    /** Colour map slot (bg1, tx1, ...) to scheme slot. Identity for names not mapped. */
    private val map: Map<String, String>,
    /** The three fill style lists of the theme, in document order. */
    private val fillStyles: List<XmlObject>,
    private val bgFillStyles: List<XmlObject>,
    private val lineStyles: List<CTLineProperties>,
) {

    /** The colour of a scheme slot after the colour map, with [phClr] standing in for the placeholder slot. */
    fun schemeColor(color: CTSchemeColor, phClr: String? = null): String? {
        val slot = color.`val`?.toString() ?: return null
        val base = if (slot == "phClr") phClr else scheme[map[slot] ?: slot]
        return base?.let { modified(it, color.lumModList, color.lumOffList, color.tintList, color.shadeList, color.alphaList) }
    }

    /** The colour of a scheme slot itself, after the colour map. */
    fun slotColor(slot: String): String? = scheme[map[slot] ?: slot]

    /** The colour a style reference or font reference carries, which becomes its placeholder colour. */
    fun refColor(srgb: CTSRgbColor?, schemeColor: CTSchemeColor?): String? =
        srgb?.let { srgbColor(it) } ?: schemeColor?.let { schemeColor(it) }

    /** The colour a solid fill declares, whichever of the colour forms it uses. */
    fun colorOf(fill: CTSolidColorFillProperties?, phClr: String? = null): String? {
        if (fill == null) return null
        fill.srgbClr?.let { return srgbColor(it) }
        fill.schemeClr?.let { return schemeColor(it, phClr) }
        fill.sysClr?.let { return sysColor(it) }
        return null
    }

    /** A fill in the editor's model from any of the DrawingML fill elements, or null for no fill. */
    fun fillOf(
        solid: CTSolidColorFillProperties?,
        gradient: CTGradientFillProperties?,
        phClr: String? = null,
    ): PptDocument.Fill? {
        colorOf(solid, phClr)?.let { return PptDocument.SolidFill(it) }
        if (gradient != null) return gradientOf(gradient, phClr)
        return null
    }

    /**
     * The fill a `fillRef` or `bgRef` points at. Indices 1..999 address the fill style
     * list, 1001 and up the background fill style list, and 0 means no fill. A theme
     * fill that is a picture is not resolved, because its image lives in the theme part
     * and the editor draws backgrounds from the slide hierarchy only.
     */
    fun fillStyle(idx: Long, phClr: String?): PptDocument.Fill? {
        val entry = when {
            idx <= 0L -> return null
            idx >= 1001L -> bgFillStyles.getOrNull((idx - 1001L).toInt())
            else -> fillStyles.getOrNull((idx - 1L).toInt())
        } ?: return null
        return when (entry) {
            is CTSolidColorFillProperties -> fillOf(entry, null, phClr)
            is CTGradientFillProperties -> gradientOf(entry, phClr)
            is CTNoFillProperties -> null
            else -> null
        }
    }

    /** The outline a `lnRef` points at: colour and width in points, or null for none. */
    fun lineStyle(idx: Long, phClr: String?): PptDocument.Outline? {
        if (idx <= 0L) return null
        val line = lineStyles.getOrNull((idx - 1L).toInt()) ?: return null
        if (line.noFill != null) return null
        val color = colorOf(line.solidFill, phClr) ?: return null
        return PptDocument.Outline(color, lineWidthOf(line))
    }

    private fun gradientOf(gradient: CTGradientFillProperties, phClr: String?): PptDocument.Fill? {
        val stops = gradient.gsLst?.gsList.orEmpty().mapNotNull { stop ->
            val position = percent(stop.pos) ?: return@mapNotNull null
            val color = stop.srgbClr?.let { srgbColor(it) }
                ?: stop.schemeClr?.let { schemeColor(it, phClr) }
                ?: stop.sysClr?.let { sysColor(it) }
                ?: return@mapNotNull null
            PptDocument.GradientStop(position, color)
        }.sortedBy { it.position }
        if (stops.isEmpty()) return null
        if (stops.size == 1) return PptDocument.SolidFill(stops.single().color)
        // A path (radial or rectangular) gradient is drawn as a linear one from the
        // top; the colours are right and the shape of the falloff is not.
        val angle = gradient.lin?.takeIf { it.isSetAng }?.ang?.let { it / 60000f } ?: 90f
        return PptDocument.GradientFill(stops, angle)
    }

    companion object {
        /** The theme of the master behind [slide], or null if the file has none. */
        fun of(slide: XSLFSlide): PptTheme? {
            val master = slide.slideMaster ?: return null
            val theme = master.theme?.xmlObject?.themeElements ?: return null
            val scheme = theme.clrScheme?.let { schemeOf(it) } ?: return null
            // A slide or layout may override the master's colour map; the first
            // override on the way up wins.
            val mapping = slide.xmlObject.clrMapOvr?.overrideClrMapping
                ?: slide.slideLayout?.xmlObject?.clrMapOvr?.overrideClrMapping
                ?: master.xmlObject.clrMap
            val format = theme.fmtScheme
            return PptTheme(
                scheme = scheme,
                map = mapping?.let { mappingOf(it) } ?: emptyMap(),
                fillStyles = format?.fillStyleLst?.let { children(it) }.orEmpty(),
                bgFillStyles = format?.bgFillStyleLst?.let { children(it) }.orEmpty(),
                lineStyles = format?.lnStyleLst?.lnList.orEmpty(),
            )
        }

        private fun schemeOf(scheme: CTColorScheme): Map<String, String> {
            val slots = listOf(
                "dk1" to scheme.dk1, "lt1" to scheme.lt1, "dk2" to scheme.dk2, "lt2" to scheme.lt2,
                "accent1" to scheme.accent1, "accent2" to scheme.accent2, "accent3" to scheme.accent3,
                "accent4" to scheme.accent4, "accent5" to scheme.accent5, "accent6" to scheme.accent6,
                "hlink" to scheme.hlink, "folHlink" to scheme.folHlink,
            )
            return slots.mapNotNull { (slot, color) -> colorOf(color)?.let { slot to it } }.toMap()
        }

        private fun colorOf(color: CTColor?): String? =
            color?.srgbClr?.let { srgbColor(it) } ?: color?.sysClr?.let { sysColor(it) }

        private fun mappingOf(mapping: CTColorMapping): Map<String, String> {
            val slots = listOf(
                "bg1" to mapping.bg1, "tx1" to mapping.tx1, "bg2" to mapping.bg2, "tx2" to mapping.tx2,
                "accent1" to mapping.accent1, "accent2" to mapping.accent2, "accent3" to mapping.accent3,
                "accent4" to mapping.accent4, "accent5" to mapping.accent5, "accent6" to mapping.accent6,
                "hlink" to mapping.hlink, "folHlink" to mapping.folHlink,
            )
            return slots.mapNotNull { (slot, target) -> target?.toString()?.let { slot to it } }.toMap()
        }

        /**
         * The child elements of [parent] in document order. The generated accessors
         * split a style list by element type, which loses the order that the index in
         * a style reference counts along.
         */
        private fun children(parent: XmlObject): List<XmlObject> {
            val out = ArrayList<XmlObject>()
            parent.newCursor().use { cursor ->
                if (cursor.toFirstChild()) {
                    do { out.add(cursor.`object`) } while (cursor.toNextSibling())
                }
            }
            return out
        }

        fun srgbColor(color: CTSRgbColor): String? {
            val hex = color.`val`?.takeIf { it.size == 3 }?.joinToString("") { "%02X".format(it) } ?: return null
            return modified(hex, color.lumModList, color.lumOffList, color.tintList, color.shadeList, color.alphaList)
        }

        private fun sysColor(color: CTSystemColor): String? =
            color.lastClr?.takeIf { it.size == 3 }?.joinToString("") { "%02X".format(it) }

        /** The width of an outline in points, DrawingML's 0.75pt default when unset. */
        fun lineWidthOf(line: CTLineProperties?): Float =
            if (line != null && line.isSetW) line.w / 12700f else 0.75f

        /** A DrawingML percentage as a fraction, whether written as 50000 or "50%". */
        private fun percent(value: Any?): Float? = when (value) {
            is Number -> value.toFloat() / 100000f
            is String -> value.trim().removeSuffix("%").toFloatOrNull()?.let { if (value.trim().endsWith("%")) it / 100f else it / 100000f }
            else -> null
        }

        /**
         * A colour with DrawingML's transforms applied in the order PowerPoint applies
         * them: luminance modulation and offset in HSL, then tint and shade towards
         * white and black, then alpha. Eight hex digits come back only when alpha is
         * below one.
         */
        private fun modified(
            hex: String,
            lumMod: List<CTPercentage>,
            lumOff: List<CTPercentage>,
            tint: List<CTPositiveFixedPercentage>,
            shade: List<CTPositiveFixedPercentage>,
            alpha: List<CTPositiveFixedPercentage>,
        ): String {
            if (lumMod.isEmpty() && lumOff.isEmpty() && tint.isEmpty() && shade.isEmpty() && alpha.isEmpty()) return hex
            var r = hex.substring(0, 2).toInt(16) / 255f
            var g = hex.substring(2, 4).toInt(16) / 255f
            var b = hex.substring(4, 6).toInt(16) / 255f
            if (lumMod.isNotEmpty() || lumOff.isNotEmpty()) {
                val hsl = toHsl(r, g, b)
                var l = hsl[2]
                lumMod.firstOrNull()?.let { percent(it.`val`) }?.let { l *= it }
                lumOff.firstOrNull()?.let { percent(it.`val`) }?.let { l += it }
                val rgb = fromHsl(hsl[0], hsl[1], l.coerceIn(0f, 1f))
                r = rgb[0]; g = rgb[1]; b = rgb[2]
            }
            tint.firstOrNull()?.let { percent(it.`val`) }?.let { t ->
                r = r * t + (1 - t); g = g * t + (1 - t); b = b * t + (1 - t)
            }
            shade.firstOrNull()?.let { percent(it.`val`) }?.let { s ->
                r *= s; g *= s; b *= s
            }
            val a = alpha.firstOrNull()?.let { percent(it.`val`) }?.coerceIn(0f, 1f)
            val rgbHex = "%02X%02X%02X".format(channel(r), channel(g), channel(b))
            return if (a != null && a < 1f) "%02X".format(channel(a)) + rgbHex else rgbHex
        }

        private fun channel(v: Float): Int = (v.coerceIn(0f, 1f) * 255f).roundToInt()

        private fun toHsl(r: Float, g: Float, b: Float): FloatArray {
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val l = (max + min) / 2f
            if (max == min) return floatArrayOf(0f, 0f, l)
            val d = max - min
            val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
            val h = when (max) {
                r -> ((g - b) / d + (if (g < b) 6f else 0f)) / 6f
                g -> ((b - r) / d + 2f) / 6f
                else -> ((r - g) / d + 4f) / 6f
            }
            return floatArrayOf(h, s, l)
        }

        private fun fromHsl(h: Float, s: Float, l: Float): FloatArray {
            if (s == 0f) return floatArrayOf(l, l, l)
            val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
            val p = 2 * l - q
            fun hue(t0: Float): Float {
                var t = t0
                if (t < 0f) t += 1f
                if (t > 1f) t -= 1f
                return when {
                    t < 1f / 6f -> p + (q - p) * 6f * t
                    t < 1f / 2f -> q
                    t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                    else -> p
                }
            }
            return floatArrayOf(hue(h + 1f / 3f), hue(h), hue(h - 1f / 3f))
        }
    }
}
