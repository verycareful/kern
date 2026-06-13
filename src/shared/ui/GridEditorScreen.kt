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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import dev.kern.shared.CellMerge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
 * @param onSave invoked with a result callback (success via snackbar, error via dialog).
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
    mergedRegions: List<CellMerge> = emptyList(),
) {
    UnsavedChangesGuard(dirty)
    val snackbar = remember { SnackbarHostState() }
    var errorDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val zoom = rememberZoomState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(exportMimeType),
    ) { uri ->
        if (uri != null) {
            onExportToUri(uri) { ok, msg ->
                if (ok) scope.launch { snackbar.showSnackbar("Exported") }
                else errorDialog = "Export failed" to (msg ?: "Unknown error")
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
                        onClick = { onSave { ok, msg ->
                            if (ok) scope.launch { snackbar.showSnackbar("Saved") }
                            else errorDialog = "Save failed" to (msg ?: "This file is read-only. Use Export to save a copy.")
                        } },
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
                        Grid(rows, selectedRow, selectedCol, hue, onSelect, zoom.scale, currentSheet, mergedRegions, Modifier.fillMaxSize())
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

    errorDialog?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = { TextButton(onClick = { errorDialog = null }) { Text("OK") } },
        )
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
    currentSheet: Int = 0,
    mergedRegions: List<CellMerge> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val flingSpec = remember { exponentialDecay<Float>() }

    val numRows = rows.size
    val columns = rows.firstOrNull()?.size ?: 1

    val cellW = CellWidth * scale
    val cellH = CellHeight * scale
    val gutterW = GutterWidth * scale
    val headerH = HeaderHeight * scale
    val cellFont = (13 * scale).sp
    val headerFont = (12 * scale).sp
    val gutterFont = (11 * scale).sp

    val cellWPx = remember(cellW, density) { with(density) { cellW.roundToPx() } }
    val cellHPx = remember(cellH, density) { with(density) { cellH.roundToPx() } }
    val gutterWPx = remember(gutterW, density) { with(density) { gutterW.roundToPx() } }

    var hScrollPx by remember { mutableStateOf(0) }
    var vScrollPx by remember { mutableStateOf(0) }

    // Reset scroll when the sheet changes (issue #3).
    LaunchedEffect(currentSheet) {
        hScrollPx = 0
        vScrollPx = 0
    }

    Column(modifier) {
        // Frozen column-header row
        Row {
            Tile("", gutterW, headerH, headerFont, hue, selected = false)
            BoxWithConstraints(Modifier.weight(1f).height(headerH).clipToBounds()) {
                val vpW = constraints.maxWidth
                val fc = (hScrollPx / cellWPx).coerceAtLeast(0)
                val lc = ((hScrollPx + vpW) / cellWPx + 1).coerceAtMost(columns - 1)
                for (c in fc..lc) {
                    key(c) {
                        Box(Modifier.offset { IntOffset(c * cellWPx - hScrollPx, 0) }) {
                            Tile(spreadsheetColumnLabel(c), cellW, headerH, headerFont, hue, c == selectedCol)
                        }
                    }
                }
            }
        }
        // Body: frozen row-gutter + 2D virtual cell area
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val vpW = constraints.maxWidth - gutterWPx
            val vpH = constraints.maxHeight
            val maxHScroll = (cellWPx * columns - vpW).coerceAtLeast(0)
            val maxVScroll = (cellHPx * numRows - vpH).coerceAtLeast(0)
            Row(Modifier.fillMaxSize()) {
                // Frozen row-number gutter
                Box(Modifier.width(gutterW).fillMaxHeight().clipToBounds()) {
                    val fr = (vScrollPx / cellHPx).coerceAtLeast(0)
                    val lr = ((vScrollPx + vpH) / cellHPx + 1).coerceAtMost(numRows - 1)
                    for (r in fr..lr) {
                        key(r) {
                            Box(Modifier.offset { IntOffset(0, r * cellHPx - vScrollPx) }) {
                                Tile((r + 1).toString(), gutterW, cellH, gutterFont, hue, r == selectedRow)
                            }
                        }
                    }
                }
                // 2D virtual cell body: SubcomposeLayout places only visible cells (and
                // merged-region origins that overlap the viewport) at pixel coordinates.
                // Both scroll axes are driven by detectDragGestures so a single finger
                // drag moves H and V simultaneously, and fling runs per-axis via Animatable.
                SubcomposeLayout(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds()
                        .pointerInput(maxHScroll, maxVScroll) {
                            val velocityTracker = VelocityTracker()
                            detectDragGestures(
                                onDragStart = { velocityTracker.resetTracking() },
                                onDragEnd = {
                                    val v = velocityTracker.calculateVelocity()
                                    coroutineScope.launch {
                                        Animatable(hScrollPx.toFloat()).animateDecay(-v.x, flingSpec) {
                                            hScrollPx = value.roundToInt().coerceIn(0, maxHScroll)
                                        }
                                    }
                                    coroutineScope.launch {
                                        Animatable(vScrollPx.toFloat()).animateDecay(-v.y, flingSpec) {
                                            vScrollPx = value.roundToInt().coerceIn(0, maxVScroll)
                                        }
                                    }
                                },
                                onDrag = { change, delta ->
                                    hScrollPx = (hScrollPx - delta.x.roundToInt()).coerceIn(0, maxHScroll)
                                    vScrollPx = (vScrollPx - delta.y.roundToInt()).coerceIn(0, maxVScroll)
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    change.consume()
                                },
                            )
                        },
                ) { constraints ->
                    val vpWPx = constraints.maxWidth
                    val vpHPx = constraints.maxHeight

                    val fr = (vScrollPx / cellHPx).coerceAtLeast(0)
                    val lr = ((vScrollPx + vpHPx) / cellHPx + 1).coerceAtMost(numRows - 1)
                    val fc = (hScrollPx / cellWPx).coerceAtLeast(0)
                    val lc = ((hScrollPx + vpWPx) / cellWPx + 1).coerceAtMost(columns - 1)

                    val placements = mutableListOf<Pair<Placeable, IntOffset>>()
                    val renderedMerges = mutableSetOf<Int>()

                    // Subcompose every visible cell. Merge origins consume their full
                    // (colSpan * cellW) x (rowSpan * cellH) size; non-origin cells within
                    // a merge are skipped entirely - the origin already covers that area.
                    for (r in fr..lr) {
                        for (c in fc..lc) {
                            val mi = mergedRegions.indexOfFirst { it.contains(r, c) }
                            val merge = mergedRegions.getOrNull(mi)
                            if (merge != null && !merge.isOrigin(r, c)) continue
                            if (merge != null && mi in renderedMerges) continue
                            if (merge != null) renderedMerges.add(mi)

                            val oR = merge?.firstRow ?: r
                            val oC = merge?.firstCol ?: c
                            val spanW = (merge?.let { it.lastCol - it.firstCol + 1 } ?: 1) * cellWPx
                            val spanH = (merge?.let { it.lastRow - it.firstRow + 1 } ?: 1) * cellHPx
                            val xOff = oC * cellWPx - hScrollPx
                            val yOff = oR * cellHPx - vScrollPx
                            val isSelected = if (merge != null) {
                                selectedRow in merge.firstRow..merge.lastRow &&
                                    selectedCol in merge.firstCol..merge.lastCol
                            } else {
                                r == selectedRow && c == selectedCol
                            }
                            val m = subcompose("c_${oR}_${oC}") {
                                GridCell(
                                    value = rows.getOrNull(oR)?.getOrElse(oC) { "" } ?: "",
                                    width = with(density) { spanW.toDp() },
                                    height = with(density) { spanH.toDp() },
                                    fontSize = cellFont,
                                    selected = isSelected,
                                    hue = hue,
                                    onClick = { onSelect(oR, oC) },
                                )
                            }
                            placements.add(
                                m.first().measure(Constraints.fixed(spanW, spanH)) to IntOffset(xOff, yOff),
                            )
                        }
                    }

                    // A merged origin whose top-left is outside the visible row/col range
                    // can still overlap the viewport. Subcompose it here so the visible
                    // portion of the spanning cell is not blank.
                    mergedRegions.forEachIndexed { mi, merge ->
                        if (mi in renderedMerges) return@forEachIndexed
                        val xM = merge.firstCol * cellWPx
                        val yM = merge.firstRow * cellHPx
                        val wM = (merge.lastCol - merge.firstCol + 1) * cellWPx
                        val hM = (merge.lastRow - merge.firstRow + 1) * cellHPx
                        if (xM >= hScrollPx + vpWPx || xM + wM <= hScrollPx) return@forEachIndexed
                        if (yM >= vScrollPx + vpHPx || yM + hM <= vScrollPx) return@forEachIndexed
                        val xOff = xM - hScrollPx
                        val yOff = yM - vScrollPx
                        val isSelected = selectedRow in merge.firstRow..merge.lastRow &&
                            selectedCol in merge.firstCol..merge.lastCol
                        val m = subcompose("c_${merge.firstRow}_${merge.firstCol}") {
                            GridCell(
                                value = rows.getOrNull(merge.firstRow)?.getOrElse(merge.firstCol) { "" } ?: "",
                                width = with(density) { wM.toDp() },
                                height = with(density) { hM.toDp() },
                                fontSize = cellFont,
                                selected = isSelected,
                                hue = hue,
                                onClick = { onSelect(merge.firstRow, merge.firstCol) },
                            )
                        }
                        placements.add(
                            m.first().measure(Constraints.fixed(wM, hM)) to IntOffset(xOff, yOff),
                        )
                    }

                    layout(vpWPx, vpHPx) {
                        placements.forEach { (placeable, offset) -> placeable.placeRelative(offset) }
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
private fun GridCell(
    value: String,
    width: Dp,
    height: Dp,
    fontSize: TextUnit,
    selected: Boolean,
    hue: Color,
    onClick: () -> Unit,
) {
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
