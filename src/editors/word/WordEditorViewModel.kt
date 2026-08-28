package dev.kern.editors.word

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kern.shared.io.DocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Word editor state over the run-level [WordDocument] model. Editable paragraphs are
 * held as [TextFieldValue]s (keyed by block index) whose AnnotatedString carries the
 * run formatting; non-editable paragraphs and tables render read-only from [blocks].
 *
 * Character-formatting actions operate on the currently focused field's selection.
 * Edits are tracked by block index so saving applies them onto the original document
 * without touching anything else (see [WordDocument]).
 */
class WordEditorViewModel(app: Application) : AndroidViewModel(app) {

    var fileName by mutableStateOf("")
        private set
    var loading by mutableStateOf(true)
        private set
    var dirty by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Body blocks in order. Paragraph text lives in [fields]; this drives structure. */
    var blocks by mutableStateOf<List<WordDocument.Block>>(emptyList())
        private set

    /** Editable paragraph values, keyed by block index. */
    val fields = mutableStateMapOf<Int, TextFieldValue>()

    var focusedIndex by mutableStateOf<Int?>(null)
        private set

    /** Most-recently-used colours (6-hex), newest first. */
    var recentColors by mutableStateOf<List<String>>(emptyList())
        private set

    /** Two user-editable custom colour slots (null = empty). */
    val customSlots = mutableStateListOf<String?>(null, null)

    private var uri: Uri? = null
    private var originalBytes: ByteArray? = null
    private val edits = mutableMapOf<Int, List<WordDocument.Run>>()
    private var started = false

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
            result.onSuccess { (name, bytes, parsed) ->
                fileName = name
                originalBytes = bytes
                blocks = parsed.blocks
                fields.clear()
                parsed.blocks.forEachIndexed { i, block ->
                    if (block is WordDocument.ParagraphBlock && block.editable) {
                        fields[i] = TextFieldValue(WordRichText.toAnnotated(block.runs))
                    }
                }
                loading = false
            }.onFailure {
                error = it.message ?: "Could not open the document."
                loading = false
            }
        }
    }

    fun setFocus(index: Int) {
        focusedIndex = index
    }

    fun onValueChange(index: Int, value: TextFieldValue) {
        val previous = fields[index]
        fields[index] = value
        focusedIndex = index
        if (previous == null || previous.annotatedString != value.annotatedString) {
            edits[index] = WordRichText.toRuns(value.annotatedString)
            dirty = true
        }
    }

    /** True when a formatting action can be applied (an editable paragraph is focused). */
    fun canFormat(): Boolean {
        val idx = focusedIndex ?: return false
        return (blocks.getOrNull(idx) as? WordDocument.ParagraphBlock)?.editable == true
    }

    /** Style at the focused caret/selection, for toolbar active states. */
    fun activeStyle(): WordDocument.RunStyle? =
        focusedIndex?.let { fields[it] }?.let { WordRichText.styleAt(it) }

    fun toggleBold() {
        mutateFocused { v ->
            val target = !WordRichText.styleAt(v).bold
            WordRichText.restyleSelection(v) { it.copy(bold = target) }
        }
    }

    fun toggleItalic() {
        mutateFocused { v ->
            val target = !WordRichText.styleAt(v).italic
            WordRichText.restyleSelection(v) { it.copy(italic = target) }
        }
    }

    fun toggleUnderline() {
        mutateFocused { v ->
            val target = !WordRichText.styleAt(v).underline
            WordRichText.restyleSelection(v) { it.copy(underline = target) }
        }
    }

    fun toggleStrike() {
        mutateFocused { v ->
            val target = !WordRichText.styleAt(v).strike
            WordRichText.restyleSelection(v) { it.copy(strike = target) }
        }
    }

    /** Sets the run colour (6-hex, no '#') on the selection, or null to clear. */
    fun setColor(hex: String?) {
        val applied = mutateFocused { v -> WordRichText.restyleSelection(v) { it.copy(colorHex = hex) } }
        if (applied && hex != null) pushRecent(hex)
    }

    /** Sets the run font size (points) on the selection, or null to inherit. */
    fun setFontSize(points: Float?) {
        mutateFocused { v -> WordRichText.restyleSelection(v) { it.copy(sizePt = points) } }
    }

    /** Validates [input] into a custom slot and applies it; returns false if invalid. */
    fun setCustomSlot(slot: Int, input: String): Boolean {
        val hex = WordRichText.normalizeHex(input) ?: return false
        if (slot in customSlots.indices) customSlots[slot] = hex
        setColor(hex)
        return true
    }

    private fun pushRecent(hex: String) {
        recentColors = (listOf(hex) + recentColors).distinct().take(6)
    }

    private fun mutateFocused(block: (TextFieldValue) -> TextFieldValue): Boolean {
        val idx = focusedIndex ?: return false
        val current = fields[idx] ?: return false
        val updated = block(current)
        fields[idx] = updated
        edits[idx] = WordRichText.toRuns(updated.annotatedString)
        dirty = true
        return true
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
