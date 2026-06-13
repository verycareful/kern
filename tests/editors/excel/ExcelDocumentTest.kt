package dev.kern.editors.excel

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class ExcelDocumentTest {

    /** Builds a 2-sheet workbook: Alpha (a header + a data row) and Beta (one cell). */
    private fun sampleWorkbook(): ByteArray {
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { wb ->
            val alpha = wb.createSheet("Alpha")
            alpha.createRow(0).also { it.createCell(0).setCellValue("Name"); it.createCell(1).setCellValue("Age") }
            alpha.createRow(1).also { it.createCell(0).setCellValue("Ada"); it.createCell(1).setCellValue(36.0) }
            wb.createSheet("Beta").createRow(0).createCell(0).setCellValue("X")
            wb.write(out)
        }
        return out.toByteArray()
    }

    @Test
    fun read_returnsEverySheetAndCellValue() {
        val parsed = ExcelDocument.read(sampleWorkbook())
        assertEquals(listOf("Alpha", "Beta"), parsed.sheetNames)
        assertEquals("Name", parsed.sheets[0][0][0])
        assertEquals("Ada", parsed.sheets[0][1][0])
        assertEquals("36", parsed.sheets[0][1][1]) // DataFormatter renders 36.0 as "36"
        assertEquals("X", parsed.sheets[1][0][0])
    }

    @Test
    fun applyEdits_changesTargetCellAndPreservesOthers() {
        val edited = ExcelDocument.applyEditsAndSerialize(
            sampleWorkbook(),
            mapOf(Triple(0, 1, 1) to "40"),
        )
        val parsed = ExcelDocument.read(edited)
        assertEquals("40", parsed.sheets[0][1][1]) // edited (and still numeric)
        assertEquals("Ada", parsed.sheets[0][1][0]) // untouched neighbour
        assertEquals("X", parsed.sheets[1][0][0])   // untouched second sheet
    }
    @Test
    fun applyEdits_preservesColumnWidthsAndRowHeights() {
        val edited = ExcelDocument.applyEditsAndSerialize(
            sampleWorkbook(),
            edits = emptyMap(),
            colWidths = mapOf(Pair(0, 1) to 150f),
            rowHeights = mapOf(Pair(0, 1) to 50f)
        )
        val parsed = ExcelDocument.read(edited)
        
        val newColWidth = parsed.colWidths[0][1] ?: 0f
        val newRowHeight = parsed.rowHeights[0][1] ?: 0f
        
        assertEquals(150f, newColWidth, 1.0f)
        assertEquals(50f, newRowHeight, 1.0f)
    }
}
