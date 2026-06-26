package dev.kern.editors.word

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kern.shared.DocumentFormat
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.theme.OutfitFamily
import dev.kern.shared.ui.EditorChrome
import dev.kern.shared.ui.EditorToolbar
import dev.kern.shared.ui.KernIcons
import dev.kern.shared.ui.ToolbarButton
import dev.kern.shared.ui.ToolbarSeparator
import dev.kern.shared.ui.pinchZoom
import dev.kern.shared.ui.rememberZoomState
import kotlin.math.roundToInt

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

@Composable
fun WordEditorScreen(
    filePath: String?,
    vm: WordEditorViewModel = viewModel(),
) {
    LaunchedEffect(filePath) { vm.start(filePath) }
    val hue = KernTheme.formatColor(DocumentFormat.WORD)

    EditorChrome(
        title = vm.fileName.ifBlank { "Document" },
        dirty = vm.dirty,
        loading = vm.loading,
        error = vm.error,
        hue = hue,
        onSave = vm::save,
        exportMimeType = DOCX_MIME,
        exportFileName = vm.fileName.ifBlank { "export.docx" },
        onExportToUri = vm::exportTo,
        toolbar = { WordToolbar() },
    ) { modifier ->
        WordPage(vm, hue, modifier)
    }
}

/**
 * Formatting toolbar. Rich-text run styling (bold/italic/underline, headings,
 * bullets) is future functional work on the Word document model; the toolbar
 * matches the design and these actions are wired as that capability lands.
 */
@Composable
private fun WordToolbar() {
    EditorToolbar {
        ToolbarButton(KernIcons.Undo, "Undo", onClick = {}, enabled = false)
        ToolbarButton(KernIcons.Redo, "Redo", onClick = {}, enabled = false)
        ToolbarSeparator()
        ToolbarButton(KernIcons.Bold, "Bold", onClick = {}, enabled = false)
        ToolbarButton(KernIcons.Italic, "Italic", onClick = {}, enabled = false)
        ToolbarButton(KernIcons.Underline, "Underline", onClick = {}, enabled = false)
        ToolbarSeparator()
        ToolbarButton(KernIcons.Text, "Heading 1", onClick = {}, label = "H1", enabled = false)
        ToolbarButton(KernIcons.Text, "Heading 2", onClick = {}, label = "H2", enabled = false)
        ToolbarButton(KernIcons.Bullet, "Bulleted list", onClick = {}, enabled = false)
    }
}

@Composable
private fun WordPage(vm: WordEditorViewModel, hue: Color, modifier: Modifier) {
    val colors = KernTheme.colors
    val zoom = rememberZoomState()
    Box(modifier.background(colors.sunken).pinchZoom(zoom)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 520.dp)
                    .clip(RoundedCornerShape(KernRadius.base))
                    .background(if (colors.dark) colors.raised else colors.surface)
                    .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.base))
                    .padding(horizontal = 26.dp, vertical = 28.dp),
            ) {
                vm.paragraphs.forEachIndexed { index, text ->
                    ParagraphField(
                        text = text,
                        style = vm.styleAt(index),
                        scale = zoom.scale,
                        onChange = { vm.editParagraph(index, it) },
                    )
                }
            }
            Text(
                "Page 1 of 1 · ${vm.paragraphs.size} blocks",
                style = KernType.caption,
                color = colors.textMid,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        if (zoom.scale != 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(KernRadius.innerSmall))
                    .background(colors.sunken)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("${(zoom.scale * 100).roundToInt()}%", style = KernType.meta, color = hue)
            }
        }
    }
}

@Composable
private fun ParagraphField(text: String, style: WordDocument.Style, scale: Float, onChange: (String) -> Unit) {
    val colors = KernTheme.colors
    val (size, weight, lineHeight) = when (style) {
        WordDocument.Style.TITLE -> Triple(26.sp, FontWeight.Bold, 30.sp)
        WordDocument.Style.HEADING1 -> Triple(24.sp, FontWeight.Bold, 30.sp)
        WordDocument.Style.HEADING2 -> Triple(17.sp, FontWeight.SemiBold, 23.sp)
        WordDocument.Style.BODY -> Triple(15.sp, FontWeight.Normal, 25.sp)
    }
    BasicTextField(
        value = text,
        onValueChange = onChange,
        textStyle = TextStyle(
            fontFamily = OutfitFamily,
            fontSize = size * scale,
            fontWeight = weight,
            color = colors.text,
            lineHeight = lineHeight * scale,
        ),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
