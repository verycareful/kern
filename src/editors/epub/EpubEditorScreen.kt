package dev.kern.editors.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kern.shared.DocumentFormat
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.theme.OutfitFamily
import dev.kern.shared.theme.SoraFamily
import dev.kern.shared.ui.EditorChrome
import dev.kern.shared.ui.KernBottomSheet
import dev.kern.shared.ui.KernIconButton
import dev.kern.shared.ui.KernIcons
import dev.kern.shared.ui.pinchZoom
import dev.kern.shared.ui.rememberZoomState
import kotlin.math.roundToInt

private const val EPUB_MIME = "application/epub+zip"

@Composable
fun EpubEditorScreen(
    filePath: String?,
    vm: EpubEditorViewModel = viewModel(),
) {
    LaunchedEffect(filePath) { vm.start(filePath) }
    val hue = KernTheme.formatColor(DocumentFormat.EPUB)

    EditorChrome(
        title = vm.bookTitle.ifBlank { vm.fileName.ifBlank { "Book" } },
        dirty = vm.dirty,
        loading = vm.loading,
        error = vm.error,
        hue = hue,
        onSave = vm::save,
        exportMimeType = EPUB_MIME,
        exportFileName = vm.fileName.ifBlank { "export.epub" },
        onExportToUri = vm::exportTo,
    ) { modifier ->
        ChapterReader(vm, hue, modifier)
    }
}

@Composable
private fun ChapterReader(vm: EpubEditorViewModel, hue: Color, modifier: Modifier) {
    val colors = KernTheme.colors
    val zoom = rememberZoomState()
    var tocOpen by remember { mutableStateOf(false) }
    val chapterLabel = vm.chapterTitles().getOrNull(vm.currentChapter)?.takeIf { it.isNotBlank() }
        ?: "Chapter ${vm.currentChapter + 1}"

    Column(modifier.background(colors.sunken)) {
        Box(Modifier.weight(1f).pinchZoom(zoom)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(KernRadius.base))
                        .background(if (colors.dark) colors.raised else colors.surface)
                        .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.base))
                        .padding(horizontal = 26.dp, vertical = 28.dp),
                ) {
                    Text(
                        chapterLabel.uppercase(),
                        style = KernType.sectionLabel,
                        color = hue,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                    val blocks = vm.currentBlocks
                    if (blocks.isEmpty()) {
                        Text("No readable text in this chapter.", style = KernType.body, color = colors.textMid)
                    } else {
                        blocks.forEach { block ->
                            BlockField(
                                text = vm.blockText(block),
                                style = block.style,
                                scale = zoom.scale,
                                onChange = { vm.editBlock(block, it) },
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
                        .clip(RoundedCornerShape(KernRadius.innerSmall))
                        .background(colors.sunken)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("${(zoom.scale * 100).roundToInt()}%", style = KernType.meta, color = hue)
                }
            }
        }
        ChapterFooter(
            current = vm.currentChapter,
            total = vm.chapterCount,
            hue = hue,
            onPrev = { vm.previousChapter() },
            onNext = { vm.nextChapter() },
            onToc = { tocOpen = true },
        )
    }

    if (tocOpen) {
        KernBottomSheet(onDismiss = { tocOpen = false }, title = "Contents") {
            vm.chapterTitles().forEachIndexed { i, title ->
                val selected = i == vm.currentChapter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.goToChapter(i); tocOpen = false }
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("${i + 1}".padStart(2, '0'), style = KernType.meta, color = if (selected) hue else colors.textDim)
                    Text(
                        title,
                        style = KernType.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (selected) colors.text else colors.textMid,
                    )
                }
            }
            Box(Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun ChapterFooter(
    current: Int,
    total: Int,
    hue: Color,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToc: () -> Unit,
) {
    val colors = KernTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KernIconButton(KernIcons.ChevronLeft, "Previous chapter", onPrev, enabled = current > 0)
        KernIconButton(KernIcons.List, "Table of contents", onToc, tint = hue)
        Text(
            text = "${if (total == 0) 0 else current + 1} / $total",
            style = KernType.meta.copy(fontSize = 13.sp),
            color = colors.textMid,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        KernIconButton(KernIcons.Chevron, "Next chapter", onNext, enabled = current < total - 1)
    }
}

@Composable
private fun BlockField(text: String, style: EpubDocument.Style, scale: Float, onChange: (String) -> Unit) {
    val colors = KernTheme.colors
    val reading = style == EpubDocument.Style.BODY
    val (size, weight, lineHeight) = when (style) {
        EpubDocument.Style.TITLE -> Triple(26.sp, FontWeight.Bold, 32.sp)
        EpubDocument.Style.HEADING1 -> Triple(22.sp, FontWeight.Bold, 28.sp)
        EpubDocument.Style.HEADING2 -> Triple(18.sp, FontWeight.SemiBold, 24.sp)
        EpubDocument.Style.BODY -> Triple(16.5.sp, FontWeight.Normal, 28.sp)
    }
    BasicTextField(
        value = text,
        onValueChange = onChange,
        textStyle = TextStyle(
            fontFamily = if (reading) SoraFamily else OutfitFamily,
            fontSize = size * scale,
            fontWeight = weight,
            color = colors.text,
            lineHeight = lineHeight * scale,
        ),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    )
}
