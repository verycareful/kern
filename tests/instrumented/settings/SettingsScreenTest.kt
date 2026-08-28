package dev.kern.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kern.shared.settings.KernSettings
import dev.kern.shared.settings.LocalKernSettings
import dev.kern.shared.theme.KernTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke + license-dialog coverage for the Settings screen. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun showSettings() {
        val settings = KernSettings.create(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent {
            CompositionLocalProvider(LocalKernSettings provides settings) {
                KernTheme { SettingsScreen(onBack = {}) }
            }
        }
    }

    @Test
    fun renders_key_sections() {
        showSettings()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Nothing leaves this device").assertIsDisplayed()
        compose.onNodeWithText("Accent color").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Storage access").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun license_row_opens_full_text() {
        showSettings()
        compose.onNodeWithText("License").performScrollTo().performClick()
        compose.onNodeWithText("GNU AFFERO GENERAL PUBLIC LICENSE", substring = true).assertIsDisplayed()
    }
}
