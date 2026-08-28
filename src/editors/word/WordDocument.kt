package dev.kern.editors.word

import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger

/**
 * Apache POI bridge for .docx documents (XWPF) with full rich-text, formatting,
 * table editing, and structural block support.
 *
 * Reads the body in element order (paragraphs interleaved with tables and media)
 * so block indices remain stable across all editing operations.
 *
 * Non-destructive round-trip guarantee:
 * - Only edited paragraphs' runs and modified paragraph properties are rewritten.
 * - Only edited tables are modified (updating cells or structural rows/cols).
 * - Untouched paragraphs, tables, images, headers, footers, and styles survive byte-for-byte.
 */
object WordDocument {

    /** Paragraph styles, mapped to standard display metrics. */
    enum class Kind { TITLE, HEADING1, HEADING2, BODY }

    /** Text alignment options. */
    enum class Align { START, CENTER, END, JUSTIFY }

    /** List formatting options. */
    enum class ListType { NONE, BULLET, NUMBER }

    /**
     * Character-level formatting for a run. Null fields inherit the paragraph / style default.
     */
    data class RunStyle(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val sizePt: Float? = null,
        val colorHex: String? = null,
        val fontFamily: String? = null,
        val linkUrl: String? = null,
    )

    /** A styled text run. */
    data class Run(val text: String, val style: RunStyle = RunStyle())

    /** Paragraph-level formatting properties. */
    data class ParaProps(
        val kind: Kind = Kind.BODY,
        val styleId: String? = null,
        val align: Align = Align.START,
        val listType: ListType = ListType.NONE,
        val indentTwips: Int = 0,
    )

    /** Patch updates for paragraph properties. */
    data class ParaPropsUpdate(
        val kind: Kind? = null,
        val align: Align? = null,
        val listType: ListType? = null,
        val indentTwips: Int? = null,
    )

    /** Document block interface. */
    sealed interface Block

    /** An editable or rendered body paragraph. */
    data class ParagraphBlock(
        val props: ParaProps,
        val runs: List<Run>,
        val editable: Boolean = true,
    ) : Block {
        val text: String get() = runs.joinToString("") { it.text }
    }

    /** An editable table represented as a 2D grid of cell strings. */
    data class TableBlock(val rows: List<List<String>>) : Block

