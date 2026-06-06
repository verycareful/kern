package dev.kern.editors.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * EPUB reader/editor. An EPUB is a ZIP whose `META-INF/container.xml` points at an
 * OPF package file; the OPF `<spine>` lists the XHTML chapters in reading order.
 *
 * Editing mirrors the Word editor: each chapter's block-level elements (h1-h6, p,
 * li) are read as plain text tagged with a coarse style. Saving re-opens the
 * ORIGINAL zip, copies every entry byte-for-byte except edited chapter files, and
 * for those sets only the edited blocks' text via Jsoup (preserving every other
 * block's inline markup). An edited block's intra-block formatting collapses to
 * plain text (the same tradeoff as Word paragraphs).
 *
 * No Android/Compose dependencies, so it stays testable.
 */
object EpubDocument {

    /** Thrown for malformed or unsupported (DRM) EPUBs. */
    class EpubException(message: String) : Exception(message)

    enum class Style { TITLE, HEADING1, HEADING2, BODY }

    /** One editable block. [index] is its ordinal in the chapter's full block selection. */
    data class Block(val text: String, val style: Style, val index: Int)

    /** [path] is the chapter's zip entry name (used when saving). */
    data class Chapter(val path: String, val title: String, val blocks: List<Block>)

    data class Parsed(val title: String, val chapters: List<Chapter>)

    private val BLOCK_SELECTOR = "h1, h2, h3, h4, h5, h6, p, li"

    fun read(bytes: ByteArray): Parsed {
        val entries = readZip(bytes)
        if (isDrmProtected(entries)) {
            throw EpubException("This EPUB is DRM-protected and cannot be edited.")
        }
        val opfPath = opfPath(entries)
        val opf = xml(entries[opfPath] ?: throw EpubException("Invalid EPUB: OPF not found ($opfPath)"))
        val title = opf.getElementsByTag("dc:title").firstOrNull()?.text()?.takeIf { it.isNotBlank() } ?: ""

        val paths = resolveSpinePaths(entries)
        if (paths.isEmpty()) throw EpubException("This EPUB has no readable chapters.")
        val titles = runCatching { tocTitles(entries, opfPath, opf) }.getOrDefault(emptyMap())

        val chapters = paths.mapIndexed { i, path ->
            val blocks = runCatching { parseBlocks(entries[path] ?: ByteArray(0)) }.getOrDefault(emptyList())
            Chapter(path, titles[path] ?: "Chapter ${i + 1}", blocks)
        }
        return Parsed(title, chapters)
    }

    /**
     * Applies [edits] (keyed by chapter index -> block index -> new text) onto the
     * original EPUB and returns the new zip bytes. Untouched entries are preserved
     * exactly; `mimetype` is rewritten first and STORED as the spec requires.
     */
    fun applyEditsAndSerialize(originalBytes: ByteArray, edits: Map<Pair<Int, Int>, String>): ByteArray {
        val entries = readZip(originalBytes)
        if (edits.isNotEmpty()) {
            val paths = resolveSpinePaths(entries)
            val byChapter = edits.entries.groupBy({ it.key.first }, { it.key.second to it.value })
            for ((chapterIndex, blockEdits) in byChapter) {
                val path = paths.getOrNull(chapterIndex) ?: continue
                val original = entries[path] ?: continue
                entries[path] = applyChapterEdits(original, blockEdits.toMap())
            }
        }
        return writeZip(entries)
    }

    // --- parsing ---------------------------------------------------------------

    private fun parseBlocks(xhtml: ByteArray): List<Block> {
        val doc = Jsoup.parse(String(xhtml, Charsets.UTF_8))
        val elements = doc.body().select(BLOCK_SELECTOR)
        val out = ArrayList<Block>()
        elements.forEachIndexed { i, el ->
            val text = el.text().trim()
            if (text.isNotEmpty()) out.add(Block(text, styleFor(el.tagName()), i))
        }
        return out
    }

    private fun applyChapterEdits(xhtml: ByteArray, blockEdits: Map<Int, String>): ByteArray {
        val doc = Jsoup.parse(String(xhtml, Charsets.UTF_8))
        val elements = doc.body().select(BLOCK_SELECTOR)
        for ((idx, text) in blockEdits) {
            if (idx in 0 until elements.size) elements[idx].text(text)
        }
        doc.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .charset("UTF-8")
            .prettyPrint(false)
        val html = doc.outerHtml()
        val withProlog = if (html.trimStart().startsWith("<?xml")) html
        else "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n$html"
        return withProlog.toByteArray(Charsets.UTF_8)
    }

    private fun styleFor(tag: String): Style = when (tag.lowercase()) {
        "h1" -> Style.TITLE
        "h2" -> Style.HEADING1
        "h3" -> Style.HEADING2
        else -> Style.BODY // h4-h6, p, li
    }

