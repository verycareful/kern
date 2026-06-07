package dev.kern.shared.ui

import android.net.Uri
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CellWidth = 108.dp
private val CellHeight = 40.dp
private val GutterWidth = 52.dp
private val HeaderHeight = 36.dp

/** Spreadsheet-style column label for a zero-based index: 0 -> A, 25 -> Z, 26 -> AA. */
fun spreadsheetColumnLabel(index: Int): String {
    var i = index
    val sb = StringBuilder()
    while (i >= 0) {
        sb.insert(0, 'A' + (i % 26))
        i = i / 26 - 1
    }
    return sb.toString()
}

/**
 * Shared editor UI for the grid-based formats (CSV, Excel): a titled top bar with
 * save state, a cell-reference/value bar, a scrollable grid with a frozen header
 * row and row gutter, and add row/column actions. The host wires its ViewModel via
 * the callbacks; this composable owns no format-specific logic.
 *
 * @param onSave invoked with a result callback so this screen can report via snackbar.
 * @param onExportToUri invoked with the chosen Save-as URI and a result callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridEditorScreen(
    title: String,
    dirty: Boolean,
    loading: Boolean,
    error: String?,
    hue: Color,
    rows: List<List<String>>,
    selectedRow: Int,
    selectedCol: Int,
    selectedValue: String,
    onSelect: (row: Int, col: Int) -> Unit,
    onEditSelected: (String) -> Unit,
    onSave: (onResult: (Boolean, String?) -> Unit) -> Unit,
    exportMimeType: String,
    exportFileName: String,
    onExportToUri: (Uri, onResult: (Boolean, String?) -> Unit) -> Unit,
    onAddRow: () -> Unit,
    onAddColumn: () -> Unit,
    sheetNames: List<String> = emptyList(),
    currentSheet: Int = 0,
    onSelectSheet: (Int) -> Unit = {},
) {
    UnsavedChangesGuard(dirty)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val zoom = rememberZoomState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(exportMimeType),
    ) { uri ->
        if (uri != null) {
            onExportToUri(uri) { ok, msg ->
                scope.launch { snackbar.showSnackbar(if (ok) "Exported" else "Export failed: ${msg ?: "unknown error"}") }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (dirty) "Unsaved changes" else "Saved - on device",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dirty) hue else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onSave { ok, msg -> scope.launch { snackbar.showSnackbar(if (ok) "Saved" else "Save failed: ${msg ?: "read-only file, use Export"}") } } },
                        enabled = dirty,
                    ) { Icon(Icons.Default.Save, contentDescription = "Save", tint = if (dirty) hue else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { exportLauncher.launch(exportFileName) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export a copy")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Text(
                    text = error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Column(Modifier.fillMaxSize()) {
                    if (sheetNames.size > 1) SheetBar(sheetNames, currentSheet, onSelectSheet, hue)
                    CellReferenceBar(rows.isNotEmpty(), selectedRow, selectedCol, selectedValue, hue, onEditSelected)
                    Box(Modifier.weight(1f).pinchZoom(zoom)) {
                        Grid(rows, selectedRow, selectedCol, hue, onSelect, zoom.scale, Modifier.fillMaxSize())
                        if (zoom.scale != 1f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("${(zoom.scale * 100).roundToInt()}%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = hue)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = onAddRow) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Text("Row")
                        }
                        TextButton(onClick = onAddColumn) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Text("Column")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetBar(names: List<String>, current: Int, onSelect: (Int) -> Unit, hue: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        names.forEachIndexed { i, name ->
            val selected = i == current
            Box(
                modifier = Modifier
                    .background(
                        color = if (selected) hue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = name,
                    color = if (selected) hue else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CellReferenceBar(
    hasCells: Boolean,
    selectedRow: Int,
    selectedCol: Int,
    value: String,
    hue: Color,
    onEditSelected: (String) -> Unit,
) {
    val ref = if (hasCells) "${spreadsheetColumnLabel(selectedCol)}${selectedRow + 1}" else "-"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(ref, color = hue, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onEditSelected,
            enabled = hasCells,
            singleLine = true,
            placeholder = { Text("Empty cell") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Grid(
    rows: List<List<String>>,
    selectedRow: Int,
    selectedCol: Int,
    hue: Color,
    onSelect: (Int, Int) -> Unit,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val hScroll = rememberScrollState()
    val columns = rows.firstOrNull()?.size ?: 0
    val cellW = CellWidth * scale
    val cellH = CellHeight * scale
    val gutterW = GutterWidth * scale
    val headerH = HeaderHeight * scale
    val cellFont = (13 * scale).sp
    val headerFont = (12 * scale).sp
    val gutterFont = (11 * scale).sp
    Column(modifier) {
        Row {
            Tile("", gutterW, headerH, headerFont, hue, selected = false)
            Row(Modifier.horizontalScroll(hScroll)) {
                repeat(columns) { c ->
                    Tile(spreadsheetColumnLabel(c), cellW, headerH, headerFont, hue, selected = c == selectedCol)
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(rows) { r, row ->
                Row {
                    Tile((r + 1).toString(), gutterW, cellH, gutterFont, hue, selected = r == selectedRow)
                    Row(Modifier.horizontalScroll(hScroll)) {
                        for (c in 0 until columns) {
                            GridCell(
                                value = row.getOrElse(c) { "" },
                                width = cellW,
                                height = cellH,
                                fontSize = cellFont,
                                selected = r == selectedRow && c == selectedCol,
                                hue = hue,
                                onClick = { onSelect(r, c) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(text: String, width: Dp, height: Dp, fontSize: TextUnit, hue: Color, selected: Boolean) {
    Box(
        modifier = Modifier
            .size(width, height)
            .background(if (selected) hue.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
            color = if (selected) hue else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GridCell(value: String, width: Dp, height: Dp, fontSize: TextUnit, selected: Boolean, hue: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width, height)
            .background(if (selected) hue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .border(width = if (selected) 2.dp else 0.5.dp, color = if (selected) hue else MaterialTheme.colorScheme.outlineVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = fontSize, color = MaterialTheme.colorScheme.onSurface)
    }
}
