package dev.kern.editors.word

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Bridges the pure [WordDocument] run model to Compose rich-text editing.
 *
 * Visual attributes (bold, italic, underline, strike, size, colour) ride on the
 * [AnnotatedString] as [SpanStyle]s so the text field renders them directly. Font
 * family is NOT rendered (the editor draws every run in the UI face for consistency)
 * but is preserved for round-trip by stashing it in a string annotation, so editing a
 * paragraph never drops the document's named fonts.
 *
 * Formatting actions operate at run granularity: the current value is converted to
 * runs, the selected range is re-styled (splitting runs at the selection boundaries),
 * and the result is converted back. This avoids ambiguous span layering.
 */
object WordRichText {

    private const val FONT_TAG = "ff"

    /** Builds an AnnotatedString carrying each run's formatting. */
    fun toAnnotated(runs: List<WordDocument.Run>): AnnotatedString = buildAnnotatedString {
        for (run in runs) {
            val start = length
            append(run.text)
            val span = spanOf(run.style)
            if (span != SpanStyle()) addStyle(span, start, length)
            run.style.fontFamily?.let { addStringAnnotation(FONT_TAG, it, start, length) }
        }
    }

    /** Splits an AnnotatedString back into runs at every style / font boundary. */
    fun toRuns(text: AnnotatedString): List<WordDocument.Run> {
        if (text.text.isEmpty()) return listOf(WordDocument.Run(""))
        val fonts = text.getStringAnnotations(FONT_TAG, 0, text.text.length)
        val bounds = sortedSetOf(0, text.text.length)
        text.spanStyles.forEach { bounds.add(it.start); bounds.add(it.end) }
        fonts.forEach { bounds.add(it.start); bounds.add(it.end) }
        val points = bounds.filter { it in 0..text.text.length }.toList()
        val out = ArrayList<WordDocument.Run>()
        for (i in 0 until points.size - 1) {
            val s = points[i]
            val e = points[i + 1]
            if (e <= s) continue
            val spans = text.spanStyles.filter { it.start <= s && it.end >= e }.map { it.item }
            val family = fonts.firstOrNull { it.start <= s && it.end >= e }?.item
            out.add(WordDocument.Run(text.text.substring(s, e), styleOf(spans, family)))
        }
        return collapse(out)
    }

    /** The style at the caret (collapsed selection) or the start of the selection. */
    fun styleAt(value: TextFieldValue): WordDocument.RunStyle {
        val runs = toRuns(value.annotatedString)
        val sel = value.selection
        val at = if (sel.collapsed) (sel.start - 1).coerceAtLeast(0) else sel.min
        var pos = 0
        for (run in runs) {
            val end = pos + run.text.length
            if (at < end) return run.style
            pos = end
        }
        return runs.lastOrNull()?.style ?: WordDocument.RunStyle()
    }

    /**
     * Re-styles the current selection with [transform]. A collapsed selection applies
     * to the whole paragraph (a predictable "format this block" on touch).
     */
    fun restyleSelection(value: TextFieldValue, transform: (WordDocument.RunStyle) -> WordDocument.RunStyle): TextFieldValue {
        val sel = value.selection
        val runs = toRuns(value.annotatedString)
        val total = value.annotatedString.text.length
        val from = if (sel.collapsed) 0 else sel.min
        val to = if (sel.collapsed) total else sel.max
        val restyled = reStyleRange(runs, from, to, transform)
        return value.copy(annotatedString = toAnnotated(restyled))
    }

    // ---- style <-> span mapping --------------------------------------------

