package dev.kern.editors.excel

import dev.kern.shared.CellMerge
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

    /** A parsed workbook: sheet names, one display grid per sheet, and merged regions per sheet. */
    data class Parsed(
        val sheetNames: List<String>,
        val sheets: List<List<List<String>>>,
        val mergedRegions: List<List<CellMerge>>,
        val colWidths: List<Map<Int, Float>> = emptyList(),
        val rowHeights: List<Map<Int, Float>> = emptyList(),
    )

    /**
     * A structural change to replay on the original workbook before applying cell
     * edits, so an inserted row/column shifts existing content (preserving formulas
     * and formatting) and the edit keys stay aligned with the displayed grid.
     */
    sealed interface StructuralOp { val sheet: Int }
    data class InsertRow(override val sheet: Int, val at: Int) : StructuralOp
    data class InsertColumn(override val sheet: Int, val at: Int) : StructuralOp

    fun read(bytes: ByteArray): Parsed {
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val formatter = DataFormatter()
            val names = ArrayList<String>(wb.numberOfSheets)
            val grids = ArrayList<List<List<String>>>(wb.numberOfSheets)
            val allMerges = ArrayList<List<CellMerge>>(wb.numberOfSheets)
            val allColWidths = ArrayList<Map<Int, Float>>(wb.numberOfSheets)
            val allRowHeights = ArrayList<Map<Int, Float>>(wb.numberOfSheets)
            for (s in 0 until wb.numberOfSheets) {
                val sheet = wb.getSheetAt(s)
                names.add(sheet.sheetName)
                val lastRow = sheet.lastRowNum
                if (lastRow < 0) {
                    grids.add(listOf(listOf("")))
                    allMerges.add(emptyList())
                    allColWidths.add(emptyMap())
                    allRowHeights.add(emptyMap())
                    continue
                }
                var width = 1
                val rHeights = mutableMapOf<Int, Float>()
                for (r in 0..lastRow) {
                    val row = sheet.getRow(r)
                    if (row != null) {
                        val cells = row.lastCellNum.toInt()
                        if (cells > width) width = cells
                        if (row.height.toInt() != -1 && row.height.toInt() != sheet.defaultRowHeight.toInt()) {
                            rHeights[r] = row.heightInPoints * 2.66f
                        }
                    }
                }
                val cWidths = mutableMapOf<Int, Float>()
                for (c in 0 until width) {
                    val w = sheet.getColumnWidth(c)
                    if (w != sheet.defaultColumnWidth) {
                        cWidths[c] = w * 0.05f
                    }
                }
                grids.add(
                    (0..lastRow).map { r ->
                        val row = sheet.getRow(r)
                        (0 until width).map { c -> row?.getCell(c)?.let { formatter.formatCellValue(it) } ?: "" }
                    },
                )
                allMerges.add(sheet.mergedRegions.map { m ->
                    CellMerge(m.firstRow, m.lastRow, m.firstColumn, m.lastColumn)
                })
                allColWidths.add(cWidths)
                allRowHeights.add(rHeights)
            }
            if (names.isEmpty()) return Parsed(listOf("Sheet1"), listOf(listOf(listOf(""))), listOf(emptyList()), listOf(emptyMap()), listOf(emptyMap()))
            return Parsed(names, grids, allMerges, allColWidths, allRowHeights)
        }
    }

    /**
     * Re-opens the original document and writes [edits] (keyed by sheet index, row,
     * col) back into the matching sheets, then returns the serialized bytes. Numeric
     * where the text parses as a number, else string. Cells/rows created on demand.
     */
    fun applyEditsAndSerialize(
        originalBytes: ByteArray,
        edits: Map<Triple<Int, Int, Int>, String>,
        structuralOps: List<StructuralOp> = emptyList(),
        colWidths: Map<Pair<Int, Int>, Float> = emptyMap(),
        rowHeights: Map<Pair<Int, Int>, Float> = emptyMap(),
    ): ByteArray {
        XSSFWorkbook(ByteArrayInputStream(originalBytes)).use { wb ->
            // Replay inserts first so existing rows/columns shift down/right (POI moves
            // their content + styles); the gap left behind is the inserted blank.
            for (op in structuralOps) {
                if (op.sheet < 0 || op.sheet >= wb.numberOfSheets) continue
                val sheet = wb.getSheetAt(op.sheet)
                when (op) {
                    is InsertRow -> {
                        val last = sheet.lastRowNum
                        if (op.at in 0..last) sheet.shiftRows(op.at, last, 1)
                    }
                    is InsertColumn -> {
                        var maxCol = -1
                        for (r in 0..sheet.lastRowNum) {
                            val row = sheet.getRow(r) ?: continue
                            maxCol = maxOf(maxCol, row.lastCellNum - 1)
                        }
                        if (op.at in 0..maxCol) sheet.shiftColumns(op.at, maxCol, 1)
                    }
                }
            }
            for ((key, value) in edits) {
                val (s, r, c) = key
                if (s < 0 || s >= wb.numberOfSheets) continue
                val sheet = wb.getSheetAt(s)
                val row = sheet.getRow(r) ?: sheet.createRow(r)
                val cell = row.getCell(c) ?: row.createCell(c)
                val number = value.toDoubleOrNull()
                if (number != null) cell.setCellValue(number) else cell.setCellValue(value)
            }
            for ((key, dp) in colWidths) {
                val (s, c) = key
                if (s < 0 || s >= wb.numberOfSheets) continue
                wb.getSheetAt(s).setColumnWidth(c, (dp / 0.05f).toInt())
            }
            for ((key, dp) in rowHeights) {
                val (s, r) = key
                if (s < 0 || s >= wb.numberOfSheets) continue
                val sheet = wb.getSheetAt(s)
                val row = sheet.getRow(r) ?: sheet.createRow(r)
                row.heightInPoints = dp / 2.66f
            }
            ByteArrayOutputStream().use { out ->
                wb.write(out)
                return out.toByteArray()
            }
        }
    }
}
