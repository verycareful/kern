package dev.kern.editors.pdf

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kern.pdfbridge.QyraPdf
import dev.kern.shared.find.FindMessage
import dev.kern.shared.find.FindState
import dev.kern.shared.find.FindTarget
import dev.kern.shared.io.DocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF viewer state. 0.1.5.0 part A: open a PDF and page through it (read-only) via
 * the framework [PdfDocument]. The edit toolkit (merge, split, redact, ...) over the
 * Qyra MuPDF bridge lands in later 0.1.5.0 commits, at which point this gains the
 * usual edit/save surface; for now Export just writes a verbatim copy.
 */
class PdfEditorViewModel(app: Application) : AndroidViewModel(app), FindTarget {

    var fileName by mutableStateOf("")
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var pageCount by mutableIntStateOf(0)
        private set

    private var uri: Uri? = null
    private var document: PdfDocument? = null
    private var started = false

    // --- Find -------------------------------------------------------------------

    override val find = FindState()

    private var textLayer by mutableStateOf<PdfTextLayer?>(null)

    /** Matches of the current search across every page, in page order. */
    var pdfMatches by mutableStateOf<List<PdfTextMatch>>(emptyList())
        private set

    private val searchQuery: String get() = if (find.isOpen) find.query else ""

    override val matchCount: Int get() = if (searchQuery.isEmpty()) 0 else pdfMatches.size
    override val supportsOptions: Boolean get() = false
    override val unavailableReason: String? get() = if (!loading && error == null && textLayer == null) PDF_TEXT_NEEDS_ANDROID_15 else null

    val currentMatch: PdfTextMatch? get() = if (searchQuery.isEmpty()) null else pdfMatches.getOrNull(find.currentIn(pdfMatches.size))

    // --- Text selection -------------------------------------------------------------

    /** The text the user has selected by long-pressing and dragging over a page. */
    var selection by mutableStateOf<PdfSelection?>(null)
        private set

    /** Whether long-press selection works here: the device has a PDF text layer. */
    val canSelectText: Boolean get() = textLayer != null

    private data class SelectRequest(val page: Int, val start: PagePoint, val end: PagePoint)
    private val selectRequests = MutableStateFlow<SelectRequest?>(null)

    /** Selects the text from [start] to [end] on [page]; drags arrive faster than pages are read, so only the latest counts. */
    fun selectText(page: Int, start: PagePoint, end: PagePoint) {
        selectRequests.value = SelectRequest(page, start, end)
    }

    fun clearSelection() {
        selectRequests.value = null
        selection = null
    }

    // --- Links ----------------------------------------------------------------------

    /** A web, mail or phone link the user tapped, waiting for them to confirm opening it. */
    var pendingLink by mutableStateOf<String?>(null)
        private set

    /** A page an internal link asked to show; the screen scrolls there and clears it. */
    var linkTargetPage by mutableStateOf<Int?>(null)
        private set

    private val linkCache = HashMap<Int, List<PdfLink>>()

    /**
     * Handles a tap at [point] on [page]: follows a link there, or clears the selection.
     * A link out of the document is only offered, never opened, until the user confirms.
     */
    fun tapPage(page: Int, point: PagePoint) {
        val layer = textLayer
        if (selection != null || layer == null) {
            clearSelection()
            return
        }
        viewModelScope.launch {
            val links = linkCache[page] ?: withContext(Dispatchers.IO) {
                runCatching { layer.links(page) }.getOrDefault(emptyList())
            }.also { linkCache[page] = it }
            when (val link = links.at(point)) {
                is PdfLink.External -> if (isOpenable(link.uri)) pendingLink = link.uri
                is PdfLink.GoTo -> if (link.page in 0 until pageCount) linkTargetPage = link.page
                null -> Unit
            }
        }
    }

    fun dismissLink() { pendingLink = null }

    fun consumeLinkTarget() { linkTargetPage = null }

