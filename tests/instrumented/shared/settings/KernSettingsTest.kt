package dev.kern.shared.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kern.shared.theme.AccentColor
import dev.kern.shared.theme.Density
import dev.kern.shared.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The persisted settings store: defaults, in-memory updates, and persistence. */
@RunWith(AndroidJUnit4::class)
class KernSettingsTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("kern_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaults_match_the_design() {
        val settings = KernSettings.create(context)
        assertEquals(ThemeMode.AUTO, settings.themeMode)
        assertEquals(Density.COZY, settings.density)
        assertEquals(AccentColor.SLATE, settings.accent)
        assertTrue(settings.scanDocuments)
        assertTrue(settings.scanDownloads)
        assertFalse(settings.gridLayout)
    }

    @Test
    fun updates_are_reflected_immediately() {
        val settings = KernSettings.create(context)
        settings.updateThemeMode(ThemeMode.DARK)
        settings.updateDensity(Density.COMPACT)
        settings.updateAccent(AccentColor.TEAL)
        settings.updateScanDocuments(false)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(Density.COMPACT, settings.density)
        assertEquals(AccentColor.TEAL, settings.accent)
        assertFalse(settings.scanDocuments)
    }

    @Test
    fun updates_persist_across_instances() {
        KernSettings.create(context).apply {
            updateThemeMode(ThemeMode.LIGHT)
            updateAccent(AccentColor.CRIMSON)
            updateScanDownloads(false)
            updateGridLayout(true)
        }
        val reloaded = KernSettings.create(context)
        assertEquals(ThemeMode.LIGHT, reloaded.themeMode)
        assertEquals(AccentColor.CRIMSON, reloaded.accent)
        assertFalse(reloaded.scanDownloads)
        assertTrue(reloaded.gridLayout)
    }
}
