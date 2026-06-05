package dev.kern.editors.excel

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Apache POI bridge for .xlsx spreadsheets (XSSF). Reads every sheet for display
 * via DataFormatter, which yields the cached result for formula cells (no
 * recalculation in alpha). Saving re-opens the original workbook and applies only
 * the edited cells, so untouched cells, formulas, and formatting survive.
 *
 * Uses XSSFWorkbook directly (not WorkbookFactory, whose ServiceLoader lookup is
 * unreliable on Android). No Android/Compose dependencies, so it stays testable.
 */
object ExcelDocument {

    /** A parsed workbook: sheet names plus one display grid per sheet (same order). */
    data class Parsed(val sheetNames: List<String>, val sheets: List<List<List<String>>>)

    fun read(bytes: ByteArray): Parsed {
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val formatter = DataFormatter()
            val names = ArrayList<String>(wb.numberOfSheets)
            val grids = ArrayList<List<List<String>>>(wb.numberOfSheets)
            for (s in 0 until wb.numberOfSheets) {
                val sheet = wb.getSheetAt(s)
                names.add(sheet.sheetName)
                val lastRow = sheet.lastRowNum
                if (lastRow < 0) {
                    grids.add(listOf(listOf("")))
                    continue
                }
                var width = 1
                for (r in 0..lastRow) {
                    val cells = sheet.getRow(r)?.lastCellNum?.toInt() ?: 0
                    if (cells > width) width = cells
                }
                grids.add(
                    (0..lastRow).map { r ->
                        val row = sheet.getRow(r)
                        (0 until width).map { c -> row?.getCell(c)?.let { formatter.formatCellValue(it) } ?: "" }
                    },
                )
            }
            if (names.isEmpty()) return Parsed(listOf("Sheet1"), listOf(listOf(listOf(""))))
            return Parsed(names, grids)
        }
    }

    /**
     * Re-opens the original document and writes [edits] (keyed by sheet index, row,
     * col) back into the matching sheets, then returns the serialized bytes. Numeric
     * where the text parses as a number, else string. Cells/rows created on demand.
     */
    fun applyEditsAndSerialize(originalBytes: ByteArray, edits: Map<Triple<Int, Int, Int>, String>): ByteArray {
        XSSFWorkbook(ByteArrayInputStream(originalBytes)).use { wb ->
            for ((key, value) in edits) {
                val (s, r, c) = key
                if (s < 0 || s >= wb.numberOfSheets) continue
                val sheet = wb.getSheetAt(s)
                val row = sheet.getRow(r) ?: sheet.createRow(r)
                val cell = row.getCell(c) ?: row.createCell(c)
                val number = value.toDoubleOrNull()
                if (number != null) cell.setCellValue(number) else cell.setCellValue(value)
            }
            ByteArrayOutputStream().use { out ->
                wb.write(out)
                return out.toByteArray()
            }
        }
    }
}
