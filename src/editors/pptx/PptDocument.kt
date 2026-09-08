package dev.kern.editors.pptx

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.xmlbeans.XmlObject
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Apache POI bridge for .pptx presentations (XSLF).
 * Extracts editable slides, text shapes, bounding boxes, and per-run character formatting.
 * Non-destructive round-trip serialization preserving unedited shapes, slides, and layouts.
 */
/**
 * English Metric Units per point. OOXML stores every offset and extent in EMU,
 * while the editor and the slide canvas both work in points.
 */
private const val EmuPerPoint = 12700f

object PptDocument {

    data class RunStyle(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val sizePt: Float? = null,
        val colorHex: String? = null,
        val fontFamily: String? = null,
    )

    data class ShapeRun(
        val text: String,
        val style: RunStyle = RunStyle(),
    )

    data class ShapeBounds(val x: Float, val y: Float, val width: Float, val height: Float)

    data class TextShapeModel(
        val shapeIndex: Int,
        val text: String,
        val runs: List<ShapeRun>,
        val bounds: ShapeBounds?,
    )

    enum class PresetLayout {
        TITLE,
        TITLE_AND_CONTENT,
        SECTION_HEADER,
        BLANK,
    }

    data class SlideModel(
        val slideIndex: Int,
        val shapes: List<TextShapeModel>,
        val width: Float,
        val height: Float,
        val backgroundColorHex: String?,
    )

    /** slides[s] = the text of each text shape on slide s, in shape order. */
    data class Parsed(
        val slides: List<List<String>>,
        val slideModels: List<SlideModel> = emptyList(),
    )

    sealed interface SlideOp
    data class InsertSlide(val atIndex: Int, val layout: PresetLayout = PresetLayout.TITLE_AND_CONTENT) : SlideOp
    data class DeleteSlide(val slideIndex: Int) : SlideOp
    data class DuplicateSlide(val fromIndex: Int, val atIndex: Int) : SlideOp
    data class MoveSlide(val fromIndex: Int, val toIndex: Int) : SlideOp
    data class AddTextBox(val slideIndex: Int, val text: String, val runs: List<ShapeRun> = emptyList()) : SlideOp

