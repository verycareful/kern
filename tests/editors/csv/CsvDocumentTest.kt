package dev.kern.editors.csv

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvDocumentTest {

    @Test
    fun parse_padsRaggedRowsToWidestRow() {
        val grid = CsvDocument.parse("a,b,c\nd\ne,f")
        assertEquals(listOf("a", "b", "c"), grid[0])
        assertEquals(listOf("d", "", ""), grid[1])
        assertEquals(listOf("e", "f", ""), grid[2])
    }

    @Test
    fun parse_emptyInputYieldsSingleEmptyCell() {
        assertEquals(listOf(listOf("")), CsvDocument.parse(""))
    }

    @Test
    fun toCsv_quotesOnlyFieldsThatNeedIt() {
        val csv = CsvDocument.toCsv(listOf(listOf("a,b", "plain", "has\"quote")))
        // Field with a comma and the field with a quote are quoted; the plain one is not.
        assertEquals("\"a,b\",plain,\"has\"\"quote\"\n", csv)
    }

    @Test
    fun parseThenToCsv_isLosslessForSimpleGrid() {
        val grid = listOf(listOf("1", "2"), listOf("3", "4"))
        assertEquals(grid, CsvDocument.parse(CsvDocument.toCsv(grid)))
    }

    @Test
    fun columnLabel_followsSpreadsheetSequence() {
        assertEquals("A", CsvDocument.columnLabel(0))
        assertEquals("Z", CsvDocument.columnLabel(25))
        assertEquals("AA", CsvDocument.columnLabel(26))
        assertEquals("AB", CsvDocument.columnLabel(27))
        assertEquals("ZZ", CsvDocument.columnLabel(701))
        assertEquals("AAA", CsvDocument.columnLabel(702))
    }
}
