package dev.kern.shared.theme

import androidx.compose.ui.graphics.Color
import dev.kern.shared.DocumentFormat

// ============================================================================
// Kern design tokens (0.1.9.0 "UI update").
//
// Source of truth: the design handoff bundle (kept local, gitignored) at
//   .internal/design/.../design_handoff_kern_ui/{README.md, kern-theme.jsx}
//
// Raw color values only live here. Resolved, theme-aware tokens are assembled
// into a KernColorScheme (see KernColors.kt) and exposed through KernTheme.
// ============================================================================

// ---- Neutrals: Light theme --------------------------------------------------
val KernLightBg = Color(0xFFFFFFFF)         // app background (pure white)
val KernLightSurface = Color(0xFFFFFFFF)    // cards, search field, sheets, rows
val KernLightRaised = Color(0xFFFFFFFF)     // elevated surfaces
val KernLightSunken = Color(0xFFF1F2F4)     // input fills, segmented track, headers
val KernLightBorder = Color(0xFFE3E5E9)     // standard borders
val KernLightBorderSoft = Color(0xFFEDEEF1) // hairline dividers, card borders
val KernLightText = Color(0xFF17181C)       // primary text
val KernLightTextMid = Color(0xFF5F636B)    // secondary text / metadata
val KernLightTextDim = Color(0xFF9CA0A8)    // tertiary / disabled / section labels
val KernLightScrim = Color(0x6B14161C)      // rgba(20,22,28,0.42) sheet backdrop

// ---- Neutrals: Dark theme ---------------------------------------------------
val KernDarkBg = Color(0xFF000000)          // app background (true black)
val KernDarkSurface = Color(0xFF0E0E0F)     // cards, search field, sheets, rows
val KernDarkRaised = Color(0xFF19191B)      // elevated surfaces (e.g. Word page)
val KernDarkSunken = Color(0xFF161617)      // input fills, segmented track, headers
val KernDarkBorder = Color(0xFF2A2A2D)      // standard borders
val KernDarkBorderSoft = Color(0xFF1D1D1F)  // hairline dividers, card borders
val KernDarkText = Color(0xFFEDEDF0)        // primary text
val KernDarkTextMid = Color(0xFFA1A1A8)     // secondary text / metadata
val KernDarkTextDim = Color(0xFF67676E)     // tertiary / disabled / section labels
val KernDarkScrim = Color(0xB3000000)       // rgba(0,0,0,0.7) sheet backdrop

// ---- Shared accents on solid fills + destructive ----------------------------
val KernAccentOn = Color(0xFFFFFFFF)        // text/icon on a solid accent fill
val KernDanger = Color(0xFFD6453D)          // destructive actions (Delete)

/** accentSoft alpha: tinted pill/well backgrounds (12% light, 20% dark). */
const val AccentSoftAlphaLight = 0.12f
const val AccentSoftAlphaDark = 0.20f

/**
 * User-selectable accent. The base hue is identical in both themes; only the
 * accentSoft alpha differs. Drives active states, FAB, selection, links, and
 * the ON-DEVICE badge. Default is Slate (Kern's seed brand colour).
 */
enum class AccentColor(val displayName: String, val color: Color) {
    SLATE("Slate", Color(0xFF3F5B8B)),
    INDIGO("Indigo", Color(0xFF4F46E5)),
    VIOLET("Violet", Color(0xFF7C6CF4)),
    PLUM("Plum", Color(0xFF9B4DCA)),
    OCEAN("Ocean", Color(0xFF0E7490)),
    TEAL("Teal", Color(0xFF1E8C84)),
    FOREST("Forest", Color(0xFF2F7A52)),
    AMBER("Amber", Color(0xFFC9881F)),
    COPPER("Copper", Color(0xFFB5651D)),
    CRIMSON("Crimson", Color(0xFFBE3455)),
    ROSE("Rose", Color(0xFFC24A6B)),
    GRAPHITE("Graphite", Color(0xFF52525B));

    companion object {
        val Default = SLATE
        fun fromName(name: String?): AccentColor =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/** Per-format identity hue, one value for light and one for dark. */
data class FormatHue(val light: Color, val dark: Color) {
    fun resolve(dark: Boolean): Color = if (dark) this.dark else light
}

/** File-format identity colour (badge, monogram, thumbnail wash, header tint). */
fun DocumentFormat.hue(): FormatHue = when (this) {
    DocumentFormat.PDF -> FormatHue(Color(0xFFC8453B), Color(0xFFF08177))
    DocumentFormat.WORD -> FormatHue(Color(0xFF2E68C4), Color(0xFF7FA8F0))
    DocumentFormat.POWERPOINT -> FormatHue(Color(0xFFD06A2C), Color(0xFFF0A06A))
    DocumentFormat.EXCEL -> FormatHue(Color(0xFF1F8454), Color(0xFF5FC893))
    DocumentFormat.CSV -> FormatHue(Color(0xFF0E8E9A), Color(0xFF54C9D4))
    DocumentFormat.EPUB -> FormatHue(Color(0xFF8657C6), Color(0xFFBB97EC))
}

/** Three-letter monogram shown in badges and grid thumbnails. */
val DocumentFormat.monogram: String
    get() = when (this) {
        DocumentFormat.PDF -> "PDF"
        DocumentFormat.WORD -> "DOC"
        DocumentFormat.POWERPOINT -> "PPT"
        DocumentFormat.EXCEL -> "XLS"
        DocumentFormat.CSV -> "CSV"
        DocumentFormat.EPUB -> "EPB"
    }

// ---- Backwards-compatibility -----------------------------------------------
// The pre-0.1.9.0 browser referenced a single static accent. It now resolves
// the live accent through KernTheme; this alias keeps older call sites building
// until they are migrated. Prefer KernTheme.accent.
@Deprecated("Use KernTheme.accent (user-selectable).", ReplaceWith("KernTheme.accent"))
val KernAccent: Color = AccentColor.Default.color
