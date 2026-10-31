package dev.kern.editors.text

import dev.kern.editors.text.MarkdownFormatting.Inline
import dev.kern.editors.text.MarkdownFormatting.LineMark
import dev.kern.editors.text.MarkdownFormatting.TextEdit
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Markdown toolbar edits: the resulting text and where the selection lands. */
class MarkdownFormattingTest {

    /** Applies [edit] and renders the result with `[` `]` around the new selection. */
    private fun TextEdit.show(text: String): String {
        val out = applyTo(text)
        return out.substring(0, selectionStart) + "[" + out.substring(selectionStart, selectionEnd) + "]" + out.substring(selectionEnd)
    }

    @Test
    fun bold_wrapsTheSelection_andKeepsItSelected() {
        val text = "make this loud"
        assertEquals("make **[this]** loud", MarkdownFormatting.inline(text, 5, 9, Inline.BOLD).show(text))
    }

    @Test
    fun bold_withoutSelection_insertsASelectedPlaceholder() {
        assertEquals("a **[bold text]**", MarkdownFormatting.inline("a ", 2, 2, Inline.BOLD).show("a "))
    }

    @Test
    fun bold_onBoldText_unwrapsIt() {
        val text = "make **this** loud"
        assertEquals("make [this] loud", MarkdownFormatting.inline(text, 7, 11, Inline.BOLD).show(text))
        assertEquals("make [this] loud", MarkdownFormatting.inline(text, 5, 13, Inline.BOLD).show(text))
    }

    @Test
    fun italic_insideBold_wrapsRatherThanUnwrapping() {
        val text = "**word**"
        assertEquals("***[word]***", MarkdownFormatting.inline(text, 2, 6, Inline.ITALIC).show(text))
    }

    @Test
    fun code_andStrikethrough_useTheirMarkers() {
        assertEquals("`[x]`", MarkdownFormatting.inline("x", 0, 1, Inline.CODE).show("x"))
        assertEquals("~~[x]~~", MarkdownFormatting.inline("x", 0, 1, Inline.STRIKETHROUGH).show("x"))
    }

    @Test
    fun heading_cyclesThroughLevelsAndBackToNone() {
        var text = "Title"
        var caret = 2
        val seen = mutableListOf<String>()
        repeat(7) {
            val edit = MarkdownFormatting.heading(text, caret, caret)
            text = edit.applyTo(text)
            caret = edit.selectionStart
            seen += text
        }
        assertEquals(listOf("# Title", "## Title", "### Title", "#### Title", "##### Title", "###### Title", "Title"), seen)
    }

    @Test
    fun heading_keepsTheCaretInTheSameWord() {
        val edit = MarkdownFormatting.heading("ab\nTitle", 5, 5)
        assertEquals("ab\n# Ti[]tle", edit.show("ab\nTitle"))
    }

    @Test
    fun bullet_marksEveryTouchedLine_thenUnmarks() {
        val text = "one\ntwo\nthree"
        val marked = MarkdownFormatting.lineMark(text, 1, 9, LineMark.BULLET)
        assertEquals("[- one\n- two\n- three]", marked.show(text))
        val again = marked.applyTo(text)
        assertEquals("[one\ntwo\nthree]", MarkdownFormatting.lineMark(again, 0, again.length, LineMark.BULLET).show(again))
    }

    @Test
    fun numbered_countsLines_andConvertsBullets() {
        val text = "- a\n- b"
        assertEquals("[1. a\n2. b]", MarkdownFormatting.lineMark(text, 0, text.length, LineMark.NUMBERED).show(text))
    }

    @Test
    fun task_addsCheckboxes_keepingIndent() {
        val text = "  item"
        assertEquals("  - [ ] it[]em", MarkdownFormatting.lineMark(text, 4, 4, LineMark.TASK).show(text))
    }

    @Test
    fun quote_togglesTheQuoteMark() {
        val text = "said"
        val quoted = MarkdownFormatting.lineMark(text, 0, 4, LineMark.QUOTE)
        assertEquals("[> said]", quoted.show(text))
        val q = quoted.applyTo(text)
        assertEquals("[said]", MarkdownFormatting.lineMark(q, 0, q.length, LineMark.QUOTE).show(q))
    }

    @Test
    fun link_withSelection_selectsTheTarget() {
        val text = "see docs"
        assertEquals("see [docs]([https://])", MarkdownFormatting.link(text, 4, 8).show(text))
    }

    @Test
    fun link_withoutSelection_selectsTheLabel() {
        assertEquals("[[link text]](https://)", MarkdownFormatting.link("", 0, 0).show(""))
    }

    @Test
    fun image_usesTheImageSyntax() {
        assertEquals("![[description]](image.png)", MarkdownFormatting.image("", 0, 0).show(""))
    }

    @Test
    fun codeBlock_wrapsTheTouchedLines_inAFence() {
        val text = "intro\nval x = 1\nend"
        val edit = MarkdownFormatting.codeBlock(text, 8, 10)
        assertEquals("intro\n\n```\n[val x = 1]\n```\n\nend", edit.show(text))
    }

    @Test
    fun codeBlock_withoutSelection_putsTheCaretInside() {
        val text = "para"
        assertEquals("para\n\n```\n[]\n```\n", MarkdownFormatting.codeBlock(text, 4, 4).show(text))
    }

    @Test
    fun table_goesAfterTheCurrentLine_onItsOwn() {
        val text = "intro text"
        val edit = MarkdownFormatting.table(text, 3, 3)
        assertEquals("intro text\n\n| [Column 1] | Column 2 |\n| --- | --- |\n|  |  |\n", edit.show(text))
    }

    @Test
    fun horizontalRule_doesNotDoubleBlankLines() {
        val text = "above\n\nbelow"
        val edit = MarkdownFormatting.horizontalRule(text, 6, 6)
        assertEquals("above\n\n---[]\n\nbelow", edit.show(text))
    }
}
