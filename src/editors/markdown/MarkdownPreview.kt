package dev.kern.editors.markdown

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import dev.kern.shared.theme.KernColorScheme
import dev.kern.shared.ui.FastScrollBox
import dev.kern.shared.ui.FastScrollMath
import dev.kern.shared.ui.FastScrollTarget
import java.io.ByteArrayInputStream

/**
 * Shows a rendered Markdown page in a WebView.
 *
 * The WebView may run JavaScript (the bundled KaTeX, highlight.js and Mermaid),
 * but it has no file or content access, so it can load only the page Kern hands
 * it and the bundled assets. Requests for anything outside the asset folder get
 * an empty answer here, before reaching the network. Tapping a web, mail or phone
 * link opens it in the app the system picks for it; links inside the page are
 * handled by the page itself. [onScroll] is called as the page scrolls, and the
 * right edge carries a fast scroll bar in [hue].
 */
@Composable
fun MarkdownPreview(
    rendered: MarkdownRenderer.Rendered,
    colors: KernColorScheme,
    hue: Color,
    modifier: Modifier = Modifier,
    onScroll: () -> Unit = {},
) {
    val currentOnScroll by rememberUpdatedState(onScroll)
    val theme = remember(colors) { colors.toPreviewTheme() }
    val page = remember(rendered, theme) { MarkdownPage.build(rendered, theme) }
    val background = colors.bg.toArgb()
    val scrollTarget = remember { WebViewScrollTarget() }
    FastScrollBox(scrollTarget, hue, modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PreviewWebView(context).apply {
                    scrollTarget.view = this
                    setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        scrollTarget.scrollY = scrollY
                        currentOnScroll()
                    }
                }
            },
            update = { view ->
                view.setBackgroundColor(background)
                if (view.tag != page) {
                    view.tag = page
                    view.loadDataWithBaseURL(MarkdownPage.BASE_URL, page, "text/html", "utf-8", null)
                }
            },
            onRelease = {
                scrollTarget.view = null
                it.destroy()
            },
        )
    }
}

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
        webViewClient = PreviewClient(context)
    }

    /** Pixels the page can scroll: its full height less the part on screen. */
    val maxScrollY: Int get() = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)

    val viewportHeight: Int get() = computeVerticalScrollExtent()
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
}

private class PreviewClient(private val context: Context) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        if (url.scheme?.lowercase() in EXTERNAL_SCHEMES) openExternally(url)
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.scheme == "file" && url.path.orEmpty().startsWith(ASSET_PATH)) return null
        if (url.scheme == "data") return null
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    private fun openExternally(url: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val ASSET_PATH = "/android_asset/preview/"
        private val EXTERNAL_SCHEMES = setOf("http", "https", "mailto", "tel")
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
