package dev.kern.editors.pptx

import dev.kern.editors.pptx.PptDocument.RunStyle
import dev.kern.editors.pptx.PptDocument.ShapeBounds
import dev.kern.editors.pptx.PptDocument.ShapeRun
import dev.kern.editors.pptx.PptFixtures.ACCENT1
import dev.kern.editors.pptx.PptFixtures.ACCENT2
import dev.kern.editors.pptx.PptFixtures.DK1
import dev.kern.editors.pptx.PptFixtures.LT1
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.sl.usermodel.ShapeType
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign
import org.apache.poi.xslf.usermodel.SlideLayout
import org.apache.poi.xslf.usermodel.XSLFAutoShape
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextBox
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun
import org.openxmlformats.schemas.drawingml.x2006.main.STFontCollectionIndex
import org.openxmlformats.schemas.drawingml.x2006.main.STSchemeColorVal
import org.openxmlformats.schemas.drawingml.x2006.main.STTextAlignType
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape
import org.openxmlformats.schemas.presentationml.x2006.main.STPlaceholderType

class PptDocumentTest {

    // Fixtures

    /**
     * A text box with two paragraphs carrying markup the editor does not model: a
     * bullet, a left margin, and a language tag and theme colour on the run.
     *
     * Paragraph 1: centred, bulleted, "One" at 24pt, accent1, fr-FR.
     * Paragraph 2: right aligned, "Two".
     */
    private fun richBox(slide: XSLFSlide): XSLFTextBox {
        val box = slide.createTextBox()
        PptFixtures.place(box, 100, 50, 300, 80)
        box.clearText()
        box.addNewTextParagraph().apply {
            textAlign = TextAlign.CENTER
            isBullet = true
            bulletCharacter = "•"
            leftMargin = 18.0
            addNewTextRun().apply {
                setText("One")
                fontSize = 24.0
                val rPr = (xmlObject as CTRegularTextRun).rPr
                rPr.lang = "fr-FR"
                rPr.addNewSolidFill().addNewSchemeClr().`val` = STSchemeColorVal.ACCENT_1
            }
        }
        box.addNewTextParagraph().apply {
            textAlign = TextAlign.RIGHT
            addNewTextRun().setText("Two")
        }
        return box
    }

    private fun richDeck(): ByteArray {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        richBox(slide)
        slide.createTextBox().setText("Neighbour")
        return PptFixtures.bytesOf(ppt)
    }

    /** The first slide's editable shape [index], from serialised bytes, via POI. */
    private fun ctShapeAt(bytes: ByteArray, slide: Int, index: Int): CTShape =
        PptFixtures.reopen(bytes).use { ppt ->
            val shape = ppt.slides[slide].shapes.filterIsInstance<XSLFTextShape>()[index]
            shape.xmlObject.copy() as CTShape
        }

    private fun model(bytes: ByteArray, slide: Int = 0): PptDocument.SlideModel =
        PptDocument.read(bytes).slideModels[slide]

    private fun autoShape(slide: XSLFSlide, type: ShapeType = ShapeType.RECT): XSLFAutoShape =
        slide.createAutoShape().apply {
            shapeType = type
            PptFixtures.place(this, 10, 20, 200, 100)
        }

    private fun CTShape.solidFill(hex: String) {
        val spPr = spPr
        if (spPr.isSetNoFill) spPr.unsetNoFill()
        spPr.addNewSolidFill().addNewSrgbClr().`val` = PptFixtures.hexBytes(hex)
    }

    /** A p:style: the fill reference in accent1, the line reference in accent2, the font reference in lt1. */
    private fun CTShape.styleRefs(fillIdx: Long, lineIdx: Long) {
        addNewStyle().apply {
            addNewLnRef().apply { idx = lineIdx; addNewSchemeClr().`val` = STSchemeColorVal.ACCENT_2 }
            addNewFillRef().apply { idx = fillIdx; addNewSchemeClr().`val` = STSchemeColorVal.ACCENT_1 }
            addNewEffectRef().idx = 0
            addNewFontRef().apply { idx = STFontCollectionIndex.MINOR; addNewSchemeClr().`val` = STSchemeColorVal.LT_1 }
        }
    }

    // Read

