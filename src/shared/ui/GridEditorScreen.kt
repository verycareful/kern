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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import dev.kern.shared.CellMerge
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.theme.OutfitFamily
import dev.kern.shared.theme.PlexMonoFamily
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.SolidColor
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
    colWidths: Map<Int, Float> = emptyMap(),
    rowHeights: Map<Int, Float> = emptyMap(),
    onResizeColumn: (Int, Float) -> Unit = { _, _ -> },
    onResizeRow: (Int, Float) -> Unit = { _, _ -> },
    onAutoResizeColumn: (Int) -> Unit = {},
    onAutoResizeRow: (Int) -> Unit = {},
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
        containerColor = KernTheme.colors.bg,
        topBar = {
            KernTopBar(
                title = title,
                monoTitle = true,
                formatColor = hue,
                subtitle = if (dirty) "● Unsaved changes" else "Saved · on-device",
                subtitleColor = if (dirty) KernTheme.colors.accent else null,
                onBack = { backDispatcher?.onBackPressed() },
                actions = {
                    KernIconButton(
                        KernIcons.Save,
                        "Save",
                        onClick = {
                            onSave { ok, msg ->
                                if (ok) scope.launch { snackbar.showSnackbar("Saved") }
                                else errorDialog = "Save failed" to (msg ?: "This file is read-only. Use Export to save a copy.")
                            }
                        },
                        enabled = dirty,
                        tint = if (dirty) KernTheme.colors.accent else null,
                    )
                    KernIconButton(KernIcons.Download, "Export a copy", onClick = { exportLauncher.launch(exportFileName) })
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = KernTheme.colors.accent)
                error != null -> Text(
                    text = error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = KernTheme.colors.danger,
                )
                else -> Column(Modifier.fillMaxSize()) {
                    if (sheetNames.size > 1) SheetBar(sheetNames, currentSheet, onSelectSheet, hue)
                    CellReferenceBar(rows.isNotEmpty(), selectedRow, selectedCol, selectedValue, hue, onEditSelected)
                    Box(Modifier.weight(1f).pinchZoom(zoom)) {
                        Grid(rows, selectedRow, selectedCol, hue, onSelect, zoom.scale, currentSheet, mergedRegions, colWidths, rowHeights, onResizeColumn, onResizeRow, onAutoResizeColumn, onAutoResizeRow, Modifier.fillMaxSize())
                        if (zoom.scale != 1f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(KernTheme.colors.sunken, RoundedCornerShape(KernRadius.innerSmall))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("${(zoom.scale * 100).roundToInt()}%", style = KernType.meta, color = hue)
                            }
                        }
                    }
                    EditorToolbar {
                        ToolbarButton(KernIcons.Undo, "Undo", onClick = {}, enabled = false)
                        ToolbarButton(KernIcons.Redo, "Redo", onClick = {}, enabled = false)
                        ToolbarSeparator()
                        ToolbarButton(KernIcons.Plus, "Insert row", onClick = onAddRow, label = "Row")
                        ToolbarButton(KernIcons.Plus, "Insert column", onClick = onAddColumn, label = "Col")
                        ToolbarSeparator()
                        ToolbarButton(KernIcons.Sort, "Sort", onClick = {}, enabled = false)
                        ToolbarButton(KernIcons.Filter, "Filter", onClick = {}, enabled = false)
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
                    .clip(RoundedCornerShape(KernRadius.innerSmall))
                    .background(if (selected) hue.copy(alpha = if (KernTheme.colors.dark) 0.2f else 0.13f) else KernTheme.colors.sunken)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = name,
                    color = if (selected) hue else KernTheme.colors.textMid,
                    style = KernType.chip.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
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
    val colors = KernTheme.colors
    val ref = if (hasCells) "${spreadsheetColumnLabel(selectedCol)}${selectedRow + 1}" else "-"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(horizontal = KernTheme.density.screenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(KernRadius.innerSmall))
                .background(colors.sunken)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(ref, style = KernType.meta.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.accent)
        }
        Text("=", style = KernType.meta.copy(fontSize = 13.sp), color = colors.textDim)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Empty cell", style = KernType.meta.copy(fontSize = 13.sp), color = colors.textDim)
            }
            BasicTextField(
                value = value,
                onValueChange = onEditSelected,
                enabled = hasCells,
                singleLine = true,
                textStyle = KernType.meta.copy(fontSize = 13.sp, color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
    colWidths: Map<Int, Float> = emptyMap(),
    rowHeights: Map<Int, Float> = emptyMap(),
    onResizeColumn: (Int, Float) -> Unit = { _, _ -> },
    onResizeRow: (Int, Float) -> Unit = { _, _ -> },
    onAutoResizeColumn: (Int) -> Unit = {},
    onAutoResizeRow: (Int) -> Unit = {},
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

    val defaultColWPx = remember(cellW, density) { with(density) { cellW.roundToPx() } }
    val defaultRowHPx = remember(cellH, density) { with(density) { cellH.roundToPx() } }
    val gutterWPx = remember(gutterW, density) { with(density) { gutterW.roundToPx() } }

    val customColWidthsPx = remember(colWidths, scale, density) {
        colWidths.mapValues { with(density) { (it.value * scale).dp.roundToPx() } }
    }
    val customRowHeightsPx = remember(rowHeights, scale, density) {
        rowHeights.mapValues { with(density) { (it.value * scale).dp.roundToPx() } }
    }

    val getColOffset = { c: Int ->
        var off = c * defaultColWPx
        customColWidthsPx.forEach { (idx, wPx) -> if (idx < c) off += (wPx - defaultColWPx) }
        off
    }
    val getRowOffset = { r: Int ->
        var off = r * defaultRowHPx
        customRowHeightsPx.forEach { (idx, hPx) -> if (idx < r) off += (hPx - defaultRowHPx) }
        off
    }

    val findFirstVisible = { scroll: Int, max: Int, offsetFn: (Int) -> Int ->
        var low = 0
        var high = max - 1
        var result = 0
        while (low <= high) {
            val mid = (low + high) / 2
            if (offsetFn(mid) <= scroll) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        result
    }

    var hScrollPx by remember { mutableIntStateOf(0) }
    var vScrollPx by remember { mutableIntStateOf(0) }
    var activeCol by remember { mutableStateOf<Int?>(null) }
    var activeRow by remember { mutableStateOf<Int?>(null) }

    val handleSelect = { r: Int, c: Int ->
        activeCol = null
        activeRow = null
        onSelect(r, c)
    }

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
                val fc = findFirstVisible(hScrollPx, columns, getColOffset)
                val lc = (findFirstVisible(hScrollPx + vpW, columns, getColOffset) + 1).coerceAtMost(columns - 1)
                for (c in fc..lc) {
                    key(c) {
                        val cWPx = customColWidthsPx[c] ?: defaultColWPx
                        val cW = if (c in customColWidthsPx) with(density) { cWPx.toDp() } else cellW
                        val xOff = getColOffset(c) - hScrollPx
                        Box(Modifier.offset { IntOffset(xOff, 0) }.clickable {
                            activeCol = c
                            activeRow = null
                        }) {
                            Tile(spreadsheetColumnLabel(c), cW, headerH, headerFont, hue, c == activeCol || (activeCol == null && c == selectedCol))
                        }
                        if (c == activeCol) {
                            val dividerWidthPx = with(density) { 48.dp.roundToPx() }
                            var currentWidthPx = cWPx.toFloat()
                            var lastClickTime by remember { mutableStateOf(0L) }
                            Box(
                                Modifier
                                    .zIndex(1f)
                                    .offset { IntOffset(xOff + cWPx - dividerWidthPx / 2, 0) }
                                    .size(48.dp, headerH)
                                    .pointerInput(c, "tap") {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                val up = waitForUpOrCancellation()
                                                if (up != null && (up.uptimeMillis - down.uptimeMillis) < 200) {
                                                    if (up.uptimeMillis - lastClickTime < 300) {
                                                        onAutoResizeColumn(c)
                                                    }
                                                    lastClickTime = up.uptimeMillis
                                                }
                                            }
                                        }
                                    }
                                    .pointerInput(c, "drag") {
                                        detectDragGestures(
                                            onDragStart = { currentWidthPx = cWPx.toFloat() },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                currentWidthPx += dragAmount.x
                                                val newWidthDp = with(density) { currentWidthPx.toDp() }.value / scale
                                                onResizeColumn(c, newWidthDp.coerceAtLeast(24f))
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size(20.dp, 20.dp)
                                        .background(hue, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("<|>", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        // Body: frozen row-gutter + 2D virtual cell area
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val vpW = constraints.maxWidth - gutterWPx
            val vpH = constraints.maxHeight
            val maxHScroll = (getColOffset(columns) - vpW).coerceAtLeast(0)
            val maxVScroll = (getRowOffset(numRows) - vpH).coerceAtLeast(0)
            Row(Modifier.fillMaxSize()) {
                // Frozen row-number gutter
                Box(Modifier.width(gutterW).fillMaxHeight().clipToBounds()) {
                    val fr = findFirstVisible(vScrollPx, numRows, getRowOffset)
                    val lr = (findFirstVisible(vScrollPx + vpH, numRows, getRowOffset) + 1).coerceAtMost(numRows - 1)
                    for (r in fr..lr) {
                        key(r) {
                            val rHPx = customRowHeightsPx[r] ?: defaultRowHPx
                            val rH = if (r in customRowHeightsPx) with(density) { rHPx.toDp() } else cellH
                            val yOff = getRowOffset(r) - vScrollPx
                            Box(Modifier.offset { IntOffset(0, yOff) }.clickable {
                                activeRow = r
                                activeCol = null
                            }) {
                                Tile((r + 1).toString(), gutterW, rH, gutterFont, hue, r == activeRow || (activeRow == null && r == selectedRow))
                            }
                            if (r == activeRow) {
                                val dividerHeightPx = with(density) { 48.dp.roundToPx() }
                                var currentHeightPx = rHPx.toFloat()
                                var lastClickTime by remember { mutableStateOf(0L) }
                                Box(
                                    Modifier
                                        .zIndex(1f)
                                        .offset { IntOffset(0, yOff + rHPx - dividerHeightPx / 2) }
                                        .size(gutterW, 48.dp)
                                        .pointerInput(r, "tap") {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    val up = waitForUpOrCancellation()
                                                    if (up != null && (up.uptimeMillis - down.uptimeMillis) < 200) {
                                                        if (up.uptimeMillis - lastClickTime < 300) {
                                                            onAutoResizeRow(r)
                                                        }
                                                        lastClickTime = up.uptimeMillis
                                                    }
                                                }
                                            }
                                        }
                                        .pointerInput(r, "drag") {
                                            detectDragGestures(
                                                onDragStart = { currentHeightPx = rHPx.toFloat() },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    currentHeightPx += dragAmount.y
                                                    val newHeightDp = with(density) { currentHeightPx.toDp() }.value / scale
                                                    onResizeRow(r, newHeightDp.coerceAtLeast(24f))
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier
                                            .size(20.dp, 20.dp)
                                            .background(hue, RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("↕", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
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

                    val fr = findFirstVisible(vScrollPx, numRows, getRowOffset)
                    val lr = (findFirstVisible(vScrollPx + vpHPx, numRows, getRowOffset) + 1).coerceAtMost(numRows - 1)
                    val fc = findFirstVisible(hScrollPx, columns, getColOffset)
                    val lc = (findFirstVisible(hScrollPx + vpWPx, columns, getColOffset) + 1).coerceAtMost(columns - 1)

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
                            val spanW = (merge?.let { getColOffset(it.lastCol + 1) - getColOffset(it.firstCol) } ?: (getColOffset(oC + 1) - getColOffset(oC)))
                            val spanH = (merge?.let { getRowOffset(it.lastRow + 1) - getRowOffset(it.firstRow) } ?: (getRowOffset(oR + 1) - getRowOffset(oR)))
                            val xOff = getColOffset(oC) - hScrollPx
                            val yOff = getRowOffset(oR) - vScrollPx
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
                                    isHeader = oR == 0,
                                    onClick = { handleSelect(oR, oC) },
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
                        val xM = getColOffset(merge.firstCol)
                        val yM = getRowOffset(merge.firstRow)
                        val wM = getColOffset(merge.lastCol + 1) - xM
                        val hM = getRowOffset(merge.lastRow + 1) - yM
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
                                isHeader = merge.firstRow == 0,
                                onClick = { handleSelect(merge.firstRow, merge.firstCol) },
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
    val colors = KernTheme.colors
    Box(
        modifier = Modifier
            .size(width, height)
            .background(if (selected) colors.accentSoft else colors.sunken)
            .border(0.5.dp, colors.borderSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = PlexMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
            color = if (selected) colors.accent else colors.textMid,
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
    isHeader: Boolean,
    onClick: () -> Unit,
) {
    val colors = KernTheme.colors
    val bg = when {
        selected -> colors.accentSoft
        isHeader -> hue.copy(alpha = if (colors.dark) 0.20f else 0.13f)
        else -> colors.surface
    }
    Box(
        modifier = Modifier
            .size(width, height)
            .background(bg)
            .border(width = if (selected) 2.dp else 0.5.dp, color = if (selected) colors.accent else colors.borderSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = fontSize,
            fontFamily = if (isHeader) PlexMonoFamily else OutfitFamily,
            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isHeader) hue else colors.text,
        )
    }
}
