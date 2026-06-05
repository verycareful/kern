package dev.kern

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.kern.browser.IntentHandler
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

        setContent {
            KernTheme {
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
