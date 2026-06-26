package dev.kern.shared.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================================================
// Shape and spacing tokens. Corner radius is locked to Soft (6dp); derived
// radii follow the formulas in the design handoff README ("Shape & spacing").
// ============================================================================

/** Locked corner radii. All derived from the Soft 6dp base. */
object KernRadius {
    val base: Dp = 6.dp          // locked base
    val badge: Dp = 4.dp         // max(base-2, 4): file badge / tile
    val innerSmall: Dp = 4.dp    // max(base-2, 4): small inner wells
    val segmentTrack: Dp = 6.dp  // max(base-2, 6)
    val segmentThumb: Dp = 4.dp  // max(base-4, 4)
    val field: Dp = 10.dp        // max(base, 10): search field, cards, sheets body
    val iconButton: Dp = 10.dp   // min(base+4, 21)
    val pdfPage: Dp = 4.dp       // max(base-4, 4)
    val fab: Dp = 16.dp          // max(base+4, 16)
    val sheetTop: Dp = 16.dp     // max(base+8, 16): bottom-sheet top corners
    val pill: Dp = 999.dp        // fully rounded chips / pills
}

/** Theme selection. AUTO follows the system setting. */
enum class ThemeMode { LIGHT, DARK, AUTO }

/**
 * Row/tile density. A user setting; Cozy is the default. Values mirror the
 * design handoff ("Density"): Cozy row 70 / Compact row 60, etc.
 */
enum class Density(
    val rowMinHeight: Dp,
    val rowPaddingVertical: Dp,
    val gap: Dp,
    val tile: Dp,
    val screenPadding: Dp,
) {
    COMPACT(rowMinHeight = 60.dp, rowPaddingVertical = 8.dp, gap = 8.dp, tile = 38.dp, screenPadding = 14.dp),
    COZY(rowMinHeight = 70.dp, rowPaddingVertical = 12.dp, gap = 12.dp, tile = 44.dp, screenPadding = 18.dp);

    companion object {
        val Default = COZY
        fun fromName(name: String?): Density = entries.firstOrNull { it.name == name } ?: Default
    }
}