    init {
        viewModelScope.launch {
            selectRequests.collectLatest { request ->
                val layer = textLayer
                if (request == null || layer == null) return@collectLatest
                val result = withContext(Dispatchers.IO) {
                    runCatching { layer.select(request.page, request.start, request.end) }.getOrNull()
                }
                if (selectRequests.value == request) selection = result
            }
        }
    }

    init {
        // Searches every page off the main thread, a moment after typing stops; a new
        // query cancels the search still running for the old one.
        viewModelScope.launch {
            snapshotFlow { searchQuery to textLayer }.collectLatest { (query, layer) ->
                pdfMatches = emptyList()
                if (query.isEmpty() || layer == null) return@collectLatest
                delay(SEARCH_DEBOUNCE_MS)
                find.message = SEARCHING
                val found = withContext(Dispatchers.IO) {
                    (0 until pageCount).flatMap { page ->
                        ensureActive()
                        runCatching { layer.search(page, query) }.getOrDefault(emptyList())
                    }
                }
                pdfMatches = found
                if (find.message == SEARCHING) find.message = null
                find.reveal()
            }
        }
    }

    // --- PDF tools (merge / split) state ---------------------------------------
    /** True while a bridge operation runs. */
    var toolBusy by mutableStateOf(false)
        private set
    /** A produced file in cache, waiting for the user to choose where to save it. */
    var pendingOutput by mutableStateOf<PendingOutput?>(null)
        private set
    /** A transient success message shown briefly as a snackbar. */
    var toolMessage by mutableStateOf<String?>(null)
        private set
    /** An error or instructional message to display in a dialog. */
    var toolError by mutableStateOf<String?>(null)
        private set

    /** A cache file produced by a tool, plus a suggested save-as name. */
    data class PendingOutput(val cachePath: String, val suggestedName: String)

    /** Whether the native PDF engine is bundled in this build. */
    val engineAvailable: Boolean get() = QyraPdf.available

