package dev.kern.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.kern.R
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.ui.KernDivider
import dev.kern.shared.ui.KernIconButton
import dev.kern.shared.ui.KernIcons

// About-section link targets and the license viewer, kept here so SettingsScreen.kt
// stays a manageable size.

internal const val LICENSE_LABEL = "AGPL-3.0"
internal const val SOURCE_URL = "https://github.com/verycareful/kern"
internal const val QYRA_URL = "https://github.com/zParik/Qyra"

/**
 * Open a URL in the user's browser via an external intent. Requires no app
 * network permission: another app handles the request, so Kern's zero-network
 * guarantee is preserved.
 */
internal fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** Full-screen scrollable dialog showing the bundled AGPL-3.0 license text. */
@Composable
internal fun LicenseDialog(onDismiss: () -> Unit) {
    val colors = KernTheme.colors
    val context = LocalContext.current
    val licenseText = remember {
        runCatching {
            context.resources.openRawResource(R.raw.license).bufferedReader().use { it.readText() }
        }.getOrDefault("License text unavailable. See the LICENSE file in the project root.")
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            color = colors.surface,
            shape = RoundedCornerShape(KernRadius.field),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("License", style = KernType.sectionTitle, color = colors.text, modifier = Modifier.weight(1f))
                    KernIconButton(KernIcons.Close, "Close", onDismiss, tint = colors.text)
                }
                KernDivider()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(LICENSE_LABEL, style = KernType.sectionLabel, color = colors.textDim, modifier = Modifier.padding(bottom = 10.dp))
                    Text(licenseText, style = KernType.meta.copy(lineHeight = 16.sp), color = colors.textMid)
                }
            }
        }
    }
}
