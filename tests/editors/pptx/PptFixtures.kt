package dev.kern.editors.pptx

import org.apache.poi.sl.usermodel.PictureData.PictureType
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.xmlbeans.XmlOptions
import org.openxmlformats.schemas.drawingml.x2006.main.CTBackgroundFillStyleList
import org.openxmlformats.schemas.drawingml.x2006.main.CTColor
import org.openxmlformats.schemas.drawingml.x2006.main.CTFillStyleList
import org.openxmlformats.schemas.drawingml.x2006.main.CTLineStyleList
import org.openxmlformats.schemas.drawingml.x2006.main.CTSchemeColor
import org.openxmlformats.schemas.drawingml.x2006.main.STSchemeColorVal
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Decks built in code for the PowerPoint tests.
 *
 * Every deck starts from POI's blank template, which carries a master, its layouts and
 * the Office theme. [themed] then pins the colour scheme and the style matrix to known
 * values, so a test asserts against numbers it chose rather than against whatever the
 * template happens to ship.
 */
internal object PptFixtures {

    const val DK1 = "111111"
    const val LT1 = "EEEEEE"
    const val DK2 = "222222"
    const val LT2 = "DDDDDD"
    const val ACCENT1 = "4472C4"
    const val ACCENT2 = "ED7D31"
    const val ACCENT3 = "808080"

    /** The second background fill style is this literal colour. */
    const val BG_STYLE_2 = "123456"

    /** The third fill style is this literal colour. */
    const val FILL_STYLE_3 = "00FF00"

    private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"

    /** Parses an element's children into a type, rather than the element itself as a child. */
    private val contentOnly = XmlOptions().setLoadReplaceDocumentElement(null)

    /** A blank deck whose theme is pinned to the constants above. */
    fun themed(): XMLSlideShow {
        val ppt = XMLSlideShow()
        val theme = ppt.slideMasters[0].theme.xmlObject.themeElements
        val scheme = theme.clrScheme
        setSrgb(scheme.dk1, DK1)
        setSrgb(scheme.lt1, LT1)
        setSrgb(scheme.dk2, DK2)
        setSrgb(scheme.lt2, LT2)
        setSrgb(scheme.accent1, ACCENT1)
        setSrgb(scheme.accent2, ACCENT2)
        setSrgb(scheme.accent3, ACCENT3)

        val format = theme.fmtScheme
        // Fill styles, by idx: 1 solid placeholder colour, 2 a vertical gradient from the
        // placeholder colour to its half shade, 3 a literal green.
        format.fillStyleLst = CTFillStyleList.Factory.parse(
            """
            <a:fillStyleLst xmlns:a="$A">
              <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
              <a:gradFill>
                <a:gsLst>
                  <a:gs pos="100000"><a:schemeClr val="phClr"><a:shade val="50000"/></a:schemeClr></a:gs>
                  <a:gs pos="0"><a:schemeClr val="phClr"/></a:gs>
                </a:gsLst>
                <a:lin ang="5400000" scaled="0"/>
              </a:gradFill>
              <a:solidFill><a:srgbClr val="$FILL_STYLE_3"/></a:solidFill>
            </a:fillStyleLst>
            """.trimIndent(),
            contentOnly,
        )
        // Background fill styles, by idx: 1001 placeholder colour, 1002 a literal, 1003 none.
        format.bgFillStyleLst = CTBackgroundFillStyleList.Factory.parse(
            """
            <a:bgFillStyleLst xmlns:a="$A">
              <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
              <a:solidFill><a:srgbClr val="$BG_STYLE_2"/></a:solidFill>
              <a:noFill/>
            </a:bgFillStyleLst>
            """.trimIndent(),
            contentOnly,
        )
        // Line styles, by idx: 1 one point in the placeholder colour, 2 none.
        format.lnStyleLst = CTLineStyleList.Factory.parse(
            """
            <a:lnStyleLst xmlns:a="$A">
              <a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
              <a:ln w="25400"><a:noFill/></a:ln>
            </a:lnStyleLst>
            """.trimIndent(),
            contentOnly,
        )
        return ppt
    }

    /** Serialises [ppt] and closes it. */
    fun bytesOf(ppt: XMLSlideShow): ByteArray = ppt.use { deck ->
        ByteArrayOutputStream().use { out -> deck.write(out); out.toByteArray() }
    }

    /** Reopens serialised bytes with POI for inspecting what the writer produced. */
    fun reopen(bytes: ByteArray): XMLSlideShow = XMLSlideShow(ByteArrayInputStream(bytes))

    /** A deck of one text box per slide, with the given texts. */
    fun textDeck(vararg texts: String): ByteArray {
        val ppt = themed()
        for (text in texts) ppt.createSlide().createTextBox().setText(text)
        return bytesOf(ppt)
    }

    /** The text of each editable shape on each slide, as the editor reads it. */
    fun texts(bytes: ByteArray): List<List<String>> = PptDocument.read(bytes).slides

    fun ctShape(shape: XSLFTextShape): CTShape = shape.xmlObject as CTShape

    fun schemeColor(slot: STSchemeColorVal.Enum): CTSchemeColor =
        CTSchemeColor.Factory.newInstance().also { it.`val` = slot }

    /**
     * A 1x1 PNG. Literal bytes because the unit test classpath is android.jar, which,
     * like the device, has no java.awt to encode one with.
     */
    fun png(): ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
    )

    /**
     * Places [shape] at the given position and size in points, through the typed
     * transform rather than POI's setAnchor, which takes a java.awt.geom.Rectangle2D.
     */
    fun place(shape: XSLFShape, x: Int, y: Int, width: Int, height: Int) {
        val spPr = when (val xml = shape.xmlObject) {
            is CTShape -> xml.spPr ?: xml.addNewSpPr()
            is CTPicture -> xml.spPr ?: xml.addNewSpPr()
            else -> error("cannot place ${xml.javaClass}")
        }
        val xfrm = spPr.xfrm ?: spPr.addNewXfrm()
        (xfrm.off ?: xfrm.addNewOff()).apply { this.x = x * 12700L; this.y = y * 12700L }
        (xfrm.ext ?: xfrm.addNewExt()).apply { cx = width * 12700L; cy = height * 12700L }
    }

    fun addPicture(ppt: XMLSlideShow, slide: XSLFSlide, bytes: ByteArray = png()) =
        slide.createPicture(ppt.addPicture(bytes, PictureType.PNG))

    fun hexBytes(hex: String): ByteArray = byteArrayOf(
        hex.substring(0, 2).toInt(16).toByte(),
        hex.substring(2, 4).toInt(16).toByte(),
        hex.substring(4, 6).toInt(16).toByte(),
    )

    private fun setSrgb(color: CTColor, hex: String) {
        if (color.isSetSysClr) color.unsetSysClr()
        if (color.isSetSchemeClr) color.unsetSchemeClr()
        (if (color.isSetSrgbClr) color.srgbClr else color.addNewSrgbClr()).`val` = hexBytes(hex)
    }
}
