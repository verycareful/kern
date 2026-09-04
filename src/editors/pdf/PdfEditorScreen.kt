package dev.kern.editors.pdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kern.shared.DocumentFormat
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.theme.PlexMonoFamily
import dev.kern.shared.ui.EditorChrome
import dev.kern.shared.ui.EditorToolbar
import dev.kern.shared.ui.KernBottomSheet
import dev.kern.shared.ui.KernIcons
import dev.kern.shared.ui.SheetActionRow
import dev.kern.shared.ui.ToolbarButton
import dev.kern.shared.ui.ToolbarSeparator
import dev.kern.shared.ui.pinchZoom
import dev.kern.shared.ui.rememberZoomState
import kotlin.math.roundToInt

private const val PDF_MIME = "application/pdf"

// A4 portrait aspect for the loading placeholder so the list does not jump.
private const val A4_PORTRAIT_RATIO = 1.414f
private val PageVerticalGap = 12.dp

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
    val hue = KernTheme.formatColor(DocumentFormat.PDF)

    // Tools-sheet open state is lifted here so the bottom annotation toolbar (in the
    // EditorChrome toolbar slot) can open the same sheet the pager layer renders.
    var toolsSheetOpen by remember { mutableStateOf(false) }

    EditorChrome(
        title = vm.fileName.ifBlank { "PDF" },
        dirty = false,
        loading = vm.loading,
        error = vm.error,
        hue = hue,
        onSave = { it(false, "PDF editing arrives later in 0.1.5.0; use Export to copy.") },
        exportMimeType = PDF_MIME,
        exportFileName = vm.fileName.ifBlank { "export.pdf" },
        onExportToUri = vm::exportTo,
        toolbar = { PdfToolbar(onOpenTools = { toolsSheetOpen = true }) },
    ) { modifier ->
        PdfPager(
            vm = vm,
            hue = hue,
            toolsSheetOpen = toolsSheetOpen,
            onToolsSheetOpenChange = { toolsSheetOpen = it },
            modifier = modifier,
        )
    }
}

/**
 * Bottom annotation toolbar. Select / Draw / Highlight / Add text are future
 * functional work on the Qyra-backed annotation model and stay disabled; Tools is
 * live and opens the page-tools sheet (merge / extract).
 */
@Composable
private fun PdfToolbar(onOpenTools: () -> Unit) {
    EditorToolbar {
        ToolbarButton(KernIcons.Text, "Select", onClick = {}, label = "Select", enabled = false)
        ToolbarButton(KernIcons.Pen, "Draw", onClick = {}, enabled = false)
        ToolbarButton(KernIcons.Highlight, "Highlight", onClick = {}, enabled = false)
        ToolbarButton(KernIcons.Text, "Add text", onClick = {}, enabled = false)
        ToolbarSeparator()
        ToolbarButton(KernIcons.Merge, "Page tools", onClick = onOpenTools, label = "Tools")
    }
}

@Composable
private fun PdfPager(
    vm: PdfEditorViewModel,
    hue: Color,
    toolsSheetOpen: Boolean,
    onToolsSheetOpenChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val colors = KernTheme.colors
    val zoom = rememberZoomState()
    val listState = rememberLazyListState()
    var containerWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier
            .background(colors.sunken)
            .onSizeChanged { containerWidthPx = it.width }
            .pinchZoom(zoom),
    ) {
        if (vm.pageCount > 0 && containerWidthPx > 0) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = PageVerticalGap),
                verticalArrangement = Arrangement.spacedBy(PageVerticalGap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(vm.pageCount) { index ->
                    PdfPageView(vm, index, baseWidthPx = containerWidthPx, zoom = zoom.scale)
                }
            }

            // Page indicator pill: which page sits at the top of the viewport.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(KernRadius.field))
                    .background(colors.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Page ${listState.firstVisibleItemIndex + 1} / ${vm.pageCount}",
                    style = KernType.meta.copy(fontSize = 13.sp),
                    color = hue,
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

        PdfToolsLayer(vm, toolsSheetOpen, onToolsSheetOpenChange)
    }
}

