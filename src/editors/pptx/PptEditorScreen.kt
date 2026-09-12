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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kern.shared.DocumentFormat
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.ui.EditorChrome
import dev.kern.shared.ui.EditorToolbar
import dev.kern.shared.ui.KernBottomSheet
import dev.kern.shared.ui.KernIconButton
import dev.kern.shared.ui.KernIcons
import dev.kern.shared.ui.ToolbarButton
import dev.kern.shared.ui.ToolbarSeparator
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.min
import kotlin.math.roundToInt
import android.content.res.Configuration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import android.os.Build
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration

private const val PPTX_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

// Slide coordinate space used when a deck declares no size of its own, in points.
private const val DefaultSlideWidth = 960f
private const val DefaultSlideHeight = 540f
// Dark-slide background (white in light theme).
private val SlideDarkBackground = Color(0xFF1A1C22)
private val SlideTextSize = 16.sp
private val SlideElevation = 8.dp
private val SlideCanvasPadding = 16.dp
// Selection outline weight in slide units; divided by the canvas scale when drawn.
private const val SelectedOutlineWidth = 1.5f

// Where a shape goes when the file declares no geometry for it, in slide points.
private const val FallbackShapeInset = 50f
private const val FallbackShapeStep = 60f
private const val FallbackShapeWidth = 300f
private const val FallbackShapeHeight = 50f

// Thumbnail rail tile dimensions (~92x52).
private val ThumbWidth = 92.dp
private val ThumbHeight = 52.dp
private val ThumbBorderWidth = 2.dp
private val PageIndicatorMinWidth = 56.dp
private val SideRailWidth = 56.dp

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
    // Phones only for now, so orientation is the whole story. In landscape a bottom bar
    // would take a third of the height, so the controls move to a side rail instead and
    // the chrome's bottom slot is left empty.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showSlides by rememberSaveable { mutableStateOf(false) }

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
        toolbar = if (landscape) null else ({ PptToolbar(vm, onShowSlides = { showSlides = true }) }),
    ) { modifier ->
        SlideEditor(vm, hue, landscape, onShowSlides = { showSlides = true }, modifier = modifier)
    }

    if (showSlides) {
        SlidesSheet(vm, onDismiss = { showSlides = false })
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

/** The portrait bottom bar. */
@Composable
private fun PptToolbar(vm: PptEditorViewModel, onShowSlides: () -> Unit) {
    EditorToolbar { PptToolButtons(vm, onShowSlides) }
}

/**
 * The editor's controls, icon only. Emitted into whatever container the orientation
 * calls for: the horizontal bottom bar in portrait, the vertical side rail in landscape.
 * The font size button keeps its number, since "16" is information rather than a label.
 */
@Composable
private fun PptToolButtons(vm: PptEditorViewModel, onShowSlides: () -> Unit) {
    val caret = vm.caretStyle
    val hasSelection = vm.selectedShapeIndex != null
    val sizeLabel = caret.sizePt?.let { "${it.roundToInt()}" }

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
    )
    ToolbarButton(
        icon = KernIcons.Text,
        contentDescription = "Add text",
        onClick = vm::addTextBox,
    )
    ToolbarButton(
        icon = KernIcons.Slides,
        contentDescription = "All slides",
        onClick = onShowSlides,
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

/**
 * The landscape controls: a narrow vertical rail on the trailing edge holding the page
 * indicator and the same buttons as the portrait bar, so the canvas keeps the full
 * height of the screen.
 */
@Composable
private fun PptSideRail(vm: PptEditorViewModel, onShowSlides: () -> Unit) {
    val colors = KernTheme.colors
    val cutouts = displayCutoutRects()
    // The band of the rail's own content that the camera hole covers, if any.
    var cutoutBand by remember { mutableStateOf<IntRange?>(null) }
    Row(Modifier.fillMaxHeight()) {
        // KernDivider is horizontal only; the rail's edge is the same hairline turned.
        Box(Modifier.fillMaxHeight().width(1.dp).background(colors.borderSoft))
        RailColumn(
            band = cutoutBand,
            spacing = 2.dp,
            modifier = Modifier
                .fillMaxHeight()
                .background(colors.bg)
                // The rail sits on the trailing edge, where a rotated phone puts its
                // navigation bar. The display cutout is deliberately not an inset
                // here: the hole is a short band, not the whole edge, and insetting
                // the full edge would push the rail a camera's width inward. The
                // band is skipped by the layout instead, so the rail stays flush.
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.End + WindowInsetsSides.Bottom))
                .width(SideRailWidth)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp)
                .onGloballyPositioned { coords ->
                    val origin = coords.positionInWindow()
                    val right = origin.x + coords.size.width
                    cutoutBand = cutouts
                        .firstOrNull { it.left < right && it.right > origin.x }
                        ?.let { (it.top - origin.y).roundToInt()..(it.bottom - origin.y).roundToInt() }
                },
        ) {
            PageIndicator(vm, vertical = true)
            ToolbarSeparator()
            PptToolButtons(vm, onShowSlides)
        }
    }
}

