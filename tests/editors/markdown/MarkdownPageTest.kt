package dev.kern.editors.markdown

import java.io.File
import java.util.Base64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The preview page's security policy, assets and theme, and local image inlining. */
class MarkdownPageTest {

    private val theme = MarkdownPage.Theme(
        dark = false, background = "#FFFFFF", text = "#111111", muted = "#666666",
        border = "#DDDDDD", surface = "#F4F4F4", accent = "#3D5AFE",
    )

    private fun page(math: Boolean = false, code: Boolean = false, mermaid: Boolean = false, dark: Boolean = false) =
        MarkdownPage.build(MarkdownRenderer.Rendered("<p>x</p>", math, code, mermaid), theme.copy(dark = dark))

    @Test
    fun policy_allowsOnlyBundledScripts_andDataImages() {
        val html = page()
        assertTrue(html.contains("script-src file:;"))
        assertTrue(html.contains("img-src data:;"))
        assertTrue(html.contains("default-src 'none'"))
        assertFalse(html.contains("unsafe-eval"))
        assertFalse(Regex("script-src[^;]*unsafe-inline").containsMatchIn(html))
    }

    @Test
    fun scripts_areIncludedOnlyWhenTheBodyNeedsThem() {
        val plain = page()
        assertFalse(plain.contains("katex.min.js") || plain.contains("highlight.min.js") || plain.contains("mermaid.min.js"))
        assertTrue(plain.contains("<script src=\"preview.js\"></script>"))
        val all = page(math = true, code = true, mermaid = true)
        assertTrue(all.contains("katex/katex.min.js") && all.contains("katex/katex.min.css"))
        assertTrue(all.contains("highlight/highlight.min.js") && all.contains("highlight/github.min.css"))
        assertTrue(all.contains("mermaid/mermaid.min.js"))
    }

    @Test
    fun darkTheme_marksThePage_andUsesTheDarkCodeColours() {
        val html = page(code = true, dark = true)
        assertTrue(html.contains("data-theme=\"dark\""))
        assertTrue(html.contains("highlight/github-dark.min.css"))
        assertTrue(html.contains("--accent:#3D5AFE;"))
    }

    @Test
    fun everyReferencedAsset_isBundled() {
        val html = page(math = true, code = true, mermaid = true) + page(code = true, dark = true)
        val refs = Regex("(?:src|href)=\"([^\"]+)\"").findAll(html).map { it.groupValues[1] }.toSet()
        for (ref in refs) assertTrue(ref, File("assets/preview/$ref").isFile)
    }

    private lateinit var dir: File

    @Before
    fun makeDir() {
        dir = File("build/tmp/markdown-page-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun removeDir() {
        dir.deleteRecursively()
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
}
