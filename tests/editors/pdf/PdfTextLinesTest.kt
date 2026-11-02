package dev.kern.editors.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pairing a page's text with the line boxes the renderer reports, for the selectable overlay. */
class PdfTextLinesTest {

    private val a = PageRect(0.1f, 0.1f, 0.5f, 0.12f)
    private val b = PageRect(0.1f, 0.2f, 0.7f, 0.22f)

    @Test
    fun oneBoxPerLine_pairsLineByLine() {
        assertEquals(listOf(PdfTextLine("first", a), PdfTextLine("second", b)), pairLines("first\nsecond", listOf(a, b)))
    }

    @Test
    fun blankLines_areDropped() {
        assertEquals(listOf(PdfTextLine("first", a)), pairLines("first\n ", listOf(a, b)))
    }

    @Test
    fun boxesAndLinesDisagreeing_giveOneBlockCoveringAll() {
        assertEquals(
            listOf(PdfTextLine("one\ntwo\nthree", PageRect(0.1f, 0.1f, 0.7f, 0.22f))),
            pairLines("one\ntwo\nthree", listOf(a, b)),
        )
    }

    @Test
    fun noTextOrNoBoxes_givesNothing() {
        assertEquals(emptyList<PdfTextLine>(), pairLines("", listOf(a)))
        assertEquals(emptyList<PdfTextLine>(), pairLines("text", emptyList()))
    }
}
