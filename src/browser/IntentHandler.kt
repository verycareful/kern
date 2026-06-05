package dev.kern.browser

import android.content.Intent
import android.net.Uri
import dev.kern.Destinations
import dev.kern.shared.DocumentFormat

/**
 * Resolves an inbound Intent (typically ACTION_VIEW / ACTION_EDIT from "Open with")
 * into a NavHost start route, so external launches land directly on the editor.
 *
 * Stub: the URI is passed through encoded for now. Persistable-permission handling
 * and actual file loading arrive with each format's editor milestone.
 */
object IntentHandler {

    data class DeepLink(val format: DocumentFormat, val route: String)

    fun resolve(intent: Intent?): DeepLink? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_EDIT) return null

        val uri: Uri = intent.data ?: return null
        val format = DocumentFormat.fromMimeType(intent.type)
            ?: DocumentFormat.fromExtension(uri.lastPathSegment?.substringAfterLast('.') ?: "")
            ?: return null

        val encoded = Uri.encode(uri.toString())
        return DeepLink(format, "${Destinations.routeFor(format)}/$encoded")
    }
}
