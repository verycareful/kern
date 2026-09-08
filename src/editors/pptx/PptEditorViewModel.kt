package dev.kern.editors.pptx

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kern.shared.io.DocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PowerPoint editor state.
 * Supports slide management, preset layouts, text box placement, rich-text formatting, and Undo/Redo.
 */
class PptEditorViewModel(app: Application) : AndroidViewModel(app) {

    /**
     * One text shape on a slide.
     *
     * Every field is Compose state, and that is load-bearing rather than tidiness:
     * the canvas renders each shape through a `BasicTextField`, which is a controlled
     * component. A write that Compose is never told about leaves the field rendering
     * its previous value, so the keystroke is discarded and the shape cannot be typed
     * into at all.
     */
    class ShapeState(
        textValue: TextFieldValue,
        bounds: PptDocument.ShapeBounds? = null,
    ) {
        var textValue by mutableStateOf(textValue)
        var bounds by mutableStateOf(bounds)

        fun copy(
            textValue: TextFieldValue = this.textValue,
            bounds: PptDocument.ShapeBounds? = this.bounds,
        ) = ShapeState(textValue, bounds)
    }

    class SlideState(
        shapes: List<ShapeState> = emptyList(),
        width: Float = 960f,
        height: Float = 540f,
        backgroundColorHex: String? = null,
    ) {
        /** A snapshot list, so adding or deleting a text box recomposes the canvas. */
        val shapes: SnapshotStateList<ShapeState> =
            mutableStateListOf<ShapeState>().apply { addAll(shapes) }
        var width by mutableStateOf(width)
        var height by mutableStateOf(height)
        var backgroundColorHex by mutableStateOf(backgroundColorHex)

        fun copy(
            shapes: List<ShapeState> = this.shapes,
            width: Float = this.width,
            height: Float = this.height,
            backgroundColorHex: String? = this.backgroundColorHex,
        ) = SlideState(shapes, width, height, backgroundColorHex)
    }

    data class HistorySnapshot(
        val slides: List<SlideState>,
        val currentSlide: Int,
        val selectedShape: Int?,
    )

    var fileName by mutableStateOf("")
        private set
    var loading by mutableStateOf(true)
        private set
    var dirty by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var currentSlide by mutableIntStateOf(0)
        private set
    var selectedShapeIndex by mutableStateOf<Int?>(null)
        private set

    var showLayoutPicker by mutableStateOf(false)
    var showSlideActions by mutableStateOf(false)
    var actionSlideIndex by mutableIntStateOf(0)
    var showFontSizeSheet by mutableStateOf(false)
    var showColorSheet by mutableStateOf(false)

    val slideStates = mutableStateListOf<SlideState>()
    val slideOps = mutableListOf<PptDocument.SlideOp>()

    private val undoStack = ArrayDeque<HistorySnapshot>()
    private val redoStack = ArrayDeque<HistorySnapshot>()
    private val maxHistory = 30

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private var uri: Uri? = null
    private var originalBytes: ByteArray? = null
    private var started = false

    val slideCount: Int get() = slideStates.size

    val currentSlideState: SlideState?
        get() = slideStates.getOrNull(currentSlide)

    val currentShapes: List<ShapeState>
        get() = currentSlideState?.shapes ?: emptyList()