/** The display cutout's non-functional areas in window pixels; empty before API 28. */
@Composable
private fun displayCutoutRects(): List<Rect> {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    return remember(view, configuration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@remember emptyList()
        view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty().map {
            Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
        }
    }
}

/**
 * A centred column that flows its children around [band], a vertical range of its own
 * coordinates that nothing may be placed in. A child that would overlap the band is
 * moved to just below it, and everything after follows.
 *
 * The band's height is reserved in the reported height even when it falls in a gap
 * between children, and one child height on top of that for the worst case. The
 * content is scrolled, and its scroll range is its height: a height that depended on
 * where the band currently sits would move the range, which moves the content, which
 * moves the band, without end.
 */
@Composable
private fun RailColumn(
    band: IntRange?,
    spacing: Dp,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity))
        }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.maxOfOrNull { it.width } ?: 0
        var y = 0
        val tops = placeables.map { placeable ->
            if (band != null && y < band.last && y + placeable.height > band.first) y = band.last + gap
            val top = y
            y += placeable.height + gap
            top
        }
        val natural = placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
        val reserved = band?.let { it.last - it.first + gap + (placeables.maxOfOrNull { p -> p.height } ?: 0) } ?: 0
        layout(width, constraints.constrainHeight(natural + reserved)) {
            placeables.forEachIndexed { i, placeable ->
                placeable.placeRelative((width - placeable.width) / 2, tops[i])
            }
        }
    }
}

/** The slide picker, behind the Slides button rather than permanently on screen. */
@Composable
private fun SlidesSheet(vm: PptEditorViewModel, onDismiss: () -> Unit) {
    KernBottomSheet(onDismiss = onDismiss, title = "Slides") {
        ThumbnailRail(vm, onPick = { index ->
            vm.goToSlide(index)
            onDismiss()
        })
    }
}

@Composable
private fun SlideEditor(
    vm: PptEditorViewModel,
    hue: Color,
    landscape: Boolean,
    onShowSlides: () -> Unit,
    modifier: Modifier,
) {
    val colors = KernTheme.colors
    val viewport = remember { SlideViewport() }
    Box(modifier.background(colors.sunken)) {
        if (landscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                SlideCanvas(vm, viewport, Modifier.weight(1f).fillMaxHeight())
                PptSideRail(vm, onShowSlides)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                SlideCanvas(vm, viewport, Modifier.weight(1f).fillMaxWidth())
                PageIndicator(vm)
            }
        }
        if (viewport.isTransformed) {
            ZoomBadge(viewport.zoom, hue, Modifier.align(Alignment.TopStart))
        }
    }
}

/**
 * The slide canvas.
 *
 * The inner box is laid out at the slide's own coordinate size in dp, and that whole box
 * is then scaled to fit the available area. This is what lets a shape sit at its authored
 * position: its offset is expressed in the same units the file stores. Sizing the box to
 * the viewport and scaling separately would leave shapes and slide chrome in two
 * different coordinate spaces, at two different sizes.
 */
