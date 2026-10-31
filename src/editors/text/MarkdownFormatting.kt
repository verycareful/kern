package dev.kern.editors.text

/**
 * The toolbar's Markdown edits, as pure functions of the text and the selection.
 * Each returns a [TextEdit]: the range to replace, what replaces it, and where the
 * selection lands afterwards, in the coordinates of the edited text.
 *
 * Inline marks (bold, italic, strikethrough, code) wrap the selection, or unwrap
 * it when the marks are already there. Line marks (headings, lists, quotes) apply
 * to every line the selection touches. Blocks (code fence, table, rule) go on
 * lines of their own, separated from the text around them by a blank line.
 */
object MarkdownFormatting {

    /** Replace [start, end) with [replacement], then select [selectionStart, selectionEnd). */
    data class TextEdit(
        val start: Int,
        val end: Int,
        val replacement: String,
        val selectionStart: Int,
        val selectionEnd: Int,
    ) {
        /** The text after this edit, used by the tests. */
        fun applyTo(text: String): String = text.substring(0, start) + replacement + text.substring(end)
    }

    enum class Inline(val marker: String, val placeholder: String) {
        BOLD("**", "bold text"),
        ITALIC("*", "italic text"),
        STRIKETHROUGH("~~", "struck text"),
        CODE("`", "code"),
    }

    enum class LineMark { BULLET, NUMBERED, TASK, QUOTE }

    private const val MAX_HEADING_LEVEL = 6
    private val HEADING = Regex("^(#{1,6}) ")
    private val BULLET = Regex("^(\\s*)[-*+] (?!\\[[ xX]] )")
    private val NUMBERED = Regex("^(\\s*)\\d+[.)] ")
    private val TASK = Regex("^(\\s*)[-*+] \\[[ xX]] ")
    private val QUOTE = Regex("^> ?")

    fun inline(text: String, selStart: Int, selEnd: Int, mark: Inline): TextEdit {
        val (start, end) = ordered(selStart, selEnd)
        val m = mark.marker
        val wrappedOutside = start >= m.length && end + m.length <= text.length &&
            text.regionMatches(start - m.length, m, 0, m.length) && text.regionMatches(end, m, 0, m.length) &&
            !(mark == Inline.ITALIC && isBoldMarkerAround(text, start, end))
        if (wrappedOutside) {
            val inner = text.substring(start, end)
            return TextEdit(start - m.length, end + m.length, inner, start - m.length, end - m.length)
        }
        val selected = text.substring(start, end)
        if (selected.length >= 2 * m.length && selected.startsWith(m) && selected.endsWith(m)) {
            val inner = selected.substring(m.length, selected.length - m.length)
            return TextEdit(start, end, inner, start, start + inner.length)
        }
        val inner = selected.ifEmpty { mark.placeholder }
        return TextEdit(start, end, m + inner + m, start + m.length, start + m.length + inner.length)
    }

    /** `*` sitting inside `**`: the selection is bold, so italic wraps it rather than unwrapping. */
    private fun isBoldMarkerAround(text: String, start: Int, end: Int): Boolean =
        start >= 2 && end + 2 <= text.length && text[start - 2] == '*' && text[end + 1] == '*'

    /** Cycles the touched lines through no heading, `#`, `##` ... `######`, then back to none. */
    fun heading(text: String, selStart: Int, selEnd: Int): TextEdit = transformLines(text, selStart, selEnd) { lines ->
        val level = HEADING.find(lines.first())?.groupValues?.get(1)?.length ?: 0
        val next = if (level >= MAX_HEADING_LEVEL) 0 else level + 1
        lines.map { line ->
            val bare = line.replaceFirst(HEADING, "")
            if (next == 0) bare else "#".repeat(next) + " " + bare
        }
    }

    fun lineMark(text: String, selStart: Int, selEnd: Int, mark: LineMark): TextEdit =
        transformLines(text, selStart, selEnd) { lines ->
            val pattern = patternFor(mark)
            val content = lines.filter { it.isNotBlank() }
            val allMarked = content.isNotEmpty() && content.all { pattern.containsMatchIn(it) }
            var number = 0
            lines.map { line ->
                when {
                    allMarked -> if (mark == LineMark.QUOTE) line.replaceFirst(QUOTE, "") else line.replaceFirst(pattern, "$1")
                    line.isBlank() && lines.size > 1 -> line
                    else -> {
                        val indent = line.takeWhile { it == ' ' || it == '\t' }
                        val bare = stripListMarks(line.substring(indent.length))
                        number++
                        when (mark) {
                            LineMark.BULLET -> "$indent- $bare"
                            LineMark.NUMBERED -> "$indent$number. $bare"
                            LineMark.TASK -> "$indent- [ ] $bare"
                            LineMark.QUOTE -> "> $line"
                        }
                    }
                }
            }
        }

    fun link(text: String, selStart: Int, selEnd: Int): TextEdit = linkLike(text, selStart, selEnd, "", "link text", "https://")

    fun image(text: String, selStart: Int, selEnd: Int): TextEdit = linkLike(text, selStart, selEnd, "!", "description", "image.png")

