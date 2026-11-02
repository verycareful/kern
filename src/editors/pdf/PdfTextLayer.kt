package dev.kern.editors.pdf

/**
 * A rectangle on a page as fractions of the page's width and height, so it can be
 * drawn over the page at whatever size the page is shown.
 */
data class PageRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** One match of a search: the page it is on and the boxes covering its text (one per line it spans). */
data class PdfTextMatch(val page: Int, val rects: List<PageRect>)

/**
 * What Kern can do with a PDF's text, as opposed to its pictures of pages. The framework
 * renderer provides it from Android 15 on; another PDF engine can provide the same
 * calls without the screens changing.
 */
interface PdfTextLayer {
    /** Every match of [query] on [page], in reading order. The search is literal and ignores case. */
    fun search(page: Int, query: String): List<PdfTextMatch>
}

/** Why a PDF has no text layer on this device, for the find bar to show. */
const val PDF_TEXT_NEEDS_ANDROID_15 = "Searching PDFs needs Android 15 or later"
