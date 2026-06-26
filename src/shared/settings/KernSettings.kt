package dev.kern.shared.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kern.shared.theme.AccentColor
import dev.kern.shared.theme.Density
import dev.kern.shared.theme.ThemeMode

/**
 * Observable, persisted user preferences (appearance + file scanning).
 *
 * Persistence uses app-private [SharedPreferences]: this is configuration, not
 * user documents, and never leaves the device. It does not contradict Kern's
 * "no app-owned folder for files" rule (that is about document storage) nor the
 * zero-network policy.
 *
 * Each property is Compose-observable; mutate via the `update…` functions, which
 * also persist. Provide a single instance through [LocalKernSettings].
 */
class KernSettings(private val prefs: SharedPreferences) {

    var themeMode by mutableStateOf(ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name))
        private set
    var accent by mutableStateOf(AccentColor.fromName(prefs.getString(KEY_ACCENT, null)))
        private set
    var density by mutableStateOf(Density.fromName(prefs.getString(KEY_DENSITY, null)))
        private set

    /** Browser default layout: true = grid, false = list. */
    var gridLayout by mutableStateOf(prefs.getBoolean(KEY_GRID, false))
        private set
    var scanDocuments by mutableStateOf(prefs.getBoolean(KEY_SCAN_DOCS, true))
        private set
    var scanDownloads by mutableStateOf(prefs.getBoolean(KEY_SCAN_DOWNLOADS, true))
        private set

    fun updateThemeMode(value: ThemeMode) { themeMode = value; edit { putString(KEY_THEME, value.name) } }
    fun updateAccent(value: AccentColor) { accent = value; edit { putString(KEY_ACCENT, value.name) } }
    fun updateDensity(value: Density) { density = value; edit { putString(KEY_DENSITY, value.name) } }
    fun updateGridLayout(value: Boolean) { gridLayout = value; edit { putBoolean(KEY_GRID, value) } }
    fun updateScanDocuments(value: Boolean) { scanDocuments = value; edit { putBoolean(KEY_SCAN_DOCS, value) } }
    fun updateScanDownloads(value: Boolean) { scanDownloads = value; edit { putBoolean(KEY_SCAN_DOWNLOADS, value) } }

    /** Run a pref edit and commit it asynchronously. */
    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val editor = prefs.edit()
        editor.block()
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "kern_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_ACCENT = "accent"
        private const val KEY_DENSITY = "density"
        private const val KEY_GRID = "grid_layout"
        private const val KEY_SCAN_DOCS = "scan_documents"
        private const val KEY_SCAN_DOWNLOADS = "scan_downloads"

        fun create(context: Context): KernSettings =
            KernSettings(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
}

val LocalKernSettings = staticCompositionLocalOf<KernSettings> {
    error("LocalKernSettings not provided. Set it in MainActivity.")
}
