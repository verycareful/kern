package dev.kern.shared.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Zero-based column index to spreadsheet label (A, B, ... Z, AA, AB, ...). */
class SpreadsheetLabelTest {

    @Test
    fun single_letter_columns() {
        assertEquals("A", spreadsheetColumnLabel(0))
        assertEquals("B", spreadsheetColumnLabel(1))
        assertEquals("Z", spreadsheetColumnLabel(25))
    }

    @Test
    fun two_letter_columns() {
        assertEquals("AA", spreadsheetColumnLabel(26))
        assertEquals("AB", spreadsheetColumnLabel(27))
        assertEquals("AZ", spreadsheetColumnLabel(51))
        assertEquals("BA", spreadsheetColumnLabel(52))
        assertEquals("ZZ", spreadsheetColumnLabel(701))
    }

    @Test
    fun three_letter_columns() {
        assertEquals("AAA", spreadsheetColumnLabel(702))
    }
}
