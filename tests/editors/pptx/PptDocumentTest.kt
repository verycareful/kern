package dev.kern.editors.pptx

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class PptDocumentTest {

    /** Builds a 2-slide deck, each with one text box. */
    private fun samplePptx(): ByteArray {
        val out = ByteArrayOutputStream()
        XMLSlideShow().use { ppt ->
            ppt.createSlide().createTextBox().setText("Title slide")
            ppt.createSlide().createTextBox().setText("Second slide")
            ppt.write(out)
        }
        return out.toByteArray()
    }

    @Test
    fun read_returnsTextOfEachSlide() {
        val parsed = PptDocument.read(samplePptx())
        assertEquals(2, parsed.slides.size)
        assertEquals("Title slide", parsed.slides[0][0])
        assertEquals("Second slide", parsed.slides[1][0])
    }

    @Test
    fun applyEdits_replacesOnlyTheEditedShape() {
        val edited = PptDocument.applyEditsAndSerialize(samplePptx(), mapOf((0 to 0) to "Edited title"))
        val parsed = PptDocument.read(edited)
        assertEquals("Edited title", parsed.slides[0][0])
        assertEquals("Second slide", parsed.slides[1][0]) // untouched
    }
}
