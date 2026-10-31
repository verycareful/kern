package dev.kern.editors.pptx

import dev.kern.editors.pptx.PptFixtures.ACCENT1
import dev.kern.editors.pptx.PptFixtures.ACCENT2
import dev.kern.editors.pptx.PptFixtures.ACCENT3
import dev.kern.editors.pptx.PptFixtures.BG_STYLE_2
import dev.kern.editors.pptx.PptFixtures.DK1
import dev.kern.editors.pptx.PptFixtures.FILL_STYLE_3
import dev.kern.editors.pptx.PptFixtures.LT1
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSheet
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openxmlformats.schemas.drawingml.x2006.main.CTColorMapping
import org.openxmlformats.schemas.drawingml.x2006.main.CTSRgbColor
import org.openxmlformats.schemas.drawingml.x2006.main.STColorSchemeIndex
import org.openxmlformats.schemas.drawingml.x2006.main.STSchemeColorVal
import org.openxmlformats.schemas.presentationml.x2006.main.CTBackground
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlide
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlideLayout
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlideMaster

class PptThemeTest {

    private fun themeOf(slide: XSLFSlide): PptTheme = requireNotNull(PptTheme.of(slide)) { "deck has no theme" }

    private fun srgb(hex: String, configure: CTSRgbColor.() -> Unit = {}): CTSRgbColor =
        CTSRgbColor.Factory.newInstance().also { it.`val` = PptFixtures.hexBytes(hex); it.configure() }

    /** Swaps the dark and light text slots, the override a dark-background slide carries. */
    private fun invertedMapping(mapping: CTColorMapping) {
        mapping.bg1 = STColorSchemeIndex.DK_1
        mapping.tx1 = STColorSchemeIndex.LT_1
        mapping.bg2 = STColorSchemeIndex.DK_2
        mapping.tx2 = STColorSchemeIndex.LT_2
    }

    private fun overrideOn(slide: XSLFSlide): CTColorMapping {
        val xml = slide.xmlObject
        val ovr = if (xml.isSetClrMapOvr) xml.clrMapOvr else xml.addNewClrMapOvr()
        if (ovr.isSetMasterClrMapping) ovr.unsetMasterClrMapping()
        return ovr.addNewOverrideClrMapping().also { invertedMapping(it) }
    }

    private fun backgroundOf(sheet: XSLFSheet): CTBackground? = when (val xml = sheet.xmlObject) {
        is CTSlide -> xml.cSld.takeIf { it.isSetBg }?.bg
        is CTSlideLayout -> xml.cSld.takeIf { it.isSetBg }?.bg
        is CTSlideMaster -> xml.cSld.takeIf { it.isSetBg }?.bg
        else -> null
    }

    private fun clearBackground(sheet: XSLFSheet) {
        when (val xml = sheet.xmlObject) {
            is CTSlide -> if (xml.cSld.isSetBg) xml.cSld.unsetBg()
            is CTSlideLayout -> if (xml.cSld.isSetBg) xml.cSld.unsetBg()
            is CTSlideMaster -> if (xml.cSld.isSetBg) xml.cSld.unsetBg()
        }
    }

    private fun newBackground(sheet: XSLFSheet): CTBackground {
        clearBackground(sheet)
        return when (val xml = sheet.xmlObject) {
            is CTSlide -> xml.cSld.addNewBg()
            is CTSlideLayout -> xml.cSld.addNewBg()
            is CTSlideMaster -> xml.cSld.addNewBg()
            else -> error("no background on ${sheet.javaClass}")
        }
    }

    private fun solidBackground(sheet: XSLFSheet, hex: String) {
        newBackground(sheet).addNewBgPr().apply {
            addNewSolidFill().addNewSrgbClr().`val` = PptFixtures.hexBytes(hex)
            addNewEffectLst()
        }
    }

    private fun backgroundRead(ppt: XMLSlideShow): PptDocument.Fill? =
        PptDocument.read(PptFixtures.bytesOf(ppt)).slideModels[0].background

    // Colour scheme and colour map

    @Test
    fun slotColor_followsMastersColourMap() {
        val ppt = PptFixtures.themed()
        val theme = themeOf(ppt.createSlide())
        assertEquals(DK1, theme.slotColor("tx1"))
        assertEquals(LT1, theme.slotColor("bg1"))
        // Scheme slots that the map does not name resolve to themselves.
        assertEquals(ACCENT1, theme.slotColor("accent1"))
        assertEquals(DK1, theme.slotColor("dk1"))
    }

    @Test
    fun schemeColor_resolvesThroughColourMap() {
        val ppt = PptFixtures.themed()
        val theme = themeOf(ppt.createSlide())
        assertEquals(DK1, theme.schemeColor(PptFixtures.schemeColor(STSchemeColorVal.TX_1)))
        assertEquals(ACCENT2, theme.schemeColor(PptFixtures.schemeColor(STSchemeColorVal.ACCENT_2)))
    }

