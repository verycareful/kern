package dev.kern.shared.find

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Compiling the find field and finding and replacing in plain text. */
class TextFinderTest {

    private fun finder(query: String, matchCase: Boolean = true, regex: Boolean = false): TextFinder {
        val compiled = FindQuery.compile(query, FindOptions(matchCase, regex))
        assertTrue("expected $query to compile, got $compiled", compiled is FindQuery.Ready)
        return (compiled as FindQuery.Ready).finder
    }

    @Test
    fun defaults_matchCaseOnRegexOff() {
        assertEquals(FindOptions(matchCase = true, regex = false), FindOptions())
    }

    @Test
    fun emptyQuery_isEmpty() {
        assertEquals(FindQuery.Empty, FindQuery.compile("", FindOptions()))
        assertEquals(FindQuery.Empty, FindQuery.compile("", FindOptions(regex = true)))
    }

    @Test
    fun invalidRegex_isInvalidWithAMessage() {
        val result = FindQuery.compile("(abc", FindOptions(regex = true))
        assertTrue(result is FindQuery.Invalid)
        assertTrue((result as FindQuery.Invalid).message.isNotBlank())
    }

    @Test
    fun regexSyntax_isLiteralWithRegexOff() {
        assertTrue(FindQuery.compile("(abc", FindOptions()) is FindQuery.Ready)
        assertEquals(listOf(TextMatch(3, 6)), finder("a.c").findAll("abca.c"))
        assertEquals(listOf(TextMatch(0, 4)), finder("[x]+").findAll("[x]+ x"))
    }

    @Test
    fun matchCase_onSkipsOtherCases() {
        assertEquals(listOf(TextMatch(4, 7)), finder("Ker").findAll("ker Ker KER"))
    }

    @Test
    fun matchCase_offFindsEveryCase() {
        assertEquals(
            listOf(TextMatch(0, 3), TextMatch(4, 7), TextMatch(8, 11)),
            finder("Ker", matchCase = false).findAll("ker Ker KER"),
        )
    }

    @Test
    fun matchCase_offFoldsNonAsciiLetters() {
        assertEquals(listOf(TextMatch(0, 5)), finder("ÜBUNG", matchCase = false).findAll("übung"))
    }

    @Test
    fun matches_doNotOverlap() {
        assertEquals(listOf(TextMatch(0, 2), TextMatch(2, 4)), finder("aa").findAll("aaaaa"))
    }

    @Test
    fun regex_findsPatterns() {
        assertEquals(
            listOf(TextMatch(4, 6), TextMatch(11, 14)),
            finder("\\d+", regex = true).findAll("abc 12 def 345"),
        )
    }

    @Test
    fun regex_anchorsMatchAtLineBoundaries() {
        assertEquals(
            listOf(TextMatch(0, 3), TextMatch(8, 11)),
            finder("^cat", regex = true).findAll("cat dog\ncat"),
        )
    }

    @Test
    fun emptyMatches_areSkipped() {
        assertEquals(emptyList<TextMatch>(), finder("^", regex = true).findAll("one\ntwo"))
        assertEquals(listOf(TextMatch(1, 3)), finder("a*", regex = true).findAll("baab"))
    }

    @Test
    fun noMatch_givesAnEmptyList() {
        assertEquals(emptyList<TextMatch>(), finder("zzz").findAll("abc"))
        assertEquals(0, finder("zzz").count("abc"))
    }

    @Test
    fun replaceAt_replacesOnlyThatMatch() {
        val f = finder("cat")
        val text = "cat cat cat"
        assertEquals("cat dog cat", f.replaceAt(text, f.findAll(text)[1], "dog").getOrThrow())
    }

    @Test
    fun replaceAll_replacesEveryMatchAndCounts() {
        assertEquals(Replaced("dog dog dog", 3), finder("cat").replaceAll("cat cat cat", "dog").getOrThrow())
    }

    @Test
    fun replaceAll_withNoMatchLeavesTheTextAlone() {
        assertEquals(Replaced("abc", 0), finder("zzz").replaceAll("abc", "y").getOrThrow())
    }

    @Test
    fun replaceAll_skipsEmptyMatches() {
        assertEquals(Replaced("bXb", 1), finder("a*", regex = true).replaceAll("baab", "X").getOrThrow())
    }

    @Test
    fun literalMode_insertsDollarAndBackslashAsTyped() {
        val f = finder("price")
        assertEquals("cost \$1 \\n", f.replaceAt("price", TextMatch(0, 5), "cost \$1 \\n").getOrThrow())
        assertEquals(Replaced("\$1 \$1", 2), f.replaceAll("price price", "\$1").getOrThrow())
    }

    @Test
    fun regexMode_expandsNumberedGroups() {
        val f = finder("(\\w+)@(\\w+)", regex = true)
        assertEquals(Replaced("b at a, d at c", 2), f.replaceAll("a@b, c@d", "\$2 at \$1").getOrThrow())
        assertEquals("b at a, c@d", f.replaceAt("a@b, c@d", TextMatch(0, 3), "\$2 at \$1").getOrThrow())
    }

    @Test
    fun regexMode_expandsNamedGroups() {
        val f = finder("(?<year>\\d{4})-(?<month>\\d{2})", regex = true)
        assertEquals(Replaced("11/2026", 1), f.replaceAll("2026-11", "\${month}/\${year}").getOrThrow())
    }

    @Test
    fun regexMode_escapedDollarIsLiteral() {
        assertEquals(Replaced("\$5", 1), finder("\\d", regex = true).replaceAll("5", "\\\$\$0").getOrThrow())
    }

    @Test
    fun regexMode_missingGroupFailsWithAMessage() {
        val f = finder("(a)", regex = true)
        val all = f.replaceAll("a", "\$2").exceptionOrNull()
        assertTrue(all is InvalidReplacementException)
        assertTrue(all!!.message!!.isNotBlank())
        assertTrue(f.replaceAt("a", TextMatch(0, 1), "\$2").exceptionOrNull() is InvalidReplacementException)
    }

    @Test
    fun regexMode_trailingBackslashFails() {
        assertTrue(finder("a", regex = true).replaceAll("a", "x\\").exceptionOrNull() is InvalidReplacementException)
    }

    @Test
    fun expand_seesTextBeforeTheMatchForLookbehind() {
        val f = finder("(?<=\\$)\\d+", regex = true)
        val text = "cost \$40 or 50"
        val matches = f.findAll(text)
        assertEquals(listOf(TextMatch(6, 8)), matches)
        assertEquals("cost \$[40] or 50", f.replaceAt(text, matches[0], "[\$0]").getOrThrow())
    }

    @Test
    fun expand_staleMatchFails() {
        val f = finder("cat", regex = true)
        assertTrue(f.expand("dog", TextMatch(0, 3), "x").exceptionOrNull() is IllegalStateException)
        assertTrue(f.expand("ca", TextMatch(0, 3), "x").exceptionOrNull() is IllegalStateException)
    }
}
