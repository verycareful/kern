package dev.kern.shared.ui

import dev.kern.shared.ui.PageNumber.Index
import dev.kern.shared.ui.PageNumber.Invalid
import org.junit.Assert.assertEquals
import org.junit.Test

/** Reading a typed page number in the go-to-page dialog. */
class PageNumberTest {

    private fun parse(input: String, total: Int = 12) = PageNumber.parse(input, total, "page")

    @Test
    fun numbersInRange_giveZeroBasedIndex() {
        assertEquals(Index(0), parse("1"))
        assertEquals(Index(6), parse("7"))
        assertEquals(Index(11), parse("12"))
    }

    @Test
    fun surroundingSpacesAndLeadingZeros_areAccepted() {
        assertEquals(Index(4), parse("  5 "))
        assertEquals(Index(4), parse("005"))
    }

    @Test
    fun outOfRange_isRefusedWithTheRange() {
        val refusal = Invalid("Enter a number from 1 to 12")
        assertEquals(refusal, parse("0"))
        assertEquals(refusal, parse("13"))
        assertEquals(refusal, parse("99999999999999"))
    }

    @Test
    fun nonNumbers_areRefused() {
        val refusal = Invalid("Enter a number from 1 to 12")
        assertEquals(refusal, parse("-3"))
        assertEquals(refusal, parse("2.5"))
        assertEquals(refusal, parse("ten"))
        assertEquals(refusal, parse("1 2"))
    }

    @Test
    fun blank_asksForANumberOfTheUnit() {
        assertEquals(Invalid("Enter a page number"), parse(""))
        assertEquals(Invalid("Enter a slide number"), PageNumber.parse("   ", 4, "slide"))
    }

    @Test
    fun nothingToGoTo_isRefused() {
        assertEquals(Invalid("There is no chapter to go to"), PageNumber.parse("1", 0, "chapter"))
    }

    @Test
    fun singlePage_acceptsOnlyOne() {
        assertEquals(Index(0), parse("1", total = 1))
        assertEquals(Invalid("Enter a number from 1 to 1"), parse("2", total = 1))
    }
}