    @Test
    fun read_returnsTextOfEachSlide() {
        val parsed = PptDocument.read(PptFixtures.textDeck("Title slide", "Second slide"))
        assertEquals(listOf(listOf("Title slide"), listOf("Second slide")), parsed.slides)
        assertEquals(listOf(0, 1), parsed.slideModels.map { it.slideIndex })
    }

    @Test
    fun read_slideSizeComesFromTheDeck() {
        val ppt = PptFixtures.themed()
        ppt.ctPresentation.sldSz.apply { cx = 720 * 12700; cy = 540 * 12700 }
        ppt.createSlide().createTextBox().setText("4:3")
        val slide = model(PptFixtures.bytesOf(ppt))
        assertEquals(720f, slide.width, 0.01f)
        assertEquals(540f, slide.height, 0.01f)
    }

    @Test
    fun read_paragraphsBecomeNewlinesWithAlignmentPerParagraph() {
        val shape = model(richDeck()).shapes[0]
        assertEquals("One\nTwo", shape.runs.joinToString("") { it.text })
        assertEquals(listOf("ctr", "r"), shape.alignments)
    }

    @Test
    fun read_runFormattingAndThemeColour() {
        val run = model(richDeck()).shapes[0].runs.first()
        assertEquals(24f, run.style.sizePt)
        // accent1, resolved through the theme to sRGB.
        assertEquals("#$ACCENT1", run.style.colorHex)
    }

    @Test
    fun read_explicitBounds() {
        assertEquals(ShapeBounds(100f, 50f, 300f, 80f), model(richDeck()).shapes[0].bounds)
    }

    @Test
    fun read_universalMeasureCoordinates() {
        val ppt = PptFixtures.themed()
        val box = ppt.createSlide().createTextBox()
        box.setText("Measured")
        PptFixtures.place(box, 0, 0, 144, 72)
        val off = (box.xmlObject as CTShape).spPr.xfrm.off
        off.x = "1in"
        off.y = "2.54cm"
        // 1in is 72pt and 2.54cm is 72pt; the extent stays in EMU.
        assertEquals(ShapeBounds(72f, 72f, 144f, 72f), model(PptFixtures.bytesOf(ppt)).shapes[0].bounds)
    }

    @Test
    fun read_placeholderWithoutGeometry_inheritsLayouts() {
        val ppt = PptFixtures.themed()
        val layout = ppt.slideMasters[0].getLayout(SlideLayout.TITLE)
        val slide = ppt.createSlide(layout)
        val title = slide.placeholders.first { it.placeholder == Placeholder.CENTERED_TITLE || it.placeholder == Placeholder.TITLE }
        title.setText("Inherited")
        val spPr = (title.xmlObject as CTShape).spPr
        if (spPr.isSetXfrm) spPr.unsetXfrm()

        val source = layout.placeholders.first { it.placeholder == title.placeholder }
        val xfrm = requireNotNull((source.xmlObject as CTShape).spPr.xfrm) { "layout placeholder declares no geometry" }
        val expected = ShapeBounds(
            (xfrm.off.x as Number).toFloat() / 12700f,
            (xfrm.off.y as Number).toFloat() / 12700f,
            xfrm.ext.cx / 12700f,
            xfrm.ext.cy / 12700f,
        )

        val shape = model(PptFixtures.bytesOf(ppt)).shapes.first { it.text == "Inherited" }
        assertEquals(expected, shape.bounds)
    }

