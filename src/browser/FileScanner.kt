package dev.kern.browser

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import dev.kern.shared.DocumentFormat
import java.io.File

/** A supported document found on disk by [FileScanner]. */
data class ScannedDoc(
    val path: String,
    val name: String,
    val format: DocumentFormat,
    val size: Long,
    val modified: Long,
)

/**
 * Finds supported documents under the public Documents and Downloads folders.
 *
 * Per ADR 003, Kern uses broad file access (MANAGE_EXTERNAL_STORAGE on API 30+,
 * READ_EXTERNAL_STORAGE below) but only ever scans these two user-facing folders -
 * it never copies files and never touches the network.
 */
object FileScanner {

    private const val MAX_DEPTH = 12

    /** Whether Kern currently has permission to read external storage. */
    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /**
     * Walks the enabled roots (Documents and/or Downloads) and returns every
     * supported document found. A folder whose scan toggle is off is skipped
     * entirely, so its files never appear in, or open from, the app.
     */
    fun scan(scanDocuments: Boolean, scanDownloads: Boolean): List<ScannedDoc> {
        val roots = buildList {
            if (scanDocuments) publicDir(Environment.DIRECTORY_DOCUMENTS)?.let(::add)
            if (scanDownloads) publicDir(Environment.DIRECTORY_DOWNLOADS)?.let(::add)
        }
        val out = ArrayList<ScannedDoc>()
        val seen = HashSet<String>()
        for (root in roots) walk(root, out, seen, 0)
        return out
    }

    private fun publicDir(name: String): File? =
        Environment.getExternalStoragePublicDirectory(name)?.takeIf { it.isDirectory }

    private fun walk(dir: File, out: MutableList<ScannedDoc>, seen: MutableSet<String>, depth: Int) {
        if (depth > MAX_DEPTH) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.isDirectory) {
                if (!f.name.startsWith(".")) walk(f, out, seen, depth + 1)
                continue
            }
            val format = DocumentFormat.fromExtension(f.extension) ?: continue
            val path = f.absolutePath
            if (!seen.add(path)) continue // Documents and Downloads can overlap on some devices
            out.add(ScannedDoc(path, f.name, format, f.length(), f.lastModified()))
        }
    }
}
