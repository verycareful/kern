package dev.kern.shared.theme

import dev.kern.shared.DocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings persistence relies on these enum mappings; the UI relies on the hues. */
class ThemeTokensTest {

    @Test
    fun accent_fromName_falls_back_to_default() {
        assertEquals(AccentColor.INDIGO, AccentColor.fromName("INDIGO"))
        assertEquals(AccentColor.Default, AccentColor.fromName(null))
        assertEquals(AccentColor.Default, AccentColor.fromName("not-an-accent"))
        assertEquals(AccentColor.SLATE, AccentColor.Default)
    }

    @Test
    fun density_fromName_falls_back_to_default() {
        assertEquals(Density.COMPACT, Density.fromName("COMPACT"))
        assertEquals(Density.COZY, Density.fromName("COZY"))
        assertEquals(Density.Default, Density.fromName(null))
        assertEquals(Density.COZY, Density.Default)
    }

    @Test
    fun themeMode_has_light_dark_auto() {
        assertEquals(3, ThemeMode.entries.size)
        assertTrue(ThemeMode.entries.containsAll(listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AUTO)))
    }

    @Test
    fun every_format_has_distinct_light_and_dark_hues() {
        for (format in DocumentFormat.entries) {
            val hue = format.hue()
            assertNotEquals("light vs dark should differ for $format", hue.light, hue.dark)
            assertEquals(hue.light, hue.resolve(dark = false))
            assertEquals(hue.dark, hue.resolve(dark = true))
        }
    }

    @Test
    fun monograms_match_design() {
        assertEquals("PDF", DocumentFormat.PDF.monogram)
        assertEquals("DOC", DocumentFormat.WORD.monogram)
        assertEquals("XLS", DocumentFormat.EXCEL.monogram)
        assertEquals("CSV", DocumentFormat.CSV.monogram)
        assertEquals("PPT", DocumentFormat.POWERPOINT.monogram)
        assertEquals("EPB", DocumentFormat.EPUB.monogram)
    }
}
