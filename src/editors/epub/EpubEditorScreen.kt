package dev.kern.editors.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

// EPUB format identity hue.
private val EpubHue = Color(0xFF6B4FA0)
private const val EPUB_MIME = "application/epub+zip"

@Composable
fun EpubEditorScreen(
    filePath: String?,
    vm: EpubEditorViewModel = viewModel(),
) {
    LaunchedEffect(filePath) { vm.start(filePath) }

    EditorChrome(
        title = vm.bookTitle.ifBlank { vm.fileName.ifBlank { "Book" } },
        dirty = vm.dirty,
        loading = vm.loading,
        error = vm.error,
        hue = EpubHue,
        onSave = vm::save,
        exportMimeType = EPUB_MIME,
        exportFileName = vm.fileName.ifBlank { "export.epub" },
        onExportToUri = vm::exportTo,
    ) { modifier ->
        ChapterEditor(vm, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterEditor(vm: EpubEditorViewModel, modifier: Modifier) {
    val zoom = rememberZoomState()
    var tocOpen by remember { mutableStateOf(false) }

    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        ChapterNavBar(
            current = vm.currentChapter,
            total = vm.chapterCount,
            onPrev = { vm.previousChapter() },
            onNext = { vm.nextChapter() },
            onToc = { tocOpen = true },
        )
        Box(Modifier.weight(1f).pinchZoom(zoom)) {
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
                        val blocks = vm.currentBlocks
                        if (blocks.isEmpty()) {
                            Text(
                                "No editable text in this chapter.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = (14 * zoom.scale).sp,
                            )
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
            }
            if (zoom.scale != 1f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("${(zoom.scale * 100).roundToInt()}%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = EpubHue)
                }
            }
        }
    }

    if (tocOpen) {
        ModalBottomSheet(onDismissRequest = { tocOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "Contents",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                vm.chapterTitles().forEachIndexed { i, title ->
                    ListItem(
                        headlineContent = { Text(title) },
                        leadingContent = {
                            Text(
                                "${i + 1}",
                                fontFamily = FontFamily.Monospace,
                                color = if (i == vm.currentChapter) EpubHue else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable {
                            vm.goToChapter(i)
                            tocOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterNavBar(current: Int, total: Int, onPrev: () -> Unit, onNext: () -> Unit, onToc: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, enabled = current > 0) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous chapter")
        }
        IconButton(onClick = onToc) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Table of contents", tint = EpubHue)
        }
        Text(
            text = "${if (total == 0) 0 else current + 1} / $total",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onNext, enabled = current < total - 1) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next chapter")
        }
    }
}

@Composable
private fun BlockField(text: String, style: EpubDocument.Style, scale: Float, onChange: (String) -> Unit) {
    val (size, weight) = when (style) {
        EpubDocument.Style.TITLE -> 26.sp to FontWeight.Bold
        EpubDocument.Style.HEADING1 -> 22.sp to FontWeight.Bold
        EpubDocument.Style.HEADING2 -> 18.sp to FontWeight.SemiBold
        EpubDocument.Style.BODY -> 15.sp to FontWeight.Normal
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
        cursorBrush = SolidColor(EpubHue),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
