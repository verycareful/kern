package dev.kern.editors.markdown

/**
 * Wraps a rendered Markdown body in the full preview document.
 *
 * The page is loaded with [BASE_URL] as its base, so the bundled stylesheet and
 * scripts under `assets/preview/` resolve as plain relative paths. Its content
 * security policy allows scripts only from `file:` URLs; the WebView cannot read
 * the file system, which leaves the bundled assets as the only scripts that run.
 * Inline `<script>` blocks, `on*=` handlers and `javascript:` links written in the
 * Markdown are refused by the policy. Images are limited to `data:` URIs, which is
 * how [FileImageResolver] delivers local pictures; anything else shows the page's
 * placeholder, so the preview makes no network requests.
 */
object MarkdownPage {
    const val BASE_URL = "file:///android_asset/preview/"

    private const val CONTENT_POLICY =
        "default-src 'none'; script-src file:; style-src file: 'unsafe-inline'; " +
            "img-src data:; font-src file: data:; base-uri 'none'; form-action 'none'"

    /** Colours from the app theme, as CSS colour strings. */
    data class Theme(
        val dark: Boolean,
        val background: String,
        val text: String,
        val muted: String,
        val border: String,
        val surface: String,
        val accent: String,
    )

    fun build(rendered: MarkdownRenderer.Rendered, theme: Theme): String = buildString {
        append("<!DOCTYPE html>\n<html data-theme=\"")
        append(if (theme.dark) "dark" else "light")
        append("\"><head><meta charset=\"utf-8\">")
        append("<meta http-equiv=\"Content-Security-Policy\" content=\"").append(CONTENT_POLICY).append("\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<link rel=\"stylesheet\" href=\"preview.css\">")
        if (rendered.hasMath) append("<link rel=\"stylesheet\" href=\"katex/katex.min.css\">")
        if (rendered.hasCode) {
            val sheet = if (theme.dark) "github-dark.min.css" else "github.min.css"
            append("<link rel=\"stylesheet\" href=\"highlight/").append(sheet).append("\">")
        }
        append("<style>:root{")
        append("--bg:").append(theme.background).append(';')
        append("--text:").append(theme.text).append(';')
        append("--muted:").append(theme.muted).append(';')
        append("--border:").append(theme.border).append(';')
        append("--surface:").append(theme.surface).append(';')
        append("--accent:").append(theme.accent).append(';')
        append("}</style></head><body><article class=\"markdown-body\">\n")
        append(rendered.body)
        append("\n</article>")
        if (rendered.hasMath) append("<script src=\"katex/katex.min.js\"></script>")
        if (rendered.hasCode) append("<script src=\"highlight/highlight.min.js\"></script>")
        if (rendered.hasMermaid) append("<script src=\"mermaid/mermaid.min.js\"></script>")
        append("<script src=\"preview.js\"></script></body></html>")
    }
}
