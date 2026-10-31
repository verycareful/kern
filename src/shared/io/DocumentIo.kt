package dev.kern.shared.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/**
 * Reads and writes documents directly at their content:// URI. Kern never copies
 * files into an app folder (see ADR 003); everything is edited in place.
 *
 * Used by every editor. URIs arrive either from an "Open with" intent or from the
 * SAF document picker; both yield a content:// URI the ContentResolver can open.
 */
object DocumentIo {

    /** Reads the whole document as bytes. Throws IOException if the URI can't be opened. */
    fun readBytes(context: Context, uri: Uri): ByteArray {
        // Files from the in-app browser scan arrive as file:// URIs (MANAGE_EXTERNAL_STORAGE);
        // read them directly. SAF content:// URIs go through the resolver.
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).readBytes() }
                ?: throw IOException("Invalid file URI $uri")
        }
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Could not open $uri for reading")
    }

    /** Reads the whole document as UTF-8 text. */
    fun readText(context: Context, uri: Uri): String =
        String(readBytes(context, uri), Charsets.UTF_8)

    /** Overwrites the document with [bytes] (truncating any previous content). */
    fun writeBytes(context: Context, uri: Uri, bytes: ByteArray) {
        if (uri.scheme == "file") {
            uri.path?.let { File(it).writeBytes(bytes) } ?: throw IOException("Invalid file URI $uri")
            return
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw IOException("Could not open $uri for writing")
    }

    /** Overwrites the document with [text] as UTF-8. */
    fun writeText(context: Context, uri: Uri, text: String) =
        writeBytes(context, uri, text.toByteArray(Charsets.UTF_8))

    /** The user-facing file name for a URI, falling back to the last path segment. */
    fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") return uri.path?.let { File(it).name } ?: "document"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.let { return it }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    }

    /**
     * The file on shared storage behind [uri], when one can be named: file:// URIs,
     * and documents from the system's external storage and downloads providers,
     * whose document IDs carry the path. Null for any other provider. Used only to
     * find files next to a document (a Markdown file's images); reading them still
     * needs Kern's storage access.
     */
    fun localFile(uri: Uri): File? {
        if (uri.scheme == "file") return uri.path?.let(::File)
        if (uri.scheme != "content") return null
        val docId = uri.lastPathSegment ?: return null
        return when (uri.authority) {
            EXTERNAL_STORAGE_AUTHORITY -> {
                val volume = docId.substringBefore(':')
                val path = docId.substringAfter(':', "")
                if (volume.equals("primary", ignoreCase = true)) {
                    File(Environment.getExternalStorageDirectory(), path)
                } else {
                    File("/storage/$volume", path)
                }
            }
            DOWNLOADS_AUTHORITY -> docId.takeIf { it.startsWith(RAW_PREFIX) }?.let { File(it.removePrefix(RAW_PREFIX)) }
            else -> null
        }
    }

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents"
    private const val RAW_PREFIX = "raw:"

    /**
     * Keeps long-lived read/write access to a picker-provided URI across restarts.
     * Safe to call for "Open with" URIs too; it is a no-op if the grant is not persistable.
     */
    fun tryPersist(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
    }
}
