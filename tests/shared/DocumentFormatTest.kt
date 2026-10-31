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
        for (ext in listOf("md", "markdown", "mdown", "mkd", "mkdn", "mdwn", "mdx")) {
            assertEquals(ext, DocumentFormat.MARKDOWN, DocumentFormat.fromExtension(ext))
        }
        for (ext in listOf("txt", "text", "rst", "org")) {
            assertEquals(ext, DocumentFormat.TEXT, DocumentFormat.fromExtension(ext))
        }
    }

    @Test
    fun fromExtension_ignores_case_and_leading_dot() {
        assertEquals(DocumentFormat.PDF, DocumentFormat.fromExtension("PDF"))
        assertEquals(DocumentFormat.PDF, DocumentFormat.fromExtension(".pdf"))
        assertEquals(DocumentFormat.EXCEL, DocumentFormat.fromExtension(".XLSX"))
    }

    @Test
    fun fromExtension_returns_null_for_unknown() {
        assertNull(DocumentFormat.fromExtension("zip"))
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
    fun fromMimeType_maps_text_types() {
        assertEquals(DocumentFormat.MARKDOWN, DocumentFormat.fromMimeType("text/markdown"))
        assertEquals(DocumentFormat.MARKDOWN, DocumentFormat.fromMimeType("text/x-markdown"))
        assertEquals(DocumentFormat.TEXT, DocumentFormat.fromMimeType("text/plain"))
        assertEquals(DocumentFormat.TEXT, DocumentFormat.fromMimeType("text/x-rst"))
    }

    @Test
    fun resolve_prefers_the_file_name_over_plain_text() {
        assertEquals(DocumentFormat.MARKDOWN, DocumentFormat.resolve("text/plain", "README.md"))
        assertEquals(DocumentFormat.TEXT, DocumentFormat.resolve("text/plain", "notes.txt"))
        assertEquals(DocumentFormat.TEXT, DocumentFormat.resolve("text/plain", "document/1234"))
    }

    @Test
    fun resolve_keeps_a_specific_mime_type_and_falls_back_to_the_name() {
        assertEquals(DocumentFormat.PDF, DocumentFormat.resolve("application/pdf", "scan.md"))
        assertEquals(DocumentFormat.MARKDOWN, DocumentFormat.resolve(null, "primary:Download/notes.md"))
        assertEquals(DocumentFormat.MARKDOWN, DocumentFormat.resolve("application/octet-stream", "notes.markdown"))
        assertNull(DocumentFormat.resolve("application/octet-stream", "archive.zip"))
        assertNull(DocumentFormat.resolve(null, null))
    }

    @Test
    fun only_markdown_has_a_preview() {
        assertEquals(listOf(DocumentFormat.MARKDOWN), DocumentFormat.entries.filter { it.hasPreview })
    }

    @Test
    fun fromMimeType_returns_null_for_null_or_unknown() {
        assertNull(DocumentFormat.fromMimeType(null))
        assertNull(DocumentFormat.fromMimeType("application/zip"))
    }
}
