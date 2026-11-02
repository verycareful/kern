package dev.kern.editors.excel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kern.shared.CellMerge
import dev.kern.shared.find.CellRef
import dev.kern.shared.find.FindMark
import dev.kern.shared.find.FindMessages
import dev.kern.shared.find.FindState
import dev.kern.shared.find.GridFindTarget
import dev.kern.shared.find.GridSearch
import dev.kern.shared.find.SegmentMatch
import dev.kern.shared.io.DocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Excel editor state with multi-sheet support. Each sheet keeps its own editable
 * grid; edits are tracked by (sheet, row, col) so saving applies them across all
 * sheets onto the original workbook (see [ExcelDocument]).
 */
class ExcelEditorViewModel(app: Application) : AndroidViewModel(app), GridFindTarget {

    var fileName by mutableStateOf("")
        private set
    var loading by mutableStateOf(true)
        private set
    var dirty by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selectedRow by mutableIntStateOf(0)
        private set
    var selectedCol by mutableIntStateOf(0)
        private set
    var sheetNames by mutableStateOf<List<String>>(emptyList())
        private set
    var currentSheet by mutableIntStateOf(0)
        private set

    private var mergedRegions: List<List<CellMerge>> = emptyList()

    /** Merged regions for the currently active sheet. */
    val currentMergedRegions: List<CellMerge> get() = mergedRegions.getOrNull(currentSheet) ?: emptyList()

    private var uri: Uri? = null
    private var originalBytes: ByteArray? = null
    private var sheetGrids: List<SnapshotStateList<SnapshotStateList<String>>> = emptyList()
    private var parsedColWidths: List<Map<Int, Float>> = emptyList()
    private var parsedRowHeights: List<Map<Int, Float>> = emptyList()
    private val edits = mutableMapOf<Triple<Int, Int, Int>, String>()
    private val structuralOps = mutableListOf<ExcelDocument.StructuralOp>()
    private val colWidthEdits = mutableMapOf<Pair<Int, Int>, Float>()
    private val rowHeightEdits = mutableMapOf<Pair<Int, Int>, Float>()
    private var started = false
    
    var colWidths by mutableStateOf<Map<Int, Float>>(emptyMap())
        private set
    var rowHeights by mutableStateOf<Map<Int, Float>>(emptyMap())
        private set

    /**
     * Per sheet, the cells still holding a formula. Find matches them, but replace leaves
     * them alone, since writing their text back would turn the formula into plain text.
     */
    private var formulaCells: List<MutableSet<Pair<Int, Int>>> = emptyList()

    override val find = FindState()

    /** Bumped when a sheet grid is replaced wholesale, so matches are searched again. */
    private var gridsVersion by mutableIntStateOf(0)

    private val matches: List<SegmentMatch<CellRef>> by derivedStateOf {
        gridsVersion
        find.activeFinder?.let { GridSearch.matches(it, sheetGrids) } ?: emptyList()
    }
    private val matchedCells: Set<CellRef> by derivedStateOf { matches.mapTo(HashSet()) { it.key } }

    override val matchCount: Int get() = matches.size
    override val canReplace: Boolean get() = true
    override val currentCell: CellRef? get() = matches.getOrNull(find.currentIn(matches.size))?.key

    override fun cellMark(sheet: Int, row: Int, col: Int): FindMark {
        val ref = CellRef(sheet, row, col)
        return when {
            ref == currentCell -> FindMark.CURRENT
            ref in matchedCells -> FindMark.MATCH
            else -> FindMark.NONE
        }
    }

    private fun isFormula(ref: CellRef) = formulaCells.getOrNull(ref.sheet)?.contains(ref.row to ref.col) == true

    override fun replaceCurrent() {
        val finder = find.finder ?: return
        val match = matches.getOrNull(find.currentIn(matches.size)) ?: return
        val ref = match.key
        if (isFormula(ref)) {
            find.message = GridSearch.formulaNotReplaced
            return
        }
        val text = sheetGrids[ref.sheet][ref.row][ref.col]
        finder.replaceAt(text, match.match, find.replacement)
            .onSuccess {
                setCell(ref, it)
                find.message = null
                find.reveal()
            }
            .onFailure { find.message = FindMessages.failed(it) }
    }

    override fun replaceAll() {
        val finder = find.finder ?: return
        GridSearch.replaceAll(finder, sheetGrids, find.replacement, locked = ::isFormula)
            .onSuccess { result ->
                for ((ref, value) in result.values) setCell(ref, value)
                find.message = GridSearch.replacedMessage(result)
            }
            .onFailure { find.message = FindMessages.failed(it) }
    }

    private fun setCell(ref: CellRef, value: String) {
        val row = sheetGrids.getOrNull(ref.sheet)?.getOrNull(ref.row) ?: return
        if (ref.col !in row.indices || row[ref.col] == value) return
        row[ref.col] = value
        edits[Triple(ref.sheet, ref.row, ref.col)] = value
        formulaCells.getOrNull(ref.sheet)?.remove(ref.row to ref.col)
        dirty = true
    }

