package dev.kern.editors.excel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
    var selectedRow by mutableStateOf(0)
        private set
    var selectedCol by mutableStateOf(0)
        private set
    var sheetNames by mutableStateOf<List<String>>(emptyList())
        private set
    var currentSheet by mutableStateOf(0)
        private set

    private var uri: Uri? = null
    private var originalBytes: ByteArray? = null
    private var sheetGrids: List<SnapshotStateList<SnapshotStateList<String>>> = emptyList()
    private val edits = mutableMapOf<Triple<Int, Int, Int>, String>()
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

    fun addRow() {
        val grid = sheetGrids.getOrNull(currentSheet) ?: return
        grid.add(MutableList(columnCount.coerceAtLeast(1)) { "" }.toMutableStateList())
        dirty = true
    }

    fun addColumn() {
        val grid = sheetGrids.getOrNull(currentSheet) ?: return
        if (grid.isEmpty()) grid.add(mutableStateListOf(""))
        grid.forEach { it.add("") }
        dirty = true
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
                runCatching { DocumentIo.writeBytes(ctx, target, ExcelDocument.applyEditsAndSerialize(bytes, snapshot)) }
            }
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }
}
