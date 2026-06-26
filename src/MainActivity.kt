package dev.kern

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import dev.kern.browser.IntentHandler
import dev.kern.shared.settings.KernSettings
import dev.kern.shared.settings.LocalKernSettings
import dev.kern.shared.theme.KernTheme

/**
 * The single Activity. Hosts the Compose NavHost and resolves any inbound
 * "Open with" intent into a direct-to-editor start destination.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // If launched via "Open with", jump straight to the right editor.
        val deepLink = IntentHandler.resolve(intent)
        val settings = KernSettings.create(applicationContext)

        setContent {
            CompositionLocalProvider(LocalKernSettings provides settings) {
                KernTheme(
                    themeMode = settings.themeMode,
                    accent = settings.accent,
                    density = settings.density,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        KernNavHost(
                            startDestination = deepLink?.route ?: Destinations.BROWSER,
                        )
                    }
                }
            }
        }
    }
}
