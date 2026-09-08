package dev.kern.editors.pptx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import dev.kern.shared.ui.KernBottomSheet
import dev.kern.shared.ui.KernIconButton
import dev.kern.shared.ui.KernIcons
import dev.kern.shared.ui.ToolbarButton
import dev.kern.shared.ui.ToolbarSeparator
import dev.kern.shared.ui.pinchZoom
import dev.kern.shared.ui.rememberZoomState
import kotlin.math.roundToInt

private const val PPTX_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

// Slide canvas: 16:9, matching the design's slide aspect ratio.
private const val SLIDE_ASPECT = 16f / 9f
// Dark-slide background (white in light theme).
private val SlideDarkBackground = Color(0xFF1A1C22)
private val SlideTextSize = 16.sp

// Thumbnail rail tile dimensions (~92x52).
private val ThumbWidth = 92.dp
private val ThumbHeight = 52.dp
private val ThumbBorderWidth = 2.dp
private val PageIndicatorMinWidth = 56.dp

private val PptFontSizes = listOf(12f, 14f, 16f, 18f, 20f, 24f, 28f, 32f, 36f, 40f, 44f, 48f, 54f, 60f, 72f)

private val PaletteColors = listOf(
    "#000000", "#FFFFFF", "#E53935", "#D81B60",
    "#8E24AA", "#5E35B1", "#3949AB", "#1E88E5",
    "#00ACC1", "#00897B", "#43A047", "#7CB342",
    "#FDD835", "#FB8C00", "#F4511E", "#6D4C41",
)

@Composable
fun PptEditorScreen(
    filePath: String?,
    vm: PptEditorViewModel = viewModel(),
) {
    LaunchedEffect(filePath) { vm.start(filePath) }
    val hue = KernTheme.formatColor(DocumentFormat.POWERPOINT)

    EditorChrome(
        title = vm.fileName.ifBlank { "Presentation" },
        dirty = vm.dirty,
        loading = vm.loading,
        error = vm.error,
        hue = hue,
        onSave = vm::save,
        exportMimeType = PPTX_MIME,
        exportFileName = vm.fileName.ifBlank { "export.pptx" },
        onExportToUri = vm::exportTo,
        toolbar = { PptToolbar(vm) },
    ) { modifier ->
        SlideEditor(vm, hue, modifier)
    }

    if (vm.showLayoutPicker) {
        LayoutPickerSheet(
            onSelect = vm::addSlide,
            onDismiss = { vm.showLayoutPicker = false },
        )
    }

    if (vm.showSlideActions) {
        SlideActionSheet(
            slideIndex = vm.actionSlideIndex,
            totalSlides = vm.slideCount,
            onDuplicate = { vm.duplicateSlide(vm.actionSlideIndex) },
            onDelete = { vm.deleteSlide(vm.actionSlideIndex) },
            onMoveLeft = { vm.moveSlide(vm.actionSlideIndex, vm.actionSlideIndex - 1) },
            onMoveRight = { vm.moveSlide(vm.actionSlideIndex, vm.actionSlideIndex + 1) },
            onDismiss = { vm.showSlideActions = false },
        )
    }

    if (vm.showFontSizeSheet) {
        PptSizePicker(
            current = vm.caretStyle.sizePt,
            onPick = {
                if (it != null) vm.setFontSize(it)
                vm.showFontSizeSheet = false
            },
            onDismiss = { vm.showFontSizeSheet = false },
        )
    }

    if (vm.showColorSheet) {
        PptColorPicker(
            currentColorHex = vm.caretStyle.colorHex ?: "#FFFFFF",
            onPick = {
                vm.setColor(it)
                vm.showColorSheet = false
            },
            onDismiss = { vm.showColorSheet = false },
        )
    }
}

