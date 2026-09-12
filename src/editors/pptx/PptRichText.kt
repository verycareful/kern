package dev.kern.editors.pptx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Bridges POI [PptDocument.ShapeRun] models to Compose rich-text editing in PowerPoint slides.
 */
object PptRichText {

    /**
     * Styled text from runs, with one paragraph style per paragraph from [alignments]
     * (one entry per paragraph, in order; null for a paragraph with no alignment). A
     * paragraph's range includes its trailing newline, which is what keeps Compose
     * from treating the newline as a paragraph of its own.
     */
    fun toAnnotated(runs: List<PptDocument.ShapeRun>, alignments: List<TextAlign?> = emptyList()): AnnotatedString {
        val text = buildAnnotatedString {
            for (run in runs) {
                val start = length
                append(run.text)
                val span = spanOf(run.style)
                if (span != SpanStyle()) addStyle(span, start, length)
                run.style.fontFamily?.let { addStringAnnotation("fontFamily", it, start, length) }
            }
        }
        return withAlignments(text, alignments)
    }

    /** [text] with its paragraph styles replaced by [alignments], one per paragraph. */
    private fun withAlignments(text: AnnotatedString, alignments: List<TextAlign?>): AnnotatedString {
        if (alignments.all { it == null }) return AnnotatedString(text.text, text.spanStyles, emptyList())
        val paragraphs = ArrayList<AnnotatedString.Range<ParagraphStyle>>()
        paragraphRanges(text.text).forEachIndexed { i, range ->
            val align = alignments.getOrNull(i) ?: return@forEachIndexed
            if (range.last >= range.first) {
                paragraphs += AnnotatedString.Range(ParagraphStyle(textAlign = align), range.first, range.last + 1)
            }
        }
        return AnnotatedString(text.text, text.spanStyles, paragraphs)
    }

