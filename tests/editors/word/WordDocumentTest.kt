package dev.kern.editors.word

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class WordDocumentTest {

    /** Builds a document with a Heading1 paragraph and two body paragraphs. */
    private fun sampleDocx(): ByteArray {
        val out = ByteArrayOutputStream()
        XWPFDocument().use { doc ->
            doc.createParagraph().also { it.style = "Heading1"; it.createRun().setText("Chapter") }
            doc.createParagraph().createRun().setText("First body line.")
            doc.createParagraph().createRun().setText("Second body line.")
            doc.write(out)
        }
        return out.toByteArray()
    }

    @Test
    fun read_returnsParagraphTextAndCoarseStyle() {
        val paras = WordDocument.read(sampleDocx())
        assertEquals(3, paras.size)
        assertEquals("Chapter", paras[0].text)
        assertEquals(WordDocument.Style.HEADING1, paras[0].style)
        assertEquals("First body line.", paras[1].text)
        assertEquals(WordDocument.Style.BODY, paras[1].style)
    }

    @Test
    fun applyEdits_replacesOnlyTheEditedParagraph() {
        val edited = WordDocument.applyEditsAndSerialize(sampleDocx(), mapOf(1 to "Edited body line."))
        val paras = WordDocument.read(edited)
        assertEquals("Edited body line.", paras[1].text)
        assertEquals("Chapter", paras[0].text)        // untouched
        assertEquals("Second body line.", paras[2].text) // untouched
    }
}