@Composable
private fun PptToolbar(vm: PptEditorViewModel) {
    val caret = vm.caretStyle
    val hasSelection = vm.selectedShapeIndex != null
    val sizeLabel = caret.sizePt?.let { "${it.roundToInt()}" } ?: "Size"

    EditorToolbar {
        ToolbarButton(
            icon = KernIcons.Undo,
            contentDescription = "Undo",
            onClick = vm::undo,
            enabled = vm.canUndo,
        )
        ToolbarButton(
            icon = KernIcons.Redo,
            contentDescription = "Redo",
            onClick = vm::redo,
            enabled = vm.canRedo,
        )
        ToolbarSeparator()
        ToolbarButton(
            icon = KernIcons.Plus,
            contentDescription = "Add slide",
            onClick = { vm.showLayoutPicker = true },
            label = "Slide",
        )
        ToolbarButton(
            icon = KernIcons.Text,
            contentDescription = "Add text",
            onClick = vm::addTextBox,
            label = "Text",
        )
        ToolbarSeparator()
        ToolbarButton(
            icon = KernIcons.Bold,
            contentDescription = "Bold",
            onClick = vm::toggleBold,
            active = caret.bold,
            enabled = hasSelection,
        )
        ToolbarButton(
            icon = KernIcons.Italic,
            contentDescription = "Italic",
            onClick = vm::toggleItalic,
            active = caret.italic,
            enabled = hasSelection,
        )
        ToolbarButton(
            icon = KernIcons.Underline,
            contentDescription = "Underline",
            onClick = vm::toggleUnderline,
            active = caret.underline,
            enabled = hasSelection,
        )
        ToolbarButton(
            icon = KernIcons.FontSize,
            contentDescription = "Font size",
            onClick = { vm.showFontSizeSheet = true },
            label = sizeLabel,
            enabled = hasSelection,
        )
        ToolbarButton(
            icon = KernIcons.FontColor,
            contentDescription = "Font color",
            onClick = { vm.showColorSheet = true },
            active = caret.colorHex != null,
            enabled = hasSelection,
        )
    }
}

@Composable
private fun SlideEditor(vm: PptEditorViewModel, hue: Color, modifier: Modifier) {
    val colors = KernTheme.colors
    val zoom = rememberZoomState()
    Box(modifier.background(colors.sunken)) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                SlideCanvas(vm, zoom.scale, Modifier.pinchZoom(zoom))
            }
            PageIndicator(vm)
            ThumbnailRail(vm)
        }
        if (zoom.scale != 1f) {
            ZoomBadge(zoom.scale, hue, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** The centered 16:9 slide canvas with selectable and editable text shapes. */
@Composable
private fun SlideCanvas(vm: PptEditorViewModel, scale: Float, modifier: Modifier) {
    val colors = KernTheme.colors
    val slideState = vm.currentSlideState
    val slideWidth = slideState?.width ?: 960f
    val slideHeight = slideState?.height ?: 540f
    val slideAspect = if (slideHeight > 0) slideWidth / slideHeight else SLIDE_ASPECT
    val bgHex = slideState?.backgroundColorHex
    val slideBackground = bgHex?.let { parseHex(it) } ?: if (colors.dark) SlideDarkBackground else Color.White
    val slideText = colors.text

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp)) {
        val containerWidth = maxWidth.value
        val drawScale = containerWidth / slideWidth
        val finalScale = drawScale * scale

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(slideAspect)
                .shadow(8.dp, RoundedCornerShape(KernRadius.base))
                .clip(RoundedCornerShape(KernRadius.base))
                .background(slideBackground)
                .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.base))
                .graphicsLayer {
                    scaleX = finalScale
                    scaleY = finalScale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            val shapes = vm.currentShapes
            if (shapes.isEmpty()) {
                Text(
                    text = "Blank slide. Tap 'Text' to add a text box.",
                    style = KernType.body.copy(fontSize = SlideTextSize),
                    color = slideText.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                shapes.forEachIndexed { i, shapeState ->
                    val isSelected = vm.selectedShapeIndex == i
                    val bounds = shapeState.bounds ?: PptDocument.ShapeBounds(50f, 50f, 300f, 50f)
                    
                    Box(
                        modifier = Modifier
                            .offset(x = bounds.x.dp, y = bounds.y.dp)
                            .size(width = bounds.width.dp, height = bounds.height.dp)
                            .clip(RoundedCornerShape(KernRadius.innerSmall))
                            .background(if (isSelected) colors.accentSoft.copy(alpha = 0.25f) else Color.Transparent)
                            .border(
                                width = if (isSelected) (1.5f / finalScale).dp else 0.5.dp,
                                color = if (isSelected) colors.accent else Color.Transparent,
                                shape = RoundedCornerShape(KernRadius.innerSmall),
                            )
                            .clickable { vm.selectShape(i) }
                            .padding(4.dp),
                    ) {
                        BasicTextField(
                            value = shapeState.textValue,
                            onValueChange = {
                                vm.selectShape(i)
                                vm.updateShapeValue(i, it)
                            },
                            textStyle = TextStyle(
                                fontFamily = OutfitFamily,
                                fontSize = SlideTextSize,
                                color = slideText,
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** Previous / "n / total" / next slide indicator. */
@Composable
private fun PageIndicator(vm: PptEditorViewModel) {
    val colors = KernTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KernIconButton(
            KernIcons.ChevronLeft,
            "Previous slide",
            onClick = { vm.previousSlide() },
            enabled = vm.currentSlide > 0,
        )
        Text(
            text = "${if (vm.slideCount == 0) 0 else vm.currentSlide + 1} / ${vm.slideCount}",
            style = KernType.meta.copy(fontSize = 13.sp),
            color = colors.textMid,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(PageIndicatorMinWidth),
        )
        KernIconButton(
            KernIcons.Chevron,
            "Next slide",
            onClick = { vm.nextSlide() },
            enabled = vm.currentSlide < vm.slideCount - 1,
        )
    }
}

/** Horizontally scrollable rail of numbered slide tiles; tapping jumps to a slide, long-press opens actions. */
@Composable
private fun ThumbnailRail(vm: PptEditorViewModel) {
    val colors = KernTheme.colors
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(vm.slideCount) { i ->
            val selected = i == vm.currentSlide
            Box(
                modifier = Modifier
                    .size(width = ThumbWidth, height = ThumbHeight)
                    .clip(RoundedCornerShape(KernRadius.badge))
                    .background(if (colors.dark) SlideDarkBackground else Color.White)
                    .border(
                        ThumbBorderWidth,
                        if (selected) colors.accent else colors.borderSoft,
                        RoundedCornerShape(KernRadius.badge),
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { vm.goToSlide(i) },
                            onLongPress = {
                                vm.actionSlideIndex = i
                                vm.showSlideActions = true
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${i + 1}",
                    style = KernType.meta.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) colors.accent else colors.textDim,
                )
            }
        }
    }
}

/** Bottom sheet with preset slide layouts for adding new slides. */
@Composable
private fun LayoutPickerSheet(
    onSelect: (PptDocument.PresetLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KernTheme.colors
    KernBottomSheet(
        onDismiss = onDismiss,
        title = "Choose Slide Layout",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val layouts = listOf(
                PptDocument.PresetLayout.TITLE to "Title Slide",
                PptDocument.PresetLayout.TITLE_AND_CONTENT to "Title & Content",
                PptDocument.PresetLayout.SECTION_HEADER to "Section Header",
                PptDocument.PresetLayout.BLANK to "Blank",
            )
            for ((layout, title) in layouts) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(KernRadius.innerSmall))
                        .background(colors.sunken)
                        .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.innerSmall))
                        .clickable { onSelect(layout) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = KernType.body, color = colors.text)
                    Text("+", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = colors.accent)
                }
            }
        }
    }
}