    @Test
    fun read_defaultTextColour_followsFontRefThenTheme() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        slide.createTextBox().setText("Plain")
        autoShape(slide).apply {
            setText("On a shape")
            (xmlObject as CTShape).styleRefs(fillIdx = 1, lineIdx = 1)
        }
        val shapes = model(PptFixtures.bytesOf(ppt)).shapes
        assertEquals(DK1, shapes.first { it.text == "Plain" }.defaultColorHex)
        assertEquals(LT1, shapes.first { it.text == "On a shape" }.defaultColorHex)
        assertEquals(DK1, model(PptFixtures.textDeck("x")).textColorHex)
    }

    // What is editable, what is decoration

    @Test
    fun read_emptyTextBoxStaysEditable() {
        val ppt = PptFixtures.themed()
        ppt.createSlide().createTextBox()
        assertEquals(listOf(listOf("")), PptDocument.read(PptFixtures.bytesOf(ppt)).slides)
    }

    @Test
    fun read_textlessFilledShape_isDecorationNotText() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        autoShape(slide, ShapeType.ROUND_RECT).apply {
            val ct = xmlObject as CTShape
            ct.solidFill("AA5500")
            ct.spPr.prstGeom.let { it.avLst ?: it.addNewAvLst() }.addNewGd().apply { name = "adj"; fmla = "val 25000" }
        }
        slide.createTextBox().setText("Caption")

        val slideModel = model(PptFixtures.bytesOf(ppt))
        assertEquals(listOf("Caption"), slideModel.shapes.map { it.text })
        val geometry = slideModel.decorations.single() as PptDocument.GeometryDecoration
        assertEquals("roundRect", geometry.preset)
        assertEquals(PptDocument.SolidFill("AA5500"), geometry.fill)
        assertEquals(mapOf("adj" to 25000), geometry.adjustments)
        assertEquals(ShapeBounds(10f, 20f, 200f, 100f), geometry.bounds)
    }

    @Test
    fun read_shapeStyleReferences_resolveThroughTheme() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        autoShape(slide, ShapeType.ELLIPSE).apply {
            val ct = xmlObject as CTShape
            if (ct.spPr.isSetNoFill) ct.spPr.unsetNoFill()
            ct.styleRefs(fillIdx = 2, lineIdx = 1)
            // A line that sets only a width keeps the referenced colour.
            ct.spPr.addNewLn().w = 38100
        }
        val geometry = model(PptFixtures.bytesOf(ppt)).decorations.single() as PptDocument.GeometryDecoration
        assertEquals("ellipse", geometry.preset)
        assertEquals(
            PptDocument.GradientFill(
                // accent1 4472C4 at half shade is 223962.
                listOf(PptDocument.GradientStop(0f, ACCENT1), PptDocument.GradientStop(1f, "223962")),
                90f,
            ),
            geometry.fill,
        )
        assertEquals(PptDocument.Outline(ACCENT2, 3f), geometry.outline)
    }

    @Test
    fun read_ownFillAndLine_winOverStyleReferences() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        autoShape(slide).apply {
            val ct = xmlObject as CTShape
            ct.styleRefs(fillIdx = 2, lineIdx = 1)
            ct.solidFill("010203")
            ct.spPr.addNewLn().addNewNoFill()
        }
        val geometry = model(PptFixtures.bytesOf(ppt)).decorations.single() as PptDocument.GeometryDecoration
        assertEquals(PptDocument.SolidFill("010203"), geometry.fill)
        assertNull(geometry.outline)
    }

    @Test
    fun read_pictureFilledShape_isACoverPictureNotText() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        val image = PptFixtures.png()
        val picture = ppt.addPicture(image, org.apache.poi.sl.usermodel.PictureData.PictureType.PNG)
        val rel = slide.addRelation(null, org.apache.poi.xslf.usermodel.XSLFRelation.IMAGES, picture)
        autoShape(slide).apply {
            val spPr = (xmlObject as CTShape).spPr
            if (spPr.isSetNoFill) spPr.unsetNoFill()
            spPr.addNewBlipFill().apply { addNewBlip().embed = rel.relationship.id; addNewStretch().addNewFillRect() }
        }
        val slideModel = model(PptFixtures.bytesOf(ppt))
        assertTrue(slideModel.shapes.isEmpty())
        val decoration = slideModel.decorations.single() as PptDocument.PictureDecoration
        assertTrue(decoration.fillsShape)
        assertTrue(decoration.bytes.contentEquals(image))
    }

    @Test
    fun read_placedPicture_tableAndGroup() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        val image = PptFixtures.png()
        PptFixtures.addPicture(ppt, slide, image)
        slide.createTable(2, 2).apply {
            getCell(0, 0).setText("a"); getCell(0, 1).setText("b")
            getCell(1, 0).setText("c"); getCell(1, 1).setText("d")
        }
        slide.createGroup()

        val decorations = model(PptFixtures.bytesOf(ppt)).decorations
        assertEquals(3, decorations.size)
        val picture = decorations[0] as PptDocument.PictureDecoration
        assertFalse(picture.fillsShape)
        assertTrue(picture.bytes.contentEquals(image))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), (decorations[1] as PptDocument.TableDecoration).rows)
        assertEquals("Grouped shapes", (decorations[2] as PptDocument.UnsupportedDecoration).label)
    }

    // applyRunsToShape, through applyEditsAndSerialize

    private val editedRuns = listOf(
        ShapeRun("One edited", RunStyle(bold = true, colorHex = "#FF0000")),
        ShapeRun("\nTwo\nThree"),
    )

    @Test
    fun richEdit_newlinesSplitIntoParagraphs() {
        val out = PptDocument.applyEditsAndSerialize(richDeck(), richEdits = mapOf((0 to 0) to editedRuns))
        val shape = ctShapeAt(out, 0, 0)
        assertEquals(3, shape.txBody.pList.size)
        assertEquals(listOf("One edited", "Two", "Three"), shape.txBody.pList.map { p -> p.rList.joinToString("") { it.t } })
    }

    @Test
    fun richEdit_paragraphKeepsPropertiesOfParagraphAtItsIndex() {
        val out = PptDocument.applyEditsAndSerialize(richDeck(), richEdits = mapOf((0 to 0) to editedRuns))
        val (p1, p2, p3) = ctShapeAt(out, 0, 0).txBody.pList
        assertEquals(STTextAlignType.CTR, p1.pPr.algn)
        assertNotNull("bullet kept", p1.pPr.buChar)
        assertEquals("•", p1.pPr.buChar.char)
        assertEquals(228600, p1.pPr.marL)
        assertEquals(STTextAlignType.R, p2.pPr.algn)
        // A paragraph the edit added takes the last paragraph's properties.
        assertEquals(STTextAlignType.R, p3.pPr.algn)
    }

    @Test
    fun richEdit_alignEditsOverrideOnlyTheirParagraphs() {
        val out = PptDocument.applyEditsAndSerialize(
            richDeck(),
            richEdits = mapOf((0 to 0) to editedRuns),
            alignEdits = mapOf((0 to 0) to listOf("l", null, "just")),
        )
        val (p1, p2, p3) = ctShapeAt(out, 0, 0).txBody.pList
        assertEquals(STTextAlignType.L, p1.pPr.algn)
        assertNotNull("other paragraph properties survive an alignment change", p1.pPr.buChar)
        assertEquals(STTextAlignType.R, p2.pPr.algn)
        assertEquals(STTextAlignType.JUST, p3.pPr.algn)
        assertEquals(listOf("l", "r", "just"), model(out).shapes[0].alignments)
    }

    @Test
    fun richEdit_runsKeepUnmodelledPropertiesAndWriteModelledOnes() {
        val out = PptDocument.applyEditsAndSerialize(richDeck(), richEdits = mapOf((0 to 0) to editedRuns))
        val pList = ctShapeAt(out, 0, 0).txBody.pList
        val first = pList[0].rList.single().rPr
        assertEquals("language tag copied from the shape's first run", "fr-FR", first.lang)
        assertTrue(first.b)
        // The colour is pinned as sRGB, and the theme colour it replaced is gone.
        assertTrue(first.solidFill.isSetSrgbClr)
        assertFalse(first.solidFill.isSetSchemeClr)
        assertEquals("FF0000", first.solidFill.srgbClr.`val`.joinToString("") { "%02X".format(it) })
        // No size in the model means inherited: the template's 24pt must not leak.
        assertFalse(first.isSetSz)

        val second = pList[1].rList.single().rPr
        assertEquals("fr-FR", second.lang)
        assertFalse(second.b)
        assertFalse("a run with no colour stays inherited", second.isSetSolidFill)
    }

    @Test
    fun richEdit_explicitSizeAndFontAreWritten() {
        val runs = listOf(ShapeRun("Sized", RunStyle(sizePt = 32f, fontFamily = "Georgia", italic = true, underline = true)))
        val out = PptDocument.applyEditsAndSerialize(richDeck(), richEdits = mapOf((0 to 0) to runs))
        val style = model(out).shapes[0].runs.single().style
        assertEquals(32f, style.sizePt)
        assertEquals("Georgia", style.fontFamily)
        assertTrue(style.italic)
        assertTrue(style.underline)
        assertFalse(style.bold)
    }

    @Test
    fun richEdit_readsBackAsTheRunsThatWereWritten() {
        val out = PptDocument.applyEditsAndSerialize(richDeck(), richEdits = mapOf((0 to 0) to editedRuns))
        val shape = model(out).shapes[0]
        assertEquals("One edited\nTwo\nThree", shape.runs.joinToString("") { it.text })
        val first = shape.runs.first()
        assertTrue(first.style.bold)
        assertEquals("#FF0000", first.style.colorHex)
        assertTrue(shape.runs.last().text.endsWith("Three"))
        assertFalse(shape.runs.last().style.bold)
    }

    @Test
    fun richEdit_leavesOtherShapesMarkupUntouched() {
        val original = richDeck()
        val before = ctShapeAt(original, 0, 0).xmlText()
        val out = PptDocument.applyEditsAndSerialize(original, richEdits = mapOf((0 to 1) to listOf(ShapeRun("Changed"))))
        assertEquals(before, ctShapeAt(out, 0, 0).xmlText())
        assertEquals(listOf("One\nTwo", "Changed"), PptFixtures.texts(out)[0])
    }

    @Test
    fun noEdits_leavesEveryShapeUntouched() {
        val original = richDeck()
        val out = PptDocument.applyEditsAndSerialize(original)
        assertEquals(ctShapeAt(original, 0, 0).xmlText(), ctShapeAt(out, 0, 0).xmlText())
        assertEquals(ctShapeAt(original, 0, 1).xmlText(), ctShapeAt(out, 0, 1).xmlText())
    }

    @Test
    fun plainEdit_replacesOnlyTheEditedShape() {
        val edited = PptDocument.applyEditsAndSerialize(
            PptFixtures.textDeck("Title slide", "Second slide"),
            edits = mapOf((0 to 0) to "Edited title"),
        )
        assertEquals(listOf(listOf("Edited title"), listOf("Second slide")), PptFixtures.texts(edited))
    }

    @Test
    fun plainEdit_isIgnoredWhenTheSameShapeHasARichEdit() {
        val edited = PptDocument.applyEditsAndSerialize(
            PptFixtures.textDeck("A"),
            edits = mapOf((0 to 0) to "plain"),
            richEdits = mapOf((0 to 0) to listOf(ShapeRun("rich"))),
        )
        assertEquals(listOf(listOf("rich")), PptFixtures.texts(edited))
    }

    @Test
    fun edits_toMissingSlidesOrShapes_areSkipped() {
        val original = PptFixtures.textDeck("A")
        val edited = PptDocument.applyEditsAndSerialize(
            original,
            edits = mapOf((5 to 0) to "x", (0 to 9) to "y"),
            richEdits = mapOf((-1 to 0) to listOf(ShapeRun("z"))),
            boundsEdits = mapOf((0 to 3) to ShapeBounds(1f, 1f, 1f, 1f)),
            anchorEdits = mapOf((2 to 0) to "b"),
            decorationBoundsEdits = mapOf((0 to 0) to ShapeBounds(1f, 1f, 1f, 1f)),
        )
        assertEquals(listOf(listOf("A")), PptFixtures.texts(edited))
    }

    // Bounds and anchor write-back

    @Test
    fun boundsEdit_isWrittenAndReadBack() {
        val moved = ShapeBounds(40f, 30f, 250f, 120f)
        val out = PptDocument.applyEditsAndSerialize(richDeck(), boundsEdits = mapOf((0 to 0) to moved))
        assertEquals(moved, model(out).shapes[0].bounds)
        // Moving a shape does not touch its text.
        assertEquals("One\nTwo", model(out).shapes[0].runs.joinToString("") { it.text })
    }

    @Test
    fun boundsEdit_onInheritingPlaceholder_writesExplicitTransform() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide(ppt.slideMasters[0].getLayout(SlideLayout.TITLE))
        val title = slide.placeholders.first { it.placeholder == Placeholder.CENTERED_TITLE || it.placeholder == Placeholder.TITLE }
        title.setText("Placeholder")
        val spPr = (title.xmlObject as CTShape).spPr
        if (spPr.isSetXfrm) spPr.unsetXfrm()
        val bytes = PptFixtures.bytesOf(ppt)
        val index = model(bytes).shapes.indexOfFirst { it.text == "Placeholder" }

        val moved = ShapeBounds(12f, 24f, 480f, 96f)
        val out = PptDocument.applyEditsAndSerialize(bytes, boundsEdits = mapOf((0 to index) to moved))
        assertEquals(moved, model(out).shapes[index].bounds)
        assertTrue(ctShapeAt(out, 0, index).spPr.isSetXfrm)
    }

    @Test
    fun anchorEdit_isWrittenAndReadBack() {
        for (token in listOf("t", "ctr", "b")) {
            val out = PptDocument.applyEditsAndSerialize(richDeck(), anchorEdits = mapOf((0 to 0) to token))
            assertEquals(token, model(out).shapes[0].anchor)
        }
    }

    @Test
    fun anchorEdit_keepsTheOtherBodyProperties() {
        val ppt = PptFixtures.themed()
        val box = ppt.createSlide().createTextBox()
        box.setText("Inset")
        (box.xmlObject as CTShape).txBody.bodyPr.lIns = 91440
        val out = PptDocument.applyEditsAndSerialize(PptFixtures.bytesOf(ppt), anchorEdits = mapOf((0 to 0) to "b"))
        val bodyPr = ctShapeAt(out, 0, 0).txBody.bodyPr
        assertEquals(91440, bodyPr.lIns)
        assertEquals("b", bodyPr.anchor.toString())
    }

    @Test
    fun decorationBoundsEdit_movesThePicture() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        slide.createTextBox().setText("Caption")
        PptFixtures.addPicture(ppt, slide)
        val moved = ShapeBounds(40f, 30f, 120f, 60f)
        val out = PptDocument.applyEditsAndSerialize(PptFixtures.bytesOf(ppt), decorationBoundsEdits = mapOf((0 to 0) to moved))
        val slideModel = model(out)
        assertEquals(moved, slideModel.decorations.single().bounds)
        assertEquals(listOf("Caption"), slideModel.shapes.map { it.text })
    }

    @Test
    fun decorationBoundsEdit_movesTheRightDecoration() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        autoShape(slide).apply { (xmlObject as CTShape).solidFill("AA0000") }
        PptFixtures.addPicture(ppt, slide)
        val bytes = PptFixtures.bytesOf(ppt)
        val before = model(bytes).decorations
        val moved = ShapeBounds(300f, 200f, 50f, 50f)
        val after = model(PptDocument.applyEditsAndSerialize(bytes, decorationBoundsEdits = mapOf((0 to 1) to moved))).decorations
        assertEquals(before[0].bounds, after[0].bounds)
        assertEquals(moved, after[1].bounds)
    }

    // Slide operations

    private fun abc() = PptFixtures.textDeck("A", "B", "C")

    private fun ops(bytes: ByteArray, vararg ops: PptDocument.SlideOp) =
        PptFixtures.texts(PptDocument.applyEditsAndSerialize(bytes, slideOps = ops.toList()))

    @Test
    fun insertSlide_landsAtItsIndex() {
        assertEquals(
            listOf(listOf("A"), listOf("Title", "Content"), listOf("B"), listOf("C")),
            ops(abc(), PptDocument.InsertSlide(1, PptDocument.PresetLayout.TITLE_AND_CONTENT)),
        )
    }

    @Test
    fun insertSlide_atStartAndAtEnd() {
        assertEquals(listOf("Section Header"), ops(abc(), PptDocument.InsertSlide(0, PptDocument.PresetLayout.SECTION_HEADER))[0])
        val atEnd = ops(abc(), PptDocument.InsertSlide(3, PptDocument.PresetLayout.SECTION_HEADER))
        assertEquals(listOf(listOf("A"), listOf("B"), listOf("C"), listOf("Section Header")), atEnd)
    }

    @Test
    fun insertSlide_successiveInsertsKeepTheirOrder() {
        // The editor inserts after the current slide and then moves to the new one.
        val result = ops(
            abc(),
            PptDocument.InsertSlide(1, PptDocument.PresetLayout.SECTION_HEADER),
            PptDocument.InsertSlide(2, PptDocument.PresetLayout.TITLE_AND_CONTENT),
        )
        assertEquals(
            listOf(listOf("A"), listOf("Section Header"), listOf("Title", "Content"), listOf("B"), listOf("C")),
            result,
        )
    }

    @Test
    fun insertSlide_thenEditIt_editLandsOnTheNewSlide() {
        val out = PptDocument.applyEditsAndSerialize(
            abc(),
            slideOps = listOf(PptDocument.InsertSlide(1, PptDocument.PresetLayout.TITLE_AND_CONTENT)),
            richEdits = mapOf((1 to 0) to listOf(ShapeRun("New title"))),
        )
        assertEquals(listOf(listOf("A"), listOf("New title", "Content"), listOf("B"), listOf("C")), PptFixtures.texts(out))
    }

    @Test
    fun deleteSlide_removesIt() {
        assertEquals(listOf(listOf("A"), listOf("C")), ops(abc(), PptDocument.DeleteSlide(1)))
    }

    @Test
    fun deleteSlide_neverRemovesTheLastSlideOrAMissingOne() {
        assertEquals(listOf(listOf("A")), ops(PptFixtures.textDeck("A"), PptDocument.DeleteSlide(0)))
        assertEquals(listOf(listOf("A"), listOf("B"), listOf("C")), ops(abc(), PptDocument.DeleteSlide(7)))
    }

    @Test
    fun moveSlide_reorders() {
        assertEquals(listOf(listOf("B"), listOf("C"), listOf("A")), ops(abc(), PptDocument.MoveSlide(0, 2)))
        assertEquals(listOf(listOf("C"), listOf("A"), listOf("B")), ops(abc(), PptDocument.MoveSlide(2, 0)))
    }

    @Test
    fun slideOps_applyInOrder() {
        val result = ops(
            abc(),
            PptDocument.MoveSlide(0, 2),
            PptDocument.DeleteSlide(0),
            PptDocument.InsertSlide(0, PptDocument.PresetLayout.SECTION_HEADER),
        )
        assertEquals(listOf(listOf("Section Header"), listOf("C"), listOf("A")), result)
    }

    @Test
    fun addTextBox_withRuns_keepsFormatting() {
        val out = PptDocument.applyEditsAndSerialize(
            PptFixtures.textDeck("A"),
            slideOps = listOf(PptDocument.AddTextBox(0, "ignored", listOf(ShapeRun("Bold box", RunStyle(bold = true))))),
        )
        val shapes = model(out).shapes
        assertEquals(listOf("A", "Bold box"), shapes.map { it.text })
        assertTrue(shapes[1].runs.single().style.bold)
    }

    @Test
    fun addTextBox_plain() {
        assertEquals(listOf(listOf("A", "Text box")), ops(PptFixtures.textDeck("A"), PptDocument.AddTextBox(0, "Text box")))
    }

    /** Slide 0: a text box, a picture and its own solid background; slide 1: "B". */
    private fun pictureDeck(image: ByteArray): ByteArray {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        slide.createTextBox().setText("A")
        PptFixtures.addPicture(ppt, slide, image)
        slide.xmlObject.cSld.addNewBg().addNewBgPr().apply {
            addNewSolidFill().addNewSrgbClr().`val` = PptFixtures.hexBytes("336699")
            addNewEffectLst()
        }
        ppt.createSlide().createTextBox().setText("B")
        return PptFixtures.bytesOf(ppt)
    }

    @Test
    fun duplicateSlide_keepsPicturesAndBackgroundAndSitsNextToSource() {
        val image = PptFixtures.png()
        val out = PptDocument.applyEditsAndSerialize(pictureDeck(image), slideOps = listOf(PptDocument.DuplicateSlide(0, 1)))
        val parsed = PptDocument.read(out)
        assertEquals(listOf(listOf("A"), listOf("A"), listOf("B")), parsed.slides)
        val copy = parsed.slideModels[1]
        assertEquals(PptDocument.SolidFill("336699"), copy.background)
        val picture = copy.decorations.single() as PptDocument.PictureDecoration
        assertTrue(picture.bytes.contentEquals(image))
    }

    @Test
    fun duplicateSlide_thenEditTheCopy_thenSave() {
        val out = PptDocument.applyEditsAndSerialize(
            pictureDeck(PptFixtures.png()),
            slideOps = listOf(PptDocument.DuplicateSlide(0, 1)),
            richEdits = mapOf((1 to 0) to listOf(ShapeRun("Copy edited"))),
        )
        assertEquals(listOf(listOf("A"), listOf("Copy edited"), listOf("B")), PptFixtures.texts(out))
        // And the copy survives a second save of the result, with nothing else changing.
        val again = PptDocument.applyEditsAndSerialize(out)
        assertEquals(listOf(listOf("A"), listOf("Copy edited"), listOf("B")), PptFixtures.texts(again))
        assertEquals(1, model(again, 1).decorations.size)
    }

    @Test
    fun duplicateSlide_thenEditTheOriginal_copyKeepsOldText() {
        val out = PptDocument.applyEditsAndSerialize(
            pictureDeck(PptFixtures.png()),
            slideOps = listOf(PptDocument.DuplicateSlide(0, 1)),
            richEdits = mapOf((0 to 0) to listOf(ShapeRun("Original edited"))),
        )
        assertEquals(listOf(listOf("Original edited"), listOf("A"), listOf("B")), PptFixtures.texts(out))
    }

    @Test
    fun duplicateSlide_thenMoveTheCopyOfItsPicture() {
        val moved = ShapeBounds(5f, 5f, 40f, 20f)
        val out = PptDocument.applyEditsAndSerialize(
            pictureDeck(PptFixtures.png()),
            slideOps = listOf(PptDocument.DuplicateSlide(0, 1)),
            decorationBoundsEdits = mapOf((1 to 0) to moved),
        )
        assertEquals(moved, model(out, 1).decorations.single().bounds)
        assertTrue("the source's picture stays put", model(out, 0).decorations.single().bounds != moved)
    }

    @Test
    fun duplicateSlide_ofLastSlide_appends() {
        assertEquals(listOf(listOf("A"), listOf("B"), listOf("C"), listOf("C")), ops(abc(), PptDocument.DuplicateSlide(2, 3)))
    }

    @Test
    fun read_ofSavedDeck_isStableAcrossRoundTrips() {
        val original = richDeck()
        val once = PptDocument.applyEditsAndSerialize(original)
        val twice = PptDocument.applyEditsAndSerialize(once)
        assertEquals(PptDocument.read(original).slideModels, PptDocument.read(twice).slideModels)
    }

    // The editor's model of an inserted slide must match what the writer creates,
    // because edits are routed to the written shapes by index.

    @Test
    fun insertSlide_title_writesTheTitleAndSubtitleTheEditorShows() {
        val out = PptDocument.applyEditsAndSerialize(
            PptFixtures.textDeck("A"),
            slideOps = listOf(PptDocument.InsertSlide(1, PptDocument.PresetLayout.TITLE)),
            richEdits = mapOf(
                (1 to 0) to listOf(ShapeRun("My title")),
                (1 to 1) to listOf(ShapeRun("My subtitle")),
            ),
        )
        assertEquals(listOf(listOf("A"), listOf("My title", "My subtitle")), PptFixtures.texts(out))
    }

    @Test
    fun insertSlide_blank_writesNoShapes() {
        val out = PptDocument.applyEditsAndSerialize(
            PptFixtures.textDeck("A"),
            slideOps = listOf(PptDocument.InsertSlide(1, PptDocument.PresetLayout.BLANK)),
        )
        assertEquals("text shapes on the blank slide", 0, model(out, 1).shapes.size)
    }

    @Test
    fun insertSlide_blank_thenAddTextBox_isTheOnlyShape() {
        val out = PptDocument.applyEditsAndSerialize(
            PptFixtures.textDeck("A"),
            slideOps = listOf(
                PptDocument.InsertSlide(1, PptDocument.PresetLayout.BLANK),
                PptDocument.AddTextBox(1, "Text box"),
            ),
            richEdits = mapOf((1 to 0) to listOf(ShapeRun("Note"))),
        )
        assertEquals(listOf(listOf("A"), listOf("Note")), PptFixtures.texts(out))
    }

    @Test
    fun read_picturePlaceholderWithoutGeometry_inheritsLayouts() {
        val ppt = PptFixtures.themed()
        val layout = ppt.slideMasters[0].getLayout(SlideLayout.PIC_TX)
        val slot = layout.placeholders.first { it.placeholder == Placeholder.PICTURE }
        val slotXml = slot.xmlObject as CTShape
        val xfrm = requireNotNull(slotXml.spPr.xfrm) { "layout picture placeholder declares no geometry" }
        val expected = ShapeBounds(
            (xfrm.off.x as Number).toFloat() / 12700f,
            (xfrm.off.y as Number).toFloat() / 12700f,
            xfrm.ext.cx / 12700f,
            xfrm.ext.cy / 12700f,
        )

        val slide = ppt.createSlide(layout)
        val picture = PptFixtures.addPicture(ppt, slide).xmlObject as CTPicture
        picture.nvPicPr.nvPr.addNewPh().apply {
            type = STPlaceholderType.PIC
            idx = slotXml.nvSpPr.nvPr.ph.idx
        }
        if (picture.spPr.isSetXfrm) picture.spPr.unsetXfrm()

        val decoration = model(PptFixtures.bytesOf(ppt)).decorations.filterIsInstance<PptDocument.PictureDecoration>().single()
        assertEquals(expected, decoration.bounds)
    }
}
