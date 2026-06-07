package dev.kern.shared

/** A rectangular merged-cell region (all indices 0-based, inclusive). */
data class CellMerge(val firstRow: Int, val lastRow: Int, val firstCol: Int, val lastCol: Int) {
    fun contains(row: Int, col: Int) = row in firstRow..lastRow && col in firstCol..lastCol
    fun isOrigin(row: Int, col: Int) = row == firstRow && col == firstCol
}
