package dev.kern.shared.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Root theme for every Kern screen.
 *
 * Resolves the Kern token set ([KernColorScheme]) for the active theme and
 * accent, exposes it through [LocalKernColors] / [LocalKernDensity] (read via
 * [KernTheme]), and additionally projects the tokens onto a Material 3
 * [androidx.compose.material3.ColorScheme] so stock Material components and any
 * not-yet-migrated screens pick up the palette automatically.
 *
 * Kern uses a fixed brand palette with a user-selectable accent. There is no
 * wallpaper-based dynamic colour: identity stays consistent on every device.
 *
 * @param themeMode Light, Dark, or Auto (follows the system).
 * @param accent user-selected accent driving interactive chrome.
 * @param density row/tile density (Cozy default).
 */
@Composable
fun KernTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    accent: AccentColor = AccentColor.Default,
    density: Density = Density.Default,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }

    val kernColors = remember(dark, accent) {
        if (dark) darkKernColors(accent) else lightKernColors(accent)
    }
    val materialScheme = remember(kernColors) { kernColors.toMaterialScheme() }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalKernColors provides kernColors,
        LocalKernDensity provides density,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = KernTypography,
            content = content,
        )
    }
}

/**
 * Project Kern tokens onto a Material 3 scheme. This keeps `MaterialTheme.*`
 * call sites (and stock components like Snackbar, AlertDialog) on-palette.
 */
private fun KernColorScheme.toMaterialScheme() = if (dark) {
    darkColorScheme(
        primary = accent,
        onPrimary = accentOn,
        primaryContainer = accentSoft,
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = accentOn,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = sunken,
        onSurfaceVariant = textMid,
        surfaceContainer = surface,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
        outline = border,
        outlineVariant = borderSoft,
        error = danger,
        onError = accentOn,
        scrim = scrim,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = accentOn,
        primaryContainer = accentSoft,
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = accentOn,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = sunken,
        onSurfaceVariant = textMid,
        surfaceContainer = surface,
        surfaceContainerHigh = sunken,
        surfaceContainerHighest = sunken,
        outline = border,
        outlineVariant = borderSoft,
        error = danger,
        onError = accentOn,
        scrim = scrim,
    )
}
