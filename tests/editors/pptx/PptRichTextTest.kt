package dev.kern.editors.pptx

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import dev.kern.editors.pptx.PptDocument.RunStyle
import dev.kern.editors.pptx.PptDocument.ShapeRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PptRichTextTest {

    private val bold = RunStyle(bold = true)
    private val plain = RunStyle()

    /** "Hello" in bold, then " world" plain. */
    private fun helloWorld(): AnnotatedString =
        PptRichText.toAnnotated(listOf(ShapeRun("Hello", bold), ShapeRun(" world", plain)))

    private fun runsAfter(before: AnnotatedString, after: String): List<ShapeRun> =
        PptRichText.toRuns(PptRichText.mergeEdit(before, after))

    // mergeEdit: formatting survives a keystroke

    @Test
    fun mergeEdit_unchangedText_returnsTheSameValue() {
        val before = helloWorld()
        assertSame(before, PptRichText.mergeEdit(before, before.text))
    }

    @Test
    fun mergeEdit_keystroke_keepsEveryOtherRunsFormatting() {
        // The text field hands back plain text; the result must still carry the spans.
        assertEquals(
            listOf(ShapeRun("HelloX", bold), ShapeRun(" world", plain)),
            runsAfter(helloWorld(), "HelloX world"),
        )
    }

    @Test
    fun mergeEdit_insertedText_takesStyleOfCharacterBefore() {
        assertEquals(
            listOf(ShapeRun("Hello", bold), ShapeRun(" wXorld", plain)),
            runsAfter(helloWorld(), "Hello wXorld"),
        )
    }

    @Test
    fun mergeEdit_typingAtStart_takesStyleOfFirstCharacter() {
        assertEquals(
            listOf(ShapeRun("XHello", bold), ShapeRun(" world", plain)),
            runsAfter(helloWorld(), "XHello world"),
        )
    }

    @Test
    fun mergeEdit_deletion_keepsBothRuns() {
        assertEquals(
            listOf(ShapeRun("Hell", bold), ShapeRun(" world", plain)),
            runsAfter(helloWorld(), "Hell world"),
        )
    }

    @Test
    fun mergeEdit_deletingAWholeRun_leavesTheRest() {
        assertEquals(listOf(ShapeRun(" world", plain)), runsAfter(helloWorld(), " world"))
    }

    @Test
    fun mergeEdit_replacingASelection_takesStyleBeforeIt() {
        // "llo w" replaced by "y": the insertion follows the bold "He".
        assertEquals(
            listOf(ShapeRun("Hey", bold), ShapeRun("orld", plain)),
            runsAfter(helloWorld(), "Heyorld"),
        )
    }

    @Test
    fun mergeEdit_pendingStyle_appliesToInsertedTextOnly() {
        val merged = PptRichText.mergeEdit(helloWorld(), "Hello world!") { it.copy(italic = true) }
        assertEquals(
            listOf(ShapeRun("Hello", bold), ShapeRun(" world", plain), ShapeRun("!", RunStyle(italic = true))),
            PptRichText.toRuns(merged),
        )
    }

    @Test
    fun mergeEdit_keepsSizeColourAndFontOfARun() {
        val styled = RunStyle(sizePt = 28f, colorHex = "#C00000", fontFamily = "Georgia")
        val before = PptRichText.toAnnotated(listOf(ShapeRun("Title", styled)))
        assertEquals(listOf(ShapeRun("Titles", styled)), runsAfter(before, "Titles"))
    }

    // mergeEdit: alignment survives Enter

    @Test
    fun mergeEdit_keystroke_keepsParagraphAlignment() {
        val before = PptRichText.toAnnotated(listOf(ShapeRun("Centred")), listOf(TextAlign.Center))
        val after = PptRichText.mergeEdit(before, "Centred!")
        assertEquals(listOf(TextAlign.Center), PptRichText.paragraphAlignments(after))
    }

    @Test
    fun mergeEdit_enterInsideParagraph_bothHalvesKeepAlignment() {
        val before = PptRichText.toAnnotated(listOf(ShapeRun("Centred")), listOf(TextAlign.Center))
        val after = PptRichText.mergeEdit(before, "Cen\ntred")
        assertEquals(listOf(TextAlign.Center, TextAlign.Center), PptRichText.paragraphAlignments(after))
    }

    @Test
    fun mergeEdit_enterAtEndThenTyping_newLineKeepsAlignment() {
        val before = PptRichText.toAnnotated(listOf(ShapeRun("Centred")), listOf(TextAlign.Center))
        val afterEnter = PptRichText.mergeEdit(before, "Centred\n")
        val afterTyping = PptRichText.mergeEdit(afterEnter, "Centred\nNext")
        assertEquals(listOf(TextAlign.Center, TextAlign.Center), PptRichText.paragraphAlignments(afterTyping))
    }

    @Test
    fun mergeEdit_editInOneParagraph_leavesOthersAlignment() {
        val before = PptRichText.toAnnotated(listOf(ShapeRun("One\nTwo")), listOf(TextAlign.Center, TextAlign.Right))
        val after = PptRichText.mergeEdit(before, "One\nTwos")
        assertEquals(listOf(TextAlign.Center, TextAlign.Right), PptRichText.paragraphAlignments(after))
        val afterFirst = PptRichText.mergeEdit(before, "Ones\nTwo")
        assertEquals(listOf(TextAlign.Center, TextAlign.Right), PptRichText.paragraphAlignments(afterFirst))
    }

    @Test
    fun mergeEdit_joiningParagraphs_keepsFirstAlignment() {
        val before = PptRichText.toAnnotated(listOf(ShapeRun("One\nTwo")), listOf(TextAlign.Center, TextAlign.Right))
        val after = PptRichText.mergeEdit(before, "OneTwo")
        assertEquals(listOf(TextAlign.Center), PptRichText.paragraphAlignments(after))
    }

    // Runs <-> AnnotatedString

    @Test
    fun toRuns_ofToAnnotated_roundTripsEveryModelledField() {
        val runs = listOf(
            ShapeRun("B", RunStyle(bold = true)),
            ShapeRun("I", RunStyle(italic = true)),
            ShapeRun("U", RunStyle(underline = true)),
            ShapeRun("S", RunStyle(sizePt = 18f)),
            ShapeRun("C", RunStyle(colorHex = "#1F4E79")),
            ShapeRun("F", RunStyle(fontFamily = "Calibri")),
            ShapeRun("all", RunStyle(true, true, true, 40f, "#FFC000", "Courier New")),
        )
        assertEquals(runs, PptRichText.toRuns(PptRichText.toAnnotated(runs)))
    }

    @Test
    fun toRuns_mergesAdjacentRunsOfOneStyle() {
        val annotated = PptRichText.toAnnotated(listOf(ShapeRun("a", bold), ShapeRun("b", bold)))
        assertEquals(listOf(ShapeRun("ab", bold)), PptRichText.toRuns(annotated))
    }

    @Test
    fun toRuns_ofEmptyText_isOneEmptyRun() {
        assertEquals(listOf(ShapeRun("")), PptRichText.toRuns(AnnotatedString("")))
    }

    @Test
    fun toAnnotated_paragraphAlignments_roundTrip() {
        val aligns = listOf(TextAlign.Left, null, TextAlign.Justify)
        val annotated = PptRichText.toAnnotated(listOf(ShapeRun("a\nb\nc")), aligns)
        assertEquals(aligns, PptRichText.paragraphAlignments(annotated))
    }

    @Test
    fun ooxmlAlignTokens_roundTrip() {
        for (token in listOf("l", "ctr", "r", "just")) {
            assertEquals(token, PptRichText.toOoxmlAlign(PptRichText.fromOoxmlAlign(token)))
        }
        assertNull(PptRichText.fromOoxmlAlign(null))
        assertNull(PptRichText.toOoxmlAlign(null))
        assertEquals(TextAlign.Justify, PptRichText.fromOoxmlAlign("dist"))
    }

    // Editing helpers

    @Test
    fun alignParagraphs_caret_alignsOnlyItsParagraph() {
        val value = TextFieldValue(
            annotatedString = PptRichText.toAnnotated(listOf(ShapeRun("One\nTwo\nThree"))),
            selection = TextRange(5),
        )
        val aligned = PptRichText.alignParagraphs(value, TextAlign.Center)
        assertEquals(listOf(null, TextAlign.Center, null), PptRichText.paragraphAlignments(aligned.annotatedString))
    }

    @Test
    fun alignParagraphs_selection_alignsEveryParagraphItTouches() {
        val value = TextFieldValue(
            annotatedString = PptRichText.toAnnotated(listOf(ShapeRun("One\nTwo\nThree"))),
            selection = TextRange(1, 5),
        )
        val aligned = PptRichText.alignParagraphs(value, TextAlign.Right)
        assertEquals(listOf(TextAlign.Right, TextAlign.Right, null), PptRichText.paragraphAlignments(aligned.annotatedString))
    }

    @Test
    fun restyleRange_restylesOnlyTheRangeAndKeepsAlignment() {
        val value = TextFieldValue(PptRichText.toAnnotated(listOf(ShapeRun("Hello")), listOf(TextAlign.Center)))
        val restyled = PptRichText.restyleRange(value, 0, 3) { it.copy(bold = true) }
        assertEquals(listOf(ShapeRun("Hel", bold), ShapeRun("lo", plain)), PptRichText.toRuns(restyled.annotatedString))
        assertEquals(listOf(TextAlign.Center), PptRichText.paragraphAlignments(restyled.annotatedString))
    }

    @Test
    fun styleAt_caret_reportsStyleOfCharacterBefore() {
        val text = helloWorld()
        assertEquals(bold, PptRichText.styleAt(TextFieldValue(text, TextRange(5))))
        assertEquals(plain, PptRichText.styleAt(TextFieldValue(text, TextRange(6))))
        assertEquals(bold, PptRichText.styleAt(TextFieldValue(text, TextRange(0))))
    }

    @Test
    fun wordAt_insideAWord_isTheWord_atABoundary_isNull() {
        assertEquals(0 until 5, PptRichText.wordAt("hello world", 2))
        assertEquals(6 until 11, PptRichText.wordAt("hello world", 8))
        // An apostrophe is part of the word.
        assertEquals(0 until 5, PptRichText.wordAt("don't", 2))
        assertNull(PptRichText.wordAt("hello world", 5))
        assertNull(PptRichText.wordAt("hello world", 0))
        assertNull(PptRichText.wordAt("hello world", 11))
    }
}
