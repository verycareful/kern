package dev.kern.editors.markdown

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.ContextThemeWrapper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import dev.kern.shared.theme.KernColorScheme
import dev.kern.shared.ui.ExternalLinks
import dev.kern.shared.ui.FastScrollBox
import dev.kern.shared.ui.FastScrollMath
import dev.kern.shared.ui.FastScrollTarget
import dev.kern.shared.ui.OpenLinkDialog
import java.io.ByteArrayInputStream

/**
 * Shows a rendered Markdown page in a WebView.
 *
 * The WebView may run JavaScript (the bundled KaTeX, highlight.js and Mermaid),
 * but it has no file or content access, so it can load only the page Kern hands
 * it and the bundled assets. Requests for anything outside the asset folder get
 * an empty answer here, before reaching the network. Tapping a web, mail or phone
 * link asks first, showing the whole address, then opens it in the app the system
 * picks; links inside the page are handled by the page itself. [onScroll] is called as the page scrolls, and the
 * right edge carries a fast scroll bar in [hue].
 *
 * Find uses the WebView's own search, which is literal and ignores case: [findQuery]
 * is searched (empty clears it), [findCurrent] is the match to show, and
 * [onFindResult] receives the current match and the total as the WebView counts them.
 */
@Composable
fun MarkdownPreview(
    rendered: MarkdownRenderer.Rendered,
    colors: KernColorScheme,
    hue: Color,
    modifier: Modifier = Modifier,
    onScroll: () -> Unit = {},
    findQuery: String = "",
    findCurrent: Int = 0,
    onFindResult: (current: Int, total: Int) -> Unit = { _, _ -> },
) {
    val currentOnFindResult by rememberUpdatedState(onFindResult)
    var pendingLink by remember { mutableStateOf<String?>(null) }
    pendingLink?.let { OpenLinkDialog(it, onDismiss = { pendingLink = null }) }
    val currentOnScroll by rememberUpdatedState(onScroll)
    val theme = remember(colors) { colors.toPreviewTheme() }
    val page = remember(rendered, theme) { MarkdownPage.build(rendered, theme) }
    val background = colors.bg.toArgb()
    val scrollTarget = remember { WebViewScrollTarget() }
    FastScrollBox(scrollTarget, hue, modifier) {
        // The WebView draws its scroll indicator from its Context's theme, which is
        // the window theme and follows the system's night mode, not Kern's. It is
        // built on a theme matching Kern's, and rebuilt when that flips.
        key(colors.dark) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PreviewWebView(ContextThemeWrapper(context, indicatorTheme(colors.dark))).apply {
                        scrollTarget.view = this
                        setOnScrollChangeListener { _, _, scrollY, _, _ ->
                            scrollTarget.scrollY = scrollY
                            currentOnScroll()
                        }
                        this.onFindResult = { current, total -> currentOnFindResult(current, total) }
                        onExternalLink = { pendingLink = it }
                    }
                },
                update = { view ->
                    view.setBackgroundColor(background)
                    if (view.tag != page) {
                        view.tag = page
                        view.loadDataWithBaseURL(MarkdownPage.BASE_URL, page, "text/html", "utf-8", null)
                    }
                    view.find(findQuery, findCurrent)
                },
                onRelease = {
                    if (scrollTarget.view === it) scrollTarget.view = null
                    it.destroy()
                },
            )
        }
    }
}

/** A platform theme whose scroll indicator stands out on Kern's light or dark page. */
private fun indicatorTheme(dark: Boolean): Int =
    if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar

/** A WebView that reports its vertical scroll range, which [WebView] keeps protected. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
private class PreviewWebView(context: Context) : WebView(context) {
    init {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        webViewClient = PreviewClient()
    }

    /** Called with a web, mail or phone link the reader tapped; the host confirms before opening it. */
    var onExternalLink: (String) -> Unit = {}

    /** Pixels the page can scroll: its full height less the part on screen. */
    val maxScrollY: Int get() = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)

    val viewportHeight: Int get() = computeVerticalScrollExtent()

    var onFindResult: (current: Int, total: Int) -> Unit = { _, _ -> }
    private var query = ""
    private var active = 0
    private var total = 0

    init {
        setFindListener { activeOrdinal, matches, _ ->
            active = activeOrdinal
            total = matches
            onFindResult(activeOrdinal, matches)
        }
    }

    /**
     * Searches for [newQuery] and steps to match [current]. The WebView keeps its own
     * current match, so stepping walks it one match at a time in the shorter direction.
     */
    fun find(newQuery: String, current: Int) {
        if (newQuery != query) {
            query = newQuery
            active = 0
            total = 0
            if (newQuery.isEmpty()) {
                clearMatches()
                onFindResult(0, 0)
            } else {
                findAllAsync(newQuery)
            }
            return
        }
        if (total == 0 || current == active) return
        val forwardSteps = Math.floorMod(current - active, total)
        if (forwardSteps <= total - forwardSteps) repeat(forwardSteps) { findNext(true) }
        else repeat(total - forwardSteps) { findNext(false) }
        active = current
    }

    /** A reloaded page has lost its matches; search it again once it has loaded. */
    fun refind() {
        if (query.isNotEmpty()) findAllAsync(query)
    }
}

/**
 * The preview page as a [FastScrollTarget]. The page's height is read from the
 * WebView when needed, since it grows as diagrams and maths finish drawing;
 * [scrollY] is mirrored into state so the thumb follows the page.
 */
private class WebViewScrollTarget : FastScrollTarget {
    var view: PreviewWebView? = null
    var scrollY by mutableIntStateOf(0)

    private val metrics: FastScrollMath.PixelMetrics
        get() {
            val v = view ?: return FastScrollMath.PixelMetrics(0, 0, 0)
            return FastScrollMath.PixelMetrics(scrollY, v.maxScrollY, v.viewportHeight)
        }

    override val fraction get() = metrics.fraction
    override val visibleFraction get() = metrics.visibleFraction
    override val canScroll get() = metrics.canScroll

    override fun scrollToFraction(fraction: Float) {
        val v = view ?: return
        v.scrollTo(v.scrollX, metrics.offsetFor(fraction))
    }

    /** The WebView's own vertical indicator is hidden while the fast scroll bar shows. */
    override fun onBarShown(shown: Boolean) {
        view?.isVerticalScrollBarEnabled = !shown
    }
}

private class PreviewClient : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (ExternalLinks.isOpenable(url)) (view as? PreviewWebView)?.onExternalLink?.invoke(url)
        return true
    }

    override fun onPageFinished(view: WebView, url: String?) {
        (view as? PreviewWebView)?.refind()
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.scheme == "file" && url.path.orEmpty().startsWith(ASSET_PATH)) return null
        if (url.scheme == "data") return null
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    companion object {
        private const val ASSET_PATH = "/android_asset/preview/"
    }
}

private fun KernColorScheme.toPreviewTheme() = MarkdownPage.Theme(
    dark = dark,
    background = bg.css(),
    text = text.css(),
    muted = textMid.css(),
    border = border.css(),
    surface = sunken.css(),
    accent = accent.css(),
)

private fun Color.css(): String = "#%06X".format(toArgb() and 0xFFFFFF)
