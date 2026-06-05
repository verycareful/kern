package dev.kern.editors.word

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kern.shared.io.DocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Word editor state. Paragraph texts are editable Compose state; edits are tracked
 * by paragraph index so saving applies them onto the original document (see
 * [WordDocument]).
 */
class WordEditorViewModel(app: Application) : AndroidViewModel(app) {

    val paragraphs = mutableStateListOf<String>()

    var fileName by mutableStateOf("")
        private set
    var loading by mutableStateOf(true)
        private set
    var dirty by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var uri: Uri? = null
    private var originalBytes: ByteArray? = null
    private var styles: List<WordDocument.Style> = emptyList()
    private val edits = mutableMapOf<Int, String>()
    private var started = false

    fun styleAt(index: Int): WordDocument.Style = styles.getOrElse(index) { WordDocument.Style.BODY }

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
                    val bytes = DocumentIo.readBytes(ctx, decoded)
                    Triple(DocumentIo.displayName(ctx, decoded), bytes, WordDocument.read(bytes))
                }
            }
            result.onSuccess { (name, bytes, paras) ->
                fileName = name
                originalBytes = bytes
                styles = paras.map { it.style }
                paragraphs.clear()
                paras.forEach { paragraphs.add(it.text) }
                loading = false
            }.onFailure {
                error = it.message ?: "Could not open the document."
                loading = false
            }
        }
    }

    fun editParagraph(index: Int, text: String) {
        if (index in paragraphs.indices && paragraphs[index] != text) {
            paragraphs[index] = text
            edits[index] = text
            dirty = true
        }
    }

    fun save(onResult: (ok: Boolean, message: String?) -> Unit) {
        val target = uri ?: run { onResult(false, "Nothing to save."); return }
        write(target) { ok, msg ->
            if (ok) dirty = false
            onResult(ok, msg)
        }
    }

    fun exportTo(target: Uri, onResult: (ok: Boolean, message: String?) -> Unit) = write(target, onResult)

    private fun write(target: Uri, onResult: (ok: Boolean, message: String?) -> Unit) {
        val ctx = getApplication<Application>()
        val bytes = originalBytes ?: run { onResult(false, "Nothing to save."); return }
        val snapshot = edits.toMap()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DocumentIo.writeBytes(ctx, target, WordDocument.applyEditsAndSerialize(bytes, snapshot)) }
            }
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }
}