    /** Ordered list of chapter zip entry paths from the OPF spine. */
    private fun resolveSpinePaths(entries: Map<String, ByteArray>): List<String> {
        val opfPath = opfPath(entries)
        val opfDir = opfPath.substringBeforeLast('/', "")
        val opf = xml(entries[opfPath] ?: throw EpubException("Invalid EPUB: OPF not found ($opfPath)"))

        val idToHref = HashMap<String, String>()
        for (item in opf.select("manifest > item")) {
            val id = item.attr("id")
            val href = item.attr("href")
            if (id.isNotBlank() && href.isNotBlank()) idToHref[id] = href
        }
        val paths = ArrayList<String>()
        for (itemref in opf.select("spine > itemref")) {
            val href = idToHref[itemref.attr("idref")] ?: continue
            val resolved = resolvePath(opfDir, href)
            if (entries.containsKey(resolved)) paths.add(resolved)
        }
        return paths
    }

    /** Best-effort map of chapter zip path -> TOC label (EPUB3 nav, then EPUB2 NCX). */
    private fun tocTitles(entries: Map<String, ByteArray>, opfPath: String, opf: Document): Map<String, String> {
        val opfDir = opfPath.substringBeforeLast('/', "")

        val navItem = opf.select("manifest > item")
            .firstOrNull { it.attr("properties").split(" ").contains("nav") }
        if (navItem != null) {
            val navPath = resolvePath(opfDir, navItem.attr("href"))
            entries[navPath]?.let { bytes ->
                val navDir = navPath.substringBeforeLast('/', "")
                val nav = Jsoup.parse(String(bytes, Charsets.UTF_8))
                val map = LinkedHashMap<String, String>()
                for (a in nav.select("nav a[href]")) {
                    val label = a.text().trim()
                    if (label.isEmpty()) continue
                    map.putIfAbsent(resolvePath(navDir, a.attr("href")), label)
                }
                if (map.isNotEmpty()) return map
            }
        }

        val ncxItem = opf.select("manifest > item")
            .firstOrNull { it.attr("media-type") == "application/x-dtbncx+xml" }
        if (ncxItem != null) {
            val ncxPath = resolvePath(opfDir, ncxItem.attr("href"))
            entries[ncxPath]?.let { bytes ->
                val ncxDir = ncxPath.substringBeforeLast('/', "")
                val ncx = xml(bytes)
                val map = LinkedHashMap<String, String>()
                for (np in ncx.select("navpoint")) {
                    val label = np.select("navlabel > text").firstOrNull()?.text()?.trim() ?: continue
                    val src = np.select("content").firstOrNull()?.attr("src") ?: continue
                    if (label.isEmpty() || src.isEmpty()) continue
                    map.putIfAbsent(resolvePath(ncxDir, src), label)
                }
                return map
            }
        }
        return emptyMap()
    }

    private fun opfPath(entries: Map<String, ByteArray>): String {
        val container = entries["META-INF/container.xml"]
            ?: throw EpubException("Invalid EPUB: missing META-INF/container.xml")
        val rootfile = xml(container).select("rootfile").firstOrNull()
            ?: throw EpubException("Invalid EPUB: no rootfile in container.xml")
        return rootfile.attr("full-path").takeIf { it.isNotBlank() }
            ?: throw EpubException("Invalid EPUB: rootfile has no full-path")
    }

    /** Adobe-style DRM; a lone encryption.xml that only obfuscates fonts is allowed. */
    private fun isDrmProtected(entries: Map<String, ByteArray>): Boolean {
        if (entries.containsKey("META-INF/rights.xml")) return true
        val enc = entries["META-INF/encryption.xml"] ?: return false
        val s = String(enc, Charsets.UTF_8)
        val fontOnly = s.contains("embedding") || s.contains("adobe.com/pdf/enc")
        return !fontOnly
    }

    // --- path helpers ----------------------------------------------------------

    /** Resolves an OPF/TOC href (relative, possibly percent-encoded, with fragment) to a zip entry name. */
    private fun resolvePath(baseDir: String, href: String): String {
        val clean = href.substringBefore('#')
        val decoded = runCatching { URLDecoder.decode(clean, "UTF-8") }.getOrDefault(clean)
        val combined = if (baseDir.isEmpty()) decoded else "$baseDir/$decoded"
        val parts = ArrayList<String>()
        for (seg in combined.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    private fun xml(bytes: ByteArray): Document =
        Jsoup.parse(String(bytes, Charsets.UTF_8), "", Parser.xmlParser())

    // --- zip i/o ---------------------------------------------------------------

    private fun readZip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) map[entry.name] = zis.readBytes()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (map.isEmpty()) throw EpubException("Not a valid EPUB (empty or unreadable archive).")
        return map
    }

    private fun writeZip(entries: LinkedHashMap<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            // The EPUB spec requires `mimetype` to be the first entry and STORED.
            entries["mimetype"]?.let { mt ->
                val e = ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mt.size.toLong()
                    compressedSize = mt.size.toLong()
                    crc = CRC32().apply { update(mt) }.value
                }
                zos.putNextEntry(e)
                zos.write(mt)
                zos.closeEntry()
            }
            for ((name, data) in entries) {
                if (name == "mimetype") continue
                zos.putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
                zos.write(data)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
