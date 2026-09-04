package dev.kern.editors.pdf

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kern.pdfbridge.QyraPdf
import dev.kern.shared.io.DocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF viewer state. 0.1.5.0 part A: open a PDF and page through it (read-only) via
 * the framework [PdfDocument]. The edit toolkit (merge, split, redact, ...) over the
 * Qyra MuPDF bridge lands in later 0.1.5.0 commits, at which point this gains the
 * usual edit/save surface; for now Export just writes a verbatim copy.
 */
class PdfEditorViewModel(app: Application) : AndroidViewModel(app) {

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
     */
    fun extractPages(rangeSpec: String) {
        val source = uri ?: run { toolError = "No open PDF."; return }
        val spec = rangeSpec.trim()
        if (spec.isEmpty()) { toolError = "Enter a page range, e.g. 1-3."; return }
        runTool {
            val dir = toolsCacheDir()
            val input = copyToCache(source, dir, "extract_src.pdf")
            val result = QyraPdf.splitRanges(input.absolutePath, spec, dir.absolutePath)
            // splitRanges writes one file per range; for a single range we expose that file.
            result to "extract.pdf"
        }
    }

    /** Writes the staged [pendingOutput] to [target] (a user-picked SAF location). */
    fun saveOutputTo(target: Uri) {
        val output = pendingOutput ?: return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val written = runCatching { DocumentIo.writeBytes(ctx, target, File(output.cachePath).readBytes()) }
                // The staged copy has served its purpose either way: the user has
                // been given somewhere to put it, and it is unreachable from here on.
                deleteToolFile(output.cachePath)
                written
            }
            pendingOutput = null
            if (result.isSuccess) toolMessage = "Saved"
            else toolError = "Save failed: ${result.exceptionOrNull()?.message}"
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
                    val first = result.outputPaths.firstOrNull()
                    if (first == null) toolError = "The operation produced no output."
                    else pendingOutput = PendingOutput(first, suggestedName)
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
            val keep = keepPaths.mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }.toSet()
            toolsCacheRoot.listFiles()?.forEach { file ->
                val path = runCatching { file.canonicalPath }.getOrNull() ?: file.absolutePath
                if (path !in keep) file.deleteRecursively()
            }
        }
    }

    /** Deletes one staged file. Call it off the main thread; it never throws. */
    private fun deleteToolFile(path: String) {
        runCatching { File(path).delete() }
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
        Thread { runCatching { root.deleteRecursively() } }.start()
    }
}
