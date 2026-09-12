package dev.kern.editors.pptx

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.sl.usermodel.TextParagraph
import org.apache.poi.sl.usermodel.VerticalAlignment
import org.apache.poi.xslf.usermodel.XSLFSheet
import org.openxmlformats.schemas.presentationml.x2006.main.CTCommonSlideData
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlide
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlideLayout
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlideMaster
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
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraph
import org.openxmlformats.schemas.drawingml.x2006.main.STTextAlignType
import org.openxmlformats.schemas.drawingml.x2006.main.STTextAnchoringType
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

    /** How a surface is painted: a slide background or an autoshape's interior. */
    sealed interface Fill

    /** Six hex digits, or eight with alpha first, no hash. */
    data class SolidFill(val hex: String) : Fill

    data class GradientStop(val position: Float, val color: String)

    /** A linear gradient; [angleDegrees] is DrawingML's, 0 pointing right and 90 down. */
    data class GradientFill(val stops: List<GradientStop>, val angleDegrees: Float) : Fill

    /** An image covering the surface, cropped to it. */
    class PictureFill(val bytes: ByteArray, val fileName: String?) : Fill {
        override fun equals(other: Any?): Boolean =
            other is PictureFill && bytes.contentEquals(other.bytes) && fileName == other.fileName

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + (fileName?.hashCode() ?: 0)
    }

    /** A shape's border: colour as hex, width in points. */
    data class Outline(val colorHex: String, val width: Float)

    data class TextShapeModel(
        val shapeIndex: Int,
        val text: String,
        val runs: List<ShapeRun>,
        val bounds: ShapeBounds?,
        /**
         * The colour a run with no colour of its own is shown in: the shape's font
         * reference, the placeholder's list style, the master's text styles, or the
         * theme's text colour, whichever the hierarchy reaches first. Display only; it
         * is never written back, so the inheritance stays intact in the file.
         */
        val defaultColorHex: String? = null,
        /** The effective alignment of each paragraph as an OOXML token (l, ctr, r, just), or null. */
        val alignments: List<String?> = emptyList(),
        /** Where the text sits in the box, as the OOXML anchor token (t, ctr, b), or null. */
        val anchor: String? = null,
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
     * A preset autoshape, drawn as vector geometry: the boxes and arrows of a diagram.
     *
     * [preset] is the DrawingML preset name (`roundRect`, `rightArrow`, ...) and the
     * renderer decides whether it knows the outline; [adjustments] are the shape's own
     * `avLst` guides, keyed by name, in DrawingML's 1/100000 units. A null fill or
     * outline means the shape has none, after its own properties and its theme style
     * references have both been consulted. When the shape also carries text, that text
     * is a separate editable shape drawn on top.
     */
    data class GeometryDecoration(
        val preset: String,
        val fill: Fill?,
        val outline: Outline?,
        val adjustments: Map<String, Int>,
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
        /** The resolved background, or null for the plain white PowerPoint shows without one. */
        val background: Fill?,
        val decorations: List<SlideDecoration> = emptyList(),
        /** The theme's text colour on this slide, for text the editor adds. */
        val textColorHex: String? = null,
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
                val theme = PptTheme.of(slide)
                val textShapes = editableTextShapes(slide)
                val shapeTexts = ArrayList<String>()
                val shapeModels = ArrayList<TextShapeModel>()

                for ((shapeIdx, shape) in textShapes.withIndex()) {
                    val rawText = shape.text ?: ""
                    shapeTexts.add(rawText)

                    val bounds = boundsOf(shape)

                    val runs = ArrayList<ShapeRun>()
                    val alignments = shape.textParagraphs.map { alignTokenOf(it) }
                    for ((paraIndex, para) in shape.textParagraphs.withIndex()) {
                        // Paragraphs are separate elements in the file; in the editor
                        // they are one text with a newline between them, and that
                        // newline is what the writer splits on to rebuild them.
                        if (paraIndex > 0) runs.add(ShapeRun("\n", runs.lastOrNull()?.style ?: RunStyle()))
                        for (r in para.textRuns) {
                            val rText = r.rawText ?: ""
                            if (rText.isNotEmpty()) {
                                val bold = r.isBold
                                val italic = r.isItalic
                                val underline = r.isUnderlined
                                val size = r.fontSize?.toFloat()
                                // A theme colour on the run is resolved to sRGB here, so
                                // a run in an edited shape is written back pinned to that
                                // value. Runs with no colour at all stay inherited.
                                val colorHex = (r.xmlObject as? CTRegularTextRun)?.rPr?.solidFill
                                    ?.let { fill -> theme?.colorOf(fill) ?: fill.srgbClr?.let { PptTheme.srgbColor(it) } }
                                    ?.let { "#$it" }
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
                            defaultColorHex = defaultTextColorOf(shape, theme),
                            alignments = alignments,
                            anchor = anchorTokenOf(shape),
                        ),
                    )
                }

                slidesText.add(shapeTexts)
                slideModels.add(
                    SlideModel(
                        slideIndex = sIndex,
                        shapes = shapeModels,
                        width = slideWidth,
                        height = slideHeight,
                        background = backgroundOf(slide, theme),
                        decorations = decorationsOf(slide, theme),
                        textColorHex = theme?.slotColor("tx1"),
                    ),
                )
            }

            return Parsed(
                slides = slidesText.ifEmpty { listOf(emptyList()) },
                slideModels = slideModels.ifEmpty { listOf(SlideModel(0, emptyList(), 960f, 540f, background = null)) },
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
        boundsEdits: Map<Pair<Int, Int>, ShapeBounds> = emptyMap(),
        /** Per paragraph alignment tokens for the shapes in [richEdits]. */
        alignEdits: Map<Pair<Int, Int>, List<String?>> = emptyMap(),
        /** New positions for decorations, keyed by (slide, index in decorationsOf). */
        decorationBoundsEdits: Map<Pair<Int, Int>, ShapeBounds> = emptyMap(),
        /** New vertical anchors (t, ctr, b) for text shapes. */
        anchorEdits: Map<Pair<Int, Int>, String> = emptyMap(),
    ): ByteArray {
        var ppt = XMLSlideShow(ByteArrayInputStream(originalBytes))
        try {
            // 1. Apply structural slide operations
            for (op in slideOps) {
                when (op) {
                    is InsertSlide -> {
                        val newSlide = ppt.createSlide()
                        // createSlide appends; the editor inserted after the current slide,
                        // and later edits are addressed by that position.
                        if (op.atIndex in ppt.slides.indices) ppt.setSlideOrder(newSlide, op.atIndex)
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

            // A duplicated slide's XML is right but POI's cached shape list for it is
            // stale (see copySlideContent), and the only rebuild path crashes on
            // Android. Serialising and reopening rebuilds every cache the honest way,
            // so edits made on the duplicate before this save reach the file.
            if (slideOps.any { it is DuplicateSlide }) {
                val snapshot = ByteArrayOutputStream().use { out -> ppt.write(out); out.toByteArray() }
                ppt.close()
                ppt = XMLSlideShow(ByteArrayInputStream(snapshot))
            }

            // 2. Apply rich-text or plain-text edits to existing shapes
            val slides = ppt.slides
            for ((key, runs) in richEdits) {
                val (slideIndex, shapeIndex) = key
                if (slideIndex < 0 || slideIndex >= slides.size) continue
                val textShapes = editableTextShapes(slides[slideIndex])
                val shape = textShapes.getOrNull(shapeIndex) ?: continue
                applyRunsToShape(shape, runs, alignEdits[key].orEmpty())
            }

            for ((key, bounds) in boundsEdits) {
                val (slideIndex, shapeIndex) = key
                if (slideIndex < 0 || slideIndex >= slides.size) continue
                val shape = editableTextShapes(slides[slideIndex]).getOrNull(shapeIndex) ?: continue
                setBounds(shape, bounds)
            }

            for ((key, anchor) in anchorEdits) {
                val (slideIndex, shapeIndex) = key
                if (slideIndex < 0 || slideIndex >= slides.size) continue
                val shape = editableTextShapes(slides[slideIndex]).getOrNull(shapeIndex) ?: continue
                val body = (shape.xmlObject as? CTShape)?.txBody ?: continue
                (body.bodyPr ?: body.addNewBodyPr()).anchor = STTextAnchoringType.Enum.forString(anchor)
            }

            for ((key, bounds) in decorationBoundsEdits) {
                val (slideIndex, decorationIndex) = key
                if (slideIndex < 0 || slideIndex >= slides.size) continue
                val slide = slides[slideIndex]
                val shape = decorationEntries(slide, PptTheme.of(slide)).getOrNull(decorationIndex)?.first ?: continue
                setBoundsOfXml(shape.xmlObject, bounds)
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
        } finally {
            ppt.close()
        }
    }

    /**
     * The text shapes of a slide, in the order the editor addresses them.
     *
     * Read and write both go through this, and they have to agree exactly: an edit is
     * routed back to POI by its index in this list, so a shape excluded on one side and
     * kept on the other would send the edit to the wrong shape.
     *
     * A shape that carries no text but does carry a fill is artwork rather than content:
     * the full-slide backdrop PowerPoint authors as an autoshape with a picture fill, or
     * the solid arrows of a diagram. XSLFAutoShape extends XSLFTextShape, so without this
     * each of them arrives as an empty text box, the backdrop covering the entire slide.
     * An empty shape with no fill stays editable, because that is an empty text box.
     */
    private fun editableTextShapes(slide: XSLFSlide): List<XSLFTextShape> =
        slide.shapes.filterIsInstance<XSLFTextShape>()
            .filterNot { it.text.isNullOrBlank() && isFilledArtwork(it) }

    private fun isFilledArtwork(shape: XSLFShape): Boolean =
        fillPictureOf(shape) != null ||
            geometryOf(shape, (shape.sheet as? XSLFSlide)?.let { PptTheme.of(it) })?.fill != null

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
     * The preset geometry and paint of an autoshape, or null when there is nothing to
     * draw: no preset, or neither a fill nor an outline once the shape's own properties
     * and its `p:style` references into the theme have both been read.
     *
     * The shape's own `spPr` wins over the style: an explicit fill, gradient or
     * `noFill` replaces the `fillRef`, and an `a:ln` that names a colour or `noFill`
     * replaces the `lnRef`. An `a:ln` that only sets a width keeps the referenced
     * colour, which is how PowerPoint writes "same outline, thicker".
     */
    private fun geometryOf(shape: XSLFShape, theme: PptTheme?): GeometryDecoration? {
        val xml = shape.xmlObject as? CTShape ?: return null
        val spPr = xml.spPr ?: return null
        val geom = spPr.prstGeom ?: return null
        val preset = geom.prst?.toString() ?: return null
        val style = xml.style

        val fill = when {
            spPr.noFill != null -> null
            spPr.solidFill != null || spPr.gradFill != null ->
                theme?.fillOf(spPr.solidFill, spPr.gradFill)
                    ?: spPr.solidFill?.srgbClr?.let { PptTheme.srgbColor(it) }?.let { SolidFill(it) }
            else -> style?.fillRef?.let { ref -> theme?.fillStyle(ref.idx, theme.refColor(ref.srgbClr, ref.schemeClr)) }
        }

        val line = spPr.ln
        val outline = when {
            line?.noFill != null -> null
            line?.solidFill != null -> {
                val color = theme?.colorOf(line.solidFill) ?: line.solidFill.srgbClr?.let { PptTheme.srgbColor(it) }
                color?.let { Outline(it, PptTheme.lineWidthOf(line)) }
            }
            else -> style?.lnRef
                ?.let { ref -> theme?.lineStyle(ref.idx, theme.refColor(ref.srgbClr, ref.schemeClr)) }
                ?.let { if (line != null && line.isSetW) it.copy(width = PptTheme.lineWidthOf(line)) else it }
        }
        if (fill == null && outline == null) return null

        val adjustments = geom.avLst?.gdList.orEmpty().mapNotNull { guide ->
            val name = guide.name ?: return@mapNotNull null
            // Adjust guides are always literal: "val 16667".
            val value = guide.fmla?.removePrefix("val ")?.trim()?.toIntOrNull() ?: return@mapNotNull null
            name to value
        }.toMap()
        val bounds = (shape as? XSLFTextShape)?.let { boundsOf(it) } ?: boundsOfXml(xml)
        return GeometryDecoration(preset, fill, outline, adjustments, bounds)
    }

    /**
     * The background PowerPoint paints behind [slide]: the slide's own `p:bg`, else its
     * layout's, else its master's. A `bgPr` carries the fill directly, a `bgRef` points
     * into the theme's background fill styles with its colour as the placeholder. An
     * image background is resolved against the sheet that declares it, because that is
     * where its relationship id is defined.
     */
    private fun backgroundOf(slide: XSLFSlide, theme: PptTheme?): Fill? {
        val sheets: List<XSLFSheet> = listOfNotNull(slide, slide.slideLayout, slide.slideMaster)
        for (sheet in sheets) {
            val bg = commonSlideDataOf(sheet)?.bg ?: continue
            bg.bgPr?.let { pr ->
                pr.blipFill?.blip?.embed?.takeIf { it.isNotBlank() }?.let { id ->
                    val picture = sheet.getRelationById(id) as? XSLFPictureData
                    picture?.data?.takeIf { it.isNotEmpty() }?.let { return PictureFill(it, picture.fileName) }
                }
                return theme?.fillOf(pr.solidFill, pr.gradFill)
                    ?: pr.solidFill?.srgbClr?.let { PptTheme.srgbColor(it) }?.let { SolidFill(it) }
            }
            bg.bgRef?.let { ref -> return theme?.fillStyle(ref.idx, theme.refColor(ref.srgbClr, ref.schemeClr)) }
            return null
        }
        return null
    }

    private fun commonSlideDataOf(sheet: XSLFSheet): CTCommonSlideData? = when (val xml = sheet.xmlObject) {
        is CTSlide -> xml.cSld
        is CTSlideLayout -> xml.cSld
        is CTSlideMaster -> xml.cSld
        else -> null
    }

    /**
     * The colour of text in [shape] that names no colour itself, walking the same
     * hierarchy PowerPoint does: the shape's font reference, the matching placeholder
     * on the layout and then the master, the master's text styles for the placeholder
     * kind, and finally the theme's text colour.
     */
    private fun defaultTextColorOf(shape: XSLFTextShape, theme: PptTheme?): String? {
        if (theme == null) return null
        val xml = shape.xmlObject as? CTShape
        xml?.style?.fontRef?.let { ref -> theme.refColor(ref.srgbClr, ref.schemeClr)?.let { return it } }
        val slot = shape.placeholder
        val layout = (shape.sheet as? XSLFSlide)?.slideLayout
        val master = layout?.slideMaster
        if (slot != null) {
            for (sheet in listOfNotNull(layout, master)) {
                val inherited = sheet.placeholders.firstOrNull { it.placeholder == slot } ?: continue
                val defRPr = (inherited.xmlObject as? CTShape)?.txBody?.lstStyle?.lvl1PPr?.defRPr
                theme.colorOf(defRPr?.solidFill)?.let { return it }
            }
        }
        val styles = master?.xmlObject?.txStyles
        val listStyle = when (slot) {
            null -> styles?.otherStyle
            Placeholder.TITLE, Placeholder.CENTERED_TITLE -> styles?.titleStyle
            Placeholder.BODY, Placeholder.CONTENT, Placeholder.SUBTITLE -> styles?.bodyStyle
            else -> styles?.otherStyle
        }
        theme.colorOf(listStyle?.lvl1PPr?.defRPr?.solidFill)?.let { return it }
        return theme.slotColor("tx1")
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
     * POI's cached shape list for the target is not rebuilt here, because the only
     * method that rebuilds it is the one that crashes; applyEditsAndSerialize reopens
     * the document after the structural operations instead, which rebuilds it.
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
        // The background and colour map live beside the shape tree, not in it. Without
        // these a duplicate of a slide with its own gradient comes back in the master's.
        val sourceData = source.xmlObject.cSld
        if (sourceData.isSetBg) target.xmlObject.cSld.bg = sourceData.bg
        if (source.xmlObject.isSetClrMapOvr) target.xmlObject.clrMapOvr = source.xmlObject.clrMapOvr
    }

    /**
     * Everything on a slide that is not editable text, in document order.
     *
     * A group is reported as unsupported rather than recursed into: its children carry
     * coordinates relative to the group's own child offset, so drawing them without
     * applying that transform would scatter them across the slide.
     */
    private fun decorationsOf(slide: XSLFSlide, theme: PptTheme?): List<SlideDecoration> =
        decorationEntries(slide, theme).map { it.second }

    /**
     * Each decoration with the POI shape it came from, in the order the editor indexes
     * them. Read and write share this so a moved decoration lands on its own shape.
     */
    private fun decorationEntries(slide: XSLFSlide, theme: PptTheme?): List<Pair<XSLFShape, SlideDecoration>> =
        slide.shapes.mapNotNull { shape -> decorationOf(shape, theme)?.let { shape to it } }

    private fun decorationOf(shape: XSLFShape, theme: PptTheme?): SlideDecoration? = when (shape) {
        is XSLFPictureShape -> shape.pictureData?.data
            ?.takeIf { it.isNotEmpty() }
            ?.let { PictureDecoration(it, shape.pictureData?.fileName, boundsOfXml(shape.xmlObject)) }

        is XSLFTable -> TableDecoration(
            rows = shape.rows.map { row -> row.cells.map { it.text ?: "" } },
            bounds = boundsOfXml(shape.xmlObject),
        )

        is XSLFGraphicFrame -> UnsupportedDecoration("Chart or diagram", boundsOfXml(shape.xmlObject))
        is XSLFGroupShape -> UnsupportedDecoration("Grouped shapes", boundsOfXml(shape.xmlObject))
        // An autoshape: either its surface is an image, which is how a full-slide
        // backdrop is normally authored, or it is preset geometry with a fill or
        // outline of its own.
        else -> fillPictureOf(shape) ?: geometryOf(shape, theme)
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

    /**
     * Replaces a shape's text with [runs], keeping what the runs do not describe.
     *
     * The editor models bold, italic, underline, size, colour and font. A shape carries
     * more than that: paragraph alignment, bullets, spacing, and per-run language,
     * kerning and effects. Clearing the text and adding fresh paragraphs would reset
     * all of it, so each new paragraph takes the paragraph properties of the one that
     * stood at its index (or the last one, for paragraphs the edit added), and every
     * run starts from a copy of the shape's first run properties before the modelled
     * fields are applied on top. A newline in a run is a paragraph boundary.
     */
    private fun applyRunsToShape(shape: XSLFTextShape, runs: List<ShapeRun>, alignments: List<String?> = emptyList()) {
        val body = (shape.xmlObject as? CTShape)?.txBody
        // Detached copies: clearText removes the originals from the document.
        val oldParagraphs = body?.pList.orEmpty().map { it.copy() as CTTextParagraph }
        val templateRPr = oldParagraphs.firstNotNullOfOrNull { it.rList.firstOrNull()?.rPr }
        shape.clearText()
        splitParagraphs(runs).forEachIndexed { index, paragraphRuns ->
            val para = shape.addNewTextParagraph()
            val source = oldParagraphs.getOrNull(index) ?: oldParagraphs.lastOrNull()
            source?.pPr?.let { para.xmlObject.pPr = it }
            source?.endParaRPr?.let { para.xmlObject.endParaRPr = it }
            alignments.getOrNull(index)?.let { token ->
                val pPr = para.xmlObject.pPr ?: para.xmlObject.addNewPPr()
                pPr.algn = STTextAlignType.Enum.forString(token)
            }
            for (run in paragraphRuns) {
                val r = para.addNewTextRun()
                val rPr = (r.xmlObject as? CTRegularTextRun)?.let { ct ->
                    templateRPr?.let { ct.rPr = it }
                    ct.rPr ?: ct.addNewRPr()
                }
                r.setText(run.text)
                r.isBold = run.style.bold
                r.isItalic = run.style.italic
                r.isUnderlined = run.style.underline
                run.style.fontFamily?.let { r.setFontFamily(it) }
                if (rPr != null) {
                    // A null in the model means "inherited": the template must not
                    // leak its own explicit value into a run that never had one.
                    val size = run.style.sizePt
                    if (size != null) r.fontSize = size.toDouble() else if (rPr.isSetSz) rPr.unsetSz()
                    val rgb = run.style.colorHex?.let { rgbBytes(it) }
                    if (rgb != null) {
                        val fill = if (rPr.isSetSolidFill) rPr.solidFill else rPr.addNewSolidFill()
                        if (fill.isSetSchemeClr) fill.unsetSchemeClr()
                        if (fill.isSetSysClr) fill.unsetSysClr()
                        (if (fill.isSetSrgbClr) fill.srgbClr else fill.addNewSrgbClr()).`val` = rgb
                    } else if (rPr.isSetSolidFill) {
                        rPr.unsetSolidFill()
                    }
                }
            }
        }
    }

    /**
     * Writes a shape's position and size as its own transform. A placeholder that
     * inherited its geometry from the layout gets an explicit one, which is exactly
     * what PowerPoint does the moment such a shape is moved.
     */
    private fun setBounds(shape: XSLFTextShape, bounds: ShapeBounds) = setBoundsOfXml(shape.xmlObject, bounds)

    /** Writes a transform onto whichever shape kind [xml] is; a group is left alone. */
    private fun setBoundsOfXml(xml: XmlObject, bounds: ShapeBounds) {
        val xfrm = when (xml) {
            is CTShape -> (xml.spPr ?: xml.addNewSpPr()).let { it.xfrm ?: it.addNewXfrm() }
            is CTPicture -> (xml.spPr ?: xml.addNewSpPr()).let { it.xfrm ?: it.addNewXfrm() }
            is CTConnector -> (xml.spPr ?: xml.addNewSpPr()).let { it.xfrm ?: it.addNewXfrm() }
            is CTGraphicalObjectFrame -> xml.xfrm ?: xml.addNewXfrm()
            else -> return
        }
        val off = xfrm.off ?: xfrm.addNewOff()
        off.x = (bounds.x * EmuPerPoint).toLong()
        off.y = (bounds.y * EmuPerPoint).toLong()
        val ext = xfrm.ext ?: xfrm.addNewExt()
        ext.cx = (bounds.width * EmuPerPoint).toLong()
        ext.cy = (bounds.height * EmuPerPoint).toLong()
    }

    /** A shape's effective vertical anchor as its OOXML token, inherited values included. */
    private fun anchorTokenOf(shape: XSLFTextShape): String? = when (shape.verticalAlignment) {
        VerticalAlignment.TOP -> "t"
        VerticalAlignment.MIDDLE -> "ctr"
        VerticalAlignment.BOTTOM -> "b"
        VerticalAlignment.JUSTIFIED, VerticalAlignment.DISTRIBUTED -> "ctr"
        null -> null
    }

    /** A paragraph's effective alignment as its OOXML token, inherited values included. */
    private fun alignTokenOf(paragraph: XSLFTextParagraph): String? = when (paragraph.textAlign) {
        TextParagraph.TextAlign.LEFT -> "l"
        TextParagraph.TextAlign.CENTER -> "ctr"
        TextParagraph.TextAlign.RIGHT -> "r"
        TextParagraph.TextAlign.JUSTIFY, TextParagraph.TextAlign.JUSTIFY_LOW,
        TextParagraph.TextAlign.DIST, TextParagraph.TextAlign.THAI_DIST -> "just"
        null -> null
    }

    /** Runs cut into paragraphs at every newline; the newlines themselves are dropped. */
    private fun splitParagraphs(runs: List<ShapeRun>): List<List<ShapeRun>> {
        val paragraphs = ArrayList<MutableList<ShapeRun>>()
        var current = ArrayList<ShapeRun>().also { paragraphs.add(it) }
        for (run in runs) {
            val pieces = run.text.split('\n')
            pieces.forEachIndexed { i, piece ->
                if (i > 0) current = ArrayList<ShapeRun>().also { paragraphs.add(it) }
                if (piece.isNotEmpty()) current.add(run.copy(text = piece))
            }
        }
        return paragraphs
    }

    private fun rgbBytes(hex: String): ByteArray? {
        val parsed = hex.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16) ?: return null
        return byteArrayOf((parsed shr 16 and 0xFF).toByte(), (parsed shr 8 and 0xFF).toByte(), (parsed and 0xFF).toByte())
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
