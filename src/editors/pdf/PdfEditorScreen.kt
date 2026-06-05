package dev.kern.editors.pdf

import androidx.compose.runtime.Composable
import dev.kern.shared.ui.EditorScaffold

/**
 * PDF editor: Compose UI layer over the Qyra MuPDF bridge. Real engine lands in
 * 0.1.5.0 (coordinate with zParik on the Rust boundary). Stub surface for the
 * design session.
 */
@Composable
fun PdfEditorScreen(filePath: String?) {
    EditorScaffold(title = "PDF", filePath = filePath)
}