    /**
     * `[label](target)`. With a selection the selection becomes the label and the
     * target is selected for typing; with none, the placeholder label is selected.
     */
    private fun linkLike(text: String, selStart: Int, selEnd: Int, prefix: String, label: String, target: String): TextEdit {
        val (start, end) = ordered(selStart, selEnd)
        val selected = text.substring(start, end)
        val shown = selected.ifEmpty { label }
        val replacement = "$prefix[$shown]($target)"
        return if (selected.isEmpty()) {
            val labelStart = start + prefix.length + 1
            TextEdit(start, end, replacement, labelStart, labelStart + shown.length)
        } else {
            val targetStart = start + prefix.length + shown.length + 3
            TextEdit(start, end, replacement, targetStart, targetStart + target.length)
        }
    }

    /** A fenced code block around the touched lines, or an empty one with the caret inside. */
    fun codeBlock(text: String, selStart: Int, selEnd: Int): TextEdit {
        val (start, end) = ordered(selStart, selEnd)
        if (start == end) return insertBlock(text, start, "```\n\n```", caretOffset = 4)
        val lineStart = lineStartOf(text, start)
        val lineEnd = lineEndOf(text, if (end > start && text.getOrNull(end - 1) == '\n') end - 1 else end)
        val body = text.substring(lineStart, lineEnd)
        val block = "```\n$body\n```"
        val (before, after) = blockPadding(text, lineStart, lineEnd)
        val replacement = before + block + after
        val bodyStart = lineStart + before.length + 4
        return TextEdit(lineStart, lineEnd, replacement, bodyStart, bodyStart + body.length)
    }

    /** A two-column table skeleton with the first header cell selected. */
    fun table(text: String, selStart: Int, selEnd: Int): TextEdit {
        val (start, _) = ordered(selStart, selEnd)
        val skeleton = "| Column 1 | Column 2 |\n| --- | --- |\n|  |  |"
        val at = lineEndOf(text, start)
        val edit = insertBlock(text, at, skeleton, caretOffset = 2)
        return edit.copy(selectionEnd = edit.selectionStart + "Column 1".length)
    }

    fun horizontalRule(text: String, selStart: Int, selEnd: Int): TextEdit {
        val (start, _) = ordered(selStart, selEnd)
        return insertBlock(text, lineEndOf(text, start), "---", caretOffset = 3)
    }

    private fun insertBlock(text: String, at: Int, block: String, caretOffset: Int): TextEdit {
        val position = if (at > 0 && text[at - 1] != '\n' && at < text.length && text[at] != '\n') lineEndOf(text, at) else at
        val (before, after) = blockPadding(text, position, position)
        val caret = position + before.length + caretOffset
        return TextEdit(position, position, before + block + after, caret, caret)
    }

    /**
     * The line breaks a block at [start, end) needs so that a blank line separates
     * it from any text before and after, without doubling blank lines already there.
     */
    private fun blockPadding(text: String, start: Int, end: Int): Pair<String, String> {
        val before = when {
            start == 0 -> ""
            text.regionMatches(start - 2, "\n\n", 0, 2) || start == 1 && text[0] == '\n' -> ""
            text[start - 1] == '\n' -> "\n"
            else -> "\n\n"
        }
        val after = when {
            end >= text.length -> "\n"
            text.startsWith("\n\n", end) -> ""
            text[end] == '\n' -> "\n"
            else -> "\n\n"
        }
        return before to after
    }

    private fun patternFor(mark: LineMark): Regex = when (mark) {
        LineMark.BULLET -> BULLET
        LineMark.NUMBERED -> NUMBERED
        LineMark.TASK -> TASK
        LineMark.QUOTE -> QUOTE
    }

    private fun stripListMarks(line: String): String =
        line.replaceFirst(TASK, "").let { if (it != line) it else line.replaceFirst(BULLET, "").replaceFirst(NUMBERED, "") }

    /** Applies [transform] to the whole lines the selection touches and keeps them selected. */
    private fun transformLines(text: String, selStart: Int, selEnd: Int, transform: (List<String>) -> List<String>): TextEdit {
        val (start, end) = ordered(selStart, selEnd)
        val lineStart = lineStartOf(text, start)
        val lastLineEnd = lineEndOf(text, if (end > start && text.getOrNull(end - 1) == '\n') end - 1 else end)
        val original = text.substring(lineStart, lastLineEnd)
        val replaced = transform(original.split('\n')).joinToString("\n")
        if (start == end && original.split('\n').size == 1) {
            val caret = (start - lineStart + replaced.length - original.length).coerceIn(0, replaced.length)
            return TextEdit(lineStart, lastLineEnd, replaced, lineStart + caret, lineStart + caret)
        }
        return TextEdit(lineStart, lastLineEnd, replaced, lineStart, lineStart + replaced.length)
    }

    private fun lineStartOf(text: String, index: Int): Int = text.lastIndexOf('\n', index - 1) + 1

    private fun lineEndOf(text: String, index: Int): Int = text.indexOf('\n', index).let { if (it < 0) text.length else it }

    private fun ordered(a: Int, b: Int): Pair<Int, Int> = if (a <= b) a to b else b to a
}
