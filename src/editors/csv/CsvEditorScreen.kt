package dev.kern.editors.csv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kern.shared.ui.GridEditorScreen

// CSV format identity hue (design handoff). The full design system arrives in the polish pass.
private val CsvHue = Color(0xFF0E8E9A)

@Composable
fun CsvEditorScreen(
    filePath: String?,
    vm: CsvEditorViewModel = viewModel(),
) {
    LaunchedEffect(filePath) { vm.start(filePath) }

    GridEditorScreen(
        title = vm.fileName.ifBlank { "CSV" },
        dirty = vm.dirty,
        loading = vm.loading,
        error = vm.error,
        hue = CsvHue,
        rows = vm.rows,
        selectedRow = vm.selectedRow,
        selectedCol = vm.selectedCol,
        selectedValue = vm.selectedValue(),
        onSelect = vm::select,
        onEditSelected = vm::editSelected,
        onSave = vm::save,
        exportMimeType = "text/csv",
        exportFileName = vm.fileName.ifBlank { "export.csv" },
        onExportToUri = vm::exportTo,
        onAddRow = vm::addRow,
        onAddColumn = vm::addColumn,
        colWidths = vm.colWidths,
        rowHeights = vm.rowHeights,
        onResizeColumn = vm::resizeColumn,
        onResizeRow = vm::resizeRow,
        onAutoResizeColumn = vm::autoResizeColumn,
        onAutoResizeRow = vm::autoResizeRow,
    )
}
