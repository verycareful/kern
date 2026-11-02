package dev.kern.shared.find

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finding and replacing across spreadsheet cells and sheets. */
class GridSearchTest {

    private fun finder(query: String, matchCase: Boolean = true, regex: Boolean = false) =
        (FindQuery.compile(query, FindOptions(matchCase, regex)) as FindQuery.Ready).finder

    private val sheets = listOf(
        listOf(listOf("cat", "dog"), listOf("cat cat", "")),
        listOf(listOf("bird", "Cat")),
    )

    @Test
    fun matches_runSheetByRowByColumn() {
        assertEquals(
            listOf(
                SegmentMatch(CellRef(0, 0, 0), TextMatch(0, 3)),
                SegmentMatch(CellRef(0, 1, 0), TextMatch(0, 3)),
                SegmentMatch(CellRef(0, 1, 0), TextMatch(4, 7)),
            ),
            GridSearch.matches(finder("cat"), sheets),
        )
        assertEquals(CellRef(1, 0, 1), GridSearch.matches(finder("cat", matchCase = false), sheets).last().key)
    }

    @Test
    fun matches_neverSpanTwoCells() {
        assertEquals(emptyList<SegmentMatch<CellRef>>(), GridSearch.matches(finder("catdog"), sheets))
    }

    @Test
    fun replaceAll_givesTheNewValueOfEachChangedCell() {
        val result = GridSearch.replaceAll(finder("cat", matchCase = false), sheets, "owl").getOrThrow()
        assertEquals(
            mapOf(CellRef(0, 0, 0) to "owl", CellRef(0, 1, 0) to "owl owl", CellRef(1, 0, 1) to "owl"),
            result.values,
        )
        assertEquals(4, result.replaced)
        assertEquals(0, result.skippedCells)
        assertEquals("Replaced 4 matches", GridSearch.replacedMessage(result).text)
    }

    @Test
    fun replaceAll_leavesLockedCellsAndCountsThem() {
        val result = GridSearch.replaceAll(finder("cat"), sheets, "owl", locked = { it == CellRef(0, 1, 0) }).getOrThrow()
        assertEquals(mapOf(CellRef(0, 0, 0) to "owl"), result.values)
        assertEquals(1, result.replaced)
        assertEquals(1, result.skippedCells)
        assertEquals("Replaced 1 match, skipped 1 formula cell", GridSearch.replacedMessage(result).text)
    }

    @Test
    fun replaceAll_expandsGroupsPerCell() {
        val result = GridSearch.replaceAll(finder("(\\w)(\\w+)", regex = true), listOf(listOf(listOf("ab", "cde"))), "\$2\$1").getOrThrow()
        assertEquals(mapOf(CellRef(0, 0, 0) to "ba", CellRef(0, 0, 1) to "dec"), result.values)
    }

    @Test
    fun replaceAll_badReplacementFails() {
        assertTrue(GridSearch.replaceAll(finder("(a)", regex = true), listOf(listOf(listOf("a"))), "\$3").isFailure)
    }
}
