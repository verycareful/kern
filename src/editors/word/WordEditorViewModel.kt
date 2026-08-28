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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kern.shared.io.DocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Word editor state with full rich-text character formatting, paragraph/list styles,
 * interactive table mutations, find & replace, undo/redo history, and word counts.
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

    var isFindOpen by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
    var replaceQuery by mutableStateOf("")
    var currentMatchIndex by mutableIntStateOf(0)
        private set

    data class SearchMatch(
        val blockIndex: Int,
        val matchIndexInBlock: Int,
        val isTable: Boolean = false,
        val row: Int = 0,
        val col: Int = 0,
    )

    val matches by derivedStateOf {
        val q = searchQuery
        if (q.isBlank()) return@derivedStateOf emptyList<SearchMatch>()
        val list = ArrayList<SearchMatch>()
        blocks.forEachIndexed { i, block ->
            when (block) {
                is WordDocument.ParagraphBlock -> {
                    val text = fields[i]?.text ?: block.text
                    var start = 0
                    var matchCount = 0
                    while (start < text.length) {
                        val pos = text.indexOf(q, start, ignoreCase = true)
                        if (pos < 0) break
                        list.add(SearchMatch(blockIndex = i, matchIndexInBlock = matchCount))
                        matchCount++
                        start = pos + q.length
                    }
                }
                is WordDocument.TableBlock -> {
                    val grid = tableStates[i] ?: block.rows
                    for (r in grid.indices) {
                        for (c in grid[r].indices) {
                            val cellText = grid[r][c]
                            var start = 0
                            var matchCount = 0
                            while (start < cellText.length) {
                                val pos = cellText.indexOf(q, start, ignoreCase = true)
                                if (pos < 0) break
                                list.add(SearchMatch(blockIndex = i, matchIndexInBlock = matchCount, isTable = true, row = r, col = c))
                                matchCount++
                                start = pos + q.length
                            }
                        }
                    }
                }
                WordDocument.OpaqueBlock, is WordDocument.ImageBlock -> Unit
            }
        }
        list
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

    // ---- Table Operations ---------------------------------------------------

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

    fun toggleFind() {
        isFindOpen = !isFindOpen
        if (!isFindOpen) {
            searchQuery = ""
            replaceQuery = ""
        }
    }

    fun nextMatch() {
        val count = matches.size
        if (count > 0) {
            currentMatchIndex = (currentMatchIndex + 1) % count
        }
    }

    fun prevMatch() {
        val count = matches.size
        if (count > 0) {
            currentMatchIndex = (currentMatchIndex - 1 + count) % count
        }
    }

    fun replaceOne() {
        val mList = matches
        if (currentMatchIndex !in mList.indices || searchQuery.isEmpty()) return
        val match = mList[currentMatchIndex]
        pushSnapshot()
        if (match.isTable) {
            val grid = tableStates[match.blockIndex] ?: return
            val cell = grid.getOrNull(match.row)?.getOrNull(match.col) ?: return
            val replaced = replaceNth(cell, searchQuery, replaceQuery, match.matchIndexInBlock)
            editTableCell(match.blockIndex, match.row, match.col, replaced)
        } else {
            val field = fields[match.blockIndex] ?: return
            val raw = field.text
            val replaced = replaceNth(raw, searchQuery, replaceQuery, match.matchIndexInBlock)
            val runs = WordRichText.toRuns(field.annotatedString)
            val updatedAnnotated = WordRichText.toAnnotated(WordRichText.toRuns(androidx.compose.ui.text.AnnotatedString(replaced)))
            fields[match.blockIndex] = field.copy(annotatedString = updatedAnnotated)
            edits[match.blockIndex] = WordRichText.toRuns(updatedAnnotated)
            dirty = true
        }
        if (currentMatchIndex >= matches.size && matches.isNotEmpty()) {
            currentMatchIndex = 0
        }
    }

    fun replaceAll() {
        if (searchQuery.isEmpty()) return
        pushSnapshot()
        blocks.forEachIndexed { i, block ->
            when (block) {
                is WordDocument.ParagraphBlock -> {
                    val field = fields[i] ?: return@forEachIndexed
                    if (field.text.contains(searchQuery, ignoreCase = true)) {
                        val newText = field.text.replace(searchQuery, replaceQuery, ignoreCase = true)
                        val newAnnotated = androidx.compose.ui.text.AnnotatedString(newText)
                        fields[i] = field.copy(annotatedString = newAnnotated)
                        edits[i] = WordRichText.toRuns(newAnnotated)
                        dirty = true
                    }
                }
                is WordDocument.TableBlock -> {
                    val grid = tableStates[i] ?: return@forEachIndexed
                    var changed = false
                    val newGrid = grid.map { row ->
                        row.map { cell ->
                            if (cell.contains(searchQuery, ignoreCase = true)) {
                                changed = true
                                cell.replace(searchQuery, replaceQuery, ignoreCase = true)
                            } else {
                                cell
                            }
                        }
                    }
                    if (changed) {
                        tableStates[i] = newGrid
                        tableEdits[i] = newGrid
                        dirty = true
                    }
                }
                WordDocument.OpaqueBlock, is WordDocument.ImageBlock -> Unit
            }
        }
        currentMatchIndex = 0
    }

    private fun replaceNth(text: String, target: String, replacement: String, n: Int): String {
        var start = 0
        var count = 0
        while (start < text.length) {
            val pos = text.indexOf(target, start, ignoreCase = true)
            if (pos < 0) break
            if (count == n) {
                return text.substring(0, pos) + replacement + text.substring(pos + target.length)
            }
            count++
            start = pos + target.length
        }
        return text
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
}
