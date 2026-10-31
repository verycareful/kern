package dev.kern.editors.markdown

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.front.matter.YamlFrontMatterBlock
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.front.matter.YamlFrontMatterNode
import org.commonmark.ext.gfm.alerts.AlertsExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.CustomNode
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.InlineParserState
import org.commonmark.parser.beta.ParsedInline
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.text.AsciiMatcher
import org.jsoup.Jsoup

/**
 * Turns Markdown source into the HTML body of the preview page.
 *
 * Parsing follows CommonMark with the GitHub additions: tables, strikethrough,
 * task lists, autolinks, footnotes, alerts (`> [!NOTE]`), heading anchors and YAML
 * front matter (shown as a table, as GitHub does). On top of those it recognises
 * `$...$` and `$$...$$` maths, ```` ```math ```` and ```` ```mermaid ```` fences,
 * and `:shortcode:` emoji. Maths, diagrams and code colouring are finished in the
 * page by the bundled scripts; [Rendered] says which of them the page needs.
 *
 * Raw HTML in the source passes through unchanged. The page's content security
 * policy, not this renderer, is what stops scripts inside it from running.
 */
class MarkdownRenderer(private val emoji: Map<String, String> = emptyMap()) {

    /** A rendered body plus the page features it uses. */
    data class Rendered(
        val body: String,
        val hasMath: Boolean,
        val hasCode: Boolean,
        val hasMermaid: Boolean,
    )

    /** Maps an image `src` from the document to a `data:` URI, or null to leave it as written. */
    fun interface ImageResolver {
        fun dataUri(src: String): String?
    }

