package dev.kern.editors.word

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
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
import dev.kern.shared.ui.KernIconButton
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
    var showStyles by remember { mutableStateOf(false) }
    var showAlign by remember { mutableStateOf(false) }

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
        toolbar = {
            WordToolbar(
                vm = vm,
                onColor = { showColor = true },
                onSize = { showSize = true },
                onStyles = { showStyles = true },
                onAlign = { showAlign = true },
            )
        },
    ) { modifier ->
        Column(modifier.fillMaxSize()) {
            if (vm.isFindOpen) {
                FindReplaceBar(vm)
            }
            WordPage(vm, hue, Modifier.weight(1f))
        }
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
    if (showStyles) {
        WordStylePicker(
            current = vm.activeParaProps()?.kind ?: WordDocument.Kind.BODY,
            onPick = { vm.setParagraphKind(it); showStyles = false },
            onDismiss = { showStyles = false },
        )
    }
    if (showAlign) {
        WordAlignPicker(
            current = vm.activeParaProps()?.align ?: WordDocument.Align.START,
            onPick = { vm.setAlignment(it); showAlign = false },
            onDismiss = { showAlign = false },
        )
    }
    if (vm.activeTableBlockIndex != null) {
        TableEditorSheet(
            vm = vm,
            onDismiss = vm::closeTableEditor,
        )
    }
}

/**
 * Full formatting toolbar with Undo/Redo, Character styling, Paragraph styles,
 * Lists, Indentation, and Find & Replace.
 */
@Composable
private fun WordToolbar(
    vm: WordEditorViewModel,
    onColor: () -> Unit,
    onSize: () -> Unit,
    onStyles: () -> Unit,
    onAlign: () -> Unit,
) {
    val style = vm.activeStyle()
    val props = vm.activeParaProps()
    val enabled = vm.canFormat()
    val sizeLabel = style?.sizePt?.let { "${it.roundToInt()}" } ?: "Size"
    val kindLabel = when (props?.kind) {
        WordDocument.Kind.TITLE -> "Title"
        WordDocument.Kind.HEADING1 -> "H1"
        WordDocument.Kind.HEADING2 -> "H2"
        WordDocument.Kind.BODY, null -> "Body"
    }

    EditorToolbar {
        ToolbarButton(KernIcons.Undo, "Undo", onClick = vm::undo, enabled = vm.canUndo)
        ToolbarButton(KernIcons.Redo, "Redo", onClick = vm::redo, enabled = vm.canRedo)
        ToolbarSeparator()
        ToolbarButton(KernIcons.Bold, "Bold", onClick = vm::toggleBold, active = style?.bold == true, enabled = enabled)
        ToolbarButton(KernIcons.Italic, "Italic", onClick = vm::toggleItalic, active = style?.italic == true, enabled = enabled)
        ToolbarButton(KernIcons.Underline, "Underline", onClick = vm::toggleUnderline, active = style?.underline == true, enabled = enabled)
        ToolbarButton(KernIcons.Strikethrough, "Strikethrough", onClick = vm::toggleStrike, active = style?.strike == true, enabled = enabled)
        ToolbarSeparator()
        ToolbarButton(KernIcons.FontSize, "Font size", onClick = onSize, label = sizeLabel, enabled = enabled)
        ToolbarButton(KernIcons.FontColor, "Text colour", onClick = onColor, active = style?.colorHex != null, enabled = enabled)
        ToolbarSeparator()
        ToolbarButton(KernIcons.Text, "Paragraph style", onClick = onStyles, label = kindLabel, enabled = enabled)
        ToolbarButton(KernIcons.AlignLeft, "Alignment", onClick = onAlign, enabled = enabled)
        ToolbarButton(KernIcons.Bullet, "Bullet list", onClick = vm::toggleBulletList, active = props?.listType == WordDocument.ListType.BULLET, enabled = enabled)
        ToolbarButton(KernIcons.NumberedList, "Numbered list", onClick = vm::toggleNumberedList, active = props?.listType == WordDocument.ListType.NUMBER, enabled = enabled)
        ToolbarButton(KernIcons.IndentIncrease, "Increase indent", onClick = vm::increaseIndent, enabled = enabled)
        ToolbarButton(KernIcons.IndentDecrease, "Decrease indent", onClick = vm::decreaseIndent, enabled = enabled)
        ToolbarSeparator()
        ToolbarButton(KernIcons.FindReplace, "Find and replace", onClick = vm::toggleFind, active = vm.isFindOpen)
    }
}

