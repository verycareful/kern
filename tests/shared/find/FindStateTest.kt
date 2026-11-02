package dev.kern.shared.find

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The find bar's state: opening, toggles, stepping through matches, and segment search. */
class FindStateTest {

    @Test
    fun startsClosedWithMatchCaseOnAndRegexOff() {
        val find = FindState()
        assertFalse(find.isOpen)
        assertTrue(find.matchCase)
        assertFalse(find.regex)
        assertEquals(FindQuery.Empty, find.compiled)
    }

    @Test
    fun activeFinder_onlyWhileOpenWithAUsableQuery() {
        val find = FindState()
        find.query = "kern"
        assertNull(find.activeFinder)
        find.open()
        assertEquals(listOf(TextMatch(0, 4)), find.activeFinder!!.findAll("kern"))
        find.regex = true
        find.query = "(kern"
        assertNull(find.activeFinder)
        assertTrue(find.compiled is FindQuery.Invalid)
    }

    @Test
    fun close_keepsTheFieldsForNextTime() {
        val find = FindState()
        find.open()
        find.query = "a"
        find.replacement = "b"
        find.close()
        assertFalse(find.isOpen)
        find.toggle()
        assertTrue(find.isOpen)
        assertEquals("a", find.query)
        assertEquals("b", find.replacement)
    }

    @Test
    fun nextAndPrevious_wrapAround() {
        val find = FindState()
        find.next(3); assertEquals(1, find.current)
        find.next(3); assertEquals(2, find.current)
        find.next(3); assertEquals(0, find.current)
        find.previous(3); assertEquals(2, find.current)
    }

    @Test
    fun noMatches_leavesTheCurrentMatchAlone() {
        val find = FindState()
        find.next(0)
        find.previous(0)
        assertEquals(0, find.current)
    }

    @Test
    fun currentIn_clampsWhenMatchesShrink() {
        val find = FindState()
        find.select(4)
        assertEquals(1, find.currentIn(2))
        assertEquals(0, find.currentIn(0))
        find.next(2)
        assertEquals(0, find.current)
    }

    @Test
    fun changingTheQueryOrAToggle_restartsAtTheFirstMatchAndClearsTheMessage() {
        val find = FindState()
        find.query = "a"
        find.select(2)
        find.message = FindMessages.replaced(3)
        find.query = "ab"
        assertEquals(0, find.current)
        assertNull(find.message)
        find.select(2)
        find.matchCase = false
        assertEquals(0, find.current)
        find.select(2)
        find.regex = true
        assertEquals(0, find.current)
    }

    @Test
    fun replacementOrSameValue_keepsTheCurrentMatch() {
        val find = FindState()
        find.query = "a"
        find.select(2)
        find.query = "a"
        find.replacement = "b"
        assertEquals(2, find.current)
    }

    @Test
    fun revision_bumpsOnEveryMoveButNotOnReplacementText() {
        val find = FindState()
        var last = find.revision
        fun bumped(): Boolean = (find.revision != last).also { last = find.revision }
        find.open(); assertTrue(bumped())
        find.query = "a"; assertTrue(bumped())
        find.next(3); assertTrue(bumped())
        find.previous(3); assertTrue(bumped())
        find.select(1); assertTrue(bumped())
        find.reveal(); assertTrue(bumped())
        find.replacement = "b"; assertFalse(bumped())
        find.close(); assertFalse(bumped())
    }

    @Test
    fun messages_readNaturally() {
        assertEquals(FindMessage("Replaced 1 match", isError = false), FindMessages.replaced(1))
        assertEquals(FindMessage("Replaced 4 matches", isError = false), FindMessages.replaced(4))
        assertEquals(FindMessage("No group 2", isError = true), FindMessages.failed(InvalidReplacementException("No group 2")))
    }

    @Test
    fun findIn_listsMatchesSegmentBySegment() {
        val finder = (FindQuery.compile("ab", FindOptions()) as FindQuery.Ready).finder
        val segments = sequenceOf<Pair<Int, CharSequence>>(0 to "ab ab", 1 to "none", 2 to "xab")
        assertEquals(
            listOf(SegmentMatch(0, TextMatch(0, 2)), SegmentMatch(0, TextMatch(3, 5)), SegmentMatch(2, TextMatch(1, 3))),
            finder.findIn(segments),
        )
    }
}