/**
 * Overlay for the PDF edit tools (merge / extract) backed by the Qyra native bridge.
 * A tool produces a file in the app cache; this layer then prompts the user for a
 * SAF destination to save it out. Lives inside the pager [Box] so it can align its
 * snackbar and busy indicator. The tools sheet open-state is hoisted to the screen
 * so the bottom toolbar can open it.
 */
@Composable
private fun BoxScope.PdfToolsLayer(
    vm: PdfEditorViewModel,
    sheetOpen: Boolean,
    onSheetOpenChange: (Boolean) -> Unit,
) {
    val colors = KernTheme.colors
    val snackbar = remember { SnackbarHostState() }
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

    if (vm.toolBusy) CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.accent)

    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))

    vm.toolError?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.consumeToolError() },
            containerColor = colors.surface,
            title = { Text("PDF tools") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { vm.consumeToolError() }) { Text("OK", color = colors.accent) } },
        )
    }

    if (sheetOpen) {
        KernBottomSheet(onDismiss = { onSheetOpenChange(false) }, title = "PDF tools") {
            if (!vm.engineAvailable) {
                Text(
                    "Native engine (libkern_pdf.so) is not bundled in this build.",
                    style = KernType.caption,
                    color = colors.danger,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                )
            }
            SheetActionRow(
                icon = KernIcons.Merge,
                label = "Merge with another PDF",
                sublabel = "Append other PDFs to this one",
                onClick = {
                    onSheetOpenChange(false)
                    pickLauncher.launch(arrayOf(PDF_MIME))
                },
            )
            SheetActionRow(
                icon = KernIcons.Split,
                label = "Extract pages",
                sublabel = "Save a page range as a new PDF",
                onClick = {
                    onSheetOpenChange(false)
                    extractOpen = true
                },
            )
        }
    }

    if (extractOpen) {
        ExtractPagesDialog(
            onDismiss = { extractOpen = false },
            onExtract = { range -> extractOpen = false; vm.extractPages(range) },
        )
    }
}

/**
 * Dialog asking for a single 1-based page range to extract into a new PDF.
 *
 * Only one produced file can be staged and saved, so the input is validated by
 * [PdfEditorViewModel.rangeSpecError] before it is submitted: a list such as
 * "1-3,5-7" is refused inline instead of quietly yielding pages 1-3 alone.
 */
@Composable
private fun ExtractPagesDialog(onDismiss: () -> Unit, onExtract: (String) -> Unit) {
    val colors = KernTheme.colors
    var range by remember { mutableStateOf("") }
    var rangeError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Extract pages") },
        text = {
            Column {
                Text("One page range (1-based), e.g. 1-3", style = KernType.body, color = colors.textMid)
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(KernRadius.field))
                        .background(colors.sunken)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    if (range.isEmpty()) {
                        Text("1-3", style = KernType.meta.copy(fontSize = 13.sp), color = colors.textDim)
                    }
                    BasicTextField(
                        value = range,
                        onValueChange = { range = it; rangeError = null },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = PlexMonoFamily,
                            fontSize = 13.sp,
                            color = colors.text,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                rangeError?.let { message ->
                    Text(
                        message,
                        style = KernType.caption,
                        color = colors.danger,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val problem = PdfEditorViewModel.rangeSpecError(range)
                    if (problem != null) rangeError = problem else onExtract(range)
                },
            ) { Text("Extract", color = colors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMid) }
        },
    )
}

/**
 * One rendered page. The bitmap is rendered once at [baseWidthPx] (crisp at 1x) and
 * only re-rendered when that width changes, so pinch-zoom just rescales the existing
 * bitmap. When zoomed past 1x the page overflows the viewport and pans horizontally.
 */
@Composable
private fun PdfPageView(vm: PdfEditorViewModel, index: Int, baseWidthPx: Int, zoom: Float) {
    val colors = KernTheme.colors
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
                    .shadow(8.dp, RoundedCornerShape(KernRadius.pdfPage))
                    .clip(RoundedCornerShape(KernRadius.pdfPage))
                    .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.pdfPage)),
            )
        }
    } else {
        // Placeholder sized to a portrait page (A4 ratio) so the list does not jump.
        Box(
            modifier = Modifier.fillMaxWidth().height(widthDp * A4_PORTRAIT_RATIO),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = colors.accent)
        }
    }
}
