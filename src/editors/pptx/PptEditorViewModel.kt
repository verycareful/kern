package dev.kern.editors.pptx

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kern.shared.io.DocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PowerPoint editor state. One editable text list per slide; edits are tracked by
 * (slide, shape) so saving applies them across slides onto the original (see
 * [PptDocument]).
 */
class PptEditorViewModel(app: Application) : AndroidViewModel(app) {

    var fileName by mutableStateOf("")
        private set
    var loading by mutableStateOf(true)
        private set
    var dirty by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var currentSlide by mutableStateOf(0)
        private set

    private var uri: Uri? = null
    private var originalBytes: ByteArray? = null
    private var slides: List<SnapshotStateList<String>> = emptyList()
    private val edits = mutableMapOf<Pair<Int, Int>, String>()
    private var started = false

    val slideCount: Int get() = slides.size

    /** Text shapes of the current slide. */
    val currentTexts: List<String> get() = slides.getOrNull(currentSlide) ?: emptyList()

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
                    Triple(DocumentIo.displayName(ctx, decoded), bytes, PptDocument.read(bytes))
                }
            }
            result.onSuccess { (name, bytes, parsed) ->
                fileName = name
                originalBytes = bytes
                slides = parsed.slides.map { it.toMutableStateList() }
                currentSlide = 0
                loading = false
            }.onFailure {
                error = it.message ?: "Could not open the presentation."
                loading = false
            }
        }
    }

    fun goToSlide(index: Int) {
        if (index in slides.indices) currentSlide = index
    }

    fun nextSlide() = goToSlide(currentSlide + 1)

    fun previousSlide() = goToSlide(currentSlide - 1)

    fun editText(shapeIndex: Int, text: String) {
        val slide = slides.getOrNull(currentSlide) ?: return
        if (shapeIndex in slide.indices && slide[shapeIndex] != text) {
            slide[shapeIndex] = text
            edits[currentSlide to shapeIndex] = text
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
                runCatching { DocumentIo.writeBytes(ctx, target, PptDocument.applyEditsAndSerialize(bytes, snapshot)) }
            }
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }
}
