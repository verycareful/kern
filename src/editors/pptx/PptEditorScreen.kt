package dev.kern.editors.pptx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
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
// Dark-slide background (white in light theme), per the design handoff.
private val SlideDarkBackground = Color(0xFF1A1C22)
// Body text size on the slide canvas (scaled by zoom).
private val SlideTextSize = 16.sp

// Thumbnail rail tile dimensions (design: ~92x52).
private val ThumbWidth = 92.dp
private val ThumbHeight = 52.dp
private val ThumbBorderWidth = 2.dp
private val PageIndicatorMinWidth = 56.dp

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
        toolbar = { PptToolbar() },
    ) { modifier ->
        SlideEditor(vm, hue, modifier)
    }
}

/**
 * Slide toolbar. Adding slides/text, changing layout, and run styling (bold/italic)
 * are future functional work on the presentation model; the buttons match the design
 * and stay disabled until the ViewModel exposes those capabilities.
 */
@Composable
private fun PptToolbar() {
    EditorToolbar {
        ToolbarButton(KernIcons.Plus, "Add slide", onClick = {}, label = "Slide", enabled = false)
        ToolbarButton(KernIcons.Text, "Add text", onClick = {}, label = "Text", enabled = false)
        ToolbarButton(KernIcons.Slides, "Layout", onClick = {}, label = "Layout", enabled = false)
        ToolbarSeparator()
        ToolbarButton(KernIcons.Bold, "Bold", onClick = {}, enabled = false)
        ToolbarButton(KernIcons.Italic, "Italic", onClick = {}, enabled = false)
    }
}

@Composable
private fun SlideEditor(vm: PptEditorViewModel, hue: Color, modifier: Modifier) {
    val colors = KernTheme.colors
    val zoom = rememberZoomState()
    Box(modifier.background(colors.sunken)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SlideCanvas(vm, zoom.scale, Modifier.pinchZoom(zoom))
            PageIndicator(vm)
            ThumbnailRail(vm)
        }
        if (zoom.scale != 1f) {
            ZoomBadge(zoom.scale, hue, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** The centered 16:9 slide canvas with directly editable text shapes. */
@Composable
private fun SlideCanvas(vm: PptEditorViewModel, scale: Float, modifier: Modifier) {
    val colors = KernTheme.colors
    val slideBackground = if (colors.dark) SlideDarkBackground else Color.White
    // The slide background mirrors the theme (white in light, dark in dark), so the
    // primary text token already contrasts correctly on the slide in both themes.
    val slideText = colors.text
    Box(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(SLIDE_ASPECT)
                .shadow(8.dp, RoundedCornerShape(KernRadius.base))
                .clip(RoundedCornerShape(KernRadius.base))
                .background(slideBackground)
                .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.base))
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val texts = vm.currentTexts
            if (texts.isEmpty()) {
                Text(
                    text = "No editable text on this slide.",
                    style = KernType.body.copy(fontSize = SlideTextSize * scale),
                    color = slideText.copy(alpha = 0.5f),
                )
            } else {
                texts.forEachIndexed { i, t ->
                    BasicTextField(
                        value = t,
                        onValueChange = { vm.editText(i, it) },
                        textStyle = TextStyle(
                            fontFamily = OutfitFamily,
                            fontSize = SlideTextSize * scale,
                            color = slideText,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
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

/** Horizontally scrollable rail of numbered slide tiles; tapping jumps to a slide. */
@Composable
private fun ThumbnailRail(vm: PptEditorViewModel) {
    val colors = KernTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (i in 0 until vm.slideCount) {
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
                    .clickable { vm.goToSlide(i) },
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

/** Small zoom-percentage badge shown while zoomed. */
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
