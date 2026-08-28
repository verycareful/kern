package dev.kern.editors.word

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
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
import dev.kern.shared.ui.KernBottomSheet
import dev.kern.shared.ui.KernIcons
import dev.kern.shared.ui.ToolbarButton
import dev.kern.shared.ui.ToolbarSeparator
import dev.kern.shared.ui.pinchZoom
import dev.kern.shared.ui.rememberZoomState
import kotlin.math.roundToInt

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

private val FontSizes = listOf(8f, 9f, 10f, 11f, 12f, 14f, 16f, 18f, 20f, 24f, 28f, 36f, 48f, 72f)

@Composable
fun WordEditorScreen(
    filePath: String?,
    vm: WordEditorViewModel = viewModel(),
) {
    LaunchedEffect(filePath) { vm.start(filePath) }
    val hue = KernTheme.formatColor(DocumentFormat.WORD)
    var showColor by remember { mutableStateOf(false) }
    var showSize by remember { mutableStateOf(false) }

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
        toolbar = { WordToolbar(vm, onColor = { showColor = true }, onSize = { showSize = true }) },
    ) { modifier ->
        WordPage(vm, hue, modifier)
    }

    if (showColor) {
        WordColorPicker(
            current = vm.activeStyle()?.colorHex,
            recent = vm.recentColors,
            customSlots = vm.customSlots,
            onPick = { vm.setColor(it) },
            onSetCustom = { slot, input -> vm.setCustomSlot(slot, input) },
            onDismiss = { showColor = false },
        )
    }
    if (showSize) {
        WordSizePicker(
            current = vm.activeStyle()?.sizePt,
            onPick = { vm.setFontSize(it); showSize = false },
            onDismiss = { showSize = false },
        )
    }
}

/**
 * Formatting toolbar. Character actions (bold/italic/underline/strike, size, colour)
 * act on the focused paragraph's selection; a collapsed selection formats the whole
 * paragraph. Heading, list, and undo/redo actions arrive in later phases and stay
 * disabled until then.
 */
@Composable
private fun WordToolbar(vm: WordEditorViewModel, onColor: () -> Unit, onSize: () -> Unit) {
    val style = vm.activeStyle()
    val enabled = vm.canFormat()
    val sizeLabel = style?.sizePt?.let { "${it.roundToInt()}" } ?: "Size"
    EditorToolbar {
        ToolbarButton(KernIcons.Undo, "Undo", onClick = {}, enabled = false)
        ToolbarButton(KernIcons.Redo, "Redo", onClick = {}, enabled = false)
        ToolbarSeparator()
        ToolbarButton(KernIcons.Bold, "Bold", onClick = vm::toggleBold, active = style?.bold == true, enabled = enabled)
        ToolbarButton(KernIcons.Italic, "Italic", onClick = vm::toggleItalic, active = style?.italic == true, enabled = enabled)
        ToolbarButton(KernIcons.Underline, "Underline", onClick = vm::toggleUnderline, active = style?.underline == true, enabled = enabled)
        ToolbarButton(KernIcons.Strikethrough, "Strikethrough", onClick = vm::toggleStrike, active = style?.strike == true, enabled = enabled)
        ToolbarSeparator()
        ToolbarButton(KernIcons.FontSize, "Font size", onClick = onSize, label = sizeLabel, enabled = enabled)
        ToolbarButton(KernIcons.FontColor, "Text colour", onClick = onColor, active = style?.colorHex != null, enabled = enabled)
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
                vm.blocks.forEachIndexed { index, block ->
                    when (block) {
                        is WordDocument.ParagraphBlock ->
                            if (block.editable) {
                                EditableParagraph(
                                    value = vm.fields[index] ?: TextFieldValue(),
                                    kind = block.props.kind,
                                    scale = zoom.scale,
                                    onChange = { vm.onValueChange(index, it) },
                                    onFocus = { vm.setFocus(index) },
                                )
                            } else {
                                ReadOnlyParagraph(block, zoom.scale)
                            }
                        is WordDocument.TableBlock -> TableView(block, zoom.scale)
                        WordDocument.OpaqueBlock -> Unit
                    }
                }
            }
            Text(
                "Page 1 of 1 · ${vm.blocks.size} blocks",
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

private data class KindMetrics(val size: TextUnit, val weight: FontWeight, val lineHeight: TextUnit)

private fun metricsFor(kind: WordDocument.Kind): KindMetrics = when (kind) {
    WordDocument.Kind.TITLE -> KindMetrics(26.sp, FontWeight.Bold, 30.sp)
    WordDocument.Kind.HEADING1 -> KindMetrics(24.sp, FontWeight.Bold, 30.sp)
    WordDocument.Kind.HEADING2 -> KindMetrics(17.sp, FontWeight.SemiBold, 23.sp)
    WordDocument.Kind.BODY -> KindMetrics(15.sp, FontWeight.Normal, 25.sp)
}

@Composable
private fun EditableParagraph(
    value: TextFieldValue,
    kind: WordDocument.Kind,
    scale: Float,
    onChange: (TextFieldValue) -> Unit,
    onFocus: () -> Unit,
) {
    val colors = KernTheme.colors
    val m = metricsFor(kind)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(
            fontFamily = OutfitFamily,
            fontSize = m.size * scale,
            fontWeight = m.weight,
            color = colors.text,
            lineHeight = m.lineHeight * scale,
        ),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onFocusChanged { if (it.isFocused) onFocus() },
    )
}

@Composable
private fun ReadOnlyParagraph(block: WordDocument.ParagraphBlock, scale: Float) {
    val colors = KernTheme.colors
    val m = metricsFor(block.props.kind)
    Text(
        text = WordRichText.toAnnotated(block.runs),
        style = TextStyle(
            fontFamily = OutfitFamily,
            fontSize = m.size * scale,
            fontWeight = m.weight,
            color = colors.text,
            lineHeight = m.lineHeight * scale,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun TableView(block: WordDocument.TableBlock, scale: Float) {
    val colors = KernTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text("Table · read-only", style = KernType.caption, color = colors.textMid, modifier = Modifier.padding(bottom = 4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(KernRadius.innerSmall))
                .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.innerSmall)),
        ) {
            block.rows.forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { cell ->
                        Box(
                            Modifier
                                .weight(1f)
                                .border(0.5.dp, colors.borderSoft)
                                .padding(8.dp),
                        ) {
                            Text(
                                cell,
                                style = KernType.body.copy(fontSize = KernType.body.fontSize * scale),
                                color = colors.text,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordSizePicker(current: Float?, onPick: (Float?) -> Unit, onDismiss: () -> Unit) {
    val colors = KernTheme.colors
    KernBottomSheet(onDismiss = onDismiss, title = "Font size") {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            SizeRow("Default", selected = current == null, onClick = { onPick(null) })
            FontSizes.forEach { pt ->
                SizeRow("${pt.roundToInt()} pt", selected = current == pt, onClick = { onPick(pt) })
            }
        }
    }
}

@Composable
private fun SizeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = KernTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            style = KernType.body,
            color = if (selected) colors.accent else colors.text,
            modifier = Modifier.weight(1f),
        )
        if (selected) Text("✓", style = KernType.body, color = colors.accent)
    }
}
