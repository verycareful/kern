package dev.kern.shared.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.kern.shared.DocumentFormat

/**
 * The full set of theme-aware colour tokens that Material 3's [androidx.compose.material3.ColorScheme]
 * cannot express (raised vs surface vs sunken, mid/dim text tiers, soft borders,
 * accentSoft, scrim, and per-format identity hues).
 *
 * Resolved once per theme by [KernTheme] and read through [KernTheme.colors].
 */
@Immutable
data class KernColorScheme(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val sunken: Color,
    val border: Color,
    val borderSoft: Color,
    val text: Color,
    val textMid: Color,
    val textDim: Color,
    val scrim: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentOn: Color,
    val danger: Color,
) {
    /** Identity hue for a file format, resolved for the current theme. */
    fun formatColor(format: DocumentFormat): Color = format.hue().resolve(dark)
}

fun lightKernColors(accent: AccentColor): KernColorScheme = KernColorScheme(
    dark = false,
    bg = KernLightBg,
    surface = KernLightSurface,
    raised = KernLightRaised,
    sunken = KernLightSunken,
    border = KernLightBorder,
    borderSoft = KernLightBorderSoft,
    text = KernLightText,
    textMid = KernLightTextMid,
    textDim = KernLightTextDim,
    scrim = KernLightScrim,
    accent = accent.color,
    accentSoft = accent.color.copy(alpha = AccentSoftAlphaLight),
    accentOn = KernAccentOn,
    danger = KernDanger,
)

fun darkKernColors(accent: AccentColor): KernColorScheme = KernColorScheme(
    dark = true,
    bg = KernDarkBg,
    surface = KernDarkSurface,
    raised = KernDarkRaised,
    sunken = KernDarkSunken,
    border = KernDarkBorder,
    borderSoft = KernDarkBorderSoft,
    text = KernDarkText,
    textMid = KernDarkTextMid,
    textDim = KernDarkTextDim,
    scrim = KernDarkScrim,
    accent = accent.color,
    accentSoft = accent.color.copy(alpha = AccentSoftAlphaDark),
    accentOn = KernAccentOn,
    danger = KernDanger,
)

val LocalKernColors = staticCompositionLocalOf<KernColorScheme> {
    error("LocalKernColors not provided. Wrap content in KernTheme { }.")
}
val LocalKernDensity = staticCompositionLocalOf { Density.Default }

/**
 * Ergonomic accessor for Kern tokens, mirroring how `MaterialTheme.colorScheme`
 * is used. `object KernTheme` co-exists with the `@Composable fun KernTheme(...)`
 * builder, exactly like Material 3's own `MaterialTheme`.
 */
object KernTheme {
    val colors: KernColorScheme
        @Composable @ReadOnlyComposable get() = LocalKernColors.current

    val density: Density
        @Composable @ReadOnlyComposable get() = LocalKernDensity.current

    val accent: Color
        @Composable @ReadOnlyComposable get() = LocalKernColors.current.accent

    @Composable
    @ReadOnlyComposable
    fun formatColor(format: DocumentFormat): Color = LocalKernColors.current.formatColor(format)
}