    /** Each paragraph's character range, the trailing newline included; may be empty. */
    private fun paragraphRanges(text: String): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = 0
        while (true) {
            val newline = text.indexOf('\n', start)
            if (newline < 0) {
                out += start until text.length
                return out
            }
            out += start..newline
            start = newline + 1
        }
    }

    /** The alignment of each paragraph of [text], null where none is set. */
    fun paragraphAlignments(text: AnnotatedString): List<TextAlign?> =
        paragraphRanges(text.text).map { range -> alignmentAt(text, range.first) }

    private fun alignmentAt(text: AnnotatedString, offset: Int): TextAlign? =
        text.paragraphStyles.firstOrNull { offset >= it.start && (offset < it.end || (it.start == it.end && offset == it.start)) }
            ?.item?.textAlign?.takeIf { it != TextAlign.Unspecified }

    /** Aligns every paragraph that the selection touches. */
    fun alignParagraphs(value: TextFieldValue, align: TextAlign): TextFieldValue {
        val text = value.annotatedString
        val selection = value.selection
        val ranges = paragraphRanges(text.text)
        val current = paragraphAlignments(text)
        val updated = ranges.mapIndexed { i, range ->
            // A paragraph is touched when the selection overlaps it, and a collapsed
            // caret touches the paragraph it sits in, including an empty last one.
            val touched = selection.min <= range.last + 1 && selection.max >= range.first
            if (touched) align else current[i]
        }
        return value.copy(annotatedString = withAlignments(text, updated))
    }

    fun toOoxmlAlign(align: TextAlign?): String? = when (align) {
        TextAlign.Center -> "ctr"
        TextAlign.Right, TextAlign.End -> "r"
        TextAlign.Justify -> "just"
        TextAlign.Left, TextAlign.Start -> "l"
        else -> null
    }

    fun fromOoxmlAlign(token: String?): TextAlign? = when (token) {
        "ctr" -> TextAlign.Center
        "r" -> TextAlign.Right
        "just", "justLow", "dist", "thaiDist" -> TextAlign.Justify
        "l" -> TextAlign.Left
        else -> null
    }

    fun toRuns(text: AnnotatedString): List<PptDocument.ShapeRun> {
        if (text.text.isEmpty()) return listOf(PptDocument.ShapeRun(""))
        val bounds = sortedSetOf(0, text.text.length)
        text.spanStyles.forEach { bounds.add(it.start); bounds.add(it.end) }
        val points = bounds.filter { it in 0..text.text.length }.toList()
        val out = ArrayList<PptDocument.ShapeRun>()
        for (i in 0 until points.size - 1) {
            val s = points[i]
            val e = points[i + 1]
            if (e <= s) continue
            val spans = text.spanStyles.filter { it.start <= s && it.end >= e }.map { it.item }
            val fontFamily = text.getStringAnnotations("fontFamily", s, e).firstOrNull()?.item
            out.add(PptDocument.ShapeRun(text.text.substring(s, e), styleOf(spans, fontFamily)))
        }
        return collapse(out)
    }

    /**
     * The styled text after an edit the text field reported as plain text.
     *
     * The legacy `BasicTextField` keeps its buffer as a plain string and hands back
     * `AnnotatedString(toString())` on every edit, so each keystroke arrives with no
     * spans at all. Taking that value as the new text would strip the whole shape of
     * its formatting one character at a time. Instead the edit is located as the
     * region between the longest common prefix and suffix of [before] and [after],
     * the untouched text keeps its runs, and the inserted text takes the style of
     * the character before it, as a word processor does, transformed by [pending] if
     * a formatting command is waiting for the next keystroke.
     */
    fun mergeEdit(
        before: AnnotatedString,
        after: String,
        pending: ((PptDocument.RunStyle) -> PptDocument.RunStyle)? = null,
    ): AnnotatedString {
        val old = before.text
        if (old == after) return before
        var prefix = 0
        val maxPrefix = minOf(old.length, after.length)
        while (prefix < maxPrefix && old[prefix] == after[prefix]) prefix++
        var suffix = 0
        val maxSuffix = minOf(old.length, after.length) - prefix
        while (suffix < maxSuffix && old[old.length - 1 - suffix] == after[after.length - 1 - suffix]) suffix++

        val runs = toRuns(before)
        val inserted = after.substring(prefix, after.length - suffix)
        val kept = ArrayList<PptDocument.ShapeRun>()
        kept += sliceRuns(runs, 0, prefix)
        if (inserted.isNotEmpty()) {
            val base = styleAtOffset(runs, if (prefix > 0) prefix - 1 else 0)
            kept += PptDocument.ShapeRun(inserted, pending?.invoke(base) ?: base)
        }
        kept += sliceRuns(runs, old.length - suffix, old.length)
        // Each new paragraph keeps the alignment of the old character its first
        // character came from; a paragraph born inside the insertion takes the one
        // before the edit, which is what pressing Enter does in any editor.
        val shift = after.length - old.length
        val alignments = paragraphRanges(after).map { range ->
            val start = range.first
            val oldIndex = when {
                start < prefix -> start
                start >= after.length - suffix -> start - shift
                else -> (prefix - 1).coerceAtLeast(0)
            }
            alignmentAt(before, oldIndex.coerceIn(0, old.length))
        }
        return toAnnotated(collapse(kept), alignments)
    }

    /** The runs covering [from, to) of the text the runs spell out, cut at the ends. */
    private fun sliceRuns(runs: List<PptDocument.ShapeRun>, from: Int, to: Int): List<PptDocument.ShapeRun> {
        if (to <= from) return emptyList()
        val out = ArrayList<PptDocument.ShapeRun>()
        var pos = 0
        for (run in runs) {
            val start = pos
            val end = pos + run.text.length
            pos = end
            if (end <= from || start >= to) continue
            out += run.copy(text = run.text.substring(maxOf(from, start) - start, minOf(to, end) - start))
        }
        return out
    }

    /** The style of the character at [offset], or of the last run when past the end. */
    private fun styleAtOffset(runs: List<PptDocument.ShapeRun>, offset: Int): PptDocument.RunStyle {
        var pos = 0
        for (run in runs) {
            val end = pos + run.text.length
            if (offset < end) return run.style
            pos = end
        }
        return runs.lastOrNull()?.style ?: PptDocument.RunStyle()
    }

    /**
     * The range a formatting command applies to when nothing is highlighted: the word
     * around the caret, or null when the caret sits at a boundary, where the command
     * should wait for the next keystroke instead.
     */
    fun wordAt(text: String, offset: Int): IntRange? {
        fun inWord(i: Int) = i in text.indices && (text[i].isLetterOrDigit() || text[i] == '\'')
        if (!inWord(offset - 1) || !inWord(offset)) return null
        var start = offset
        while (inWord(start - 1)) start--
        var end = offset
        while (inWord(end)) end++
        return start until end
    }

    /** Restyles [from, to) of the text. */
    fun restyleRange(
        value: TextFieldValue,
        from: Int,
        to: Int,
        transform: (PptDocument.RunStyle) -> PptDocument.RunStyle,
    ): TextFieldValue {
        val restyled = reStyleRange(toRuns(value.annotatedString), from, to, transform)
        return value.copy(annotatedString = toAnnotated(restyled, paragraphAlignments(value.annotatedString)))
    }

    fun styleAt(value: TextFieldValue): PptDocument.RunStyle {
        val runs = toRuns(value.annotatedString)
        val sel = value.selection
        val at = if (sel.collapsed) (sel.start - 1).coerceAtLeast(0) else sel.min
        var pos = 0
        for (run in runs) {
            val end = pos + run.text.length
            if (at < end) return run.style
            pos = end
        }
        return runs.lastOrNull()?.style ?: PptDocument.RunStyle()
    }

    private fun spanOf(s: PptDocument.RunStyle): SpanStyle {
        val decorations = buildList {
            if (s.underline) add(TextDecoration.Underline)
        }
        // A deck's actual typeface cannot be reproduced without shipping it, so each
        // family is mapped to the closest generic Android provides. The original name is
        // kept as a string annotation and written back on save, so another application
        // still opens the file in Calibri or whatever it was authored in.
        val composeFont = when (s.fontFamily?.lowercase()) {
            "times new roman", "times", "georgia", "garamond", "cambria", "palatino",
            "book antiqua", "constantia", "serif" -> FontFamily.Serif

            "courier new", "courier", "consolas", "lucida console", "monaco", "menlo",
            "monospace" -> FontFamily.Monospace

            null -> null
            else -> FontFamily.SansSerif
        }
        return SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            textDecoration = if (decorations.isNotEmpty()) TextDecoration.combine(decorations) else null,
            fontSize = s.sizePt?.let { it.sp } ?: androidx.compose.ui.unit.TextUnit.Unspecified,
            color = parseHexColor(s.colorHex) ?: Color.Unspecified,
            fontFamily = composeFont,
        )
    }

    private fun styleOf(spans: List<SpanStyle>, fontFamily: String? = null): PptDocument.RunStyle {
        var bold = false
        var italic = false
        var underline = false
        var size: Float? = null
        var colorHex: String? = null

        for (span in spans) {
            if (span.fontWeight == FontWeight.Bold) bold = true
            if (span.fontStyle == FontStyle.Italic) italic = true
            val dec = span.textDecoration
            if (dec != null && dec.contains(TextDecoration.Underline)) underline = true
            if (span.fontSize.isSp) size = span.fontSize.value
            if (span.color != Color.Unspecified) colorHex = colorToHex(span.color)
        }
        return PptDocument.RunStyle(
            bold = bold,
            italic = italic,
            underline = underline,
            sizePt = size,
            colorHex = colorHex,
            fontFamily = fontFamily,
        )
    }

    private fun reStyleRange(
        runs: List<PptDocument.ShapeRun>,
        from: Int,
        to: Int,
        transform: (PptDocument.RunStyle) -> PptDocument.RunStyle,
    ): List<PptDocument.ShapeRun> {
        val out = ArrayList<PptDocument.ShapeRun>()
        var pos = 0
        for (run in runs) {
            val rLen = run.text.length
            val rFrom = pos
            val rTo = pos + rLen
            pos = rTo

            if (rTo <= from || rFrom >= to) {
                out.add(run)
                continue
            }

            val oStart = maxOf(rFrom, from) - rFrom
            val oEnd = minOf(rTo, to) - rFrom

            if (oStart > 0) {
                out.add(run.copy(text = run.text.substring(0, oStart)))
            }
            out.add(
                PptDocument.ShapeRun(
                    text = run.text.substring(oStart, oEnd),
                    style = transform(run.style),
                ),
            )
            if (oEnd < rLen) {
                out.add(run.copy(text = run.text.substring(oEnd)))
            }
        }
        return collapse(out)
    }

    private fun collapse(runs: List<PptDocument.ShapeRun>): List<PptDocument.ShapeRun> {
        val out = ArrayList<PptDocument.ShapeRun>()
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.style == run.style) {
                out[out.lastIndex] = last.copy(text = last.text + run.text)
            } else {
                out.add(run)
            }
        }
        return out
    }

    private fun parseHexColor(hex: String?): Color? {
        if (hex == null) return null
        val clean = hex.removePrefix("#")
        val parsed = clean.toLongOrNull(16) ?: return null
        return if (clean.length <= 6) Color(0xFF000000 or parsed) else Color(parsed)
    }

    private fun colorToHex(color: Color): String {
        val argb = color.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format("#%02X%02X%02X", r, g, b)
    }
}
