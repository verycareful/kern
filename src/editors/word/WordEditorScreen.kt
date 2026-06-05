package dev.kern.editors.word

import androidx.compose.runtime.Composable
import dev.kern.shared.ui.EditorScaffold

/**
 * Word editor (Apache POI docx). Real read/edit/export lands in 0.1.3.0.
 * Stub surface for the design session.
 */
@Composable
fun WordEditorScreen(filePath: String?) {
    EditorScaffold(title = "Word", filePath = filePath)
}
