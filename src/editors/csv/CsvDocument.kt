package dev.kern.editors.csv

import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import java.io.StringReader
import java.io.StringWriter

/**
 * Pure parsing/serialization for CSV, backed by OpenCSV. No Android or Compose
 * dependencies, so it stays unit-testable on its own.
 */
object CsvDocument {

    /**
     * Parses CSV text into a rectangular grid (all rows padded to the widest row,
     * with a minimum of one cell) so the editor can render a uniform table.
     */
    fun parse(text: String): List<List<String>> {
        val raw: List<Array<String>> = CSVReader(StringReader(text)).use { it.readAll() }
        if (raw.isEmpty()) return listOf(listOf(""))
        val width = maxOf(1, raw.maxOf { it.size })
        return raw.map { row -> List(width) { c -> row.getOrElse(c) { "" } } }
    }

    /**
     * Serializes the grid back to CSV. Quotes are applied only where needed
     * (commas, quotes, newlines) rather than around every field.
     */
    fun toCsv(rows: List<List<String>>): String {
        val writer = StringWriter()
        CSVWriter(writer, CSVWriter.DEFAULT_SEPARATOR, CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, "\n").use { csv ->
            rows.forEach { row -> csv.writeNext(row.toTypedArray(), false) }
        }
        return writer.toString()
    }

    /** Spreadsheet-style column label for a zero-based index: 0 -> A, 25 -> Z, 26 -> AA. */
    fun columnLabel(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + (i % 26)))
            i = i / 26 - 1
        }
        return sb.toString()
    }
}
