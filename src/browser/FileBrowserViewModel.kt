package dev.kern.browser

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kern.shared.DocumentFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class BrowserView { RECENT, ALL }
enum class BrowserLayout { LIST, GRID }
enum class SortKey(val label: String) { RECENT("Date modified"), NAME("Name"), SIZE("Size"), TYPE("Type") }

/**
 * File browser state: storage access, the scan results, persisted pins/recents,
 * and the view controls (tab, search, format filter, layout, sort). Exposes the
 * derived lists the screen renders plus the pin/open/delete actions.
 */
class FileBrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val store = BrowserStore(app)

    var hasAccess by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set

    private var docs by mutableStateOf<List<ScannedDoc>>(emptyList())
    var pins by mutableStateOf<Set<String>>(emptySet())
        private set
    private var recents by mutableStateOf<List<String>>(emptyList())

    var view by mutableStateOf(BrowserView.RECENT)
        private set
    var query by mutableStateOf("")
        private set
    var filter by mutableStateOf<DocumentFormat?>(null)
        private set
    var layout by mutableStateOf(BrowserLayout.LIST)
        private set
    var sort by mutableStateOf(SortKey.RECENT)
        private set
    var sortAscending by mutableStateOf(false)
        private set

    /** Re-checks permission and (if granted) rescans. Call on entry and on resume. */
    fun refresh() {
        val ctx = getApplication<Application>()
        hasAccess = FileScanner.hasAccess(ctx)
        pins = store.pins()
        recents = store.recents()
        if (!hasAccess) {
            docs = emptyList()
            return
        }
        loading = true
        viewModelScope.launch {
            val scanned = withContext(Dispatchers.IO) {
                runCatching { FileScanner.scan() }.getOrDefault(emptyList())
            }
            docs = scanned
            loading = false
        }
    }

    fun selectView(v: BrowserView) { view = v }
    fun updateQuery(q: String) { query = q }
    fun selectFilter(f: DocumentFormat?) { filter = f }
    fun toggleLayout() { layout = if (layout == BrowserLayout.LIST) BrowserLayout.GRID else BrowserLayout.LIST }

    /** Picks a sort field; tapping the active field again reverses direction. */
    fun pickSort(key: SortKey) {
        if (key == sort) sortAscending = !sortAscending
        else {
            sort = key
            sortAscending = key == SortKey.NAME || key == SortKey.TYPE
        }
    }

    // --- derived lists ---------------------------------------------------------

    private fun matchesQuery(d: ScannedDoc) = query.isBlank() || d.name.contains(query, ignoreCase = true)

    val pinnedDocs: List<ScannedDoc> get() = docs.filter { it.path in pins }

    val recentDocs: List<ScannedDoc>
        get() {
            val byPath = docs.associateBy { it.path }
            return recents.mapNotNull { byPath[it] }.filter { matchesQuery(it) }
        }

    val allDocs: List<ScannedDoc>
        get() {
            val filtered = docs.filter { matchesQuery(it) && (filter == null || it.format == filter) }
            val ascending = filtered.sortedWith(comparatorFor(sort))
            return if (sortAscending) ascending else ascending.reversed()
        }

    private fun comparatorFor(key: SortKey): Comparator<ScannedDoc> = when (key) {
        SortKey.RECENT -> compareBy { it.modified }
        SortKey.NAME -> compareBy { it.name.lowercase() }
        SortKey.SIZE -> compareBy { it.size }
        SortKey.TYPE -> compareBy({ it.format.ordinal }, { it.name.lowercase() })
    }

    // --- actions ---------------------------------------------------------------

    fun isPinned(d: ScannedDoc) = d.path in pins

    fun togglePin(d: ScannedDoc) {
        store.togglePin(d.path)
        pins = store.pins()
    }

    fun recordOpen(d: ScannedDoc) {
        store.recordOpen(d.path)
        recents = store.recents()
    }

    /** Deletes the file from disk and prunes it from pins/recents. Returns success. */
    fun delete(d: ScannedDoc): Boolean {
        val ok = runCatching { File(d.path).delete() }.getOrDefault(false)
        if (ok) {
            store.forget(d.path)
            docs = docs.filter { it.path != d.path }
            pins = store.pins()
            recents = store.recents()
        }
        return ok
    }
}