    /** Grid for the currently selected sheet. */
    val rows: List<List<String>> get() = sheetGrids.getOrNull(currentSheet) ?: emptyList()
    val columnCount: Int get() = rows.firstOrNull()?.size ?: 0

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
                    Triple(DocumentIo.displayName(ctx, decoded), bytes, ExcelDocument.read(bytes))
                }
            }
            result.onSuccess { (name, bytes, parsed) ->
                fileName = name
                originalBytes = bytes
                sheetGrids = parsed.sheets.map { grid -> grid.map { it.toMutableStateList() }.toMutableStateList() }
                formulaCells = parsed.sheets.indices.map { parsed.formulaCells.getOrNull(it).orEmpty().toMutableSet() }
                gridsVersion++
                sheetNames = parsed.sheetNames
                mergedRegions = parsed.mergedRegions
                parsedColWidths = parsed.colWidths
                parsedRowHeights = parsed.rowHeights
                currentSheet = 0
                colWidths = parsedColWidths.getOrNull(0) ?: emptyMap()
                rowHeights = parsedRowHeights.getOrNull(0) ?: emptyMap()
                selectedRow = 0
                selectedCol = 0
                loading = false
            }.onFailure {
                error = it.message ?: "Could not open the spreadsheet."
                loading = false
            }
        }
    }

    fun selectSheet(index: Int) {
        if (index in sheetNames.indices && index != currentSheet) {
            currentSheet = index
            colWidths = (parsedColWidths.getOrNull(index) ?: emptyMap()) + colWidthEdits.filterKeys { it.first == index }.mapKeys { it.key.second }
            rowHeights = (parsedRowHeights.getOrNull(index) ?: emptyMap()) + rowHeightEdits.filterKeys { it.first == index }.mapKeys { it.key.second }
            selectedRow = 0
            selectedCol = 0
        }
    }

    fun select(row: Int, col: Int) {
        selectedRow = row
        selectedCol = col
    }

    fun selectedValue(): String = sheetGrids.getOrNull(currentSheet)?.getOrNull(selectedRow)?.getOrNull(selectedCol) ?: ""

    fun editSelected(value: String) = setCell(CellRef(currentSheet, selectedRow, selectedCol), value)

    fun resizeColumn(c: Int, dp: Float) {
        colWidths = colWidths + (c to dp)
        colWidthEdits[Pair(currentSheet, c)] = dp
        dirty = true
    }

    fun resizeRow(r: Int, dp: Float) {
        rowHeights = rowHeights + (r to dp)
        rowHeightEdits[Pair(currentSheet, r)] = dp
        dirty = true
    }

    fun autoResizeColumn(c: Int) {
        val grid = sheetGrids.getOrNull(currentSheet) ?: return
        var maxLen = 4
        for (row in grid) {
            val cell = row.getOrNull(c) ?: continue
            if (cell.length > maxLen) maxLen = cell.length
        }
        resizeColumn(c, (maxLen * 8f + 24f).coerceIn(40f, 600f))
    }

    fun autoResizeRow(r: Int) {
        val grid = sheetGrids.getOrNull(currentSheet) ?: return
        val row = grid.getOrNull(r) ?: return
        var maxLines = 1
        for (cell in row) {
            val lines = cell.count { it == '\n' } + 1
            if (lines > maxLines) maxLines = lines
        }
        resizeRow(r, (maxLines * 20f + 16f).coerceIn(24f, 400f))
    }

    /**
     * Inserts a blank row below the selection. The displayed grid, the edit keys
     * (shifted down past the insert), and the recorded structural op all stay in
     * lockstep so the edit-only save reproduces the insert on the original workbook.
     */
    fun addRow() {
        val grid = sheetGrids.getOrNull(currentSheet) ?: return
        val at = (selectedRow + 1).coerceIn(0, grid.size)
        grid.add(at, MutableList(columnCount.coerceAtLeast(1)) { "" }.toMutableStateList())
        rekeyRowInsert(currentSheet, at)
        structuralOps.add(ExcelDocument.InsertRow(currentSheet, at))
        dirty = true
    }

    /** Inserts a blank column to the right of the selection (see [addRow]). */
    fun addColumn() {
        val grid = sheetGrids.getOrNull(currentSheet) ?: return
        if (grid.isEmpty()) {
            grid.add(mutableStateListOf(""))
            structuralOps.add(ExcelDocument.InsertColumn(currentSheet, 0))
            dirty = true
            return
        }
        val at = (selectedCol + 1).coerceIn(0, grid.first().size)
        grid.forEach { row -> row.add(at.coerceIn(0, row.size), "") }
        rekeyColInsert(currentSheet, at)
        structuralOps.add(ExcelDocument.InsertColumn(currentSheet, at))
        dirty = true
    }

    /** Shifts edits at or below [at] in [sheet] down one row to match a row insert. */
    private fun rekeyRowInsert(sheet: Int, at: Int) {
        val remapped = edits.mapKeys { (k, _) ->
            if (k.first == sheet && k.second >= at) Triple(k.first, k.second + 1, k.third) else k
        }
        edits.clear()
        edits.putAll(remapped)
        formulaCells.getOrNull(sheet)?.let { cells ->
            val shifted = cells.map { (r, c) -> if (r >= at) r + 1 to c else r to c }
            cells.clear()
            cells.addAll(shifted)
        }
    }

    /** Shifts edits at or right of [at] in [sheet] one column over to match a column insert. */
    private fun rekeyColInsert(sheet: Int, at: Int) {
        val remapped = edits.mapKeys { (k, _) ->
            if (k.first == sheet && k.third >= at) Triple(k.first, k.second, k.third + 1) else k
        }
        edits.clear()
        edits.putAll(remapped)
        formulaCells.getOrNull(sheet)?.let { cells ->
            val shifted = cells.map { (r, c) -> if (c >= at) r to c + 1 else r to c }
            cells.clear()
            cells.addAll(shifted)
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
        val ops = structuralOps.toList()
        val cwSnapshot = colWidthEdits.toMap()
        val rhSnapshot = rowHeightEdits.toMap()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DocumentIo.writeBytes(ctx, target, ExcelDocument.applyEditsAndSerialize(bytes, snapshot, ops, cwSnapshot, rhSnapshot)) }
            }
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }
}
