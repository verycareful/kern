package dev.kern.editors.word

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kern.shared.ui.EditorChrome
import dev.kern.shared.ui.pinchZoom
import dev.kern.shared.ui.rememberZoomState
import kotlin.math.roundToInt

// Word format identity hue (design handoff).
private val WordHue = Color(0xFF2E68C4)
private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

@Composable
fun WordEditorScreen(
    filePath: String?,
    vm: WordEditorViewModel = viewModel(),
) {
    LaunchedEffect(filePath) { vm.start(filePath) }

    EditorChrome(
        title = vm.fileName.ifBlank { "Document" },
        dirty = vm.dirty,
        loading = vm.loading,
        error = vm.error,
        hue = WordHue,
        onSave = vm::save,
        exportMimeType = DOCX_MIME,
        exportFileName = vm.fileName.ifBlank { "export.docx" },
        onExportToUri = vm::exportTo,
    ) { modifier ->
        WordPage(vm, modifier)
    }
}

@Composable
private fun WordPage(vm: WordEditorViewModel, modifier: Modifier) {
    val zoom = rememberZoomState()
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).pinchZoom(zoom)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
                    vm.paragraphs.forEachIndexed { index, text ->
                        ParagraphField(
                            text = text,
                            style = vm.styleAt(index),
                            scale = zoom.scale,
                            onChange = { vm.editParagraph(index, it) },
                        )
                    }
                }
            }
        }
        if (zoom.scale != 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("${(zoom.scale * 100).roundToInt()}%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = WordHue)
            }
        }
    }
}

@Composable
private fun ParagraphField(text: String, style: WordDocument.Style, scale: Float, onChange: (String) -> Unit) {
    val (size, weight) = when (style) {
        WordDocument.Style.TITLE -> 26.sp to FontWeight.Bold
        WordDocument.Style.HEADING1 -> 22.sp to FontWeight.Bold
        WordDocument.Style.HEADING2 -> 18.sp to FontWeight.SemiBold
        WordDocument.Style.BODY -> 15.sp to FontWeight.Normal
    }
    BasicTextField(
        value = text,
        onValueChange = onChange,
        textStyle = TextStyle(
            fontSize = size * scale,
            fontWeight = weight,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = size * scale * 1.5f,
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(WordHue),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
