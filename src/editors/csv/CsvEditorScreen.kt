package dev.kern.editors.csv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kern.shared.DocumentFormat
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.ui.GridEditorScreen

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
        hue = KernTheme.formatColor(DocumentFormat.CSV),
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
