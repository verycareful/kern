package dev.kern.editors.pptx

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
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
/** Keystrokes closer together than this are one undo step. */
private const val TypingBurstMillis = 1500L

/** A shape can be no smaller than this on either axis, in points. */
private const val MinShapeSize = 20f

/** At least this much of a moved shape stays on the slide, so it can be grabbed again. */
private const val MinShapeVisible = 24f

private fun decorationStates(slide: PptDocument.SlideModel): List<PptEditorViewModel.DecorationState> =
    slide.decorations.map { PptEditorViewModel.DecorationState(it, it.bounds ?: FallbackDecorationBounds) }

/**
 * Where a text shape goes when the file declares no geometry for it: staggered by
 * index so several of them stay separately reachable instead of covering one another.
 */
private fun fallbackBounds(index: Int) = PptDocument.ShapeBounds(
    x = 50f,
    y = 50f + index * 60f,
    width = 300f,
    height = 50f,
)

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
        /** Where the shape is drawn, in slide points. Never null: see [fallbackBounds]. */
        bounds: PptDocument.ShapeBounds,
        /**
         * The text as it was read from the file, or null for a shape the file does not
         * have yet. A shape whose text still equals this is left untouched on save, so
         * the parts of its markup the editor does not model (paragraph spacing,
         * bullets, language tags, effects) survive a save that never meant to change
         * them.
         */
        val savedText: AnnotatedString? = null,
        /** The geometry the file declares, or null when it declares none and [bounds] is a fallback. */
        val savedBounds: PptDocument.ShapeBounds? = null,
        /** The inherited colour for runs that name none; display only, see [PptDocument.TextShapeModel.defaultColorHex]. */
        val defaultColorHex: String? = null,
        /** Where the text sits in the box: t, ctr or b. Null means the file's default, top. */
        anchor: String? = null,
        val savedAnchor: String? = anchor,
    ) {
        var textValue by mutableStateOf(textValue)
        var bounds by mutableStateOf(bounds)
        var anchor by mutableStateOf(anchor)

        val textChanged: Boolean get() = textValue.annotatedString != savedText
        val boundsChanged: Boolean get() = bounds != savedBounds
        val anchorChanged: Boolean get() = anchor != savedAnchor

        fun copy(
            textValue: TextFieldValue = this.textValue,
            bounds: PptDocument.ShapeBounds = this.bounds,
        ) = ShapeState(textValue, bounds, savedText, savedBounds, defaultColorHex, anchor, savedAnchor)
    }

    /**
     * A picture, drawn shape, table or placeholder on a slide. Its content is read-only;
     * its position is not, so that is the one field held as state.
     */
    class DecorationState(
        val decoration: PptDocument.SlideDecoration,
        bounds: PptDocument.ShapeBounds,
        /** The geometry the file declares, or null when [bounds] is a fallback. */
        val savedBounds: PptDocument.ShapeBounds? = decoration.bounds,
    ) {
        var bounds by mutableStateOf(bounds)
        val boundsChanged: Boolean get() = bounds != savedBounds
        fun copy() = DecorationState(decoration, bounds, savedBounds)
    }

    class SlideState(
        shapes: List<ShapeState> = emptyList(),
        width: Float = 960f,
        height: Float = 540f,
        /** The resolved background; null is PowerPoint's plain white. Read-only. */
        val background: PptDocument.Fill? = null,
        /** Pictures, tables, drawn shapes and placeholders, in file order. */
        val decorations: List<DecorationState> = emptyList(),
        /** The theme's text colour, for text boxes added on this slide. */
        val textColorHex: String? = null,
    ) {
        /** A snapshot list, so adding or deleting a text box recomposes the canvas. */
        val shapes: SnapshotStateList<ShapeState> =
            mutableStateListOf<ShapeState>().apply { addAll(shapes) }
        var width by mutableStateOf(width)
        var height by mutableStateOf(height)

        fun copy(
            shapes: List<ShapeState> = this.shapes,
            width: Float = this.width,
            height: Float = this.height,
            background: PptDocument.Fill? = this.background,
            decorations: List<DecorationState> = this.decorations.map { it.copy() },
            textColorHex: String? = this.textColorHex,
        ) = SlideState(shapes, width, height, background, decorations, textColorHex)
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
    /** The selected decoration, exclusive with [selectedShapeIndex]. */
    var selectedDecorationIndex by mutableStateOf<Int?>(null)
        private set

    var showLayoutPicker by mutableStateOf(false)
    var showSlideActions by mutableStateOf(false)
    var actionSlideIndex by mutableIntStateOf(0)
    var showFontSizeSheet by mutableStateOf(false)
    var showColorSheet by mutableStateOf(false)
    var showAlignSheet by mutableStateOf(false)

    val slideStates = mutableStateListOf<SlideState>()
    val slideOps = mutableListOf<PptDocument.SlideOp>()

    private val undoStack = ArrayDeque<HistorySnapshot>()
    private val redoStack = ArrayDeque<HistorySnapshot>()
    private val maxHistory = 30

    /** The typing burst that the most recent undo step is absorbing, if one is open. */
    private class TypingBurst(val slide: Int, val shape: Int, val lastEditMillis: Long)
    private var typingBurst: TypingBurst? = null

    /** A formatting command issued at a caret, waiting for the text typed there. */
    private class PendingStyle(
        val slide: Int,
        val shape: Int,
        val offset: Int,
        val transform: (PptDocument.RunStyle) -> PptDocument.RunStyle,
    )
    private var pendingStyle by mutableStateOf<PendingStyle?>(null)

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

    val currentDecorations: List<DecorationState>
        get() = currentSlideState?.decorations ?: emptyList()

    /** The style the toolbar shows: what is at the caret, plus any command waiting there. */
    val caretStyle: PptDocument.RunStyle
        get() {
            val shapeIdx = selectedShapeIndex ?: return PptDocument.RunStyle()
            val shapeState = slideStates.getOrNull(currentSlide)?.shapes?.getOrNull(shapeIdx) ?: return PptDocument.RunStyle()
            val atCaret = PptRichText.styleAt(shapeState.textValue)
            val pending = pendingStyle?.takeIf {
                it.slide == currentSlide && it.shape == shapeIdx && it.offset == shapeState.textValue.selection.start
            }
            return pending?.transform?.invoke(atCaret) ?: atCaret
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
                    val shapes = slide.shapes.mapIndexed { index, shape ->
                        val text = PptRichText.toAnnotated(shape.runs, shape.alignments.map { PptRichText.fromOoxmlAlign(it) })
                        ShapeState(
                            textValue = TextFieldValue(annotatedString = text),
                            bounds = shape.bounds ?: fallbackBounds(index),
                            savedText = text,
                            savedBounds = shape.bounds,
                            defaultColorHex = shape.defaultColorHex,
                            anchor = shape.anchor,
                        )
                    }.toMutableList()
                    if (shapes.isEmpty() && parsed.slides.isNotEmpty()) {
                        val fallback = parsed.slides.getOrNull(slide.slideIndex)?.mapIndexed { index, text ->
                            ShapeState(textValue = TextFieldValue(text), bounds = fallbackBounds(index))
                        }?.toMutableList() ?: mutableListOf(ShapeState(textValue = TextFieldValue(""), bounds = fallbackBounds(0)))
                        slideStates.add(SlideState(shapes = fallback, width = slide.width, height = slide.height, background = slide.background, decorations = decorationStates(slide), textColorHex = slide.textColorHex))
                    } else {
                        slideStates.add(SlideState(shapes = if (shapes.isEmpty()) mutableListOf(ShapeState(textValue = TextFieldValue(""), bounds = fallbackBounds(0))) else shapes, width = slide.width, height = slide.height, background = slide.background, decorations = decorationStates(slide), textColorHex = slide.textColorHex))
                    }
                }
                if (slideStates.isEmpty()) {
                    slideStates.add(SlideState(mutableListOf(ShapeState(textValue = TextFieldValue("Title"), bounds = fallbackBounds(0)))))
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
            selectedDecorationIndex = null
        }
    }

    fun nextSlide() = goToSlide(currentSlide + 1)
    fun previousSlide() = goToSlide(currentSlide - 1)

    fun selectShape(index: Int?) {
        selectedShapeIndex = index
        if (index != null) selectedDecorationIndex = null
    }

    fun selectDecoration(index: Int?) {
        selectedDecorationIndex = index
        if (index != null) selectedShapeIndex = null
    }

    /**
     * Applies a text field change. A change to the text itself is undoable; consecutive
     * changes to the same shape within [TypingBurstMillis] of each other share one undo
     * step, so undo takes back a burst of typing rather than one character, and a
     * caret move or selection change on its own records nothing.
     */
    fun updateShapeValue(shapeIndex: Int, value: TextFieldValue) {
        val slide = slideStates.getOrNull(currentSlide) ?: return
        val shape = slide.shapes.getOrNull(shapeIndex) ?: return
        val before = shape.textValue.annotatedString
        if (before.text != value.text) {
            val now = System.nanoTime() / 1_000_000
            val burst = typingBurst
            val continues = burst != null && burst.slide == currentSlide &&
                burst.shape == shapeIndex && now - burst.lastEditMillis <= TypingBurstMillis
            if (!continues) pushHistory()
            typingBurst = TypingBurst(currentSlide, shapeIndex, now)
            // The field reports plain text; the formatting is put back around the edit.
            // A pending command applies to what was just typed, then it is spent.
            val pending = pendingStyle?.takeIf {
                it.slide == currentSlide && it.shape == shapeIndex && it.offset == shape.textValue.selection.start
            }
            val merged = PptRichText.mergeEdit(before, value.text, pending?.transform)
            pendingStyle = null
            shape.textValue = value.copy(annotatedString = merged)
            dirty = true
        } else {
            // A caret move or selection change. Moving away from where a command was
            // issued abandons it, as in any word processor.
            if (value.selection != shape.textValue.selection) pendingStyle = null
            shape.textValue = value.copy(annotatedString = before)
        }
    }

    fun addSlide(layout: PptDocument.PresetLayout) {
        pushHistory()
        // A deck has one slide size and one theme. Taking both from the neighbour keeps
        // a new slide the same shape as the rest, rather than the 16:9 default in a 4:3
        // presentation, and gives its text the deck's colour rather than the app's.
        val existing = currentSlideState
        fun shape(index: Int, text: String) =
            ShapeState(TextFieldValue(text), bounds = fallbackBounds(index), defaultColorHex = existing?.textColorHex)
        val newShapes = when (layout) {
            PptDocument.PresetLayout.TITLE -> mutableListOf(shape(0, "Title"), shape(1, "Subtitle"))
            PptDocument.PresetLayout.TITLE_AND_CONTENT -> mutableListOf(shape(0, "Title"), shape(1, "Content"))
            PptDocument.PresetLayout.SECTION_HEADER -> mutableListOf(shape(0, "Section Header"))
            PptDocument.PresetLayout.BLANK -> mutableListOf()
        }
        val insertAt = currentSlide + 1
        slideStates.add(
            insertAt,
            if (existing == null) SlideState(newShapes)
            else SlideState(newShapes, existing.width, existing.height, textColorHex = existing.textColorHex),
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
        slideStates.add(insertAt, SlideState(shapes = cloneShapes, width = slide.width, height = slide.height, background = slide.background, decorations = slide.decorations.map { it.copy() }, textColorHex = slide.textColorHex))
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
        slide.shapes.add(
            ShapeState(TextFieldValue("Text box"), bounds = fallbackBounds(slide.shapes.size), defaultColorHex = slide.textColorHex),
        )
        val newIdx = slide.shapes.lastIndex
        selectedShapeIndex = newIdx
        slideOps.add(PptDocument.AddTextBox(currentSlide, "Text box"))
        dirty = true
    }

    /**
     * Records one undo step for a drag that is about to begin, so the whole gesture,
     * however many move events it delivers, comes back in a single undo.
     */
    fun beginShapeDrag(index: Int) {
        val slide = slideStates.getOrNull(currentSlide) ?: return
        if (index !in slide.shapes.indices) return
        pushHistory()
        selectShape(index)
    }

    fun beginDecorationDrag(index: Int) {
        val slide = slideStates.getOrNull(currentSlide) ?: return
        if (index !in slide.decorations.indices) return
        pushHistory()
        selectDecoration(index)
    }

    /** Moves a text shape by a delta in slide points, kept at least partly on the slide. */
    fun moveShape(index: Int, dx: Float, dy: Float) {
        val slide = slideStates.getOrNull(currentSlide) ?: return
        val shape = slide.shapes.getOrNull(index) ?: return
        shape.bounds = moved(shape.bounds, dx, dy, slide)
        dirty = true
    }

    fun moveDecoration(index: Int, dx: Float, dy: Float) {
        val slide = slideStates.getOrNull(currentSlide) ?: return
        val decoration = slide.decorations.getOrNull(index) ?: return
        decoration.bounds = moved(decoration.bounds, dx, dy, slide)
        dirty = true
    }

    /**
     * Resizes a text shape by dragging one corner; the opposite corner stays put.
     * [left] and [top] say which corner: the delta is applied to the edge on that side.
     */
    fun resizeShape(index: Int, left: Boolean, top: Boolean, dx: Float, dy: Float) {
        val slide = slideStates.getOrNull(currentSlide) ?: return
        val shape = slide.shapes.getOrNull(index) ?: return
        shape.bounds = resized(shape.bounds, left, top, dx, dy)
        dirty = true
    }

    fun resizeDecoration(index: Int, left: Boolean, top: Boolean, dx: Float, dy: Float) {
        val slide = slideStates.getOrNull(currentSlide) ?: return
        val decoration = slide.decorations.getOrNull(index) ?: return
        decoration.bounds = resized(decoration.bounds, left, top, dx, dy)
        dirty = true
    }

    private fun moved(b: PptDocument.ShapeBounds, dx: Float, dy: Float, slide: SlideState) = b.copy(
        x = (b.x + dx).coerceIn(MinShapeVisible - b.width, slide.width - MinShapeVisible),
        y = (b.y + dy).coerceIn(MinShapeVisible - b.height, slide.height - MinShapeVisible),
    )

    private fun resized(b: PptDocument.ShapeBounds, left: Boolean, top: Boolean, dx: Float, dy: Float): PptDocument.ShapeBounds {
        var x1 = b.x
        var y1 = b.y
        var x2 = b.x + b.width
        var y2 = b.y + b.height
        if (left) x1 = (x1 + dx).coerceAtMost(x2 - MinShapeSize) else x2 = (x2 + dx).coerceAtLeast(x1 + MinShapeSize)
        if (top) y1 = (y1 + dy).coerceAtMost(y2 - MinShapeSize) else y2 = (y2 + dy).coerceAtLeast(y1 + MinShapeSize)
        return PptDocument.ShapeBounds(x1, y1, x2 - x1, y2 - y1)
    }

    /** The alignment of the paragraph at the caret, for the toolbar. */
    val caretAlignment: TextAlign?
        get() {
            val shapeIdx = selectedShapeIndex ?: return null
            val value = slideStates.getOrNull(currentSlide)?.shapes?.getOrNull(shapeIdx)?.textValue ?: return null
            val paragraph = value.text.substring(0, value.selection.min).count { it == '\n' }
            return PptRichText.paragraphAlignments(value.annotatedString).getOrNull(paragraph)
        }

    /** The selected shape's vertical anchor (t, ctr, b), for the toolbar. */
    val caretAnchor: String?
        get() = selectedShapeIndex?.let { slideStates.getOrNull(currentSlide)?.shapes?.getOrNull(it)?.anchor }

    /** Sets where the selected shape's text sits in its box. */
    fun setAnchor(anchor: String) {
        val shapeIdx = selectedShapeIndex ?: return
        val shape = slideStates.getOrNull(currentSlide)?.shapes?.getOrNull(shapeIdx) ?: return
        if (shape.anchor == anchor) return
        pushHistory()
        shape.anchor = anchor
        dirty = true
    }

    /** Aligns the paragraphs the selection touches, or the one the caret is in. */
    fun setAlignment(align: TextAlign) {
        val shapeIdx = selectedShapeIndex ?: return
        val slide = slideStates.getOrNull(currentSlide) ?: return
        val shape = slide.shapes.getOrNull(shapeIdx) ?: return
        pushHistory()
        shape.textValue = PptRichText.alignParagraphs(shape.textValue, align)
        dirty = true
    }

    fun toggleBold() = applyRunTransform { it.copy(bold = !it.bold) }
    fun toggleItalic() = applyRunTransform { it.copy(italic = !it.italic) }
    fun toggleUnderline() = applyRunTransform { it.copy(underline = !it.underline) }
    fun setFontSize(sizePt: Float) = applyRunTransform { it.copy(sizePt = sizePt) }
    fun setColor(colorHex: String) = applyRunTransform { it.copy(colorHex = colorHex) }

    /**
     * Applies a formatting command the way PowerPoint does: to the highlighted text if
     * there is any, else to the word around the caret, else (caret at a boundary or in
     * an empty shape) to whatever is typed next.
     */
    private fun applyRunTransform(transform: (PptDocument.RunStyle) -> PptDocument.RunStyle) {
        val shapeIdx = selectedShapeIndex ?: return
        val slide = slideStates.getOrNull(currentSlide) ?: return
        val shape = slide.shapes.getOrNull(shapeIdx) ?: return
        val value = shape.textValue
        val selection = value.selection
        val range = when {
            !selection.collapsed -> selection.min until selection.max
            else -> PptRichText.wordAt(value.text, selection.start)
        }
        if (range == null) {
            val previous = pendingStyle?.takeIf { it.slide == currentSlide && it.shape == shapeIdx && it.offset == selection.start }
            val combined: (PptDocument.RunStyle) -> PptDocument.RunStyle =
                if (previous != null) { style -> transform(previous.transform(style)) } else transform
            pendingStyle = PendingStyle(currentSlide, shapeIdx, selection.start, combined)
            return
        }
        pushHistory()
        shape.textValue = PptRichText.restyleRange(value, range.first, range.last + 1, transform)
        dirty = true
    }

    /**
     * Records the current state as an undo step. Every edit that is not typing goes
     * through here, and so does the first keystroke of a burst; either way an open burst
     * is over, so the next keystroke starts a fresh step.
     */
    private fun pushHistory() {
        typingBurst = null
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
        typingBurst = null
        slideStates.clear()
        for (slideSnap in snap.slides) {
            slideStates.add(slideSnap.copy(shapes = slideSnap.shapes.map { it.copy(textValue = it.textValue.copy()) }.toMutableList()))
        }
        currentSlide = snap.currentSlide.coerceIn(0, slideStates.lastIndex)
        selectedShapeIndex = snap.selectedShape
        selectedDecorationIndex = null
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

        // Only what changed is written. Rewriting every shape would replace markup the
        // editor does not model with its own defaults, on shapes the user never touched.
        val richEdits = mutableMapOf<Pair<Int, Int>, List<PptDocument.ShapeRun>>()
        val alignEdits = mutableMapOf<Pair<Int, Int>, List<String?>>()
        val boundsEdits = mutableMapOf<Pair<Int, Int>, PptDocument.ShapeBounds>()
        val decorationBoundsEdits = mutableMapOf<Pair<Int, Int>, PptDocument.ShapeBounds>()
        val anchorEdits = mutableMapOf<Pair<Int, Int>, String>()
        for ((sIdx, slide) in slideStates.withIndex()) {
            for ((shapeIdx, shapeState) in slide.shapes.withIndex()) {
                if (shapeState.textChanged) {
                    val text = shapeState.textValue.annotatedString
                    richEdits[sIdx to shapeIdx] = PptRichText.toRuns(text)
                    alignEdits[sIdx to shapeIdx] = PptRichText.paragraphAlignments(text).map { PptRichText.toOoxmlAlign(it) }
                }
                if (shapeState.boundsChanged) boundsEdits[sIdx to shapeIdx] = shapeState.bounds
                if (shapeState.anchorChanged) shapeState.anchor?.let { anchorEdits[sIdx to shapeIdx] = it }
            }
            for ((decorationIdx, decoration) in slide.decorations.withIndex()) {
                if (decoration.boundsChanged) decorationBoundsEdits[sIdx to decorationIdx] = decoration.bounds
            }
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val serialized = PptDocument.applyEditsAndSerialize(
                        originalBytes = bytes,
                        slideOps = slideOps.toList(),
                        richEdits = richEdits,
                        boundsEdits = boundsEdits,
                        alignEdits = alignEdits,
                        decorationBoundsEdits = decorationBoundsEdits,
                        anchorEdits = anchorEdits,
                    )
                    DocumentIo.writeBytes(ctx, target, serialized)
                }
            }
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }
}
