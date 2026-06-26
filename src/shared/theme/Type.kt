package dev.kern.shared.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kern.R

// ============================================================================
// Typefaces.
//
// The design locks in four families:
//   Outfit         -> all interface text (weights 400/500/600/700)
//   Quicksand 700  -> the "kern" wordmark ONLY
//   IBM Plex Mono  -> all metadata (sizes, dates, monograms, labels, refs)
//   Sora           -> EPUB reading body ONLY
//
// Kern ships with ZERO network permissions, so downloadable Google Fonts are
// not an option: the .ttf files are bundled under res/font/ (all SIL Open Font
// License, license texts kept in licenses/fonts/). The families below reference
// those resources directly.
// ============================================================================

// ---- Bundled families (res/font/*.ttf) -------------------------------------
val OutfitFamily = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
)
val QuicksandFamily = FontFamily(Font(R.font.quicksand_bold, FontWeight.Bold))
val PlexMonoFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)
val SoraFamily = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_medium, FontWeight.Medium),
)

/**
 * Named text styles taken directly from the handoff type scale. Reskinned
 * screens use these instead of guessing sizes/weights.
 */
object KernType {
    val screenTitle = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.02).em,
    )
    val sectionTitle = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 20.sp,
    )
    val fileName = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 19.sp, letterSpacing = (-0.005).em,
    )
    val body = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.5.sp, lineHeight = 20.sp,
    )
    val chip = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 16.sp,
    )
    /** Mono metadata: file size/date, monograms, version strings, cell refs. */
    val meta = TextStyle(
        fontFamily = PlexMonoFamily, fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp, lineHeight = 15.sp,
    )
    /** Mono uppercase section label (e.g. PINNED, RECENT). */
    val sectionLabel = TextStyle(
        fontFamily = PlexMonoFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp, lineHeight = 14.sp, letterSpacing = 0.08.em,
    )
    val caption = TextStyle(
        fontFamily = PlexMonoFamily, fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp, lineHeight = 13.sp,
    )
    /** EPUB reading body (Sora). */
    val readingBody = TextStyle(
        fontFamily = SoraFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.5.sp, lineHeight = 28.sp,
    )
}

/**
 * Material 3 typography, themed to Outfit so stock Material components inherit
 * the UI face. Kern-specific roles live in [KernType].
 */
val KernTypography = Typography(
    displaySmall = KernType.screenTitle,
    headlineSmall = KernType.screenTitle,
    titleLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em,
    ),
    titleMedium = KernType.sectionTitle,
    bodyLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp, lineHeight = 20.sp,
    ),
    labelLarge = KernType.chip,
    labelSmall = TextStyle(
        fontFamily = PlexMonoFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp, lineHeight = 14.sp,
    ),
)
