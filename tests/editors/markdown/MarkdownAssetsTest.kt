package dev.kern.editors.markdown

import java.io.File
import java.util.Base64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The bundled emoji table, and inlining local images for the preview. */
class MarkdownAssetsTest {

    private lateinit var dir: File

    @Before
    fun makeDir() {
        dir = File("build/tmp/markdown-assets-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun removeDir() {
        dir.deleteRecursively()
    }

    @Test
    fun bundledEmojiTable_isComplete() {
        val emoji = File("assets/preview/emoji.tsv").inputStream().use(EmojiTable::parse)
        assertTrue(emoji.size > 1800)
        assertEquals("🎉", emoji["tada"])
        assertEquals("👍", emoji["+1"])
    }

    @Test
    fun emojiTable_skipsMalformedLines() {
        val parsed = EmojiTable.parse("ok\t✅\nno-tab\n\tglyph-only\nname-only\t\n".byteInputStream())
        assertEquals(mapOf("ok" to "✅"), parsed)
    }

    @Test
    fun resolver_inlinesLocalImages_includingParentFolders_andEncodedNames() {
        val png = byteArrayOf(1, 2, 3)
        File(dir, "docs").mkdirs()
        File(dir, "logo.png").writeBytes(png)
        File(dir, "docs/my pic.JPG").writeBytes(png)
        val resolver = FileImageResolver(File(dir, "docs"))
        val encoded = Base64.getEncoder().encodeToString(png)
        assertEquals("data:image/png;base64,$encoded", resolver.dataUri("../logo.png"))
        assertEquals("data:image/jpeg;base64,$encoded", resolver.dataUri("my%20pic.JPG"))
        assertEquals("data:image/jpeg;base64,$encoded", resolver.dataUri("my pic.JPG?raw=true"))
    }

    @Test
    fun resolver_refusesMissing_nonImage_andOversizedFiles() {
        File(dir, "notes.txt").writeText("x")
        File(dir, "big.png").writeBytes(ByteArray(11))
        val resolver = FileImageResolver(dir, maxBytes = 10)
        assertNull(resolver.dataUri("missing.png"))
        assertNull(resolver.dataUri("notes.txt"))
        assertNull(resolver.dataUri("big.png"))
    }

    @Test
    fun mimeFor_knowsImageTypesOnly() {
        assertEquals("image/svg+xml", FileImageResolver.mimeFor("diagram.SVG"))
        assertEquals("image/webp", FileImageResolver.mimeFor("a/b.webp"))
        assertNull(FileImageResolver.mimeFor("notes.md"))
        assertNull(FileImageResolver.mimeFor("no-extension"))
    }
}
