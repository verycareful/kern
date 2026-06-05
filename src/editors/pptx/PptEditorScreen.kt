package dev.kern.editors.pptx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kern.shared.ui.EditorChrome
import dev.kern.shared.ui.pinchZoom
import dev.kern.shared.ui.rememberZoomState
import kotlin.math.roundToInt

// PowerPoint format identity hue (design handoff).
private val PptHue = Color(0xFFD06A2C)
private const val PPTX_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

@Composable
fun PptEditorScreen(
    filePath: String?,
    vm: PptEditorViewModel = viewModel(),
) {
    LaunchedEffect(filePath) { vm.start(filePath) }

    EditorChrome(
        title = vm.fileName.ifBlank { "Presentation" },
        dirty = vm.dirty,
        loading = vm.loading,
        error = vm.error,
        hue = PptHue,
        onSave = vm::save,
        exportMimeType = PPTX_MIME,
        exportFileName = vm.fileName.ifBlank { "export.pptx" },
        onExportToUri = vm::exportTo,
    ) { modifier ->
        SlideEditor(vm, modifier)
    }
}

@Composable
private fun SlideEditor(vm: PptEditorViewModel, modifier: Modifier) {
    val zoom = rememberZoomState()
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.previousSlide() }, enabled = vm.currentSlide > 0) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous slide")
            }
            Text(
                text = "${if (vm.slideCount == 0) 0 else vm.currentSlide + 1} / ${vm.slideCount}",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { vm.nextSlide() }, enabled = vm.currentSlide < vm.slideCount - 1) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next slide")
            }
        }
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
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val texts = vm.currentTexts
                        if (texts.isEmpty()) {
                            Text(
                                text = "No editable text on this slide.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = (14 * zoom.scale).sp,
                            )
                        } else {
                            texts.forEachIndexed { i, t ->
                                OutlinedTextField(
                                    value = t,
                                    onValueChange = { vm.editText(i, it) },
                                    textStyle = TextStyle(fontSize = (15 * zoom.scale).sp),
                                    modifier = Modifier.fillMaxWidth(),
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
                    Text("${(zoom.scale * 100).roundToInt()}%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = PptHue)
                }
            }
        }
    }
}