    private fun spanOf(s: WordDocument.RunStyle): SpanStyle {
        val decorations = buildList {
            if (s.underline) add(TextDecoration.Underline)
            if (s.strike) add(TextDecoration.LineThrough)
        }
        return SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
            fontSize = s.sizePt?.sp ?: TextUnit.Unspecified,
            color = s.colorHex?.let(::hexToColor) ?: Color.Unspecified,
        )
    }

    private fun styleOf(spans: List<SpanStyle>, family: String?): WordDocument.RunStyle {
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var sizePt: Float? = null
        var colorHex: String? = null
        // Later spans override earlier ones per attribute.
        for (span in spans) {
            span.fontWeight?.let { bold = it.weight >= FontWeight.SemiBold.weight }
            span.fontStyle?.let { italic = it == FontStyle.Italic }
            span.textDecoration?.let {
                underline = it.contains(TextDecoration.Underline)
                strike = it.contains(TextDecoration.LineThrough)
            }
            if (span.fontSize != TextUnit.Unspecified) sizePt = span.fontSize.value
            if (span.color != Color.Unspecified) colorHex = colorToHex(span.color)
        }
        return WordDocument.RunStyle(bold, italic, underline, strike, sizePt, colorHex, family)
    }

    private fun reStyleRange(
        runs: List<WordDocument.Run>,
        from: Int,
        to: Int,
        transform: (WordDocument.RunStyle) -> WordDocument.RunStyle,
    ): List<WordDocument.Run> {
        if (to <= from) return runs
        val out = ArrayList<WordDocument.Run>()
        var pos = 0
        for (run in runs) {
            val start = pos
            val end = pos + run.text.length
            pos = end
            if (end <= from || start >= to) {
                out.add(run)
                continue
            }
            val a = (from - start).coerceIn(0, run.text.length)
            val b = (to - start).coerceIn(0, run.text.length)
            if (a > 0) out.add(run.copy(text = run.text.substring(0, a)))
            if (b > a) out.add(WordDocument.Run(run.text.substring(a, b), transform(run.style)))
            if (b < run.text.length) out.add(run.copy(text = run.text.substring(b)))
        }
        return collapse(out)
    }

    private fun collapse(runs: List<WordDocument.Run>): List<WordDocument.Run> {
        val out = ArrayList<WordDocument.Run>()
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.style == run.style) {
                out[out.size - 1] = last.copy(text = last.text + run.text)
            } else {
                out.add(run)
            }
        }
        return out.ifEmpty { listOf(WordDocument.Run("")) }
    }

    // ---- hex helpers --------------------------------------------------------

    fun hexToColor(hex: String): Color {
        val v = hex.removePrefix("#").toLong(16)
        return Color(0xFF000000L or v)
    }

    private fun colorToHex(color: Color): String =
        "%06X".format(color.toArgb() and 0xFFFFFF)

    /** Normalises typed/pasted input to 6 upper-case hex digits, or null if invalid. */
    fun normalizeHex(input: String): String? {
        var h = input.trim().removePrefix("#").uppercase()
        if (h.length == 3 && h.all { it.isHex() }) {
            h = buildString { h.forEach { append(it); append(it) } }
        }
        return if (h.length == 6 && h.all { it.isHex() }) h else null
    }

    // ---- search highlighting -----------------------------------------------

    /** Applies background highlight spans for occurrences of [query]. */
    fun highlightMatches(
        text: AnnotatedString,
        query: String,
        matchColor: Color,
        activeMatchColor: Color,
        isCurrentBlock: Boolean,
        currentMatchIndex: Int,
    ): AnnotatedString {
        if (query.isBlank() || text.text.isEmpty()) return text
        return buildAnnotatedString {
            append(text)
            val raw = text.text
            var startIndex = 0
            var matchCount = 0
            while (startIndex < raw.length) {
                val found = raw.indexOf(query, startIndex, ignoreCase = true)
                if (found < 0) break
                val end = found + query.length
                val isCurrent = isCurrentBlock && matchCount == currentMatchIndex
                addStyle(
                    SpanStyle(background = if (isCurrent) activeMatchColor else matchColor),
                    found,
                    end,
                )
                matchCount++
                startIndex = end
            }
        }
    }

    private fun Char.isHex(): Boolean = this in '0'..'9' || this in 'A'..'F'
}
