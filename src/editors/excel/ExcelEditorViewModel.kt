package dev.kern.editors.excel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kern.shared.CellMerge
import dev.kern.shared.io.DocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Excel editor state with multi-sheet support. Each sheet keeps its own editable
 * grid; edits are tracked by (sheet, row, col) so saving applies them across all
 * sheets onto the original workbook (see [ExcelDocument]).
 */
class ExcelEditorViewModel(app: Application) : AndroidViewModel(app) {

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
    private val edits = mutableMapOf<Triple<Int, Int, Int>, String>()
    private val structuralOps = mutableListOf<ExcelDocument.StructuralOp>()
    private var started = false

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
                sheetNames = parsed.sheetNames
                mergedRegions = parsed.mergedRegions
                currentSheet = 0
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
            selectedRow = 0
            selectedCol = 0
        }
    }

    fun select(row: Int, col: Int) {
        selectedRow = row
        selectedCol = col
    }

    fun selectedValue(): String = sheetGrids.getOrNull(currentSheet)?.getOrNull(selectedRow)?.getOrNull(selectedCol) ?: ""

    fun editSelected(value: String) {
        val row = sheetGrids.getOrNull(currentSheet)?.getOrNull(selectedRow) ?: return
        if (selectedCol in row.indices && row[selectedCol] != value) {
            row[selectedCol] = value
            edits[Triple(currentSheet, selectedRow, selectedCol)] = value
            dirty = true
        }
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
    }

    /** Shifts edits at or right of [at] in [sheet] one column over to match a column insert. */
    private fun rekeyColInsert(sheet: Int, at: Int) {
        val remapped = edits.mapKeys { (k, _) ->
            if (k.first == sheet && k.third >= at) Triple(k.first, k.second, k.third + 1) else k
        }
        edits.clear()
        edits.putAll(remapped)
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
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DocumentIo.writeBytes(ctx, target, ExcelDocument.applyEditsAndSerialize(bytes, snapshot, ops)) }
            }
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }
}