@Composable
private fun FindReplaceBar(vm: WordEditorViewModel) {
    val colors = KernTheme.colors
    val matches = vm.matches
    val matchText = if (matches.isEmpty()) "No matches" else "${vm.currentMatchIndex + 1} of ${matches.size}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(1.dp, colors.borderSoft)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = vm.searchQuery,
                onValueChange = { vm.searchQuery = it },
                textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(KernRadius.innerSmall))
                    .background(colors.sunken)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    if (vm.searchQuery.isEmpty()) {
                        Text("Find...", color = colors.textDim, fontSize = 14.sp)
                    }
                    inner()
                },
            )
            Text(matchText, style = KernType.meta, color = colors.textMid)
            KernIconButton(KernIcons.ArrowUp, "Previous", onClick = vm::prevMatch)
            KernIconButton(KernIcons.ArrowDown, "Next", onClick = vm::nextMatch)
            KernIconButton(KernIcons.Close, "Close", onClick = vm::toggleFind)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = vm.replaceQuery,
                onValueChange = { vm.replaceQuery = it },
                textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(KernRadius.innerSmall))
                    .background(colors.sunken)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    if (vm.replaceQuery.isEmpty()) {
                        Text("Replace with...", color = colors.textDim, fontSize = 14.sp)
                    }
                    inner()
                },
            )
            ToolbarButton(KernIcons.Pen, "Replace", onClick = vm::replaceOne, label = "Replace")
            ToolbarButton(KernIcons.Check, "Replace all", onClick = vm::replaceAll, label = "All")
        }
    }
}

@Composable
private fun WordPage(vm: WordEditorViewModel, hue: Color, modifier: Modifier) {
    val colors = KernTheme.colors
    val zoom = rememberZoomState()
    Box(modifier.background(colors.sunken).pinchZoom(zoom)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            item(key = "paper_header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = KernRadius.base, topEnd = KernRadius.base))
                        .background(if (colors.dark) colors.raised else colors.surface)
                        .border(
                            1.dp,
                            colors.borderSoft,
                            RoundedCornerShape(topStart = KernRadius.base, topEnd = KernRadius.base),
                        )
                        .padding(top = 28.dp, start = 26.dp, end = 26.dp),
                )
            }
            itemsIndexed(
                items = vm.blocks,
                key = { index, _ -> "block_$index" },
            ) { index, block ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (colors.dark) colors.raised else colors.surface)
                        .padding(horizontal = 26.dp),
                ) {
                    when (block) {
                        is WordDocument.ParagraphBlock -> {
                            val props = vm.paraProps[index] ?: block.props
                            EditableParagraph(
                                value = vm.fields[index] ?: TextFieldValue(WordRichText.toAnnotated(block.runs)),
                                props = props,
                                isFindOpen = vm.isFindOpen,
                                searchQuery = vm.searchQuery,
                                scale = zoom.scale,
                                onChange = { vm.onValueChange(index, it) },
                                onFocus = { vm.setFocus(index) },
                            )
                        }
                        is WordDocument.TableBlock -> {
                            val currentRows = vm.tableStates[index] ?: block.rows
                            TablePreviewCard(
                                blockIndex = index,
                                rows = currentRows,
                                scale = zoom.scale,
                                onEditTable = { vm.openTableEditor(index) },
                            )
                        }
                        is WordDocument.ImageBlock -> {
                            ImageView(block, zoom.scale)
                        }
                        WordDocument.OpaqueBlock -> Unit
                    }
                }
            }
            item(key = "paper_footer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = KernRadius.base, bottomEnd = KernRadius.base))
                        .background(if (colors.dark) colors.raised else colors.surface)
                        .border(
                            1.dp,
                            colors.borderSoft,
                            RoundedCornerShape(bottomStart = KernRadius.base, bottomEnd = KernRadius.base),
                        )
                        .padding(bottom = 28.dp, start = 26.dp, end = 26.dp),
                ) {
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    "Page 1 of 1 · ${vm.blocks.size} blocks · ${vm.totalWordCount} words · ${vm.totalCharCount} chars",
                    style = KernType.caption,
                    color = colors.textMid,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                )
            }
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
    WordDocument.Kind.TITLE -> KindMetrics(26.sp, FontWeight.Bold, 32.sp)
    WordDocument.Kind.HEADING1 -> KindMetrics(22.sp, FontWeight.Bold, 28.sp)
    WordDocument.Kind.HEADING2 -> KindMetrics(18.sp, FontWeight.SemiBold, 24.sp)
    WordDocument.Kind.BODY -> KindMetrics(15.sp, FontWeight.Normal, 24.sp)
}

private fun mapTextAlign(align: WordDocument.Align): TextAlign = when (align) {
    WordDocument.Align.START -> TextAlign.Start
    WordDocument.Align.CENTER -> TextAlign.Center
    WordDocument.Align.END -> TextAlign.End
    WordDocument.Align.JUSTIFY -> TextAlign.Justify
}

