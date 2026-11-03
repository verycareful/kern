package dev.kern.editors.word

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kern.shared.find.FindMessage
import dev.kern.shared.find.FindMessages
import dev.kern.shared.find.FindState
import dev.kern.shared.find.FindTarget
import dev.kern.shared.find.SegmentMatch
import dev.kern.shared.find.findIn
import dev.kern.shared.find.replaceAll
import dev.kern.shared.find.replaceAt
import dev.kern.shared.io.DocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Word editor state with full rich-text character formatting, paragraph/list styles,
 * interactive table mutations, find & replace, undo/redo history, and word counts.
 */
class WordEditorViewModel(app: Application) : AndroidViewModel(app), FindTarget {

    var fileName by mutableStateOf("")
        private set
    var loading by mutableStateOf(true)
        private set
    var dirty by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Body blocks in order. */
    var blocks by mutableStateOf<List<WordDocument.Block>>(emptyList())
        private set

    /** Editable paragraph values, keyed by block index. */
    val fields = mutableStateMapOf<Int, TextFieldValue>()

    /** Paragraph properties (kind, alignment, list type, indent), keyed by block index. */
    val paraProps = mutableStateMapOf<Int, WordDocument.ParaProps>()

    /** Editable table cell states, keyed by block index. */
    val tableStates = mutableStateMapOf<Int, List<List<String>>>()

    var focusedIndex by mutableStateOf<Int?>(null)
        private set

    /** Most-recently-used colours (6-hex), newest first. */
    var recentColors by mutableStateOf<List<String>>(emptyList())
        private set

    /** User-editable custom colour slots. */
    val customSlots = mutableStateListOf<String?>(null, null)

    // ---- Undo / Redo --------------------------------------------------------

    private data class Snapshot(
        val fields: Map<Int, TextFieldValue>,
        val paraProps: Map<Int, WordDocument.ParaProps>,
        val tableStates: Map<Int, List<List<String>>>,
    )

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private val maxHistory = 30

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    // ---- Find & Replace -----------------------------------------------------

    override val find = FindState()

    /** Where a match is: a paragraph ([row] and [col] -1) or one cell of a table. */
    data class WordSpot(val block: Int, val row: Int = -1, val col: Int = -1) {
        val isCell: Boolean get() = row >= 0
    }

    val matches: List<SegmentMatch<WordSpot>> by derivedStateOf {
        val finder = find.activeFinder ?: return@derivedStateOf emptyList()
        finder.findIn(searchSegments())
    }

    override val matchCount: Int get() = matches.size
    override val canReplace: Boolean get() = true

    /** The current match, for the page to highlight and scroll to. */
    val currentMatch: SegmentMatch<WordSpot>? get() = matches.getOrNull(find.currentIn(matches.size))

    private fun searchSegments(): Sequence<Pair<WordSpot, CharSequence>> = sequence {
        blocks.forEachIndexed { i, block ->
            when (block) {
                is WordDocument.ParagraphBlock -> yield(WordSpot(i) to (fields[i]?.text ?: block.text))
                is WordDocument.TableBlock -> (tableStates[i] ?: block.rows).forEachIndexed { r, row ->
                    row.forEachIndexed { c, cell -> yield(WordSpot(i, r, c) to cell) }
                }
                WordDocument.OpaqueBlock, is WordDocument.ImageBlock -> Unit
            }
        }
    }

    // ---- Statistics ---------------------------------------------------------

    val totalWordCount by derivedStateOf {
        var count = 0
        blocks.forEachIndexed { i, block ->
            when (block) {
                is WordDocument.ParagraphBlock -> {
                    val t = fields[i]?.text ?: block.text
                    count += countWords(t)
                }
                is WordDocument.TableBlock -> {
                    val grid = tableStates[i] ?: block.rows
                    grid.forEach { row -> row.forEach { cell -> count += countWords(cell) } }
                }
                WordDocument.OpaqueBlock, is WordDocument.ImageBlock -> Unit
            }
        }
        count
    }

