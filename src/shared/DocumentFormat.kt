package dev.kern.shared

/**
 * Every file format Kern knows how to route. The browser and the "Open with"
 * intent handler both map an incoming file to one of these, which then selects
 * the editor destination in the NavHost.
 */
enum class DocumentFormat(
    val label: String,
    val extensions: List<String>,
    val mimeTypes: List<String>,
) {
    CSV(
        label = "CSV",
        extensions = listOf("csv"),
        mimeTypes = listOf("text/csv", "text/comma-separated-values"),
    ),
    EXCEL(
        label = "Excel",
        extensions = listOf("xlsx", "xls"),
        mimeTypes = listOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
        ),
    ),
    WORD(
        label = "Word",
        extensions = listOf("docx", "doc"),
        mimeTypes = listOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
        ),
    ),
    POWERPOINT(
        label = "PowerPoint",
        extensions = listOf("pptx", "ppt"),
        mimeTypes = listOf(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
        ),
    ),
    PDF(
        label = "PDF",
        extensions = listOf("pdf"),
        mimeTypes = listOf("application/pdf"),
    ),
    EPUB(
        label = "EPUB",
        extensions = listOf("epub"),
        mimeTypes = listOf("application/epub+zip"),
    );

    companion object {
        fun fromExtension(extension: String): DocumentFormat? {
            val normalized = extension.lowercase().removePrefix(".")
            return entries.firstOrNull { normalized in it.extensions }
        }

        fun fromMimeType(mimeType: String?): DocumentFormat? {
            if (mimeType == null) return null
            return entries.firstOrNull { mimeType in it.mimeTypes }
        }
    }
}
