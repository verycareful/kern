package dev.kern.editors.excel

import androidx.compose.runtime.Composable
import dev.kern.shared.ui.EditorScaffold

/**
 * Excel editor (Apache POI xlsx). Real read/edit/export lands in 0.1.2.0.
 * Stub surface for the design session.
 */
@Composable
fun ExcelEditorScreen(filePath: String?) {
    EditorScaffold(title = "Excel", filePath = filePath)
}
