package dev.kern.editors.markdown

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The preview page's security policy, assets and theme. */
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

    @Test
    fun previewStylesheet_usesKernFonts_andBundlesThem() {
        val css = File("assets/preview/preview.css").readText()
        assertTrue(css.contains("font: 16px/1.6 \"Outfit\""))
        assertTrue(css.contains("font-family: \"IBM Plex Mono\""))
        val urls = Regex("url\\(([^)]+)\\)").findAll(css).map { it.groupValues[1] }.toList()
        assertTrue(urls.isNotEmpty())
        for (url in urls) assertTrue(url, File("assets/preview/$url").isFile)
    }
}