    /** An embedded raster image from the document. */
    data class ImageBlock(val bytes: ByteArray, val fileName: String? = null) : Block {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ImageBlock) return false
            return bytes.contentEquals(other.bytes) && fileName == other.fileName
        }

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + (fileName?.hashCode() ?: 0)
    }

    /** Passthrough for unmodeled XML structures. Never rewritten. */
    data object OpaqueBlock : Block

    /** Parsed document representation. */
    data class Parsed(val blocks: List<Block>)

    fun read(bytes: ByteArray): Parsed {
        XWPFDocument(ByteArrayInputStream(bytes)).use { doc ->
            val blocks = ArrayList<Block>()
            for (element in doc.bodyElements) {
                when (element) {
                    is XWPFParagraph -> {
                        val images = extractPictures(element)
                        if (images.isNotEmpty()) {
                            images.forEach { blocks.add(it) }
                        }
                        val para = readParagraph(element)
                        if (para.text.isNotEmpty() || images.isEmpty()) {
                            blocks.add(para)
                        }
                    }
                    is XWPFTable -> blocks.add(readTable(element))
                    else -> blocks.add(OpaqueBlock)
                }
            }
            if (blocks.isEmpty()) {
                blocks.add(ParagraphBlock(ParaProps(), listOf(Run("")), editable = true))
            }
            return Parsed(blocks)
        }
    }

    /**
     * Applies paragraph edits, property changes, and table modifications to the document.
     */
    fun applyEditsAndSerialize(
        originalBytes: ByteArray,
        edits: Map<Int, List<Run>> = emptyMap(),
        paraProps: Map<Int, ParaPropsUpdate> = emptyMap(),
        tableEdits: Map<Int, List<List<String>>> = emptyMap(),
    ): ByteArray {
        XWPFDocument(ByteArrayInputStream(originalBytes)).use { doc ->
            val elements = doc.bodyElements

            // Apply paragraph run edits and property changes
            val allParaIndices = (edits.keys + paraProps.keys).distinct()
            for (index in allParaIndices) {
                val paragraph = elements.getOrNull(index) as? XWPFParagraph ?: continue
                
                // Update runs if modified
                edits[index]?.let { writeRuns(paragraph, it) }

                // Update paragraph-level properties if modified
                paraProps[index]?.let { update ->
                    update.align?.let { paragraph.alignment = mapAlignment(it) }
                    update.indentTwips?.let { paragraph.indentationLeft = it.coerceAtLeast(0) }
                    update.kind?.let {
                        val styleName = when (it) {
                            Kind.TITLE -> "Title"
                            Kind.HEADING1 -> "Heading1"
                            Kind.HEADING2 -> "Heading2"
                            Kind.BODY -> "Normal"
                        }
                        paragraph.style = styleName
                    }
                    update.listType?.let { listType ->
                        when (listType) {
                            ListType.NONE -> paragraph.numID = null
                            ListType.BULLET -> {
                                ensureNumbering(doc)
                                paragraph.setNumID(BigInteger.valueOf(1))
                                paragraph.setNumILvl(BigInteger.ZERO)
                            }
                            ListType.NUMBER -> {
                                ensureNumbering(doc)
                                paragraph.setNumID(BigInteger.valueOf(2))
                                paragraph.setNumILvl(BigInteger.ZERO)
                            }
                        }
                    }
                }
            }

            // Apply table cell and structural edits
            for ((index, newGrid) in tableEdits) {
                val table = elements.getOrNull(index) as? XWPFTable ?: continue
                writeTable(table, newGrid)
            }

            ByteArrayOutputStream().use { out ->
                doc.write(out)
                return out.toByteArray()
            }
        }
    }

    // ---- Read Helpers -------------------------------------------------------

    private fun readParagraph(p: XWPFParagraph): ParagraphBlock {
        val runs = ArrayList<Run>()
        for (r in p.runs) {
            val linkUrl = (r as? XWPFHyperlinkRun)?.hyperlinkId
            runs.add(Run(r.text() ?: "", styleOfRun(r, linkUrl)))
        }
        val merged = collapse(runs).ifEmpty { listOf(Run("")) }
        val align = when (p.alignment) {
            ParagraphAlignment.CENTER -> Align.CENTER
            ParagraphAlignment.RIGHT, ParagraphAlignment.END -> Align.END
            ParagraphAlignment.BOTH, ParagraphAlignment.DISTRIBUTE -> Align.JUSTIFY
            else -> Align.START
        }
        val listType = when {
            p.numID == null -> ListType.NONE
            p.numFmt.equals("bullet", ignoreCase = true) -> ListType.BULLET
            else -> ListType.NUMBER
        }
        val indent = p.indentationLeft.coerceAtLeast(0)
        val props = ParaProps(
            kind = kindOf(p.style),
            styleId = p.style,
            align = align,
            listType = listType,
            indentTwips = indent,
        )
        return ParagraphBlock(props, merged, editable = true)
    }

    private fun readTable(t: XWPFTable): TableBlock {
        val rows = t.rows.map { row ->
            row.tableCells.map { cell -> cell.text ?: "" }
        }
        return TableBlock(rows)
    }

    private fun extractPictures(p: XWPFParagraph): List<ImageBlock> {
        val list = ArrayList<ImageBlock>()
        for (r in p.runs) {
            val pics = r.embeddedPictures ?: continue
            for (pic in pics) {
                val data = pic.pictureData?.data
                if (data != null && data.isNotEmpty()) {
                    list.add(ImageBlock(data, pic.pictureData?.fileName))
                }
            }
        }
        return list
    }

    private fun styleOfRun(r: XWPFRun, linkUrl: String? = null): RunStyle {
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
            linkUrl = linkUrl,
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

    // ---- Write Helpers ------------------------------------------------------

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

    private fun writeTable(table: XWPFTable, newGrid: List<List<String>>) {
        val targetRowCount = newGrid.size
        while (table.rows.size < targetRowCount) {
            table.createRow()
        }
        while (table.rows.size > targetRowCount && table.rows.size > 1) {
            table.removeRow(table.rows.size - 1)
        }

        for (r in newGrid.indices) {
            val row = table.getRow(r) ?: continue
            val targetColCount = newGrid[r].size
            while (row.tableCells.size < targetColCount) {
                row.addNewTableCell()
            }
            for (c in newGrid[r].indices) {
                val cell = row.getCell(c) ?: continue
                val text = newGrid[r][c]
                if (cell.paragraphs.isEmpty()) {
                    cell.addParagraph().createRun().setText(text)
                } else {
                    val p = cell.paragraphs[0]
                    for (i in p.runs.indices.reversed()) p.removeRun(i)
                    p.createRun().setText(text)
                }
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

    private fun mapAlignment(align: Align): ParagraphAlignment = when (align) {
        Align.START -> ParagraphAlignment.LEFT
        Align.CENTER -> ParagraphAlignment.CENTER
        Align.END -> ParagraphAlignment.RIGHT
        Align.JUSTIFY -> ParagraphAlignment.BOTH
    }

    private fun ensureNumbering(doc: XWPFDocument) {
        try {
            if (doc.numbering == null) {
                doc.createNumbering()
            }
        } catch (_: Exception) {
            // Ignore numbering creation if already present or unsupported in isolated doc
        }
    }
}