@Composable
private fun SlideCanvas(vm: PptEditorViewModel, viewport: SlideViewport, modifier: Modifier) {
    val colors = KernTheme.colors
    val density = LocalDensity.current
    val slideState = vm.currentSlideState
    val slideW = slideState?.width?.takeIf { it > 0f } ?: DefaultSlideWidth
    val slideH = slideState?.height?.takeIf { it > 0f } ?: DefaultSlideHeight
    val slideBackground = slideState?.backgroundColorHex?.let { parseHex(it) }
        ?: if (colors.dark) SlideDarkBackground else Color.White
    val shape = RoundedCornerShape(KernRadius.base)

    BoxWithConstraints(modifier.padding(SlideCanvasPadding)) {
        val viewSize = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val naturalSize = with(density) { Size(slideW.dp.toPx(), slideH.dp.toPx()) }
        // Fit on both axes, so a slide uses the whole area rather than only its width.
        val fit = min(viewSize.width / naturalSize.width, viewSize.height / naturalSize.height)
        val fittedSize = Size(naturalSize.width * fit, naturalSize.height * fit)
        val scale = fit * viewport.zoom

        // A rotation or a slide of a different size changes what can be panned to.
        LaunchedEffect(viewSize, fittedSize) { viewport.clamp(viewSize, fittedSize) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .slideTransform(viewport, { viewSize }, { fittedSize }),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    // requiredSize, not size: the slide is a fixed coordinate space and
                    // must keep its own aspect ratio. A preferred size would be coerced
                    // to the parent's constraints, which are far smaller than the slide,
                    // and the box would come out the shape of the viewport instead.
                    .requiredSize((slideW * scale).dp, (slideH * scale).dp)
                    .graphicsLayer {
                        translationX = viewport.offset.x
                        translationY = viewport.offset.y
                    }
                    .shadow(SlideElevation, shape)
                    .clip(shape)
                    .background(slideBackground)
                    .border(1.dp, colors.borderSoft, shape)
            ) {
                // The slide is laid out at its on-screen size, not laid out at natural
                // size and scaled at draw time. A drawn scale is invisible to the text
                // cursor handle and the text toolbar, which are popups anchored in
                // real pixels: they landed at the unscaled offset from the shape's
                // origin, nowhere near the caret. Scaling the density instead makes
                // every dp and sp inside the slide mean "slide points times scale", so
                // one point of slide geometry is one dp of layout and text measures at
                // its true on-screen size.
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density * scale, density.fontScale),
                    // No teardrop: the caret itself marks the insertion point. Selection
                    // keeps its highlight; only the draggable handles go.
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = Color.Transparent,
                        backgroundColor = colors.accent.copy(alpha = 0.35f),
                    ),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        // Beneath the text, so a text box always wins a tap.
                        SlideDecorationLayer(vm.currentDecorations, Modifier.fillMaxSize())

                        val shapes = vm.currentShapes
                        if (shapes.isEmpty() && vm.currentDecorations.isEmpty()) {
                            Text(
                                text = "Blank slide. Add a text box from the toolbar.",
                                style = KernType.body.copy(fontSize = SlideTextSize),
                                color = colors.text.copy(alpha = 0.5f),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            shapes.forEachIndexed { i, shapeState ->
                                SlideTextShape(vm, i, shapeState, scale)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One editable text shape, positioned in the slide's coordinate space.
 *
 * A shape the file gives no geometry for is staggered by its index rather than dropped
 * at a fixed spot, so several of them stay separately reachable instead of covering
 * one another exactly.
 */
@Composable
private fun SlideTextShape(
    vm: PptEditorViewModel,
    index: Int,
    shapeState: PptEditorViewModel.ShapeState,
    scale: Float,
) {
    val colors = KernTheme.colors
    val isSelected = vm.selectedShapeIndex == index
    val bounds = shapeState.bounds ?: PptDocument.ShapeBounds(
        x = FallbackShapeInset,
        y = FallbackShapeInset + index * FallbackShapeStep,
        width = FallbackShapeWidth,
        height = FallbackShapeHeight,
    )
    val outline = RoundedCornerShape(KernRadius.innerSmall)

    Box(
        modifier = Modifier
            .offset(x = bounds.x.dp, y = bounds.y.dp)
            .size(width = bounds.width.dp, height = bounds.height.dp)
            .clip(outline)
            .background(if (isSelected) colors.accentSoft.copy(alpha = 0.25f) else Color.Transparent)
            // Dp inside the slide is scaled with it; dividing by the canvas scale keeps
            // the selection outline at the same apparent weight at every zoom.
            .border(
                width = if (isSelected) (SelectedOutlineWidth / scale).dp else 0.dp,
                color = if (isSelected) colors.accent else Color.Transparent,
                shape = outline,
            )
            .padding(4.dp),
    ) {
        BasicTextField(
            value = shapeState.textValue,
            onValueChange = {
                vm.selectShape(index)
                vm.updateShapeValue(index, it)
            },
            // The base for a run that names no font. A neutral system sans, not the
            // app's own display face: this is document content, and it has to sit
            // consistently beside runs whose named font maps to the same generic.
            textStyle = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = SlideTextSize,
                color = colors.text,
            ),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Previous / "n / total" / next. A row under the canvas in portrait; stacked in the side
 * rail in landscape, where the chevrons keep their direction because previous and next
 * do not change meaning when the bar turns.
 */
@Composable
private fun PageIndicator(vm: PptEditorViewModel, vertical: Boolean = false) {
    val colors = KernTheme.colors
    val previous = @Composable {
        KernIconButton(
            KernIcons.ChevronLeft,
            "Previous slide",
            onClick = { vm.previousSlide() },
            enabled = vm.currentSlide > 0,
        )
    }
    val counter = @Composable {
        Text(
            text = "${if (vm.slideCount == 0) 0 else vm.currentSlide + 1} / ${vm.slideCount}",
            style = KernType.meta.copy(fontSize = 13.sp),
            color = colors.textMid,
            textAlign = TextAlign.Center,
            modifier = if (vertical) Modifier else Modifier.width(PageIndicatorMinWidth),
        )
    }
    val next = @Composable {
        KernIconButton(
            KernIcons.Chevron,
            "Next slide",
            onClick = { vm.nextSlide() },
            enabled = vm.currentSlide < vm.slideCount - 1,
        )
    }
    if (vertical) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            previous(); counter(); next()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            previous(); counter(); next()
        }
    }
}

/** Horizontally scrollable rail of numbered slide tiles; tapping picks a slide, long-press opens actions. */
@Composable
private fun ThumbnailRail(vm: PptEditorViewModel, onPick: (Int) -> Unit) {
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
                            onTap = { onPick(i) },
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
