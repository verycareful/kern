package dev.kern.editors.text

import dev.kern.editors.text.MarkdownHighlighter.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The source colouring finds the syntax it names and leaves code alone. */
class MarkdownHighlighterTest {

    private fun kindsOf(text: String): List<Pair<String, Kind>> =
        MarkdownHighlighter.spans(text).map { text.substring(it.start, it.end) to it.kind }

    @Test
    fun heading_coversTheWholeLine() {
        assertEquals(listOf("## Install" to Kind.HEADING), kindsOf("## Install"))
    }

    @Test
    fun hashWithoutSpace_isNotAHeading() {
        assertTrue(kindsOf("#hashtag").none { it.second == Kind.HEADING })
    }

    @Test
    fun inlineMarks_areFound() {
        val kinds = kindsOf("a **b** *c* ~~d~~ `e` [f](g) \$x\$ <kbd>").toSet()
        assertTrue(("**b**" to Kind.STRONG) in kinds)
        assertTrue(("*c*" to Kind.EMPHASIS) in kinds)
        assertTrue(("~~d~~" to Kind.STRIKE) in kinds)
        assertTrue(("`e`" to Kind.CODE) in kinds)
        assertTrue(("[f](g)" to Kind.LINK) in kinds)
        assertTrue(("\$x\$" to Kind.MATH) in kinds)
        assertTrue(("<kbd>" to Kind.HTML) in kinds)
    }

    @Test
    fun markupInsideInlineCode_isNotColoured() {
        assertEquals(listOf("`**not bold**`" to Kind.CODE), kindsOf("`**not bold**`"))
    }

    @Test
    fun fencedCode_isOneSpan_andItsContentsAreIgnored() {
        val text = "intro\n```kotlin\n# not a heading\n**x**\n```\nafter **y**"
        val kinds = kindsOf(text)
        assertEquals("```kotlin\n# not a heading\n**x**\n```" to Kind.CODE_BLOCK, kinds[0])
        assertEquals(listOf("**y**" to Kind.STRONG), kinds.drop(1))
    }

    @Test
    fun unclosedFence_runsToTheEnd() {
        val text = "```\ncode"
        assertEquals(listOf(text to Kind.CODE_BLOCK), kindsOf(text))
    }

    @Test
    fun listMarkers_andTaskBoxes_areMarked() {
        assertEquals("- [x]" to Kind.LIST_MARKER, kindsOf("- [x] done").first())
        assertEquals("12." to Kind.LIST_MARKER, kindsOf("12. twelfth").first())
    }

    @Test
    fun quote_andRule_areMarked() {
        assertEquals("> said" to Kind.QUOTE, kindsOf("> said").first())
        assertEquals(listOf("---" to Kind.RULE), kindsOf("---"))
    }

    @Test
    fun veryLargeText_isLeftPlain() {
        val text = "# h\n".repeat(MarkdownHighlighter.MAX_HIGHLIGHT_CHARS / 4 + 1)
        assertTrue(MarkdownHighlighter.spans(text).isEmpty())
    }
}
