package dev.kern.editors.pdf

/**
 * A rectangle on a page as fractions of the page's width and height, so it can be
 * drawn over the page at whatever size the page is shown.
 */
data class PageRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A point on a page as fractions of the page's width and height. */
data class PagePoint(val x: Float, val y: Float)

/** One match of a search: the page it is on and the boxes covering its text (one per line it spans). */
data class PdfTextMatch(val page: Int, val rects: List<PageRect>)

/** One line of a page's text and the box it is drawn in. */
data class PdfTextLine(val text: String, val rect: PageRect)

/**
 * Pairs a page's text with the line boxes the renderer reports for it. When there is one
 * box per line they are paired line by line; otherwise the whole text gets one box
 * covering every line, so it can still be selected, if less exactly.
 */
fun pairLines(text: String, boxes: List<PageRect>): List<PdfTextLine> {
    if (text.isBlank() || boxes.isEmpty()) return emptyList()
    val lines = text.split('\n')
    if (lines.size == boxes.size) {
        return lines.zip(boxes).filter { it.first.isNotBlank() }.map { (line, box) -> PdfTextLine(line, box) }
    }
    val union = PageRect(boxes.minOf { it.left }, boxes.minOf { it.top }, boxes.maxOf { it.right }, boxes.maxOf { it.bottom })
    return listOf(PdfTextLine(text, union))
}

/** A link on a page: the boxes that take a tap, and where it goes. */
sealed interface PdfLink {
    val rects: List<PageRect>

    /** A link out of the document, such as a web address. */
    data class External(override val rects: List<PageRect>, val uri: String) : PdfLink

    /** A link to another page of the same document (0-based [page]). */
    data class GoTo(override val rects: List<PageRect>, val page: Int) : PdfLink
}

/** The link whose boxes contain [point], if any. */
fun List<PdfLink>.at(point: PagePoint): PdfLink? = firstOrNull { link ->
    link.rects.any { point.x in it.left..it.right && point.y in it.top..it.bottom }
}

/**
 * What Kern can do with a PDF's text, as opposed to its pictures of pages. The framework
 * renderer provides it from Android 15 on; another PDF engine can provide the same
 * calls without the screens changing.
 */
interface PdfTextLayer {
    /** Every match of [query] on [page], in reading order. The search is literal and ignores case. */
    fun search(page: Int, query: String): List<PdfTextMatch>

    /** The text of [page], line by line, with where each line is drawn. */
    fun lines(page: Int): List<PdfTextLine>

    /** Every link on [page]: links out of the document, then links within it. */
    fun links(page: Int): List<PdfLink>
}

/** Why a PDF has no text layer on this device, for the find bar to show. */
const val PDF_TEXT_NEEDS_ANDROID_15 = "Searching PDFs needs Android 15 or later"
