package dev.kern.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Routing logic that the browser and "Open with" handler depend on. */
class DocumentFormatTest {

    @Test
    fun fromExtension_maps_known_extensions() {
        assertEquals(DocumentFormat.CSV, DocumentFormat.fromExtension("csv"))
        assertEquals(DocumentFormat.EXCEL, DocumentFormat.fromExtension("xlsx"))
        assertEquals(DocumentFormat.EXCEL, DocumentFormat.fromExtension("xls"))
        assertEquals(DocumentFormat.WORD, DocumentFormat.fromExtension("docx"))
        assertEquals(DocumentFormat.WORD, DocumentFormat.fromExtension("doc"))
        assertEquals(DocumentFormat.POWERPOINT, DocumentFormat.fromExtension("pptx"))
        assertEquals(DocumentFormat.PDF, DocumentFormat.fromExtension("pdf"))
        assertEquals(DocumentFormat.EPUB, DocumentFormat.fromExtension("epub"))
    }

    @Test
    fun fromExtension_ignores_case_and_leading_dot() {
        assertEquals(DocumentFormat.PDF, DocumentFormat.fromExtension("PDF"))
        assertEquals(DocumentFormat.PDF, DocumentFormat.fromExtension(".pdf"))
        assertEquals(DocumentFormat.EXCEL, DocumentFormat.fromExtension(".XLSX"))
    }

    @Test
    fun fromExtension_returns_null_for_unknown() {
        assertNull(DocumentFormat.fromExtension("txt"))
        assertNull(DocumentFormat.fromExtension(""))
    }

    @Test
    fun fromMimeType_maps_known_types() {
        assertEquals(DocumentFormat.PDF, DocumentFormat.fromMimeType("application/pdf"))
        assertEquals(DocumentFormat.CSV, DocumentFormat.fromMimeType("text/csv"))
        assertEquals(DocumentFormat.EPUB, DocumentFormat.fromMimeType("application/epub+zip"))
        assertEquals(
            DocumentFormat.EXCEL,
            DocumentFormat.fromMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        )
    }

    @Test
    fun fromMimeType_returns_null_for_null_or_unknown() {
        assertNull(DocumentFormat.fromMimeType(null))
        assertNull(DocumentFormat.fromMimeType("application/zip"))
    }
}
