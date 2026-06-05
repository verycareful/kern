package dev.kern.editors.word

import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Apache POI bridge for .docx documents (XWPF). Reads paragraphs as plain text
 * tagged with a coarse style (title / heading / body) for display. Saving re-opens
 * the original document and replaces only the edited paragraphs' text, keeping the
 * paragraph's own style and every untouched paragraph intact. Intra-paragraph rich
 * formatting (per-run bold/italic) on an edited paragraph is reset to the paragraph
 * default (alpha tradeoff).
 *
 * No Android/Compose dependencies, so it stays testable.
 */
object WordDocument {

    enum class Style { TITLE, HEADING1, HEADING2, BODY }

    data class Paragraph(val text: String, val style: Style)

    fun read(bytes: ByteArray): List<Paragraph> {
        XWPFDocument(ByteArrayInputStream(bytes)).use { doc ->
            val paras = doc.paragraphs.map { Paragraph(it.text ?: "", styleOf(it.style)) }
            return paras.ifEmpty { listOf(Paragraph("", Style.BODY)) }
        }
    }

    /** Writes [edits] (paragraph index -> new text) back into the original document. */
    fun applyEditsAndSerialize(originalBytes: ByteArray, edits: Map<Int, String>): ByteArray {
        XWPFDocument(ByteArrayInputStream(originalBytes)).use { doc ->
            val paragraphs = doc.paragraphs
            for ((index, text) in edits) {
                if (index < 0 || index >= paragraphs.size) continue
                val paragraph = paragraphs[index]
                for (r in paragraph.runs.indices.reversed()) paragraph.removeRun(r)
                val run = paragraph.createRun()
                val lines = text.split("\n")
                run.setText(lines.first(), 0)
                for (i in 1 until lines.size) {
                    run.addBreak()
                    run.setText(lines[i])
                }
            }
            ByteArrayOutputStream().use { out ->
                doc.write(out)
                return out.toByteArray()
            }
        }
    }

    private fun styleOf(styleId: String?): Style {
        val s = styleId ?: return Style.BODY
        return when {
            s.contains("Title", ignoreCase = true) -> Style.TITLE
            s.contains("Heading1", ignoreCase = true) || s.contains("Heading 1", ignoreCase = true) -> Style.HEADING1
            s.contains("Heading", ignoreCase = true) -> Style.HEADING2
            else -> Style.BODY
        }
    }
}
