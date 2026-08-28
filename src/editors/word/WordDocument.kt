package dev.kern.editors.word

import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Apache POI bridge for .docx documents (XWPF) with a run-level rich-text model.
 *
 * Reads the body in element order (paragraphs interleaved with tables) so block
 * indices are stable: a table counts as one opaque block even before tables become
 * editable, which keeps a paragraph's index identical whether or not tables precede
 * it. Each paragraph is read as an ordered list of [Run]s carrying per-run formatting
 * (bold/italic/underline/strike, font size, colour, family).
 *
 * Saving re-opens the original document and rewrites ONLY the paragraphs the user
 * edited, rebuilding their runs from the supplied model while leaving the paragraph
 * element (and its paragraph-level properties) in place. Every untouched paragraph,
 * table, image, header/footer, and section survives byte-for-byte (full round-trip).
 *
 * A paragraph that carries an embedded image or a hyperlink run — or whose plain-run
 * text does not reconstruct the paragraph's full text — is marked non-[editable] so
 * those runs are never rewritten in this phase (hyperlink editing lands in Phase 4).
 *
 * No Android/Compose dependencies, so it stays JVM-unit-testable. Uses XWPFDocument
 * directly (not WorkbookFactory), matching the Excel bridge's note on unreliable
 * ServiceLoader lookup on Android.
 */
object WordDocument {

    /** Coarse paragraph kind, used for display sizing. */
    enum class Kind { TITLE, HEADING1, HEADING2, BODY }

    /**
     * Character-level formatting for a run. All fields default to "inherit": a null
     * size/colour/family means the run does not override the style default.
     */
    data class RunStyle(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val sizePt: Float? = null,
        val colorHex: String? = null,   // 6 hex digits, upper-case, no leading '#'
        val fontFamily: String? = null,
    )

    /** A styled text run. Line breaks inside [text] are '\n'. */
    data class Run(val text: String, val style: RunStyle = RunStyle())

    /** Paragraph-level properties. [styleId] is the raw docx style id, preserved verbatim. */
    data class ParaProps(val kind: Kind, val styleId: String?)

    /** A block in body order. */
    sealed interface Block

    /**
     * A body paragraph. [editable] is false when the paragraph holds content this
     * phase does not model (images, hyperlink runs); such paragraphs are shown
     * read-only and never rewritten on save.
     */
    data class ParagraphBlock(
        val props: ParaProps,
        val runs: List<Run>,
        val editable: Boolean,
    ) : Block {
        val text: String get() = runs.joinToString("") { it.text }
    }

    /** A table, flattened to display text per cell. Read-only in this phase. */
    data class TableBlock(val rows: List<List<String>>) : Block

    /** Any body element not otherwise modelled. Never rewritten. */
    data object OpaqueBlock : Block

    /** A parsed document: blocks in body order. */
    data class Parsed(val blocks: List<Block>)

    fun read(bytes: ByteArray): Parsed {
        XWPFDocument(ByteArrayInputStream(bytes)).use { doc ->
            val blocks = ArrayList<Block>()
            for (element in doc.bodyElements) {
                when (element) {
                    is XWPFParagraph -> blocks.add(readParagraph(element))
                    is XWPFTable -> blocks.add(readTable(element))
                    else -> blocks.add(OpaqueBlock)
                }
            }
            if (blocks.isEmpty()) {
                blocks.add(ParagraphBlock(ParaProps(Kind.BODY, null), listOf(Run("")), editable = true))
            }
            return Parsed(blocks)
        }
    }

    /**
     * Applies [edits] (block index -> the paragraph's full new run list) to the
     * original document and serializes. Non-paragraph or out-of-range indices are
     * ignored; only the listed paragraphs are rewritten.
     */
    fun applyEditsAndSerialize(originalBytes: ByteArray, edits: Map<Int, List<Run>>): ByteArray {
        XWPFDocument(ByteArrayInputStream(originalBytes)).use { doc ->
            val elements = doc.bodyElements
            for ((index, runs) in edits) {
                val paragraph = elements.getOrNull(index) as? XWPFParagraph ?: continue
                writeRuns(paragraph, runs)
            }
            ByteArrayOutputStream().use { out ->
                doc.write(out)
                return out.toByteArray()
            }
        }
    }

