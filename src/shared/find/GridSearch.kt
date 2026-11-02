package dev.kern.shared.find

/** One cell of a spreadsheet: [sheet] is 0 for a CSV file. */
data class CellRef(val sheet: Int, val row: Int, val col: Int)

/** How a grid cell is painted while the find bar is open. */
enum class FindMark { NONE, MATCH, CURRENT }

/**
 * A grid editor's side of the find bar. Besides the [FindTarget] calls, it says which
 * cells match and which match is current, so the grid can paint and reveal them.
 */
interface GridFindTarget : FindTarget {
    /** The cell holding the current match, on whichever sheet it is. */
    val currentCell: CellRef?

    fun cellMark(sheet: Int, row: Int, col: Int): FindMark
}

/** The result of replacing every match in a grid. */
data class GridReplaceAll(val values: Map<CellRef, String>, val replaced: Int, val skippedCells: Int)

/**
 * Find and replace over spreadsheet cells. Each cell is searched on its own, so a match
 * never runs from one cell into the next. Sheets are searched in order, each row by row.
 */
object GridSearch {

    fun matches(finder: TextFinder, sheets: List<List<List<String>>>): List<SegmentMatch<CellRef>> =
        finder.findIn(cells(sheets))

    /**
     * The new value of every cell with a match, except cells [locked] says to leave
     * alone (a formula, which a replace would turn into plain text). Fails, changing
     * nothing, if the replacement cannot be expanded.
     */
    fun replaceAll(
        finder: TextFinder,
        sheets: List<List<List<String>>>,
        replacement: String,
        locked: (CellRef) -> Boolean = { false },
    ): Result<GridReplaceAll> = runCatching {
        val values = LinkedHashMap<CellRef, String>()
        var replaced = 0
        var skipped = 0
        for ((ref, text) in cells(sheets)) {
            if (finder.findAll(text).isEmpty()) continue
            if (locked(ref)) {
                skipped++
                continue
            }
            val result = finder.replaceAll(text.toString(), replacement).getOrThrow()
            values[ref] = result.value
            replaced += result.count
        }
        GridReplaceAll(values, replaced, skipped)
    }

    /** The bar's note after a replace all that left formula cells alone. */
    fun replacedMessage(result: GridReplaceAll, deleted: Boolean = false): FindMessage = when (result.skippedCells) {
        0 -> FindMessages.replaced(result.replaced, deleted)
        else -> FindMessage(
            FindMessages.replaced(result.replaced, deleted).text + ", " + skippedText(result.skippedCells),
            isError = false,
        )
    }

    val formulaNotReplaced = FindMessage("Formula cells are found but not replaced", isError = true)

    private fun skippedText(cells: Int) = if (cells == 1) "skipped 1 formula cell" else "skipped $cells formula cells"

    private fun cells(sheets: List<List<List<String>>>): Sequence<Pair<CellRef, CharSequence>> = sequence {
        sheets.forEachIndexed { s, rows ->
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, text -> if (text.isNotEmpty()) yield(CellRef(s, r, c) to text) }
            }
        }
    }
}