    fun read(bytes: ByteArray): Parsed {
        XMLSlideShow(ByteArrayInputStream(bytes)).use { ppt ->
            val slidesText = ArrayList<List<String>>()
            val slideModels = ArrayList<SlideModel>()
            // ppt.pageSize would give this directly but returns java.awt.Dimension,
            // and Android ships no java.awt.geom. This typed accessor is equivalent
            // and drags in no AWT. A deck declaring no sldSz keeps the default below.
            var slideWidth = 960f
            var slideHeight = 540f
            ppt.ctPresentation?.sldSz?.let { size ->
                slideWidth = size.cx / EmuPerPoint
                slideHeight = size.cy / EmuPerPoint
            }

            for ((sIndex, slide) in ppt.slides.withIndex()) {
                val textShapes = slide.shapes.filterIsInstance<XSLFTextShape>()
                val shapeTexts = ArrayList<String>()
                val shapeModels = ArrayList<TextShapeModel>()
                
                var bgColorHex: String? = null
                try {
                    val slideXml = slide.xmlObject.toString()
                    val bgMatch = Regex("""<p:bg[^>]*>.*?<a:srgbClr\s+val="([A-Fa-f0-9]{6})"""", RegexOption.DOT_MATCHES_ALL).find(slideXml)
                    if (bgMatch != null) {
                        bgColorHex = "#" + bgMatch.groupValues[1]
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                for ((shapeIdx, shape) in textShapes.withIndex()) {
                    val rawText = shape.text ?: ""
                    shapeTexts.add(rawText)
                    
                    val bounds = boundsOf(shape)

                    val runs = ArrayList<ShapeRun>()
                    for (para in shape.textParagraphs) {
                        for (r in para.textRuns) {
                            val rText = r.rawText ?: ""
                            if (rText.isNotEmpty()) {
                                val bold = r.isBold
                                val italic = r.isItalic
                                val underline = r.isUnderlined
                                val size = r.fontSize?.toFloat()
                                var colorHex: String? = null
                                try {
                                    val rXml = r.xmlObject.toString()
                                    val clrMatch = Regex("""<a:srgbClr\s+val="([A-Fa-f0-9]{6})"""").find(rXml)
                                    if (clrMatch != null) {
                                        colorHex = "#" + clrMatch.groupValues[1]
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                val fontFamily = r.fontFamily
                                runs.add(
                                    ShapeRun(
                                        text = rText,
                                        style = RunStyle(
                                            bold = bold,
                                            italic = italic,
                                            underline = underline,
                                            sizePt = size,
                                            colorHex = colorHex,
                                            fontFamily = fontFamily,
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                    val collapsed = collapse(runs).ifEmpty { listOf(ShapeRun(rawText)) }
                    shapeModels.add(
                        TextShapeModel(
                            shapeIndex = shapeIdx,
                            text = rawText,
                            runs = collapsed,
                            bounds = bounds,
                        ),
                    )
                }

                slidesText.add(shapeTexts)
                slideModels.add(SlideModel(slideIndex = sIndex, shapes = shapeModels, width = slideWidth, height = slideHeight, backgroundColorHex = bgColorHex))
            }

            return Parsed(
                slides = slidesText.ifEmpty { listOf(emptyList()) },
                slideModels = slideModels.ifEmpty { listOf(SlideModel(0, emptyList(), 960f, 540f, null)) },
            )
        }
    }

    /**
     * Writes edits back into the original presentation.
     */
    fun applyEditsAndSerialize(
        originalBytes: ByteArray,
        edits: Map<Pair<Int, Int>, String> = emptyMap(),
        slideOps: List<SlideOp> = emptyList(),
        richEdits: Map<Pair<Int, Int>, List<ShapeRun>> = emptyMap(),
    ): ByteArray {
        XMLSlideShow(ByteArrayInputStream(originalBytes)).use { ppt ->
            // 1. Apply structural slide operations
            for (op in slideOps) {
                when (op) {
                    is InsertSlide -> {
                        val newSlide = ppt.createSlide()
                        val tb = newSlide.createTextBox()
                        if (op.layout == PresetLayout.TITLE) {
                            tb.setText("Title")
                        } else if (op.layout == PresetLayout.TITLE_AND_CONTENT) {
                            tb.setText("Title")
                            newSlide.createTextBox().setText("Content")
                        } else if (op.layout == PresetLayout.SECTION_HEADER) {
                            tb.setText("Section Header")
                        }
                    }
                    is DeleteSlide -> {
                        if (ppt.slides.size > 1 && op.slideIndex in ppt.slides.indices) {
                            ppt.removeSlide(op.slideIndex)
                        }
                    }
                    is DuplicateSlide -> {
                        if (op.fromIndex in ppt.slides.indices) {
                            val sourceSlide = ppt.slides[op.fromIndex]
                            val newSlide = ppt.createSlide()
                            for (shape in sourceSlide.shapes.filterIsInstance<XSLFTextShape>()) {
                                val newTb = newSlide.createTextBox()
                                newTb.text = shape.text
                            }
                        }
                    }
                    is MoveSlide -> {
                        if (op.fromIndex in ppt.slides.indices && op.toIndex in ppt.slides.indices) {
                            ppt.setSlideOrder(ppt.slides[op.fromIndex], op.toIndex)
                        }
                    }
                    is AddTextBox -> {
                        if (op.slideIndex in ppt.slides.indices) {
                            val slide = ppt.slides[op.slideIndex]
                            val tb = slide.createTextBox()
                            if (op.runs.isNotEmpty()) {
                                applyRunsToShape(tb, op.runs)
                            } else {
                                tb.setText(op.text)
                            }
                        }
                    }
                }
            }

            // 2. Apply rich-text or plain-text edits to existing shapes
            val slides = ppt.slides
            for ((key, runs) in richEdits) {
                val (slideIndex, shapeIndex) = key
                if (slideIndex < 0 || slideIndex >= slides.size) continue
                val textShapes = slides[slideIndex].shapes.filterIsInstance<XSLFTextShape>()
                val shape = textShapes.getOrNull(shapeIndex) ?: continue
                applyRunsToShape(shape, runs)
            }

            for ((key, text) in edits) {
                if (richEdits.containsKey(key)) continue
                val (slideIndex, shapeIndex) = key
                if (slideIndex < 0 || slideIndex >= slides.size) continue
                val textShapes = slides[slideIndex].shapes.filterIsInstance<XSLFTextShape>()
                textShapes.getOrNull(shapeIndex)?.setText(text)
            }

            ByteArrayOutputStream().use { out ->
                ppt.write(out)
                return out.toByteArray()
            }
        }
    }

    /**
     * A shape's position in points, or null when nothing in the file declares one.
     *
     * Geometry sits on the shape when it was placed explicitly, and is inherited from
     * the slide layout's matching placeholder when it was not, which is the normal case
     * for the title and body of a deck built from a template. Reading only the first of
     * those leaves every inherited placeholder with no position at all.
     *
     * POI exposes the resolved rectangle as getAnchor(), but that returns
     * java.awt.geom.Rectangle2D and Android ships no java.awt.geom, so this reads the
     * typed OOXML accessors instead. Do not replace it with shape.anchor: it compiles
     * and then throws NoClassDefFoundError on a device.
     */
    private fun boundsOf(shape: XSLFTextShape): ShapeBounds? {
        boundsOfXml(shape.xmlObject)?.let { return it }
        val slot = shape.placeholder ?: return null
        val layout = (shape.sheet as? XSLFSlide)?.slideLayout ?: return null
        val inherited = layout.placeholders.firstOrNull { it.placeholder == slot } ?: return null
        return boundsOfXml(inherited.xmlObject)
    }

    private fun boundsOfXml(xml: XmlObject): ShapeBounds? {
        val xfrm = (xml as? CTShape)?.spPr?.xfrm ?: return null
        val off = xfrm.off ?: return null
        val ext = xfrm.ext ?: return null
        val x = coordinateToPoints(off.x) ?: return null
        val y = coordinateToPoints(off.y) ?: return null
        return ShapeBounds(x, y, ext.cx / EmuPerPoint, ext.cy / EmuPerPoint)
    }

    /**
     * A drawing coordinate in points, or null if it cannot be read.
     *
     * ST_Coordinate is a union type, which is why the generated accessor hands back
     * Object rather than a number. PowerPoint writes plain EMU integers, but the schema
     * equally allows a universal measure such as "1in" or "2.5cm", and a file using one
     * would otherwise lose the position entirely.
     */
    private fun coordinateToPoints(value: Any?): Float? = when (value) {
        is Number -> value.toFloat() / EmuPerPoint
        is String -> measureToPoints(value)
        else -> null
    }

    private fun measureToPoints(raw: String): Float? {
        val text = raw.trim()
        text.toFloatOrNull()?.let { return it / EmuPerPoint }
        val match = Regex("""^(-?[0-9]*\.?[0-9]+)(mm|cm|in|pt|pc|pi)$""").find(text) ?: return null
        val n = match.groupValues[1].toFloatOrNull() ?: return null
        return when (match.groupValues[2]) {
            "pt" -> n
            "in" -> n * 72f
            "cm" -> n * 72f / 2.54f
            "mm" -> n * 72f / 25.4f
            else -> n * 12f // pc and pi are both picas
        }
    }

    private fun applyRunsToShape(shape: XSLFTextShape, runs: List<ShapeRun>) {
        shape.clearText()
        val para = if (shape.textParagraphs.isEmpty()) shape.addNewTextParagraph() else shape.textParagraphs.first()
        for (run in runs) {
            val r = para.addNewTextRun()
            r.setText(run.text)
            r.isBold = run.style.bold
            r.isItalic = run.style.italic
            r.isUnderlined = run.style.underline
            run.style.sizePt?.let { r.fontSize = it.toDouble() }
            run.style.fontFamily?.let { r.setFontFamily(it) }
            run.style.colorHex?.let { hex ->
                try {
                    val rPrMethod = r.xmlObject.javaClass.getMethod("isSetRPr")
                    val isSet = rPrMethod.invoke(r.xmlObject) as Boolean
                    val rPr = if (isSet) {
                        r.xmlObject.javaClass.getMethod("getRPr").invoke(r.xmlObject)
                    } else {
                        r.xmlObject.javaClass.getMethod("addNewRPr").invoke(r.xmlObject)
                    }
                    val solidFillMethod = rPr.javaClass.getMethod("isSetSolidFill")
                    val isSetSolid = solidFillMethod.invoke(rPr) as Boolean
                    val solidFill = if (isSetSolid) {
                        rPr.javaClass.getMethod("getSolidFill").invoke(rPr)
                    } else {
                        rPr.javaClass.getMethod("addNewSolidFill").invoke(rPr)
                    }
                    val srgbClrMethod = solidFill.javaClass.getMethod("isSetSrgbClr")
                    val isSetSrgb = srgbClrMethod.invoke(solidFill) as Boolean
                    val srgbClr = if (isSetSrgb) {
                        solidFill.javaClass.getMethod("getSrgbClr").invoke(solidFill)
                    } else {
                        solidFill.javaClass.getMethod("addNewSrgbClr").invoke(solidFill)
                    }
                    val setValMethod = srgbClr.javaClass.getMethod("setVal", ByteArray::class.java)
                    val clean = hex.removePrefix("#")
                    val parsed = clean.toLongOrNull(16) ?: return@let
                    val rVal = (parsed shr 16 and 0xFF).toByte()
                    val gVal = (parsed shr 8 and 0xFF).toByte()
                    val bVal = (parsed and 0xFF).toByte()
                    setValMethod.invoke(srgbClr, byteArrayOf(rVal, gVal, bVal))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun collapse(runs: List<ShapeRun>): List<ShapeRun> {
        val out = ArrayList<ShapeRun>()
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.style == run.style) {
                out[out.lastIndex] = last.copy(text = last.text + run.text)
            } else {
                out.add(run)
            }
        }
        return out
    }
}
