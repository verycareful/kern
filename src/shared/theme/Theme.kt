package dev.kern.shared.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = KernBlue,
    onPrimary = KernSurfaceLight,
    secondary = KernAccent,
    background = KernSurfaceLight,
    onBackground = KernOnSurfaceLight,
    surface = KernSurfaceLight,
    onSurface = KernOnSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = KernBlueLight,
    onPrimary = KernBlueDark,
    secondary = KernAccentDark,
    background = KernSurfaceDark,
    onBackground = KernOnSurfaceDark,
    surface = KernSurfaceDark,
    onSurface = KernOnSurfaceDark,
)

/**
 * Root theme for every Kern screen.
 *
 * @param dynamicColor pull the palette from the system wallpaper (Android 12+).
 *        Off by default so Kern keeps a consistent brand identity.
 */
@Composable
fun KernTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KernTypography,
        content = content,
    )
}