    fun start(encodedUri: String?) {
        if (started) return
        started = true
        val ctx = getApplication<Application>()
        val decoded = encodedUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(Uri.decode(it)) }
        if (decoded == null) {
            loading = false
            error = "No file was provided."
            return
        }
        uri = decoded
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    DocumentIo.tryPersist(ctx, decoded)
                    val name = DocumentIo.displayName(ctx, decoded)
                    val doc = PdfDocument.open(ctx, decoded)
                    name to doc
                }
            }
            result.onSuccess { (name, doc) ->
                fileName = name
                document = doc
                pageCount = doc.pageCount
                textLayer = doc.textLayer
                loading = false
            }.onFailure {
                error = it.message ?: "Could not open the PDF."
                loading = false
            }
        }
    }

    /** Renders [index] at [widthPx] off the main thread, or null if the doc is closed. */
    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { document?.renderPage(index, widthPx) }.getOrNull()
    }

    /** Writes a verbatim copy of the original file to [target]. */
    fun exportTo(target: Uri, onResult: (ok: Boolean, message: String?) -> Unit) {
        val ctx = getApplication<Application>()
        val source = uri ?: run { onResult(false, "Nothing to export."); return }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DocumentIo.writeBytes(ctx, target, DocumentIo.readBytes(ctx, source)) }
            }
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    /**
     * Merges the open PDF with [extraUris] (appended in order) into one file.
     * Inputs are copied into the app cache and handed to the native bridge by
     * path; the result is staged in [pendingOutput] for the caller to save out.
     */
    fun merge(extraUris: List<Uri>) {
        val source = uri ?: run { toolError = "No open PDF to merge."; return }
        if (extraUris.isEmpty()) { toolError = "Pick at least one more PDF to merge."; return }
        runTool {
            val dir = toolsCacheDir()
            val inputs = buildList {
                add(copyToCache(source, dir, "merge_00.pdf"))
                extraUris.forEachIndexed { i, u -> add(copyToCache(u, dir, "merge_%02d.pdf".format(i + 1))) }
            }
            val out = File(dir, "merged.pdf")
            QyraPdf.merge(inputs.map { it.absolutePath }, out.absolutePath) to "merged.pdf"
        }
    }

    /**
     * Extracts a single 1-based page range (e.g. "3-7") from the open PDF into a
     * new file, staged in [pendingOutput].
     *
     * The bridge also understands comma-separated lists and writes one file per
     * range, but only one produced file can be staged and saved, so a list is
     * refused by [rangeSpecError] instead of being silently truncated to its
     * first range. Ranges are extracted one at a time.
     */
    fun extractPages(rangeSpec: String) {
        val source = uri ?: run { toolError = "No open PDF."; return }
        val spec = rangeSpec.filterNot { it.isWhitespace() }
        rangeSpecError(spec)?.let { toolError = it; return }
        runTool {
            val dir = toolsCacheDir()
            val input = copyToCache(source, dir, "extract_src.pdf")
            val result = QyraPdf.splitRanges(input.absolutePath, spec, dir.absolutePath)
            // splitRanges writes one file per range; spec is a single range, so this
            // is the one file the user asked for.
            result to "extract.pdf"
        }
    }

    companion object {
        /** How long typing must pause before a PDF search starts. */
        private const val SEARCH_DEBOUNCE_MS = 250L

        private val SEARCHING = FindMessage("Searching", isError = false)

        /** The kinds of link handed to other apps, the same ones the Markdown preview opens. */
        private val OPENABLE_SCHEMES = setOf("http", "https", "mailto", "tel")

        internal fun isOpenable(uri: String): Boolean =
            uri.substringBefore(':', "").lowercase() in OPENABLE_SCHEMES

        /** One 1-based page range: "4" or "2-7", after whitespace has been stripped. */
        private val SINGLE_RANGE = Regex("""(\d{1,6})(?:-(\d{1,6}))?""")

        /**
         * Checks [spec] as a single 1-based page range, returning null when it is
         * usable or a message explaining why it is not. Shared with the extract
         * dialog so bad input is refused at the point of entry rather than costing
         * the user a range further down the pipeline.
         */
        fun rangeSpecError(spec: String): String? {
            val compact = spec.filterNot { it.isWhitespace() }
            if (compact.isEmpty()) return "Enter a page range, e.g. 1-3."
            if (compact.contains(',')) {
                return "Extract handles one range at a time. Enter a single range like 1-3, " +
                    "then run Extract again for the next one."
            }
            val match = SINGLE_RANGE.matchEntire(compact)
                ?: return "Use one range like 1-3, or a single page number like 4."
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: start
            if (start < 1) return "Page numbers start at 1."
            if (end < start) return "The last page of the range cannot come before the first."
            return null
        }
    }

    /** Writes the staged [pendingOutput] to [target] (a user-picked SAF location). */
    fun saveOutputTo(target: Uri) {
        val output = pendingOutput ?: return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val written = runCatching {
                    val source = resolveToolFile(output.cachePath)
                        ?: throw java.io.IOException("Output is not in the tool cache")
                    DocumentIo.writeBytes(ctx, target, source.readBytes())
                }
                // Drop the staged copy only once it is safely written. If the save
                // failed there is nowhere else for this data to exist, so keep it
                // (and keep it staged) so the user can pick another destination.
                if (written.isSuccess) deleteToolFile(output.cachePath)
                written
            }
            if (result.isSuccess) {
                pendingOutput = null
                toolMessage = "Saved"
            } else {
                toolError = "Save failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun dismissPendingOutput() {
        val output = pendingOutput ?: return
        pendingOutput = null
        viewModelScope.launch { withContext(Dispatchers.IO) { deleteToolFile(output.cachePath) } }
    }
    fun consumeToolMessage() { toolMessage = null }
    fun consumeToolError() { toolError = null }

    /** Runs [block] off-main, mapping its [QyraPdf.Result] into [pendingOutput] / [toolMessage]. */
    private fun runTool(block: suspend () -> Pair<QyraPdf.Result, String>) {
        if (toolBusy) return
        toolBusy = true
        val staged = listOfNotNull(pendingOutput?.cachePath)
        viewModelScope.launch {
            val (result, suggestedName) = withContext(Dispatchers.IO) {
                // Anything left over from an earlier operation is dead weight.
                pruneToolsCache(staged)
                val outcome = runCatching { block() }
                    .getOrElse { QyraPdf.Result.Failure(it.message ?: "Operation failed") to "" }
                // The copied inputs are only needed for the duration of the native
                // call, and a failed call leaves nothing worth keeping at all.
                pruneToolsCache(staged + (outcome.first as? QyraPdf.Result.Success)?.outputPaths.orEmpty())
                outcome
            }
            when (result) {
                is QyraPdf.Result.Success -> {
                    val paths = result.outputPaths
                    // Only one produced file can be staged for saving, so keeping just
                    // the first would discard the rest: say so rather than lose it.
                    when {
                        paths.isEmpty() -> toolError = "The operation produced no output."
paths.size > 1 -> toolError =
    "That request produced ${paths.size} files, but only one can be saved at a time."
                        else -> pendingOutput = PendingOutput(paths[0], suggestedName)
                    }
                }
                is QyraPdf.Result.Failure -> toolError = result.message
            }
            toolBusy = false
        }
    }

    /**
     * Scratch space for the native bridge. It holds plaintext copies of the user's
     * documents, so it is treated as short-lived: see [pruneToolsCache].
     */
    private val toolsCacheRoot: File
        get() = File(getApplication<Application>().cacheDir, "pdf-tools")

    private fun toolsCacheDir(): File = toolsCacheRoot.apply { mkdirs() }

    /**
     * Deletes everything in the tool cache except [keepPaths]. Call it off the main
     * thread; it never throws, because a failed cleanup must not fail an operation.
     */
    private fun pruneToolsCache(keepPaths: Collection<String> = emptyList()) {
        runCatching {
            // Resolve keep-paths exactly as the listing below resolves candidates, so a
            // path that fails canonicalisation still matches itself and survives.
            val keep = keepPaths.map {
                runCatching { File(it).canonicalPath }.getOrNull() ?: File(it).absolutePath
            }.toSet()
            toolsCacheRoot.listFiles()?.forEach { file ->
                val path = runCatching { file.canonicalPath }.getOrNull() ?: file.absolutePath
                if (path !in keep) file.deleteRecursively()
            }
        }
    }

    /**
     * Resolves [path] to a canonical file inside [toolsCacheRoot], or null if it lands
     * outside that directory, is the directory itself, or cannot be resolved. Tool
     * paths arrive from the native bridge, so every read and delete goes through this
     * to keep the reachable set to the scratch directory.
     */
    private fun resolveToolFile(path: String): File? = runCatching {
        val root = toolsCacheRoot.canonicalFile
        val file = File(path).canonicalFile
        if (file != root && file.startsWith(root)) file else null
    }.getOrNull()

    /** Deletes one staged file. Call it off the main thread; it never throws. */
    private fun deleteToolFile(path: String) {
        runCatching { resolveToolFile(path)?.deleteRecursively() }
    }

    private fun copyToCache(src: Uri, dir: File, name: String): File {
        val ctx = getApplication<Application>()
        val dest = File(dir, name)
        ctx.contentResolver.openInputStream(src)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } ?: throw java.io.IOException("Could not read $src")
        return dest
    }

    override fun onCleared() {
        document?.close()
        document = null
        // Backstop: leaving the editor must not leave copies of the user's
        // documents behind. viewModelScope is cancelled by now, so this runs on a
        // plain background thread rather than blocking the main one.
        val root = toolsCacheRoot
        Thread({ runCatching { root.deleteRecursively() } }, "pdf-tools-cleanup").start()
    }
}
