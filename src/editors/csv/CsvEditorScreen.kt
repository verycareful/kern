package dev.kern.editors.csv

import androidx.compose.runtime.Composable
import dev.kern.shared.ui.EditorScaffold

/**
 * CSV editor (OpenCSV-backed table). Real read/edit/export lands in 0.1.1.0.
 * Stub surface for the design session.
 */
@Composable
fun CsvEditorScreen(filePath: String?) {
    EditorScaffold(title = "CSV", filePath = filePath)
}
