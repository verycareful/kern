package dev.kern.editors.csv

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
import dev.kern.shared.io.DocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the editable CSV grid and drives load/save/export. File I/O runs off the
 * main thread; the grid is Compose snapshot state so cell edits recompose directly.
 */
class CsvEditorViewModel(app: Application) : AndroidViewModel(app) {

    val rows = mutableStateListOf<SnapshotStateList<String>>()

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

    private var uri: Uri? = null
    private var started = false

    val columnCount: Int get() = rows.firstOrNull()?.size ?: 0

    /** Loads the document once for the given encoded content URI (safe to call on recomposition). */
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
                    DocumentIo.displayName(ctx, decoded) to CsvDocument.parse(DocumentIo.readText(ctx, decoded))
                }
            }
            result.onSuccess { (name, grid) ->
                fileName = name
                rows.clear()
                grid.forEach { rows.add(it.toMutableStateList()) }
                selectedRow = 0
                selectedCol = 0
                loading = false
            }.onFailure {
                error = it.message ?: "Could not open the file."
                loading = false
            }
        }
    }

    fun select(row: Int, col: Int) {
        selectedRow = row
        selectedCol = col
    }

    fun selectedValue(): String = rows.getOrNull(selectedRow)?.getOrNull(selectedCol) ?: ""

    fun editSelected(value: String) {
        val row = rows.getOrNull(selectedRow) ?: return
        if (selectedCol in row.indices && row[selectedCol] != value) {
            row[selectedCol] = value
            dirty = true
        }
    }

    /** Inserts a blank row just below the selected row (appends if nothing useful is selected). */
    fun addRow() {
        val insertAt = (selectedRow + 1).coerceIn(0, rows.size)
        rows.add(insertAt, MutableList(columnCount.coerceAtLeast(1)) { "" }.toMutableStateList())
        dirty = true
    }

    /** Inserts a blank column just to the right of the selected column. */
    fun addColumn() {
        if (rows.isEmpty()) {
            rows.add(mutableStateListOf(""))
            dirty = true
            return
        }
        rows.forEach { row ->
            val insertAt = (selectedCol + 1).coerceIn(0, row.size)
            row.add(insertAt, "")
        }
        dirty = true
    }

    /** Writes back to the original URI. */
    fun save(onResult: (ok: Boolean, message: String?) -> Unit) {
        val target = uri ?: run { onResult(false, "Nothing to save."); return }
        write(target) { ok, msg ->
            if (ok) dirty = false
            onResult(ok, msg)
        }
    }

    /** Writes a copy to a user-chosen URI (Save as). */
    fun exportTo(target: Uri, onResult: (ok: Boolean, message: String?) -> Unit) = write(target, onResult)

    private fun write(target: Uri, onResult: (ok: Boolean, message: String?) -> Unit) {
        val ctx = getApplication<Application>()
        val snapshot = rows.map { it.toList() }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DocumentIo.writeText(ctx, target, CsvDocument.toCsv(snapshot)) }
            }
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }
}
