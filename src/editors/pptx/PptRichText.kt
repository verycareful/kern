package dev.kern.editors.pptx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Bridges POI [PptDocument.ShapeRun] models to Compose rich-text editing in PowerPoint slides.
 */
object PptRichText {

    fun toAnnotated(runs: List<PptDocument.ShapeRun>): AnnotatedString = buildAnnotatedString {
        for (run in runs) {
            val start = length
            append(run.text)
            val span = spanOf(run.style)
            if (span != SpanStyle()) addStyle(span, start, length)
            run.style.fontFamily?.let { addStringAnnotation("fontFamily", it, start, length) }
        }
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

    fun restyleSelection(
        value: TextFieldValue,
        transform: (PptDocument.RunStyle) -> PptDocument.RunStyle,
    ): TextFieldValue {
        val sel = value.selection
        val runs = toRuns(value.annotatedString)
        val total = value.annotatedString.text.length
        val from = if (sel.collapsed) 0 else sel.min
        val to = if (sel.collapsed) total else sel.max
        val restyled = reStyleRange(runs, from, to, transform)
        return value.copy(annotatedString = toAnnotated(restyled))
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
