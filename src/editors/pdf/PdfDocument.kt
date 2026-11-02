package dev.kern.editors.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import java.io.Closeable
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Read-only PDF page source backed by the framework [PdfRenderer].
 *
 * Viewing needs ZERO native code: Android renders PDFs itself. The Qyra MuPDF
 * bridge is only needed for the edit toolkit (merge, split, redact, ...), which
 * arrives later in 0.1.5.0. This class is the viewer half.
 *
 * [PdfRenderer] is not thread-safe and only one page may be open at a time, so
 * every render is serialized on [lock]. Always [close] to release the fd.
 */
class PdfDocument private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    private val lock = Any()

    val pageCount: Int get() = renderer.pageCount

    /** The page text, where the framework renderer can read it (Android 15 and later). */
    val textLayer: PdfTextLayer? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) FrameworkTextLayer() else null

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private inner class FrameworkTextLayer : PdfTextLayer {
        override fun search(page: Int, query: String): List<PdfTextMatch> = synchronized(lock) {
            renderer.openPage(page).use { p ->
                val width = p.width.toFloat()
                val height = p.height.toFloat()
                p.searchText(query).map { found ->
                    PdfTextMatch(
                        page,
                        found.bounds.map { PageRect(it.left / width, it.top / height, it.right / width, it.bottom / height) },
                    )
                }
            }
        }
    }

    /**
     * Renders [index] into an ARGB bitmap [targetWidthPx] wide, height following the
     * page aspect ratio. The page is painted onto a white background first, since
     * PDF pages are transparent where they have no content.
     */
    fun renderPage(index: Int, targetWidthPx: Int): Bitmap = synchronized(lock) {
        renderer.openPage(index).use { page ->
            val width = targetWidthPx.coerceAtLeast(1)
            val height = (width * page.height.toFloat() / page.width).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }

    companion object {
        /** Opens the PDF at [uri] for reading. Throws [IOException] if it can't be opened. */
        fun open(context: Context, uri: Uri): PdfDocument {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("Could not open $uri")
            return try {
                PdfDocument(pfd, PdfRenderer(pfd))
            } catch (t: Throwable) {
                runCatching { pfd.close() }
                throw t
            }
        }
    }
}
