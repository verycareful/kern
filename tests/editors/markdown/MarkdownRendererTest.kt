package dev.kern.editors.markdown

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The HTML the preview gets for each Markdown construct it supports. */
class MarkdownRendererTest {

    private val emoji = File("assets/preview/emoji.tsv").inputStream().use(EmojiTable::parse)
    private val renderer = MarkdownRenderer(emoji)

    private fun html(source: String) = renderer.render(source).body

    @Test
    fun table_withAlignment() {
        val out = html("| a | b |\n| :-- | --: |\n| 1 | 2 |")
        assertTrue(out, out.contains("<th align=\"left\">a</th>"))
        assertTrue(out, out.contains("<td align=\"right\">2</td>"))
    }

    @Test
    fun taskList_strikethrough_andAutolink() {
        val out = html("- [x] done\n- [ ] todo\n\n~~gone~~ and www.example.com")
        assertTrue(out, out.contains("<input type=\"checkbox\" disabled=\"\" checked=\"\"> done"))
        assertTrue(out, out.contains("<input type=\"checkbox\" disabled=\"\"> todo"))
        assertTrue(out, out.contains("<del>gone</del>"))
        assertTrue(out, out.contains("<a href=\"http://www.example.com\">www.example.com</a>"))
    }

    @Test
    fun alerts_getTheirTypeAndTitle() {
        val out = html("> [!WARNING]\n> Careful.")
        assertTrue(out, out.contains("markdown-alert markdown-alert-warning"))
        assertTrue(out, out.contains("<p class=\"markdown-alert-title\">Warning</p>"))
    }

    @Test
    fun headings_getAnchors_andFootnotesLink() {
        val out = html("## Getting Started\n\nText[^1].\n\n[^1]: Note.")
        assertTrue(out, out.contains("<h2 id=\"getting-started\">"))
        assertTrue(out, out.contains("href=\"#fn-1\""))
        assertTrue(out, out.contains("<li id=\"fn-1\">"))
    }

    @Test
    fun frontMatter_isATable() {
        val out = html("---\ntitle: Hello\ntags: [a, b]\n---\n\nBody")
        assertTrue(out, out.startsWith("<table class=\"front-matter\"><thead><tr><th>title</th><th>tags</th>"))
        assertTrue(out, out.contains("<td>Hello</td>"))
        assertTrue(out, out.contains("<p>Body</p>"))
    }

    @Test
    fun inlineAndDisplayMaths_keepTheirTex() {
        val rendered = renderer.render("Inline \$a_1 * b_2\$ and\n\n\$\$\n\\frac{1}{2}\n\$\$")
        assertTrue(rendered.body, rendered.body.contains("<span class=\"math math-inline\">a_1 * b_2</span>"))
        assertTrue(rendered.body, rendered.body.contains("<span class=\"math math-display\">\\frac{1}{2}</span>"))
        assertTrue(rendered.hasMath)
    }

    @Test
    fun mathFence_isDisplayMaths() {
        val out = html("```math\nx^2\n```")
        assertTrue(out, out.contains("<div class=\"math math-display\">x^2\n</div>"))
    }

    @Test
    fun prices_andEscapedDollars_stayText() {
        val rendered = renderer.render("It costs \$5 and \$10. Also \\\$x\\\$ and \$ spaced \$.")
        assertFalse(rendered.body, rendered.hasMath)
        assertTrue(rendered.body, rendered.body.contains("It costs \$5 and \$10."))
    }

    @Test
    fun dollarsInsideCode_areCode() {
        val rendered = renderer.render("`\$HOME` and `\$x\$`")
        assertFalse(rendered.hasMath)
        assertTrue(rendered.body, rendered.body.contains("<code>\$HOME</code>"))
    }

    @Test
    fun mermaidFence_isADiagram_andOtherFencesAreCode() {
        val rendered = renderer.render("```mermaid\ngraph LR\nA-->B\n```\n\n```kotlin\nval x = 1 < 2\n```\n\n```\nplain\n```")
        assertTrue(rendered.body, rendered.body.contains("<pre class=\"mermaid\">graph LR\nA--&gt;B\n</pre>"))
        assertTrue(rendered.body, rendered.body.contains("<pre><code class=\"language-kotlin\">val x = 1 &lt; 2\n</code></pre>"))
        assertTrue(rendered.body, rendered.body.contains("<pre><code>plain\n</code></pre>"))
        assertTrue(rendered.hasMermaid)
        assertTrue(rendered.hasCode)
    }

    @Test
    fun plainDocument_needsNoScripts() {
        val rendered = renderer.render("# Hi\n\nJust text.")
        assertFalse(rendered.hasMath || rendered.hasCode || rendered.hasMermaid)
    }

    @Test
    fun emojiShortcodes_becomeEmoji_unknownOnesStay() {
        val out = html("Ship it :rocket: :+1: but :not_an_emoji: and 10:30:00")
        assertTrue(out, out.contains("Ship it 🚀 👍 but :not_an_emoji: and 10:30:00"))
    }

    @Test
    fun emojiShortcodes_doNotBreakUrls() {
        val out = html("See https://example.com:8080/path")
        assertTrue(out, out.contains("<a href=\"https://example.com:8080/path\">"))
    }

    @Test
    fun rawHtml_passesThrough() {
        val out = html("<details>\n<summary>More</summary>\n\nInside\n\n</details>\n\nPress <kbd>Ctrl</kbd>")
        assertTrue(out, out.contains("<details>\n<summary>More</summary>"))
        assertTrue(out, out.contains("<kbd>Ctrl</kbd>"))
    }

    @Test
    fun relativeImages_goThroughTheResolver_othersDoNot() {
        val asked = mutableListOf<String>()
        val resolver = MarkdownRenderer.ImageResolver { src -> asked += src; "data:image/png;base64,AAAA" }
        val out = renderer.render(
            "![a](pics/a.png) ![b](https://example.com/b.png) <img src=\"c.gif\" width=\"10\"> ![d](data:image/gif;base64,R0)",
            resolver,
        ).body
        assertEquals(listOf("pics/a.png", "c.gif"), asked)
        assertTrue(out, out.contains("<img src=\"data:image/png;base64,AAAA\" alt=\"a\">"))
        assertTrue(out, out.contains("src=\"https://example.com/b.png\""))
        assertTrue(out, out.contains("<img src=\"data:image/png;base64,AAAA\" width=\"10\">"))
    }

    @Test
    fun documentRelative_rules() {
        assertTrue(MarkdownRenderer.isDocumentRelative("img.png"))
        assertTrue(MarkdownRenderer.isDocumentRelative("../img.png"))
        assertTrue(MarkdownRenderer.isDocumentRelative("/docs/img.png"))
        assertFalse(MarkdownRenderer.isDocumentRelative("//cdn.example.com/a.png"))
        assertFalse(MarkdownRenderer.isDocumentRelative("https://example.com/a.png"))
        assertFalse(MarkdownRenderer.isDocumentRelative("data:image/png;base64,AA"))
        assertFalse(MarkdownRenderer.isDocumentRelative(""))
    }

    @Test
    fun bundledEmojiTable_isComplete() {
        assertTrue(emoji.size > 1800)
        assertEquals("🎉", emoji["tada"])
    }
}
