package dev.kern.editors.pdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kern.shared.ui.EditorChrome
import dev.kern.shared.ui.pinchZoom
import dev.kern.shared.ui.rememberZoomState
import kotlin.math.roundToInt

// PDF format identity hue (red, the conventional PDF colour).
private val PdfHue = Color(0xFFC4332E)
private const val PDF_MIME = "application/pdf"

/**
 * PDF viewer (0.1.5.0 part A): paged, pinch-zoomable rendering via the framework
 * [PdfDocument]. Read-only for now; the Qyra-backed edit toolkit lands in later
 * 0.1.5.0 commits. Save is disabled; Export writes a verbatim copy.
 */
@Composable
fun PdfEditorScreen(
    filePath: String?,
    vm: PdfEditorViewModel = viewModel(),
) {
    LaunchedEffect(filePath) { vm.start(filePath) }

    EditorChrome(
        title = vm.fileName.ifBlank { "PDF" },
        dirty = false,
        loading = vm.loading,
        error = vm.error,
        hue = PdfHue,
        onSave = { it(false, "PDF editing arrives later in 0.1.5.0; use Export to copy.") },
        exportMimeType = PDF_MIME,
        exportFileName = vm.fileName.ifBlank { "export.pdf" },
        onExportToUri = vm::exportTo,
    ) { modifier ->
        PdfPager(vm, modifier)
    }
}

@Composable
private fun PdfPager(vm: PdfEditorViewModel, modifier: Modifier) {
    val zoom = rememberZoomState()
    val listState = rememberLazyListState()
    var containerWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { containerWidthPx = it.width }
            .pinchZoom(zoom),
    ) {
        if (vm.pageCount > 0 && containerWidthPx > 0) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(vm.pageCount) { index ->
                    PdfPageView(vm, index, baseWidthPx = containerWidthPx, zoom = zoom.scale)
                }
            }

            // Page indicator: which page sits at the top of the viewport.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "${listState.firstVisibleItemIndex + 1} / ${vm.pageCount}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = PdfHue,
                )
            }
        }

        if (zoom.scale != 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("${(zoom.scale * 100).roundToInt()}%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = PdfHue)
            }
        }

        PdfToolsLayer(vm)
    }
}

/**
 * Overlay for the PDF edit tools (merge / split) backed by the Qyra native bridge.
 * A tool produces a file in the app cache; this layer then prompts the user for a
 * SAF destination to save it out. Lives inside the pager [Box] so it can align its
 * FAB, snackbar, and error dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.PdfToolsLayer(vm: PdfEditorViewModel) {
    val snackbar = remember { SnackbarHostState() }
    var sheetOpen by remember { mutableStateOf(false) }
    var extractOpen by remember { mutableStateOf(false) }

    // Save a produced file to a user-chosen SAF location.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PDF_MIME),
    ) { uri -> if (uri != null) vm.saveOutputTo(uri) else vm.dismissPendingOutput() }

    // When a tool finishes, prompt for where to save its output.
    LaunchedEffect(vm.pendingOutput) {
        vm.pendingOutput?.let { saveLauncher.launch(it.suggestedName) }
    }

    // Merge: pick the additional PDFs to append.
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) vm.merge(uris) }

    LaunchedEffect(vm.toolMessage) {
        vm.toolMessage?.let { snackbar.showSnackbar(it); vm.consumeToolMessage() }
    }

    FloatingActionButton(
        onClick = { sheetOpen = true },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        containerColor = PdfHue,
        contentColor = Color.White,
    ) { Icon(Icons.Default.Build, contentDescription = "PDF tools") }

    if (vm.toolBusy) CircularProgressIndicator(Modifier.align(Alignment.Center))

    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))

    vm.toolError?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.consumeToolError() },
            title = { Text("PDF Tools") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { vm.consumeToolError() }) { Text("OK") } },
        )
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "PDF tools",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (!vm.engineAvailable) {
                    Text(
                        "Native engine (libqyra_lib.so) is not bundled in this build.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                ListItem(
                    headlineContent = { Text("Merge with...") },
                    supportingContent = { Text("Append other PDFs to this one") },
                    leadingContent = { Icon(Icons.Default.MergeType, contentDescription = null) },
                    modifier = Modifier.clickable {
                        sheetOpen = false
                        pickLauncher.launch(arrayOf(PDF_MIME))
                    },
                )
                ListItem(
                    headlineContent = { Text("Extract pages...") },
                    supportingContent = { Text("Save a page range as a new PDF") },
                    leadingContent = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                    modifier = Modifier.clickable {
                        sheetOpen = false
                        extractOpen = true
                    },
                )
            }
        }
    }

    if (extractOpen) {
        var range by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { extractOpen = false },
            title = { Text("Extract pages") },
            text = {
                Column {
                    Text("Page range (1-based), e.g. 1-3", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = range,
                        onValueChange = { range = it },
                        singleLine = true,
                        placeholder = { Text("1-3") },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { extractOpen = false; vm.extractPages(range) }) { Text("Extract") }
            },
            dismissButton = {
                TextButton(onClick = { extractOpen = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * One rendered page. The bitmap is rendered once at [baseWidthPx] (crisp at 1x) and
 * only re-rendered when that width changes, so pinch-zoom just rescales the existing
 * bitmap. When zoomed past 1x the page overflows the viewport and pans horizontally.
 */
@Composable
private fun PdfPageView(vm: PdfEditorViewModel, index: Int, baseWidthPx: Int, zoom: Float) {
    var bitmap by remember(index, baseWidthPx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(index, baseWidthPx) {
        bitmap = vm.renderPage(index, baseWidthPx)?.asImageBitmap()
    }

    val widthDp = with(LocalDensity.current) { (baseWidthPx * zoom).toDp() }
    val bmp = bitmap
    if (bmp != null) {
        Box(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Image(
                bitmap = bmp,
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .width(widthDp)
                    .shadow(2.dp, RoundedCornerShape(2.dp)),
            )
        }
    } else {
        // Placeholder sized to a portrait page (A4 ratio) so the list does not jump.
        Box(
            modifier = Modifier.fillMaxWidth().height(widthDp * 1.414f),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}
