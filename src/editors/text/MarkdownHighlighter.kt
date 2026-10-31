package dev.kern.editors.text

/**
 * Finds the Markdown syntax in the source so the editor can colour it. Works line
 * by line: fenced code blocks, headings, quotes, list markers and rules are
 * recognised from the start of a line, and inline code, emphasis, strikethrough,
 * links and maths within a line. It colours, it does not parse: an odd construct
 * is at worst coloured plainly, and the preview stays the reference rendering.
 */
object MarkdownHighlighter {

    enum class Kind { HEADING, STRONG, EMPHASIS, STRIKE, CODE, CODE_BLOCK, QUOTE, LIST_MARKER, LINK, URL, RULE, MATH, HTML }

    data class Span(val start: Int, val end: Int, val kind: Kind)

    /** Beyond this the editor shows plain text: colouring a very large file on every keystroke would lag typing. */
    const val MAX_HIGHLIGHT_CHARS = 200_000

    private val FENCE = Regex("^ {0,3}(`{3,}|~{3,})")
    private val HEADING = Regex("^ {0,3}#{1,6}(\\s|$)")
    private val QUOTE = Regex("^ {0,3}>")
    private val LIST = Regex("^\\s*([-*+]|\\d{1,9}[.)])(\\s+\\[[ xX]])?(?=\\s)")
    private val RULE = Regex("^ {0,3}([-*_])( *\\1){2,} *$")

    private val CODE = Regex("(`+)(?!`)(.+?)(?<!`)\\1(?!`)")
    private val STRONG = Regex("(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1")
    private val EMPHASIS = Regex("(?<![*_\\w])([*_])(?![\\s*_])(.+?)(?<![\\s*_])\\1(?![*_\\w])")
    private val STRIKE = Regex("(~~?)(?=\\S)(.+?)(?<=\\S)\\1")
    private val LINK = Regex("!?\\[[^\\]\\n]*]\\([^)\\n]*\\)|!?\\[[^\\]\\n]*]\\[[^\\]\\n]*]")
    private val URL = Regex("<?(https?://|www\\.)[^\\s<>()]+>?")
    private val MATH = Regex("\\$\\$.+?\\$\\$|(?<![\\\\$])\\$(?=\\S)[^$\\n]+?(?<=\\S)\\$(?!\\d)")
    private val HTML = Regex("</?[a-zA-Z][a-zA-Z0-9-]*(\\s[^<>\\n]*)?/?>|<!--.*?-->")

    fun spans(text: CharSequence): List<Span> {
        if (text.length > MAX_HIGHLIGHT_CHARS) return emptyList()
        val out = ArrayList<Span>()
        var fence: String? = null
        var fenceStart = 0
        var lineStart = 0
        while (lineStart <= text.length) {
            val newline = indexOf(text, '\n', lineStart)
            val lineEnd = if (newline < 0) text.length else newline
            val line = text.subSequence(lineStart, lineEnd).toString()
            val fenceMatch = FENCE.find(line)
            if (fence != null) {
                if (fenceMatch != null && fenceMatch.groupValues[1].startsWith(fence) && line.trim().all { it == fence!![0] }) {
                    out.add(Span(fenceStart, lineEnd, Kind.CODE_BLOCK))
                    fence = null
                }
            } else if (fenceMatch != null) {
                fence = fenceMatch.groupValues[1]
                fenceStart = lineStart
            } else {
                lineSpans(line, lineStart, out)
            }
            if (newline < 0) break
            lineStart = newline + 1
        }
        if (fence != null) out.add(Span(fenceStart, text.length, Kind.CODE_BLOCK))
        return out
    }

    private fun lineSpans(line: String, offset: Int, out: MutableList<Span>) {
        if (line.isBlank()) return
        when {
            HEADING.containsMatchIn(line) -> {
                out.add(Span(offset, offset + line.length, Kind.HEADING))
                return
            }
            RULE.matches(line) -> {
                out.add(Span(offset, offset + line.length, Kind.RULE))
                return
            }
            QUOTE.containsMatchIn(line) -> out.add(Span(offset, offset + line.length, Kind.QUOTE))
        }
        LIST.find(line)?.let { out.add(Span(offset + it.range.first, offset + it.range.last + 1, Kind.LIST_MARKER)) }

        val code = CODE.findAll(line).map { it.range }.toList()
        code.forEach { out.add(Span(offset + it.first, offset + it.last + 1, Kind.CODE)) }
        fun outsideCode(range: IntRange) = code.none { it.first <= range.last && range.first <= it.last }
        fun add(regex: Regex, kind: Kind) = regex.findAll(line).forEach {
            if (outsideCode(it.range)) out.add(Span(offset + it.range.first, offset + it.range.last + 1, kind))
        }
        add(MATH, Kind.MATH)
        add(LINK, Kind.LINK)
        add(URL, Kind.URL)
        add(HTML, Kind.HTML)
        add(STRONG, Kind.STRONG)
        add(EMPHASIS, Kind.EMPHASIS)
        add(STRIKE, Kind.STRIKE)
    }

    private fun indexOf(text: CharSequence, c: Char, from: Int): Int {
        for (i in from until text.length) if (text[i] == c) return i
        return -1
    }
}