/** Bottom sheet with contextual operations for an individual slide. */
@Composable
private fun SlideActionSheet(
    slideIndex: Int,
    totalSlides: Int,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KernTheme.colors
    KernBottomSheet(
        onDismiss = onDismiss,
        title = "Slide ${slideIndex + 1} Actions",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionRow("Duplicate Slide", onClick = onDuplicate)
            if (slideIndex > 0) {
                ActionRow("Move Left", onClick = onMoveLeft)
            }
            if (slideIndex < totalSlides - 1) {
                ActionRow("Move Right", onClick = onMoveRight)
            }
            if (totalSlides > 1) {
                ActionRow("Delete Slide", isDestructive = true, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = KernTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KernRadius.innerSmall))
            .background(colors.sunken)
            .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.innerSmall))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = KernType.body,
            color = if (isDestructive) colors.danger else colors.text,
            fontWeight = if (isDestructive) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PptSizePicker(
    current: Float?,
    onPick: (Float?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KernTheme.colors
    KernBottomSheet(onDismiss = onDismiss, title = "Font Size") {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            PptFontSizes.forEach { pt ->
                val selected = current == pt
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(pt) }
                        .background(if (selected) colors.accentSoft else Color.Transparent)
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${pt.roundToInt()} pt", style = KernType.body, color = if (selected) colors.accent else colors.text)
                    if (selected) Text("✓", style = KernType.body, color = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun PptColorPicker(
    currentColorHex: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KernTheme.colors
    KernBottomSheet(onDismiss = onDismiss, title = "Font Color") {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PaletteColors.forEach { hex ->
                    val color = parseHex(hex)
                    val isSelected = currentColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) colors.accent else colors.borderSoft,
                                shape = CircleShape,
                            )
                            .clickable { onPick(hex) },
                    )
                }
            }
        }
    }
}

private fun parseHex(hex: String): Color {
    val clean = hex.removePrefix("#")
    val parsed = clean.toLongOrNull(16) ?: 0xFFFFFFFF
    return if (clean.length <= 6) Color(0xFF000000 or parsed) else Color(parsed)
}

@Composable
private fun ZoomBadge(scale: Float, hue: Color, modifier: Modifier) {
    val colors = KernTheme.colors
    Box(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(KernRadius.innerSmall))
            .background(colors.sunken)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text("${(scale * 100).roundToInt()}%", style = KernType.meta, color = hue)
    }
}