    // ---- read helpers -------------------------------------------------------

    private fun readParagraph(p: XWPFParagraph): ParagraphBlock {
        val runs = ArrayList<Run>()
        var complex = false
        for (r in p.runs) {
            if (r is XWPFHyperlinkRun || r.embeddedPictures?.isNotEmpty() == true) complex = true
            runs.add(Run(r.text() ?: "", styleOfRun(r)))
        }
        val merged = collapse(runs).ifEmpty { listOf(Run("")) }
        // Guard: if our plain-run model does not reconstruct the paragraph's full
        // text, there is content we do not model (fields, hyperlinks). Keep it
        // read-only so a save never drops it.
        val reconstructs = merged.joinToString("") { it.text } == (p.text ?: "")
        val editable = !complex && reconstructs
        return ParagraphBlock(ParaProps(kindOf(p.style), p.style), merged, editable)
    }

    private fun readTable(t: XWPFTable): TableBlock {
        val rows = t.rows.map { row ->
            row.tableCells.map { cell -> cell.text ?: "" }
        }
        return TableBlock(rows)
    }

    private fun styleOfRun(r: XWPFRun): RunStyle {
        // getFontSizeAsDouble() returns a BOXED Double that is null when the run has no
        // explicit size (it inherits from the paragraph/style) — the common case. It
        // must be treated as nullable, or unboxing throws NullPointerException.
        val size = r.fontSizeAsDouble?.takeIf { it > 0.0 }?.toFloat()
        val color = r.color?.takeIf { it.isNotBlank() && !it.equals("auto", ignoreCase = true) }?.uppercase()
        val underlined = (r.underline ?: UnderlinePatterns.NONE) != UnderlinePatterns.NONE
        return RunStyle(
            bold = r.isBold,
            italic = r.isItalic,
            underline = underlined,
            strike = r.isStrikeThrough,
            sizePt = size,
            colorHex = color,
            fontFamily = r.fontFamily,
        )
    }

    private fun kindOf(styleId: String?): Kind {
        val s = styleId ?: return Kind.BODY
        return when {
            s.contains("Title", ignoreCase = true) -> Kind.TITLE
            s.contains("Heading1", ignoreCase = true) || s.contains("Heading 1", ignoreCase = true) -> Kind.HEADING1
            s.contains("Heading", ignoreCase = true) -> Kind.HEADING2
            else -> Kind.BODY
        }
    }

    /** Merges adjacent runs that share a style and drops empty runs. */
    private fun collapse(runs: List<Run>): List<Run> {
        val out = ArrayList<Run>()
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.style == run.style) {
                out[out.size - 1] = last.copy(text = last.text + run.text)
            } else {
                out.add(run)
            }
        }
        return out
    }

    // ---- write helpers ------------------------------------------------------

    /** Rebuilds [p]'s runs from [runs], preserving the paragraph element and its pPr. */
    private fun writeRuns(p: XWPFParagraph, runs: List<Run>) {
        for (i in p.runs.indices.reversed()) p.removeRun(i)
        val effective = if (runs.isEmpty()) listOf(Run("")) else runs
        for (run in effective) {
            val r = p.createRun()
            applyStyle(r, run.style)
            val lines = run.text.split("\n")
            r.setText(lines.first(), 0)
            for (k in 1 until lines.size) {
                r.addBreak()
                r.setText(lines[k])
            }
        }
    }

    private fun applyStyle(r: XWPFRun, s: RunStyle) {
        if (s.bold) r.isBold = true
        if (s.italic) r.isItalic = true
        if (s.underline) r.underline = UnderlinePatterns.SINGLE
        if (s.strike) r.isStrikeThrough = true
        s.sizePt?.let { r.setFontSize(it.toDouble()) }
        s.colorHex?.let { r.setColor(it) }
        s.fontFamily?.let { r.setFontFamily(it) }
    }
}