    private val extensions: List<Extension> = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        AlertsExtension.create(),
        TaskListItemsExtension.create(),
        AutolinkExtension.create(),
        FootnotesExtension.builder().inlineFootnotes(true).build(),
        HeadingAnchorExtension.create(),
        YamlFrontMatterExtension.create(),
    )

    private val parser: Parser = Parser.builder()
        .extensions(extensions)
        .customInlineContentParserFactory(MathParserFactory)
        .apply { if (emoji.isNotEmpty()) customInlineContentParserFactory(EmojiParserFactory(emoji)) }
        .build()

    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .nodeRendererFactory { MathRenderer(it) }
        .nodeRendererFactory { FenceRenderer(it) }
        .nodeRendererFactory { FrontMatterRenderer(it) }
        .build()

    fun render(source: String, images: ImageResolver? = null): Rendered {
        var body = renderer.render(parser.parse(source))
        if (images != null && body.contains("<img", ignoreCase = true)) body = inlineImages(body, images)
        return Rendered(
            body = body,
            hasMath = body.contains(MATH_CLASS),
            hasCode = body.contains(CODE_CLASS_PREFIX),
            hasMermaid = body.contains(MERMAID_CLASS),
        )
    }

    private fun inlineImages(body: String, images: ImageResolver): String {
        val doc = Jsoup.parseBodyFragment(body)
        doc.outputSettings().prettyPrint(false)
        var changed = false
        for (img in doc.select("img[src]")) {
            val src = img.attr("src")
            if (!isDocumentRelative(src)) continue
            val uri = images.dataUri(src) ?: continue
            img.attr("src", uri)
            changed = true
        }
        return if (changed) doc.body().html() else body
    }

    /** Maths written as `$tex$` (inline) or `$$tex$$` (display). */
    class MathNode(val tex: String, val display: Boolean) : CustomNode()

    private object MathParserFactory : InlineContentParserFactory {
        override fun getTriggerCharacters(): Set<Char> = setOf('$')
        override fun create(): InlineContentParser = MathParser()
    }

    /**
     * Reads `$...$` and `$$...$$` the way GitHub does. Inline maths may not open
     * with a space or close after one, and a closing `$` followed by a digit does
     * not close, so prices such as "$5 and $10" stay text.
     */
    private class MathParser : InlineContentParser {
        override fun tryParse(state: InlineParserState): ParsedInline? {
            val scanner = state.scanner()
            val opening = scanner.matchMultiple('$')
            if (opening > 2) return ParsedInline.none()
            val contentStart = scanner.position()
            if (opening == 1 && scanner.peek().isWhitespace()) return ParsedInline.none()
            while (scanner.find('$') >= 0) {
                if (scanner.peekPreviousCodePoint() == '\\'.code) {
                    scanner.next()
                    continue
                }
                val contentEnd = scanner.position()
                val closing = scanner.matchMultiple('$')
                if (closing != opening) continue
                val tex = scanner.getSource(contentStart, contentEnd).content
                if (tex.isBlank()) return ParsedInline.none()
                if (opening == 1 && (tex.last().isWhitespace() || scanner.peek().isDigit())) continue
                return ParsedInline.of(MathNode(tex.trim(), display = opening == 2), scanner.position())
            }
            return ParsedInline.none()
        }
    }

    private class EmojiParserFactory(private val table: Map<String, String>) : InlineContentParserFactory {
        override fun getTriggerCharacters(): Set<Char> = setOf(':')
        override fun create(): InlineContentParser = EmojiParser(table)
    }

    private class EmojiParser(private val table: Map<String, String>) : InlineContentParser {
        override fun tryParse(state: InlineParserState): ParsedInline? {
            val scanner = state.scanner()
            scanner.next()
            val nameStart = scanner.position()
            if (scanner.match(EMOJI_NAME) == 0) return ParsedInline.none()
            val nameEnd = scanner.position()
            if (!scanner.next(':')) return ParsedInline.none()
            val glyph = table[scanner.getSource(nameStart, nameEnd).content] ?: return ParsedInline.none()
            return ParsedInline.of(Text(glyph), scanner.position())
        }
    }

    private class MathRenderer(private val context: HtmlNodeRendererContext) : NodeRenderer {
        override fun getNodeTypes(): Set<Class<out Node>> = setOf(MathNode::class.java)
        override fun render(node: Node) {
            val math = node as MathNode
            val html = context.writer
            val kind = if (math.display) MATH_DISPLAY else MATH_INLINE
            html.tag("span", mapOf("class" to "$MATH_CLASS $kind"))
            html.text(math.tex)
            html.tag("/span")
        }
    }

    /**
     * Fenced code: ```` ```math ```` becomes display maths, ```` ```mermaid ```` a
     * diagram, and any other fence the usual `<pre><code class="language-x">` that
     * the page colours.
     */
    private class FenceRenderer(private val context: HtmlNodeRendererContext) : NodeRenderer {
        override fun getNodeTypes(): Set<Class<out Node>> = setOf(FencedCodeBlock::class.java)
        override fun render(node: Node) {
            val fence = node as FencedCodeBlock
            val html = context.writer
            val language = fence.info.orEmpty().trim().substringBefore(' ').lowercase()
            html.line()
            when (language) {
                "math" -> {
                    html.tag("div", mapOf("class" to "$MATH_CLASS $MATH_DISPLAY"))
                    html.text(fence.literal)
                    html.tag("/div")
                }
                "mermaid" -> {
                    html.tag("pre", mapOf("class" to MERMAID_CLASS))
                    html.text(fence.literal)
                    html.tag("/pre")
                }
                else -> {
                    val codeAttrs = if (language.isEmpty()) emptyMap() else mapOf("class" to CODE_CLASS_PREFIX + language)
                    html.tag("pre", context.extendAttributes(fence, "pre", emptyMap()))
                    html.tag("code", context.extendAttributes(fence, "code", codeAttrs))
                    html.text(fence.literal)
                    html.tag("/code")
                    html.tag("/pre")
                }
            }
            html.line()
        }
    }

    /** YAML front matter as a one-row table, keys across the header. */
    private class FrontMatterRenderer(private val context: HtmlNodeRendererContext) : NodeRenderer {
        override fun getNodeTypes(): Set<Class<out Node>> = setOf(YamlFrontMatterBlock::class.java)
        override fun render(node: Node) {
            val entries = generateSequence(node.firstChild) { it.next }.filterIsInstance<YamlFrontMatterNode>().toList()
            if (entries.isEmpty()) return
            val html = context.writer
            html.line()
            html.tag("table", mapOf("class" to "front-matter"))
            html.tag("thead")
            html.tag("tr")
            for (entry in entries) {
                html.tag("th")
                html.text(entry.key)
                html.tag("/th")
            }
            html.tag("/tr")
            html.tag("/thead")
            html.tag("tbody")
            html.tag("tr")
            for (entry in entries) {
                html.tag("td")
                html.text(entry.values.joinToString(", "))
                html.tag("/td")
            }
            html.tag("/tr")
            html.tag("/tbody")
            html.tag("/table")
            html.line()
        }
    }

    companion object {
        const val MATH_CLASS = "math"
        const val MATH_INLINE = "math-inline"
        const val MATH_DISPLAY = "math-display"
        const val MERMAID_CLASS = "mermaid"
        const val CODE_CLASS_PREFIX = "language-"

        private val EMOJI_NAME = AsciiMatcher.builder()
            .range('a', 'z').range('A', 'Z').range('0', '9').anyOf("_+-")
            .build()

        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

        /** A `src` naming a file beside the document: no scheme, not protocol-relative, not empty. */
        internal fun isDocumentRelative(src: String): Boolean =
            src.isNotBlank() && !src.startsWith("//") && !src.startsWith("#") && !SCHEME.containsMatchIn(src)
    }
}
