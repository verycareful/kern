package dev.kern.editors.excel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kern.shared.ui.GridEditorScreen

// Excel format identity hue (design handoff).
private val ExcelHue = Color(0xFF1F8454)
private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun ExcelEditorScreen(
    filePath: String?,
    vm: ExcelEditorViewModel = viewModel(),
) {
    LaunchedEffect(filePath) { vm.start(filePath) }

    GridEditorScreen(
        title = vm.fileName.ifBlank { "Excel" },
        dirty = vm.dirty,
        loading = vm.loading,
        error = vm.error,
        hue = ExcelHue,
        rows = vm.rows,
        selectedRow = vm.selectedRow,
        selectedCol = vm.selectedCol,
        selectedValue = vm.selectedValue(),
        onSelect = vm::select,
        onEditSelected = vm::editSelected,
        onSave = vm::save,
        exportMimeType = XLSX_MIME,
        exportFileName = vm.fileName.ifBlank { "export.xlsx" },
        onExportToUri = vm::exportTo,
        onAddRow = vm::addRow,
        onAddColumn = vm::addColumn,
        sheetNames = vm.sheetNames,
        currentSheet = vm.currentSheet,
        onSelectSheet = vm::selectSheet,
        mergedRegions = vm.currentMergedRegions,
        colWidths = vm.colWidths,
        rowHeights = vm.rowHeights,
        onResizeColumn = vm::resizeColumn,
        onResizeRow = vm::resizeRow,
        onAutoResizeColumn = vm::autoResizeColumn,
        onAutoResizeRow = vm::autoResizeRow,
    )
}
