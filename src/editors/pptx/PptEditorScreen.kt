package dev.kern.editors.pptx

import androidx.compose.runtime.Composable
import dev.kern.shared.ui.EditorScaffold

/**
 * PowerPoint editor (Apache POI pptx). Real slide viewer/edit lands in 0.1.4.0.
 * Stub surface for the design session.
 */
@Composable
fun PptEditorScreen(filePath: String?) {
    EditorScaffold(title = "PowerPoint", filePath = filePath)
}