    @Test
    fun slideColourMapOverride_replacesMasters() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        overrideOn(slide)
        val theme = themeOf(slide)
        assertEquals(LT1, theme.slotColor("tx1"))
        assertEquals(DK1, theme.slotColor("bg1"))
    }

    @Test
    fun layoutColourMapOverride_appliesWhenSlideHasNone() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        val layout = slide.slideLayout.xmlObject
        val ovr = if (layout.isSetClrMapOvr) layout.clrMapOvr else layout.addNewClrMapOvr()
        if (ovr.isSetMasterClrMapping) ovr.unsetMasterClrMapping()
        invertedMapping(ovr.addNewOverrideClrMapping())

        assertEquals(LT1, themeOf(slide).slotColor("tx1"))
    }

    @Test
    fun slideOverride_winsOverLayoutOverride() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        val layout = slide.slideLayout.xmlObject
        val layoutOvr = if (layout.isSetClrMapOvr) layout.clrMapOvr else layout.addNewClrMapOvr()
        if (layoutOvr.isSetMasterClrMapping) layoutOvr.unsetMasterClrMapping()
        invertedMapping(layoutOvr.addNewOverrideClrMapping())
        // The slide maps text back to dark.
        overrideOn(slide).apply {
            tx1 = STColorSchemeIndex.DK_1
            bg1 = STColorSchemeIndex.LT_1
        }

        assertEquals(DK1, themeOf(slide).slotColor("tx1"))
    }

    @Test
    fun phClr_takesThePlaceholderColour() {
        val ppt = PptFixtures.themed()
        val theme = themeOf(ppt.createSlide())
        assertEquals("ABCDEF", theme.schemeColor(PptFixtures.schemeColor(STSchemeColorVal.PH_CLR), phClr = "ABCDEF"))
        assertNull(theme.schemeColor(PptFixtures.schemeColor(STSchemeColorVal.PH_CLR)))
    }

    // Colour transforms

    @Test
    fun srgbColor_withoutTransforms_isTheLiteral() {
        assertEquals("4472C4", PptTheme.srgbColor(srgb("4472C4")))
    }

    @Test
    fun lumMod_scalesLightnessOfAGrey() {
        // 808080 has lightness 128/255; half of that is 64/255.
        assertEquals("404040", PptTheme.srgbColor(srgb(ACCENT3) { addNewLumMod().`val` = 50000 }))
    }

    @Test
    fun lumMod_keepsHueAndSaturation() {
        // Pure red at lightness 0.5; halving lightness gives a dark red, not a grey.
        assertEquals("800000", PptTheme.srgbColor(srgb("FF0000") { addNewLumMod().`val` = 50000 }))
    }

    @Test
    fun lumModThenLumOff_isPowerPointsLighterVariant() {
        // 128/255 * 0.75 + 0.25 = 0.62647, which is 160/255.
        val color = srgb(ACCENT3) {
            addNewLumMod().`val` = 75000
            addNewLumOff().`val` = 25000
        }
        assertEquals("A0A0A0", PptTheme.srgbColor(color))
    }

    @Test
    fun lumOff_isClampedToWhite() {
        val color = srgb("FFFFFF") { addNewLumOff().`val` = 50000 }
        assertEquals("FFFFFF", PptTheme.srgbColor(color))
    }

    @Test
    fun tint_movesTowardsWhite() {
        // A quarter of the way from white towards black: 0.75 * 255 = 191.
        assertEquals("BFBFBF", PptTheme.srgbColor(srgb("000000") { addNewTint().`val` = 25000 }))
    }

    @Test
    fun shade_movesTowardsBlack() {
        assertEquals("808080", PptTheme.srgbColor(srgb("FFFFFF") { addNewShade().`val` = 50000 }))
    }

    @Test
    fun alpha_belowOne_prefixesAlphaByte() {
        assertEquals("80$ACCENT1", PptTheme.srgbColor(srgb(ACCENT1) { addNewAlpha().`val` = 50000 }))
    }

    @Test
    fun alpha_ofOne_staysSixDigits() {
        assertEquals(ACCENT1, PptTheme.srgbColor(srgb(ACCENT1) { addNewAlpha().`val` = 100000 }))
    }

    @Test
    fun schemeColor_appliesTransformsAfterColourMap() {
        val ppt = PptFixtures.themed()
        val theme = themeOf(ppt.createSlide())
        val color = PptFixtures.schemeColor(STSchemeColorVal.ACCENT_3).apply { addNewLumMod().`val` = 50000 }
        assertEquals("404040", theme.schemeColor(color))
    }

    // Style matrix

    @Test
    fun fillStyle_zeroIsNoFill() {
        val theme = themeOf(PptFixtures.themed().createSlide())
        assertNull(theme.fillStyle(0, ACCENT1))
    }

    @Test
    fun fillStyle_solidPlaceholder_takesReferenceColour() {
        val theme = themeOf(PptFixtures.themed().createSlide())
        assertEquals(PptDocument.SolidFill(ACCENT1), theme.fillStyle(1, ACCENT1))
    }

    @Test
    fun fillStyle_gradient_sortsStopsAndReadsAngle() {
        val theme = themeOf(PptFixtures.themed().createSlide())
        val fill = theme.fillStyle(2, ACCENT1)
        // 4472C4 at half shade: 44/2 = 22, 72/2 = 39, C4/2 = 62.
        val expected = PptDocument.GradientFill(
            stops = listOf(PptDocument.GradientStop(0f, ACCENT1), PptDocument.GradientStop(1f, "223962")),
            angleDegrees = 90f,
        )
        assertEquals(expected, fill)
    }

    @Test
    fun fillStyle_literal_ignoresReferenceColour() {
        val theme = themeOf(PptFixtures.themed().createSlide())
        assertEquals(PptDocument.SolidFill(FILL_STYLE_3), theme.fillStyle(3, ACCENT1))
    }

    @Test
    fun fillStyle_pastTheEndOfTheList_isNull() {
        val theme = themeOf(PptFixtures.themed().createSlide())
        assertNull(theme.fillStyle(4, ACCENT1))
        assertNull(theme.fillStyle(1004, ACCENT1))
    }

    @Test
    fun fillStyle_from1001_addressesBackgroundList() {
        val theme = themeOf(PptFixtures.themed().createSlide())
        assertEquals(PptDocument.SolidFill(ACCENT2), theme.fillStyle(1001, ACCENT2))
        assertEquals(PptDocument.SolidFill(BG_STYLE_2), theme.fillStyle(1002, ACCENT2))
        assertNull(theme.fillStyle(1003, ACCENT2))
    }

    @Test
    fun lineStyle_readsColourAndWidth() {
        val theme = themeOf(PptFixtures.themed().createSlide())
        assertEquals(PptDocument.Outline(ACCENT2, 1f), theme.lineStyle(1, ACCENT2))
        assertNull("noFill line", theme.lineStyle(2, ACCENT2))
        assertNull("idx 0", theme.lineStyle(0, ACCENT2))
    }

    @Test
    fun lineWidthOf_defaultsToThreeQuarterPoint() {
        assertEquals(0.75f, PptTheme.lineWidthOf(null), 0f)
    }

    // Backgrounds, read through the document

    @Test
    fun bgRef_resolvesThroughBackgroundStyleWithItsColour() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        newBackground(slide).addNewBgRef().apply {
            idx = 1001
            addNewSchemeClr().`val` = STSchemeColorVal.ACCENT_1
        }
        assertEquals(PptDocument.SolidFill(ACCENT1), backgroundRead(ppt))
    }

    @Test
    fun bgRef_toLiteralStyle_ignoresItsColour() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        newBackground(slide).addNewBgRef().apply {
            idx = 1002
            addNewSchemeClr().`val` = STSchemeColorVal.ACCENT_1
        }
        assertEquals(PptDocument.SolidFill(BG_STYLE_2), backgroundRead(ppt))
    }

    @Test
    fun bgRef_schemeColourGoesThroughSlidesColourMap() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        overrideOn(slide)
        newBackground(slide).addNewBgRef().apply {
            idx = 1001
            addNewSchemeClr().`val` = STSchemeColorVal.BG_1
        }
        // bg1 is mapped to dk1 on this slide.
        assertEquals(PptDocument.SolidFill(DK1), backgroundRead(ppt))
    }

    @Test
    fun background_slideOwnWinsOverLayoutAndMaster() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        solidBackground(slide.slideMaster, "0000AA")
        solidBackground(slide.slideLayout, "00AA00")
        solidBackground(slide, "AA0000")
        assertEquals(PptDocument.SolidFill("AA0000"), backgroundRead(ppt))
    }

    @Test
    fun background_fallsBackToLayoutThenMaster() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        clearBackground(slide)
        solidBackground(slide.slideMaster, "0000AA")
        solidBackground(slide.slideLayout, "00AA00")
        assertEquals(PptDocument.SolidFill("00AA00"), backgroundRead(ppt))

        val ppt2 = PptFixtures.themed()
        val slide2 = ppt2.createSlide()
        clearBackground(slide2)
        clearBackground(slide2.slideLayout)
        solidBackground(slide2.slideMaster, "0000AA")
        assertEquals(PptDocument.SolidFill("0000AA"), backgroundRead(ppt2))
    }

    @Test
    fun background_noneAnywhere_isNull() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        clearBackground(slide)
        clearBackground(slide.slideLayout)
        clearBackground(slide.slideMaster)
        assertNull(backgroundOf(slide.slideMaster))
        assertNull(backgroundRead(ppt))
    }

    @Test
    fun background_picture_isReadAsPictureFill() {
        val ppt = PptFixtures.themed()
        val slide = ppt.createSlide()
        val image = PptFixtures.png()
        val picture = ppt.addPicture(image, org.apache.poi.sl.usermodel.PictureData.PictureType.PNG)
        val rel = slide.addRelation(null, org.apache.poi.xslf.usermodel.XSLFRelation.IMAGES, picture)
        newBackground(slide).addNewBgPr().apply {
            addNewBlipFill().addNewBlip().embed = rel.relationship.id
            addNewEffectLst()
        }
        val fill = backgroundRead(ppt)
        assertEquals(PptDocument.PictureFill(image, picture.fileName), fill)
    }
}
