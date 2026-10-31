package dev.kern.editors.markdown

import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.Base64

/** The `:shortcode:` table bundled at `assets/preview/emoji.tsv`: one `name<TAB>emoji` per line. */
object EmojiTable {
    const val ASSET_PATH = "preview/emoji.tsv"

    fun parse(input: InputStream): Map<String, String> = input.bufferedReader(Charsets.UTF_8).useLines { lines ->
        lines.mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0 || tab == line.length - 1) null else line.substring(0, tab) to line.substring(tab + 1)
        }.toMap()
    }
}

/**
 * Resolves image paths written relative to a Markdown file against the folder the
 * file sits in, and inlines each image as a `data:` URI. The preview page may only
 * load `data:` images, so this is the one way a local picture reaches it. `../`
 * paths work as they do on disk; a leading `/` is read from [baseDir], since the
 * repository root it names elsewhere is unknown here. Files that are missing, too
 * large or not an image type resolve to null and the page shows a placeholder.
 */
class FileImageResolver(
    private val baseDir: File,
    private val maxBytes: Long = MAX_IMAGE_BYTES,
) : MarkdownRenderer.ImageResolver {

    override fun dataUri(src: String): String? {
        val path = runCatching { URLDecoder.decode(src.substringBefore('#').substringBefore('?').replace("+", "%2B"), "UTF-8") }
            .getOrNull() ?: return null
        val mime = mimeFor(path) ?: return null
        val file = runCatching { File(baseDir, path.removePrefix("/")).canonicalFile }.getOrNull() ?: return null
        if (!file.isFile || file.length() > maxBytes) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
    }

    companion object {
        /** Large enough for a photo, small enough that a page of them stays responsive. */
        const val MAX_IMAGE_BYTES = 15L * 1024 * 1024

        private val MIME_BY_EXTENSION = mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "svg" to "image/svg+xml",
            "bmp" to "image/bmp",
            "avif" to "image/avif",
            "ico" to "image/x-icon",
        )

        internal fun mimeFor(path: String): String? =
            MIME_BY_EXTENSION[path.substringAfterLast('.', "").lowercase()]
    }
}
