package dev.kern.shared.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.theme.PlexMonoFamily

/**
 * Links a document can hand to another app. Kern itself never touches the network: a
 * web, mail or phone link goes to the app the system picks, and only after the user
 * confirms it in [OpenLinkDialog].
 */
object ExternalLinks {
    private val OPENABLE_SCHEMES = setOf("http", "https", "mailto", "tel")

    fun isOpenable(uri: String): Boolean = uri.substringBefore(':', "").lowercase() in OPENABLE_SCHEMES

    /** Hands [uri] to the app the system picks, or says that none can open it. */
    fun open(context: Context, uri: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Asks before a link leaves Kern, showing the whole address: a link's visible text can
 * say one thing and point at another.
 */
@Composable
fun OpenLinkDialog(link: String, onDismiss: () -> Unit) {
    val colors = KernTheme.colors
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Open link?") },
        text = { Text(link, style = KernType.meta.copy(fontFamily = PlexMonoFamily), color = colors.text) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    ExternalLinks.open(context, link)
                },
            ) { Text("Open", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMid) } },
    )
}
