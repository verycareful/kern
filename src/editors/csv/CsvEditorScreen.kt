package dev.kern.editors.csv

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

// CSV format identity hue (design handoff). Used as a quiet header tint for now;
// the full design system arrives in the 0.1.6.0 polish pass.
private val CsvHue = Color(0xFF0E8E9A)

private val CellWidth = 108.dp
private val CellHeight = 40.dp
private val GutterWidth = 52.dp
private val HeaderHeight = 36.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvEditorScreen(
    filePath: String?,
    vm: CsvEditorViewModel = viewModel(),
) {
    LaunchedEffect(filePath) { vm.start(filePath) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            vm.exportTo(uri) { ok, msg ->
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
                        Text(
                            text = vm.fileName.ifBlank { "CSV" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (vm.dirty) "Unsaved changes" else "Saved - on device",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (vm.dirty) CsvHue else MaterialTheme.colorScheme.onSurfaceVariant,
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
                        onClick = {
                            vm.save { ok, msg ->
                                scope.launch { snackbar.showSnackbar(if (ok) "Saved" else "Save failed: ${msg ?: "read-only file, use Export"}") }
                            }
                        },
                        enabled = vm.dirty,
                    ) { Icon(Icons.Default.Save, contentDescription = "Save", tint = if (vm.dirty) CsvHue else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { exportLauncher.launch(vm.fileName.ifBlank { "export.csv" }) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export a copy")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.error != null -> Text(
                    text = vm.error!!,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                else -> CsvBody(vm)
            }
        }
    }
}

@Composable
private fun CsvBody(vm: CsvEditorViewModel) {
    Column(Modifier.fillMaxSize()) {
        CellReferenceBar(vm)
        Grid(vm, Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { vm.addRow() }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Row")
            }
            TextButton(onClick = { vm.addColumn() }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Column")
            }
        }
    }
}

@Composable
private fun CellReferenceBar(vm: CsvEditorViewModel) {
    val hasCells = vm.rows.isNotEmpty()
    val ref = if (hasCells) "${CsvDocument.columnLabel(vm.selectedCol)}${vm.selectedRow + 1}" else "-"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = ref,
            color = CsvHue,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        OutlinedTextField(
            value = vm.selectedValue(),
            onValueChange = { vm.editSelected(it) },
            enabled = hasCells,
            singleLine = true,
            placeholder = { Text("Empty cell") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Grid(vm: CsvEditorViewModel, modifier: Modifier = Modifier) {
    val hScroll = rememberScrollState()
    val columns = vm.columnCount
    Column(modifier) {
        // Header row (frozen vertically, scrolls horizontally with the body).
        Row {
            HeaderTile(text = "", width = GutterWidth, height = HeaderHeight)
            Row(Modifier.horizontalScroll(hScroll)) {
                repeat(columns) { c ->
                    HeaderTile(
                        text = CsvDocument.columnLabel(c),
                        width = CellWidth,
                        height = HeaderHeight,
                        selected = c == vm.selectedCol,
                    )
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(vm.rows) { r, row ->
                Row {
                    GutterTile(number = r + 1, selected = r == vm.selectedRow)
                    Row(Modifier.horizontalScroll(hScroll)) {
                        for (c in 0 until columns) {
                            val value = row.getOrElse(c) { "" }
                            GridCell(
                                value = value,
                                selected = r == vm.selectedRow && c == vm.selectedCol,
                                onClick = { vm.select(r, c) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderTile(text: String, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, selected: Boolean = false) {
    Box(
        modifier = Modifier
            .size(width, height)
            .background(if (selected) CsvHue.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = if (selected) CsvHue else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GutterTile(number: Int, selected: Boolean) {
    Box(
        modifier = Modifier
            .size(GutterWidth, CellHeight)
            .background(if (selected) CsvHue.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = if (selected) CsvHue else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GridCell(value: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(CellWidth, CellHeight)
            .background(if (selected) CsvHue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 0.5.dp,
                color = if (selected) CsvHue else MaterialTheme.colorScheme.outlineVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
