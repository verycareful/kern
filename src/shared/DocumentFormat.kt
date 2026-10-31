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
    ),
    MARKDOWN(
        label = "Markdown",
        extensions = listOf("md", "markdown", "mdown", "mkd", "mkdn", "mdwn", "mdx"),
        mimeTypes = listOf("text/markdown", "text/x-markdown"),
    ),
    TEXT(
        label = "Text",
        extensions = listOf("txt", "text", "rst", "org"),
        mimeTypes = listOf("text/plain", "text/x-rst", "text/prs.fallenstein.rst", "text/org", "text/x-org"),
    );

    /** Whether the editor offers a rendered preview alongside the source. */
    val hasPreview: Boolean get() = this == MARKDOWN

    companion object {
        fun fromExtension(extension: String): DocumentFormat? {
            val normalized = extension.lowercase().removePrefix(".")
            return entries.firstOrNull { normalized in it.extensions }
        }

        fun fromMimeType(mimeType: String?): DocumentFormat? {
            if (mimeType == null) return null
            return entries.firstOrNull { mimeType in it.mimeTypes }
        }

        /**
         * Resolves an incoming file from its MIME type and name together. A specific
         * MIME type wins; `text/plain` says only that the bytes are text, so a file
         * name with a known extension (`notes.md` sent as `text/plain`) decides.
         */
        fun resolve(mimeType: String?, fileName: String?): DocumentFormat? {
            val byMime = fromMimeType(mimeType)
            val byName = fileName?.substringAfterLast('.', "")?.let(::fromExtension)
            return if (byMime == TEXT && byName != null) byName else byMime ?: byName
        }
    }
}