    val totalCharCount by derivedStateOf {
        var count = 0
        blocks.forEachIndexed { i, block ->
            when (block) {
                is WordDocument.ParagraphBlock -> {
                    val t = fields[i]?.text ?: block.text
                    count += t.length
                }
                is WordDocument.TableBlock -> {
                    val grid = tableStates[i] ?: block.rows
                    grid.forEach { row -> row.forEach { cell -> count += cell.length } }
                }
                WordDocument.OpaqueBlock, is WordDocument.ImageBlock -> Unit
            }
        }
        count
    }

    private var uri: Uri? = null
    private var originalBytes: ByteArray? = null
    private val edits = mutableMapOf<Int, List<WordDocument.Run>>()
    private val paraPropOps = mutableMapOf<Int, WordDocument.ParaPropsUpdate>()
    private val tableEdits = mutableMapOf<Int, List<List<String>>>()
    private var started = false

    fun start(routeArgument: String?) {
        if (started) return
        started = true
        val ctx = getApplication<Application>()
        val decoded = DocumentIo.fromRouteArgument(routeArgument)
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
                paraProps.clear()
                tableStates.clear()
                parsed.blocks.forEachIndexed { i, block ->
                    when (block) {
                        is WordDocument.ParagraphBlock -> {
                            paraProps[i] = block.props
                            if (block.editable) {
                                fields[i] = TextFieldValue(WordRichText.toAnnotated(block.runs))
                            }
                        }
                        is WordDocument.TableBlock -> {
                            tableStates[i] = block.rows
                        }
                        WordDocument.OpaqueBlock, is WordDocument.ImageBlock -> Unit
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
        if (previous != null && previous.annotatedString.text != value.annotatedString.text) {
            pushSnapshot()
        }
        fields[index] = value
        focusedIndex = index
        if (previous == null || previous.annotatedString != value.annotatedString) {
            edits[index] = WordRichText.toRuns(value.annotatedString)
            dirty = true
        }
    }

    fun canFormat(): Boolean {
        val idx = focusedIndex ?: return false
        return (blocks.getOrNull(idx) as? WordDocument.ParagraphBlock)?.editable == true
    }

    fun activeStyle(): WordDocument.RunStyle? =
        focusedIndex?.let { fields[it] }?.let { WordRichText.styleAt(it) }

    fun activeParaProps(): WordDocument.ParaProps? =
        focusedIndex?.let { paraProps[it] }

    // ---- Character Formatting -----------------------------------------------

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

    fun setColor(hex: String?) {
        val applied = mutateFocused { v -> WordRichText.restyleSelection(v) { it.copy(colorHex = hex) } }
        if (applied && hex != null) pushRecent(hex)
    }

    fun setFontSize(points: Float?) {
        mutateFocused { v -> WordRichText.restyleSelection(v) { it.copy(sizePt = points) } }
    }

    fun setCustomSlot(slot: Int, input: String): Boolean {
        val hex = WordRichText.normalizeHex(input) ?: return false
        if (slot in customSlots.indices) customSlots[slot] = hex
        setColor(hex)
        return true
    }

    // ---- Paragraph & List Formatting ----------------------------------------

    fun setParagraphKind(kind: WordDocument.Kind) {
        val idx = focusedIndex ?: return
        val current = paraProps[idx] ?: return
        pushSnapshot()
        val updated = current.copy(kind = kind)
        paraProps[idx] = updated
        paraPropOps[idx] = (paraPropOps[idx] ?: WordDocument.ParaPropsUpdate()).copy(kind = kind)
        dirty = true
    }

    fun setAlignment(align: WordDocument.Align) {
        val idx = focusedIndex ?: return
        val current = paraProps[idx] ?: return
        pushSnapshot()
        val updated = current.copy(align = align)
        paraProps[idx] = updated
        paraPropOps[idx] = (paraPropOps[idx] ?: WordDocument.ParaPropsUpdate()).copy(align = align)
        dirty = true
    }

    fun toggleBulletList() {
        val idx = focusedIndex ?: return
        val current = paraProps[idx] ?: return
        pushSnapshot()
        val target = if (current.listType == WordDocument.ListType.BULLET) WordDocument.ListType.NONE else WordDocument.ListType.BULLET
        val updated = current.copy(listType = target)
        paraProps[idx] = updated
        paraPropOps[idx] = (paraPropOps[idx] ?: WordDocument.ParaPropsUpdate()).copy(listType = target)
        dirty = true
    }

    fun toggleNumberedList() {
        val idx = focusedIndex ?: return
        val current = paraProps[idx] ?: return
        pushSnapshot()
        val target = if (current.listType == WordDocument.ListType.NUMBER) WordDocument.ListType.NONE else WordDocument.ListType.NUMBER
        val updated = current.copy(listType = target)
        paraProps[idx] = updated
        paraPropOps[idx] = (paraPropOps[idx] ?: WordDocument.ParaPropsUpdate()).copy(listType = target)
        dirty = true
    }

    fun increaseIndent() {
        val idx = focusedIndex ?: return
        val current = paraProps[idx] ?: return
        pushSnapshot()
        val newIndent = current.indentTwips + 360 // 0.25 in
        val updated = current.copy(indentTwips = newIndent)
        paraProps[idx] = updated
        paraPropOps[idx] = (paraPropOps[idx] ?: WordDocument.ParaPropsUpdate()).copy(indentTwips = newIndent)
        dirty = true
    }

    fun decreaseIndent() {
        val idx = focusedIndex ?: return
        val current = paraProps[idx] ?: return
        pushSnapshot()
        val newIndent = (current.indentTwips - 360).coerceAtLeast(0)
        val updated = current.copy(indentTwips = newIndent)
        paraProps[idx] = updated
        paraPropOps[idx] = (paraPropOps[idx] ?: WordDocument.ParaPropsUpdate()).copy(indentTwips = newIndent)
        dirty = true
    }

    // ---- Table Operations & Dedicated Modal --------------------------------

    var activeTableBlockIndex by mutableStateOf<Int?>(null)
        private set
    var activeTableData by mutableStateOf<List<List<String>>>(emptyList())
        private set
    var activeSelectedRow by mutableIntStateOf(0)
    var activeSelectedCol by mutableIntStateOf(0)

    fun openTableEditor(blockIndex: Int) {
        val grid = tableStates[blockIndex] ?: (blocks.getOrNull(blockIndex) as? WordDocument.TableBlock)?.rows ?: return
        activeTableBlockIndex = blockIndex
        activeTableData = grid.map { it.toList() }
        activeSelectedRow = 0
        activeSelectedCol = 0
    }

    fun closeTableEditor() {
        activeTableBlockIndex = null
        activeTableData = emptyList()
    }

    fun updateActiveTableCell(row: Int, col: Int, text: String) {
        if (row !in activeTableData.indices || col !in activeTableData[row].indices) return
        activeTableData = activeTableData.mapIndexed { r, rList ->
            if (r == row) {
                rList.mapIndexed { c, cellText -> if (c == col) text else cellText }
            } else {
                rList
            }
        }
    }

    fun insertTableRow(atIndex: Int) {
        val colCount = activeTableData.firstOrNull()?.size ?: 2
        val newRow = List(colCount) { "" }
        val insertPos = atIndex.coerceIn(0, activeTableData.size)
        val list = activeTableData.toMutableList()
        list.add(insertPos, newRow)
        activeTableData = list
        activeSelectedRow = insertPos
    }

    fun deleteTableRow(atIndex: Int) {
        if (activeTableData.size <= 1 || atIndex !in activeTableData.indices) return
        val list = activeTableData.toMutableList()
        list.removeAt(atIndex)
        activeTableData = list
        if (activeSelectedRow >= activeTableData.size) {
            activeSelectedRow = (activeTableData.size - 1).coerceAtLeast(0)
        }
    }

    fun insertTableColumn(atIndex: Int) {
        val insertPos = atIndex.coerceIn(0, activeTableData.firstOrNull()?.size ?: 0)
        activeTableData = activeTableData.map { row ->
            val rList = row.toMutableList()
            rList.add(insertPos, "")
            rList
        }
        activeSelectedCol = insertPos
    }

    fun deleteTableColumn(atIndex: Int) {
        val colCount = activeTableData.firstOrNull()?.size ?: 0
        if (colCount <= 1 || atIndex !in 0 until colCount) return
        activeTableData = activeTableData.map { row ->
            val rList = row.toMutableList()
            if (atIndex in rList.indices) rList.removeAt(atIndex)
            rList
        }
        val maxCol = (activeTableData.firstOrNull()?.size ?: 1) - 1
        if (activeSelectedCol > maxCol) {
            activeSelectedCol = maxCol.coerceAtLeast(0)
        }
    }

    fun saveTableEditor() {
        val blockIndex = activeTableBlockIndex ?: return
        val newGrid = activeTableData
        pushSnapshot()
        tableStates[blockIndex] = newGrid
        tableEdits[blockIndex] = newGrid
        dirty = true
        closeTableEditor()
    }

    fun editTableCell(blockIndex: Int, row: Int, col: Int, text: String) {
        val grid = tableStates[blockIndex] ?: return
        if (row !in grid.indices || col !in grid[row].indices) return
        if (grid[row][col] == text) return
        pushSnapshot()
        val newGrid = grid.mapIndexed { r, rList ->
            if (r == row) {
                rList.mapIndexed { c, cellText -> if (c == col) text else cellText }
            } else {
                rList
            }
        }
        tableStates[blockIndex] = newGrid
        tableEdits[blockIndex] = newGrid
        dirty = true
    }

    fun addTableRow(blockIndex: Int) {
        val grid = tableStates[blockIndex] ?: return
        pushSnapshot()
        val colCount = grid.firstOrNull()?.size ?: 2
        val newRow = List(colCount) { "" }
        val newGrid = grid + listOf(newRow)
        tableStates[blockIndex] = newGrid
        tableEdits[blockIndex] = newGrid
        dirty = true
    }

    fun deleteTableRow(blockIndex: Int, row: Int) {
        val grid = tableStates[blockIndex] ?: return
        if (grid.size <= 1 || row !in grid.indices) return
        pushSnapshot()
        val newGrid = grid.filterIndexed { index, _ -> index != row }
        tableStates[blockIndex] = newGrid
        tableEdits[blockIndex] = newGrid
        dirty = true
    }

    fun addTableColumn(blockIndex: Int) {
        val grid = tableStates[blockIndex] ?: return
        pushSnapshot()
        val newGrid = grid.map { row -> row + "" }
        tableStates[blockIndex] = newGrid
        tableEdits[blockIndex] = newGrid
        dirty = true
    }

    fun deleteTableColumn(blockIndex: Int, col: Int) {
        val grid = tableStates[blockIndex] ?: return
        if ((grid.firstOrNull()?.size ?: 0) <= 1) return
        pushSnapshot()
        val newGrid = grid.map { row -> row.filterIndexed { index, _ -> index != col } }
        tableStates[blockIndex] = newGrid
        tableEdits[blockIndex] = newGrid
        dirty = true
    }

    // ---- Find & Replace Actions ---------------------------------------------

    /**
     * Replaces the current match as one undo step. In a paragraph the replacement keeps
     * the formatting of the first character it replaces.
     */
    override fun replaceCurrent() {
        val finder = find.finder ?: return
        val match = currentMatch ?: return
        val spot = match.key
        if (spot.isCell) {
            val cell = tableStates[spot.block]?.getOrNull(spot.row)?.getOrNull(spot.col) ?: return
            finder.replaceAt(cell, match.match, find.replacement)
                .onSuccess { editTableCell(spot.block, spot.row, spot.col, it); afterReplace() }
                .onFailure { find.message = FindMessages.failed(it) }
            return
        }
        val field = fields[spot.block] ?: run { find.message = READ_ONLY_PARAGRAPH; return }
        finder.replaceAt(field.annotatedString, match.match, find.replacement)
            .onSuccess { replaced ->
                pushSnapshot()
                setParagraph(spot.block, field, replaced)
                afterReplace()
            }
            .onFailure { find.message = FindMessages.failed(it) }
    }

    /** Replaces every match as one undo step; nothing changes if any replacement fails. */
    override fun replaceAll() {
        val finder = find.finder ?: return
        val replacement = find.replacement
        val outcome = runCatching {
            val paragraphs = HashMap<Int, AnnotatedString>()
            val tables = HashMap<Int, List<List<String>>>()
            var count = 0
            var readOnly = 0
            blocks.forEachIndexed { i, block ->
                when (block) {
                    is WordDocument.ParagraphBlock -> {
                        val field = fields[i]
                        if (field == null) {
                            if (finder.count(block.text) > 0) readOnly++
                        } else if (finder.count(field.text) > 0) {
                            val result = finder.replaceAll(field.annotatedString, replacement).getOrThrow()
                            paragraphs[i] = result.value
                            count += result.count
                        }
                    }
                    is WordDocument.TableBlock -> {
                        val grid = tableStates[i] ?: return@forEachIndexed
                        var changed = false
                        val newGrid = grid.map { row ->
                            row.map { cell ->
                                val result = finder.replaceAll(cell, replacement).getOrThrow()
                                if (result.count > 0) changed = true
                                count += result.count
                                result.value
                            }
                        }
                        if (changed) tables[i] = newGrid
                    }
                    WordDocument.OpaqueBlock, is WordDocument.ImageBlock -> Unit
                }
            }
            Triple(paragraphs, tables, count to readOnly)
        }
        val (paragraphs, tables, counts) = outcome.getOrElse { find.message = FindMessages.failed(it); return }
        if (paragraphs.isNotEmpty() || tables.isNotEmpty()) {
            pushSnapshot()
            for ((i, text) in paragraphs) setParagraph(i, fields.getValue(i), text)
            for ((i, grid) in tables) {
                tableStates[i] = grid
                tableEdits[i] = grid
            }
            dirty = true
        }
        val (count, readOnly) = counts
        val replaced = FindMessages.replaced(count, replacement.isEmpty())
        find.message = if (readOnly == 0) replaced else FindMessage("${replaced.text}, skipped $readOnly read-only paragraphs", isError = false)
    }

    private fun setParagraph(index: Int, field: TextFieldValue, text: AnnotatedString) {
        fields[index] = field.copy(annotatedString = text, selection = TextRange(field.selection.start.coerceAtMost(text.length)))
        edits[index] = WordRichText.toRuns(text)
        dirty = true
    }

    private fun afterReplace() {
        find.message = null
        find.reveal()
    }

    // ---- History Management -------------------------------------------------

    private fun pushSnapshot() {
        val snap = Snapshot(
            fields = fields.toMap(),
            paraProps = paraProps.toMap(),
            tableStates = tableStates.toMap(),
        )
        undoStack.addLast(snap)
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
        updateHistoryState()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val currentSnap = Snapshot(
            fields = fields.toMap(),
            paraProps = paraProps.toMap(),
            tableStates = tableStates.toMap(),
        )
        redoStack.addLast(currentSnap)
        val target = undoStack.removeLast()
        applySnapshot(target)
        updateHistoryState()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val currentSnap = Snapshot(
            fields = fields.toMap(),
            paraProps = paraProps.toMap(),
            tableStates = tableStates.toMap(),
        )
        undoStack.addLast(currentSnap)
        val target = redoStack.removeLast()
        applySnapshot(target)
        updateHistoryState()
    }

    private fun applySnapshot(snap: Snapshot) {
        fields.clear()
        fields.putAll(snap.fields)
        paraProps.clear()
        paraProps.putAll(snap.paraProps)
        tableStates.clear()
        tableStates.putAll(snap.tableStates)
        snap.fields.forEach { (i, v) -> edits[i] = WordRichText.toRuns(v.annotatedString) }
        snap.tableStates.forEach { (i, grid) -> tableEdits[i] = grid }
        dirty = true
    }

    private fun updateHistoryState() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun pushRecent(hex: String) {
        recentColors = (listOf(hex) + recentColors).distinct().take(6)
    }

    private fun mutateFocused(block: (TextFieldValue) -> TextFieldValue): Boolean {
        val idx = focusedIndex ?: return false
        val current = fields[idx] ?: return false
        pushSnapshot()
        val updated = block(current)
        fields[idx] = updated
        edits[idx] = WordRichText.toRuns(updated.annotatedString)
        dirty = true
        return true
    }

    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    }

    // ---- Save & Export ------------------------------------------------------

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
        val editsSnap = edits.toMap()
        val propsSnap = paraPropOps.toMap()
        val tablesSnap = tableEdits.toMap()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val serialized = WordDocument.applyEditsAndSerialize(
                        originalBytes = bytes,
                        edits = editsSnap,
                        paraProps = propsSnap,
                        tableEdits = tablesSnap,
                    )
                    DocumentIo.writeBytes(ctx, target, serialized)
                }
            }
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    private companion object {
        val READ_ONLY_PARAGRAPH = FindMessage("This paragraph is read-only, so it is found but not replaced", isError = true)
    }
}
