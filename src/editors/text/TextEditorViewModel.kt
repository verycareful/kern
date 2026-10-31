package dev.kern.editors.text

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kern.editors.markdown.EmojiTable
import dev.kern.editors.markdown.FileImageResolver
import dev.kern.editors.markdown.MarkdownRenderer
import dev.kern.shared.DocumentFormat
import dev.kern.shared.io.DocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds one plain text or Markdown file: the editable text (a [TextFieldState],
 * which also keeps the undo history), the byte-level details needed to save it as
 * it came ([TextDocument]), and for Markdown the rendered preview.
 *
 * The file counts as changed while its text differs from what was last loaded or
 * saved, so undoing back to the saved text clears the unsaved mark.
 */
@OptIn(ExperimentalFoundationApi::class)
class TextEditorViewModel(app: Application) : AndroidViewModel(app) {

    enum class Mode { EDIT, PREVIEW }

    val state = TextFieldState()

    var fileName by mutableStateOf("")
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var markdown by mutableStateOf(false)
        private set
    var mode by mutableStateOf(Mode.EDIT)
        private set
    var preview by mutableStateOf<MarkdownRenderer.Rendered?>(null)
        private set

    private var savedText by mutableStateOf("")
    val dirty: Boolean by derivedStateOf { !loading && !state.text.contentEquals(savedText) }

    private var document: TextDocument? = null
    private var uri: Uri? = null
    private var started = false
    private var renderer: MarkdownRenderer? = null
    private var imageResolver: MarkdownRenderer.ImageResolver? = null
    private var renderJob: Job? = null
    private var renderedText: String? = null

    /** Loads the document once for the given encoded URI (safe to call on recomposition). */
    fun start(encodedUri: String?, format: DocumentFormat) {
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
                    val bytes = DocumentIo.readBytes(ctx, decoded)
                    require(bytes.size <= MAX_FILE_BYTES) { "This file is too large to edit here (over 8 MB)." }
                    Loaded(name, TextDocument.decode(bytes), isMarkdown(format, name))
                }
            }
            result.onSuccess { loaded ->
                fileName = loaded.name
                document = loaded.document
                markdown = loaded.markdown
                savedText = loaded.document.text
                state.edit {
                    replace(0, length, loaded.document.text)
                    selection = TextRange.Zero
                }
                state.undoState.clearHistory()
                loading = false
                if (markdown) showPreview()
            }.onFailure {
                error = it.message ?: "Could not open the file."
                loading = false
            }
        }
    }

    private data class Loaded(val name: String, val document: TextDocument, val markdown: Boolean)

    fun showEditor() {
        mode = Mode.EDIT
    }

    /** Switches to the preview, rendering the current text unless it was rendered already. */
    fun showPreview() {
        if (!markdown) return
        mode = Mode.PREVIEW
        val text = state.text.toString()
        if (text == renderedText && preview != null) return
        renderJob?.cancel()
        val ctx = getApplication<Application>()
        renderJob = viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                val r = renderer ?: MarkdownRenderer(loadEmoji(ctx)).also { renderer = it }
                val images = imageResolver ?: resolverFor(uri).also { imageResolver = it }
                r.render(text, images)
            }
            renderedText = text
            preview = rendered
        }
    }

    /** Applies a toolbar edit as one undoable step. */
    fun apply(edit: (text: String, selStart: Int, selEnd: Int) -> MarkdownFormatting.TextEdit) {
        val current = state.selection
        val change = edit(state.text.toString(), current.start, current.end)
        state.edit {
            replace(change.start, change.end, change.replacement)
            selection = TextRange(change.selectionStart, change.selectionEnd)
        }
    }

    fun undo() = state.undoState.undo()
    fun redo() = state.undoState.redo()

    /** Writes back to the original URI. */
    fun save(onResult: (ok: Boolean, message: String?) -> Unit) {
        val target = uri ?: run { onResult(false, "Nothing to save."); return }
        val text = state.text.toString()
        write(target, text) { ok, msg ->
            if (ok) savedText = text
            onResult(ok, msg)
        }
    }

    /** Writes a copy to a user-chosen URI (Save as). */
    fun exportTo(target: Uri, onResult: (ok: Boolean, message: String?) -> Unit) =
        write(target, state.text.toString(), onResult)

    private fun write(target: Uri, text: String, onResult: (ok: Boolean, message: String?) -> Unit) {
        val doc = document ?: run { onResult(false, "Nothing to save."); return }
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DocumentIo.writeBytes(ctx, target, doc.encode(text)) }
            }
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    companion object {
        /** Large enough for any hand-written document; beyond it a phone text field becomes unusable. */
        const val MAX_FILE_BYTES = 8 * 1024 * 1024

        @Volatile private var emojiCache: Map<String, String>? = null

        private fun loadEmoji(app: Application): Map<String, String> = emojiCache ?: runCatching {
            app.assets.open(EmojiTable.ASSET_PATH).use(EmojiTable::parse)
        }.getOrDefault(emptyMap()).also { emojiCache = it }

        private fun resolverFor(uri: Uri?): MarkdownRenderer.ImageResolver? =
            uri?.let(DocumentIo::localFile)?.parentFile?.let(::FileImageResolver)

        /**
         * A file opened as plain text still gets the Markdown preview when its name
         * says Markdown: "Open with" often labels `.md` files `text/plain`.
         */
        internal fun isMarkdown(format: DocumentFormat, fileName: String): Boolean =
            format.hasPreview || DocumentFormat.fromExtension(fileName.substringAfterLast('.', ""))?.hasPreview == true
    }
}
