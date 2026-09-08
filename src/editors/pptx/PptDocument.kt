package dev.kern.editors.pptx

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFGraphicFrame
import org.apache.poi.xslf.usermodel.XSLFGroupShape
import org.apache.poi.xslf.usermodel.XSLFPictureData
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.xmlbeans.XmlObject
import org.openxmlformats.schemas.presentationml.x2006.main.CTConnector
import org.openxmlformats.schemas.presentationml.x2006.main.CTGraphicalObjectFrame
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture
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

    /**
     * Something on a slide that the editor draws but does not edit.
     *
     * Editable text keeps its own list with stable indices, because those indices are how
     * an edit is routed back to the right POI shape on save. Decorations carry no index
     * and are drawn underneath, so adding support for a new kind cannot disturb editing.
     */
    sealed interface SlideDecoration {
        val bounds: ShapeBounds?
    }

    /**
     * An embedded raster image.
     *
     * [fillsShape] separates the two ways an image reaches a slide. A placed picture is
     * framed to its own proportions and must not be cropped, so it is fitted. A shape's
     * fill is the shape's surface: PowerPoint scales it to cover and crops the overflow,
     * which is what `a:stretch` with negative `fillRect` insets describes. Fitting one of
     * those leaves the slide showing through around a full-bleed backdrop.
     */
    data class PictureDecoration(
        val bytes: ByteArray,
        val fileName: String?,
        override val bounds: ShapeBounds?,
        val fillsShape: Boolean = false,
    ) : SlideDecoration {
        override fun equals(other: Any?): Boolean {
            if (other !is PictureDecoration) return false
            return bytes.contentEquals(other.bytes) && fileName == other.fileName &&
                bounds == other.bounds && fillsShape == other.fillsShape
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + (fileName?.hashCode() ?: 0)
            result = 31 * result + (bounds?.hashCode() ?: 0)
            return 31 * result + fillsShape.hashCode()
        }
    }

    /** A table, as plain cell text in row order. */
    data class TableDecoration(
        val rows: List<List<String>>,
        override val bounds: ShapeBounds?,
    ) : SlideDecoration

    /**
     * A shape the editor cannot draw yet: a chart, SmartArt, an embedded object, or a
     * group. Drawn as a labelled outline in the right place, because a reader needs to
     * know something is there. Silently dropping it is what makes a deck look corrupted.
     */
    data class UnsupportedDecoration(
        val label: String,
        override val bounds: ShapeBounds?,
    ) : SlideDecoration

    data class SlideModel(
        val slideIndex: Int,
        val shapes: List<TextShapeModel>,
        val width: Float,
        val height: Float,
        val backgroundColorHex: String?,
        val decorations: List<SlideDecoration> = emptyList(),
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
                val textShapes = editableTextShapes(slide)
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
                slideModels.add(SlideModel(slideIndex = sIndex, shapes = shapeModels, width = slideWidth, height = slideHeight, backgroundColorHex = bgColorHex, decorations = decorationsOf(slide)))
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
                            val newSlide = ppt.createSlide(sourceSlide.slideLayout)
                            copySlideContent(sourceSlide, newSlide)
                            // createSlide appends; the duplicate belongs next to its source.
                            if (op.atIndex in ppt.slides.indices) {
                                ppt.setSlideOrder(newSlide, op.atIndex)
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
                val textShapes = editableTextShapes(slides[slideIndex])
                val shape = textShapes.getOrNull(shapeIndex) ?: continue
                applyRunsToShape(shape, runs)
            }

            for ((key, text) in edits) {
                if (richEdits.containsKey(key)) continue
                val (slideIndex, shapeIndex) = key
                if (slideIndex < 0 || slideIndex >= slides.size) continue
                val textShapes = editableTextShapes(slides[slideIndex])
                textShapes.getOrNull(shapeIndex)?.setText(text)
            }

            ByteArrayOutputStream().use { out ->
                ppt.write(out)
                return out.toByteArray()
            }
        }
    }

    /**
     * The text shapes of a slide, in the order the editor addresses them.
     *
     * Read and write both go through this, and they have to agree exactly: an edit is
     * routed back to POI by its index in this list, so a shape excluded on one side and
     * kept on the other would send the edit to the wrong shape.
     *
     * A shape that is filled with a picture and carries no text is background art rather
     * than content. PowerPoint draws a full-slide backdrop as an autoshape with a picture
     * fill, and XSLFAutoShape extends XSLFTextShape, so without this it arrives as an
     * empty text box covering the entire slide.
     */
    private fun editableTextShapes(slide: XSLFSlide): List<XSLFTextShape> =
        slide.shapes.filterIsInstance<XSLFTextShape>()
            .filterNot { it.text.isNullOrBlank() && fillPictureOf(it) != null }

    /**
     * The image a shape is filled with, resolved through the sheet's relationships.
     *
     * This is a separate path from XSLFPictureShape: a picture placed on a slide is a
     * p:pic, but a shape whose surface is an image is a p:sp whose spPr carries a
     * blipFill referencing the image by relationship id.
     */
    private fun fillPictureOf(shape: XSLFShape): PictureDecoration? {
        val spPr = when (val xml = shape.xmlObject) {
            is CTShape -> xml.spPr
            is CTConnector -> xml.spPr
            else -> null
        } ?: return null
        // A blip with no embed is an SVG carried only in an extension list, which has no
        // raster for BitmapFactory to decode.
        val embedId = spPr.blipFill?.blip?.embed?.takeIf { it.isNotBlank() } ?: return null
        val picture = shape.sheet?.getRelationById(embedId) as? XSLFPictureData ?: return null
        val bytes = picture.data?.takeIf { it.isNotEmpty() } ?: return null
        return PictureDecoration(bytes, picture.fileName, boundsOfXml(shape.xmlObject), fillsShape = true)
    }

    /**
     * Copies one slide's shape tree onto another, keeping images, tables, formatting and
     * positions. Rebuilding a slide from its text alone would keep the words and discard
     * everything else, which is silent data loss on the user's file.
     *
     * POI's importContent does exactly this and then walks the copied shapes through
     * XSLFShape.copy, which reads getAnchor and constructs java.awt.geom.Rectangle2D.
     * Android ships no java.awt.geom, so that path throws NoClassDefFoundError the moment
     * the slide holds a picture. Copying the XML and re-declaring the source's package
     * relationships reaches the same result without constructing a shape object at all.
     *
     * Known limit: POI's cached shape list for the target is not rebuilt, because the only
     * method that rebuilds it is the one that crashes. A text edit made to a duplicated
     * slide before the file has been saved is therefore not written. Editing the duplicate
     * after reopening the file behaves normally.
     */
    private fun copySlideContent(source: XSLFSlide, target: XSLFSlide) {
        // The copied shape XML refers to its images by relationship id (r:embed="rId2"),
        // so the target part has to declare the same ids against the same targets. The
        // image parts themselves are shared, not duplicated.
        val taken = target.packagePart.relationships.map { it.id }.toSet()
        for (rel in source.packagePart.relationships) {
            if (rel.id in taken) continue
            target.packagePart.addRelationship(rel.targetURI, rel.targetMode, rel.relationshipType, rel.id)
        }
        target.xmlObject.cSld.spTree.set(source.xmlObject.cSld.spTree)
    }

    /**
     * Everything on a slide that is not editable text, in document order.
     *
     * A group is reported as unsupported rather than recursed into: its children carry
     * coordinates relative to the group's own child offset, so drawing them without
     * applying that transform would scatter them across the slide.
     */
    private fun decorationsOf(slide: XSLFSlide): List<SlideDecoration> =
        slide.shapes.mapNotNull { shape ->
            when (shape) {
                is XSLFPictureShape -> shape.pictureData?.data
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { PictureDecoration(it, shape.pictureData?.fileName, boundsOfXml(shape.xmlObject)) }

                is XSLFTable -> TableDecoration(
                    rows = shape.rows.map { row -> row.cells.map { it.text ?: "" } },
                    bounds = boundsOfXml(shape.xmlObject),
                )

                is XSLFGraphicFrame -> UnsupportedDecoration("Chart or diagram", boundsOfXml(shape.xmlObject))
                is XSLFGroupShape -> UnsupportedDecoration("Grouped shapes", boundsOfXml(shape.xmlObject))
                // An autoshape whose surface is an image, which is how a full-slide
                // backdrop is normally authored.
                else -> fillPictureOf(shape)
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
        // Each shape kind hangs its transform in a slightly different place, and a
        // graphic frame keeps xfrm directly rather than under spPr.
        val xfrm = when (xml) {
            is CTShape -> xml.spPr?.xfrm
            is CTPicture -> xml.spPr?.xfrm
            is CTConnector -> xml.spPr?.xfrm
            is CTGraphicalObjectFrame -> xml.xfrm
            else -> null
        } ?: return null
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