@Composable
private fun EditableParagraph(
    value: TextFieldValue,
    props: WordDocument.ParaProps,
    isFindOpen: Boolean,
    searchQuery: String,
    scale: Float,
    onChange: (TextFieldValue) -> Unit,
    onFocus: () -> Unit,
) {
    val colors = KernTheme.colors
    val m = metricsFor(props.kind)
    val indentPadding = (props.indentTwips / 20).dp.coerceAtMost(120.dp) * scale

    val displayValue = if (isFindOpen && searchQuery.isNotEmpty()) {
        val highlighted = WordRichText.highlightMatches(
            text = value.annotatedString,
            query = searchQuery,
            matchColor = colors.accentSoft,
            activeMatchColor = colors.accent,
            isCurrentBlock = true,
            currentMatchIndex = 0,
        )
        value.copy(annotatedString = highlighted)
    } else {
        value
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentPadding)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (props.listType == WordDocument.ListType.BULLET) {
            Text("• ", style = TextStyle(fontFamily = OutfitFamily, fontSize = m.size * scale, color = colors.accent), modifier = Modifier.padding(end = 4.dp))
        } else if (props.listType == WordDocument.ListType.NUMBER) {
            Text("1. ", style = TextStyle(fontFamily = OutfitFamily, fontSize = m.size * scale, color = colors.accent), modifier = Modifier.padding(end = 4.dp))
        }
        BasicTextField(
            value = displayValue,
            onValueChange = onChange,
            textStyle = TextStyle(
                fontFamily = OutfitFamily,
                fontSize = m.size * scale,
                fontWeight = m.weight,
                color = colors.text,
                lineHeight = m.lineHeight * scale,
                textAlign = mapTextAlign(props.align),
            ),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (it.isFocused) onFocus() },
        )
    }
}

