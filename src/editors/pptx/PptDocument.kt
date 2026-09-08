package dev.kern.editors.pptx

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Apache POI bridge for .pptx presentations (XSLF).
 * Extracts editable slides, text shapes, bounding boxes, and per-run character formatting.
 * Non-destructive round-trip serialization preserving unedited shapes, slides, and layouts.
 */
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
            var slideWidth = 960f
            var slideHeight = 540f
            try {
                val presXml = ppt.ctPresentation.toString()
                val match = Regex("""<p:sldSz[^>]*\s+cx="(\d+)"\s+cy="(\d+)"""").find(presXml)
                if (match != null) {
                    slideWidth = match.groupValues[1].toFloat() / 12700f
                    slideHeight = match.groupValues[2].toFloat() / 12700f
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
                    
                    var bounds: ShapeBounds? = null
                    try {
                        val shapeXml = shape.xmlObject.toString()
                        val offMatch = Regex("""<a:off\s+x="(-?\d+)"\s+y="(-?\d+)"""").find(shapeXml)
                        val extMatch = Regex("""<a:ext\s+cx="(\d+)"\s+cy="(\d+)"""").find(shapeXml)
                        if (offMatch != null && extMatch != null) {
                            val x = offMatch.groupValues[1].toFloat() / 12700f
                            val y = offMatch.groupValues[2].toFloat() / 12700f
                            val w = extMatch.groupValues[1].toFloat() / 12700f
                            val h = extMatch.groupValues[2].toFloat() / 12700f
                            bounds = ShapeBounds(x, y, w, h)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

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
