package dev.kern.shared.find

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString

/**
 * [this] with `[start, end)` replaced by [replacement], keeping every style.
 *
 * The replacement takes the styles of the first character it replaces, so replacing a
 * bold word gives bold text and a paragraph keeps its alignment. Span styles, paragraph
 * styles and string annotations outside the range keep their place, shifted by the change
 * in length; one that covered only replaced characters other than the first disappears.
 */
fun AnnotatedString.replaceKeepingStyle(start: Int, end: Int, replacement: String): AnnotatedString {
    require(start in 0..end && end <= length) { "Range $start..$end is outside 0..$length" }
    val replacedEnd = start + replacement.length
    val shift = replacement.length - (end - start)
    fun map(position: Int) = when {
        position <= start -> position
        position >= end -> position + shift
        else -> replacedEnd
    }
    val source = this
    return buildAnnotatedString {
        append(source.text, 0, start)
        append(replacement)
        append(source.text, end, source.length)
        for (range in source.spanStyles) {
            val a = map(range.start)
            val b = map(range.end)
            if (b > a) addStyle(range.item, a, b)
        }
        for (range in source.paragraphStyles) {
            val a = map(range.start)
            val b = map(range.end)
            if (b > a) addStyle(range.item, a, b)
        }
        for (range in source.getStringAnnotations(0, source.length)) {
            val a = map(range.start)
            val b = map(range.end)
            if (b > a) addStringAnnotation(range.tag, range.item, a, b)
        }
    }
}

/** [text] with [match] replaced, keeping styles as [replaceKeepingStyle] describes. */
fun TextFinder.replaceAt(text: AnnotatedString, match: TextMatch, replacement: String): Result<AnnotatedString> =
    expand(text.text, match, replacement).map { text.replaceKeepingStyle(match.start, match.end, it) }

/** [text] with every match replaced, keeping styles as [replaceKeepingStyle] describes. */
fun TextFinder.replaceAll(text: AnnotatedString, replacement: String): Result<Replaced<AnnotatedString>> =
    runCatching {
        val plain = text.text
        val matches = findAll(plain)
        var out = text
        // Last match first, so the offsets of the matches still to replace stay valid.
        for (match in matches.asReversed()) {
            out = out.replaceKeepingStyle(match.start, match.end, expand(plain, match, replacement).getOrThrow())
        }
        Replaced(out, matches.size)
    }