@Composable
private fun TablePreviewCard(
    blockIndex: Int,
    rows: List<List<String>>,
    scale: Float,
    onEditTable: () -> Unit,
) {
    val colors = KernTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(KernRadius.innerSmall))
            .background(colors.sunken)
            .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.innerSmall))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Table · ${rows.size} rows × ${rows.firstOrNull()?.size ?: 0} cols",
                style = KernType.meta,
                color = colors.textMid,
            )
            ToolbarButton(
                icon = KernIcons.Table,
                contentDescription = "Edit Table",
                onClick = onEditTable,
                label = "Edit Table",
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(KernRadius.innerSmall))
                    .border(0.5.dp, colors.borderSoft, RoundedCornerShape(KernRadius.innerSmall)),
            ) {
                rows.take(6).forEach { row ->
                    Row {
                        row.forEach { cell ->
                            Box(
                                Modifier
                                    .width(100.dp)
                                    .border(0.5.dp, colors.borderSoft)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = cell.ifBlank { "-" },
                                    style = TextStyle(
                                        fontFamily = OutfitFamily,
                                        fontSize = 13.sp * scale,
                                        color = if (cell.isBlank()) colors.textDim else colors.text,
                                    ),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                if (rows.size > 6) {
                    Box(Modifier.padding(6.dp)) {
                        Text("+ ${rows.size - 6} more rows", style = KernType.caption, color = colors.textDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableEditorSheet(
    vm: WordEditorViewModel,
    onDismiss: () -> Unit,
) {
    val colors = KernTheme.colors
    val grid = vm.activeTableData
    val selectedRow = vm.activeSelectedRow.coerceIn(0, (grid.size - 1).coerceAtLeast(0))
    val selectedCol = vm.activeSelectedCol.coerceIn(0, ((grid.getOrNull(selectedRow)?.size ?: 1) - 1).coerceAtLeast(0))
    val activeCellText = grid.getOrNull(selectedRow)?.getOrNull(selectedCol) ?: ""

    KernBottomSheet(
        onDismiss = onDismiss,
        title = "Edit Table (${grid.size} × ${grid.firstOrNull()?.size ?: 0})",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            // Active Cell Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(KernRadius.innerSmall))
                    .background(colors.sunken)
                    .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.innerSmall))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(KernRadius.innerSmall))
                        .background(colors.accentSoft)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "R${selectedRow + 1}:C${selectedCol + 1}",
                        style = KernType.meta,
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                BasicTextField(
                    value = activeCellText,
                    onValueChange = { vm.updateActiveTableCell(selectedRow, selectedCol, it) },
                    textStyle = TextStyle(
                        fontFamily = OutfitFamily,
                        fontSize = 14.sp,
                        color = colors.text,
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (activeCellText.isEmpty()) {
                            Text("Cell text...", color = colors.textDim, fontSize = 14.sp)
                        }
                        inner()
                    },
                )
            }

            // Table Action Controls Toolbar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToolbarButton(KernIcons.Plus, "Add Row Above", onClick = { vm.insertTableRow(selectedRow) }, label = "+Row Above")
                    ToolbarButton(KernIcons.Plus, "Add Row Below", onClick = { vm.insertTableRow(selectedRow + 1) }, label = "+Row Below")
                    if (grid.size > 1) {
                        ToolbarButton(KernIcons.Trash, "Delete Row", onClick = { vm.deleteTableRow(selectedRow) }, label = "-Row")
                    }
                    ToolbarSeparator()
                    ToolbarButton(KernIcons.Plus, "Add Col Left", onClick = { vm.insertTableColumn(selectedCol) }, label = "+Col Left")
                    ToolbarButton(KernIcons.Plus, "Add Col Right", onClick = { vm.insertTableColumn(selectedCol + 1) }, label = "+Col Right")
                    if ((grid.firstOrNull()?.size ?: 0) > 1) {
                        ToolbarButton(KernIcons.Trash, "Delete Col", onClick = { vm.deleteTableColumn(selectedCol) }, label = "-Col")
                    }
                }
            }

            // 2D Scrollable Matrix of Selectable Tiles
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(KernRadius.innerSmall))
                    .background(colors.sunken)
                    .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.innerSmall))
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            ) {
                Column {
                    grid.forEachIndexed { r, row ->
                        Row {
                            row.forEachIndexed { c, cell ->
                                val isSelected = r == selectedRow && c == selectedCol
                                Box(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .heightIn(min = 44.dp)
                                        .border(
                                            width = if (isSelected) 2.dp else 0.5.dp,
                                            color = if (isSelected) colors.accent else colors.borderSoft,
                                        )
                                        .background(if (isSelected) colors.accentSoft.copy(alpha = 0.35f) else colors.surface)
                                        .clickable {
                                            vm.activeSelectedRow = r
                                            vm.activeSelectedCol = c
                                        }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        text = cell.ifBlank { "-" },
                                        style = TextStyle(
                                            fontFamily = OutfitFamily,
                                            fontSize = 13.sp,
                                            color = if (isSelected) colors.accent else if (cell.isBlank()) colors.textDim else colors.text,
                                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                        ),
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Done Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                ToolbarButton(
                    icon = KernIcons.Check,
                    contentDescription = "Save and Close",
                    onClick = vm::saveTableEditor,
                    label = "Done",
                    active = true,
                )
            }
        }
    }
}

@Composable
private fun ImageView(block: WordDocument.ImageBlock, scale: Float) {
    val bitmap = remember(block.bytes) {
        try {
            BitmapFactory.decodeByteArray(block.bytes, 0, block.bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = block.fileName ?: "Embedded Image",
                modifier = Modifier
                    .clip(RoundedCornerShape(KernRadius.innerSmall))
                    .heightIn(max = (260 * scale).dp),
            )
        }
    }
}

@Composable
private fun WordSizePicker(current: Float?, onPick: (Float?) -> Unit, onDismiss: () -> Unit) {
    val colors = KernTheme.colors
    KernBottomSheet(onDismiss = onDismiss, title = "Font size") {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            OptionRow("Default", selected = current == null, onClick = { onPick(null) })
            FontSizes.forEach { pt ->
                OptionRow("${pt.roundToInt()} pt", selected = current == pt, onClick = { onPick(pt) })
            }
        }
    }
}

@Composable
private fun WordStylePicker(current: WordDocument.Kind, onPick: (WordDocument.Kind) -> Unit, onDismiss: () -> Unit) {
    KernBottomSheet(onDismiss = onDismiss, title = "Paragraph style") {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            OptionRow("Title", selected = current == WordDocument.Kind.TITLE, onClick = { onPick(WordDocument.Kind.TITLE) })
            OptionRow("Heading 1", selected = current == WordDocument.Kind.HEADING1, onClick = { onPick(WordDocument.Kind.HEADING1) })
            OptionRow("Heading 2", selected = current == WordDocument.Kind.HEADING2, onClick = { onPick(WordDocument.Kind.HEADING2) })
            OptionRow("Body Text", selected = current == WordDocument.Kind.BODY, onClick = { onPick(WordDocument.Kind.BODY) })
        }
    }
}

@Composable
private fun WordAlignPicker(current: WordDocument.Align, onPick: (WordDocument.Align) -> Unit, onDismiss: () -> Unit) {
    KernBottomSheet(onDismiss = onDismiss, title = "Text alignment") {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            OptionRow("Left Align", selected = current == WordDocument.Align.START, onClick = { onPick(WordDocument.Align.START) })
            OptionRow("Center Align", selected = current == WordDocument.Align.CENTER, onClick = { onPick(WordDocument.Align.CENTER) })
            OptionRow("Right Align", selected = current == WordDocument.Align.END, onClick = { onPick(WordDocument.Align.END) })
            OptionRow("Justify", selected = current == WordDocument.Align.JUSTIFY, onClick = { onPick(WordDocument.Align.JUSTIFY) })
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
