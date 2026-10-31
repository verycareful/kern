package dev.kern.settings

/**
 * One entry of the open source notices bundled under `assets/notices/`: a library
 * group or a bundled asset, the licence it is used under, and the licence texts
 * that apply to it.
 */
internal data class Notice(
    val title: String,
    val subtitle: String,
    val license: String,
    val url: String,
    val textIds: List<String>,
)

/**
 * Reads the notices the build writes into the app's assets: `notices/index.tsv`,
 * one tab-separated entry per line (title, subtitle, licence, URL, comma-separated
 * text ids), and `notices/texts/<id>.txt`, each distinct licence text once.
 */
internal object Notices {
    const val INDEX_PATH = "notices/index.tsv"
    private const val FIELDS = 5

    fun textPath(id: String): String = "notices/texts/$id.txt"

    /** Entries in index order; a malformed line is skipped rather than failing the list. */
    fun parse(index: String): List<Notice> = index.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size < FIELDS) {
                null
            } else {
                Notice(
                    title = fields[0],
                    subtitle = fields[1],
                    license = fields[2],
                    url = fields[3],
                    textIds = fields[4].split(',').map(String::trim).filter(String::isNotEmpty),
                )
            }
        }
        .toList()

    /** A licence text split at blank lines, so a long text can be shown paragraph by paragraph. */
    fun paragraphs(text: String): List<String> =
        text.replace("\r\n", "\n").split(Regex("\n\\s*\n")).map(String::trimEnd).filter(String::isNotBlank)
}