    val caretStyle: PptDocument.RunStyle
        get() {
            val shapeIdx = selectedShapeIndex ?: return PptDocument.RunStyle()
            val shapeState = slideStates.getOrNull(currentSlide)?.shapes?.getOrNull(shapeIdx) ?: return PptDocument.RunStyle()
            return PptRichText.styleAt(shapeState.textValue)
        }

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
                slideStates.clear()
                for (slide in parsed.slideModels) {
                    val shapes = slide.shapes.map { shape ->
                        ShapeState(
                            textValue = TextFieldValue(annotatedString = PptRichText.toAnnotated(shape.runs)),
                            bounds = shape.bounds,
                        )
                    }.toMutableList()
                    if (shapes.isEmpty() && parsed.slides.isNotEmpty()) {
                        val fallback = parsed.slides.getOrNull(slide.slideIndex)?.map {
                            ShapeState(textValue = TextFieldValue(it))
                        }?.toMutableList() ?: mutableListOf(ShapeState(textValue = TextFieldValue("")))
                        slideStates.add(SlideState(shapes = fallback, width = slide.width, height = slide.height, backgroundColorHex = slide.backgroundColorHex))
                    } else {
                        slideStates.add(SlideState(shapes = if (shapes.isEmpty()) mutableListOf(ShapeState(textValue = TextFieldValue(""))) else shapes, width = slide.width, height = slide.height, backgroundColorHex = slide.backgroundColorHex))
                    }
                }
                if (slideStates.isEmpty()) {
                    slideStates.add(SlideState(mutableListOf(ShapeState(textValue = TextFieldValue("Title")))))
                }
                currentSlide = 0
                selectedShapeIndex = null
                loading = false
            }.onFailure {
                error = it.message ?: "Could not open the presentation."
                loading = false
            }
        }
    }

    fun goToSlide(index: Int) {
        if (index in slideStates.indices) {
            currentSlide = index
            selectedShapeIndex = null
        }
    }

    fun nextSlide() = goToSlide(currentSlide + 1)
    fun previousSlide() = goToSlide(currentSlide - 1)

    fun selectShape(index: Int?) {
        selectedShapeIndex = index
    }

    fun updateShapeValue(shapeIndex: Int, value: TextFieldValue) {
        val slide = slideStates.getOrNull(currentSlide) ?: return
        if (shapeIndex in slide.shapes.indices) {
            slide.shapes[shapeIndex].textValue = value
            dirty = true
        }
    }

    fun addSlide(layout: PptDocument.PresetLayout) {
        pushHistory()
        val newShapes = when (layout) {
            PptDocument.PresetLayout.TITLE -> mutableListOf(ShapeState(TextFieldValue("Title")), ShapeState(TextFieldValue("Subtitle")))
            PptDocument.PresetLayout.TITLE_AND_CONTENT -> mutableListOf(ShapeState(TextFieldValue("Title")), ShapeState(TextFieldValue("Content")))
            PptDocument.PresetLayout.SECTION_HEADER -> mutableListOf(ShapeState(TextFieldValue("Section Header")))
            PptDocument.PresetLayout.BLANK -> mutableListOf()
        }
        val insertAt = currentSlide + 1
        // A deck has one slide size. Taking it from the neighbour keeps a new slide the
        // same shape as the rest, rather than the 16:9 default in a 4:3 presentation.
        val existing = currentSlideState
        slideStates.add(
            insertAt,
            if (existing == null) SlideState(newShapes)
            else SlideState(newShapes, existing.width, existing.height),
        )
        slideOps.add(PptDocument.InsertSlide(insertAt, layout))
        currentSlide = insertAt
        selectedShapeIndex = if (newShapes.isNotEmpty()) 0 else null
        dirty = true
        showLayoutPicker = false
    }

    fun duplicateSlide(index: Int) {
        val slide = slideStates.getOrNull(index) ?: return
        pushHistory()
        val cloneShapes = slide.shapes.map { it.copy(textValue = it.textValue.copy()) }.toMutableList()
        val insertAt = index + 1
        slideStates.add(insertAt, SlideState(shapes = cloneShapes, width = slide.width, height = slide.height, backgroundColorHex = slide.backgroundColorHex))
        slideOps.add(PptDocument.DuplicateSlide(index, insertAt))
        currentSlide = insertAt
        selectedShapeIndex = null
        dirty = true
        showSlideActions = false
    }

    fun deleteSlide(index: Int) {
        if (slideStates.size <= 1) return
        if (index !in slideStates.indices) return
        pushHistory()
        slideStates.removeAt(index)
        slideOps.add(PptDocument.DeleteSlide(index))
        currentSlide = currentSlide.coerceIn(0, slideStates.lastIndex)
        selectedShapeIndex = null
        dirty = true
        showSlideActions = false
    }

    fun moveSlide(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in slideStates.indices || toIndex !in slideStates.indices || fromIndex == toIndex) return
        pushHistory()
        val item = slideStates.removeAt(fromIndex)
        slideStates.add(toIndex, item)
        slideOps.add(PptDocument.MoveSlide(fromIndex, toIndex))
        currentSlide = toIndex
        selectedShapeIndex = null
        dirty = true
        showSlideActions = false
    }

    fun addTextBox() {
        val slide = slideStates.getOrNull(currentSlide) ?: return
        pushHistory()
        slide.shapes.add(ShapeState(TextFieldValue("Text box")))
        val newIdx = slide.shapes.lastIndex
        selectedShapeIndex = newIdx
        slideOps.add(PptDocument.AddTextBox(currentSlide, "Text box"))
        dirty = true
    }

    fun toggleBold() = applyRunTransform { it.copy(bold = !it.bold) }
    fun toggleItalic() = applyRunTransform { it.copy(italic = !it.italic) }
    fun toggleUnderline() = applyRunTransform { it.copy(underline = !it.underline) }
    fun setFontSize(sizePt: Float) = applyRunTransform { it.copy(sizePt = sizePt) }
    fun setColor(colorHex: String) = applyRunTransform { it.copy(colorHex = colorHex) }

    private fun applyRunTransform(transform: (PptDocument.RunStyle) -> PptDocument.RunStyle) {
        val shapeIdx = selectedShapeIndex ?: return
        val slide = slideStates.getOrNull(currentSlide) ?: return
        val currentShape = slide.shapes.getOrNull(shapeIdx) ?: return
        pushHistory()
        slide.shapes[shapeIdx].textValue = PptRichText.restyleSelection(currentShape.textValue, transform)
        dirty = true
    }

    private fun pushHistory() {
        val snapshot = HistorySnapshot(
            slides = slideStates.map { s -> s.copy(shapes = s.shapes.map { it.copy(textValue = it.textValue.copy()) }.toMutableList()) },
            currentSlide = currentSlide,
            selectedShape = selectedShapeIndex,
        )
        if (undoStack.size >= maxHistory) undoStack.removeFirst()
        undoStack.addLast(snapshot)
        redoStack.clear()
        updateHistoryFlags()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = HistorySnapshot(
            slides = slideStates.map { s -> s.copy(shapes = s.shapes.map { it.copy(textValue = it.textValue.copy()) }.toMutableList()) },
            currentSlide = currentSlide,
            selectedShape = selectedShapeIndex,
        )
        redoStack.addLast(current)
        val snap = undoStack.removeLast()
        restoreSnapshot(snap)
        updateHistoryFlags()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = HistorySnapshot(
            slides = slideStates.map { s -> s.copy(shapes = s.shapes.map { it.copy(textValue = it.textValue.copy()) }.toMutableList()) },
            currentSlide = currentSlide,
            selectedShape = selectedShapeIndex,
        )
        undoStack.addLast(current)
        val snap = redoStack.removeLast()
        restoreSnapshot(snap)
        updateHistoryFlags()
    }

    private fun restoreSnapshot(snap: HistorySnapshot) {
        slideStates.clear()
        for (slideSnap in snap.slides) {
            slideStates.add(slideSnap.copy(shapes = slideSnap.shapes.map { it.copy(textValue = it.textValue.copy()) }.toMutableList()))
        }
        currentSlide = snap.currentSlide.coerceIn(0, slideStates.lastIndex)
        selectedShapeIndex = snap.selectedShape
        dirty = true
    }

    private fun updateHistoryFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
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

        val richEdits = mutableMapOf<Pair<Int, Int>, List<PptDocument.ShapeRun>>()
        for ((sIdx, slide) in slideStates.withIndex()) {
            for ((shapeIdx, shapeState) in slide.shapes.withIndex()) {
                richEdits[sIdx to shapeIdx] = PptRichText.toRuns(shapeState.textValue.annotatedString)
            }
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val serialized = PptDocument.applyEditsAndSerialize(
                        originalBytes = bytes,
                        slideOps = slideOps.toList(),
                        richEdits = richEdits,
                    )
                    DocumentIo.writeBytes(ctx, target, serialized)
                }
            }
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }
}
