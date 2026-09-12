package dev.kern.editors.pptx

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType

/** An sRGB hex string, with or without a hash, as a colour; six digits are opaque. */
internal fun parseHex(hex: String): Color {
    val clean = hex.removePrefix("#")
    val parsed = clean.toLongOrNull(16) ?: 0xFFFFFFFF
    return if (clean.length <= 6) Color(0xFF000000 or parsed) else Color(parsed)
}

/** Where a decoration goes when the file declares no geometry for it, in slide points. */
internal val FallbackDecorationBounds = PptDocument.ShapeBounds(80f, 80f, 400f, 240f)

private val DecorationLabelSize = 13.sp
private val TableCellTextSize = 11.sp

/** A one-pixel line in screen pixels, unaffected by the slide's scaled density. */
private const val Hairline = 1f
private const val PlaceholderDash = 6f

/**
 * The read-only layer of a slide: pictures, tables, shapes, and outlines standing in for
 * what the editor cannot draw yet.
 *
 * Drawn beneath the editable text so a text box always wins a tap. Composed under the
 * slide's scaled density, so a dp here is a slide point; the editor's own chrome (the
 * hairlines and dashes of a placeholder) is drawn in raw pixels so it keeps the same
 * apparent weight at every zoom.
 */
@Composable
fun SlideDecorationLayer(
    decorations: List<PptEditorViewModel.DecorationState>,
    modifier: Modifier = Modifier,
) {
    if (decorations.isEmpty()) return
    Box(modifier) {
        decorations.forEach { state ->
            val decoration = state.decoration
            val bounds = state.bounds
            val placed = Modifier
                .offset(x = bounds.x.dp, y = bounds.y.dp)
                .size(width = bounds.width.dp, height = bounds.height.dp)
            when (decoration) {
                is PptDocument.PictureDecoration -> PictureDecorationView(decoration, placed)
                is PptDocument.TableDecoration -> TableDecorationView(decoration, placed)
                is PptDocument.GeometryDecoration -> GeometryDecorationView(decoration, placed)
                is PptDocument.UnsupportedDecoration -> UnsupportedDecorationView(decoration.label, placed)
            }
        }
    }
}

/**
 * An embedded image. Decoding is remembered against the byte array so a recomposition,
 * of which there is one per keystroke elsewhere on the slide, does not decode it again.
 */
@Composable
private fun PictureDecorationView(
    picture: PptDocument.PictureDecoration,
    modifier: Modifier,
) {
    val bitmap = remember(picture.bytes) {
        runCatching { BitmapFactory.decodeByteArray(picture.bytes, 0, picture.bytes.size)?.asImageBitmap() }
            .getOrNull()
    }
    if (bitmap == null) {
        // A format Android cannot decode, EMF and WMF being the common ones in a deck
        // authored on Windows. Saying so beats an empty rectangle.
        UnsupportedDecorationView("Unsupported image", modifier)
        return
    }
    Image(
        bitmap = bitmap,
        contentDescription = picture.fileName ?: "Slide image",
        // A fill covers its shape and is cropped; a placed picture is framed to its own
        // proportions and must not be.
        contentScale = if (picture.fillsShape) ContentScale.Crop else ContentScale.Fit,
        modifier = modifier.clipToBounds(),
    )
}

/**
 * A preset autoshape: its fill, then its outline, at the file's colours. The outline is
 * in slide points like the geometry, so it zooms with the slide rather than staying a
 * hairline; that is what PowerPoint does with a shape's border. A preset the geometry
 * builder does not know falls back to the labelled placeholder.
 */
@Composable
private fun GeometryDecorationView(
    shape: PptDocument.GeometryDecoration,
    modifier: Modifier,
) {
    if (shape.preset !in PptShapeGeometry.supported) {
        UnsupportedDecorationView("Shape: ${shape.preset}", modifier)
        return
    }
    val outline = shape.outline?.let { parseHex(it.colorHex) to it.width }
    Box(
        modifier.drawBehind {
            val path = PptShapeGeometry.path(shape.preset, size, shape.adjustments) ?: return@drawBehind
            when (val fill = shape.fill) {
                is PptDocument.SolidFill -> drawPath(path, parseHex(fill.hex))
                is PptDocument.GradientFill -> drawPath(path, gradientBrush(fill, size))
                // Picture fills on shapes arrive as PictureDecoration instead.
                is PptDocument.PictureFill, null -> Unit
            }
            if (outline != null) drawPath(path, outline.first, style = Stroke(width = outline.second.dp.toPx()))
        },
    )
}

/**
 * The surface of a slide: white unless the file paints it, as PowerPoint does. Drawn
 * first inside the slide box so everything else sits on it.
 */
@Composable
fun SlideBackground(fill: PptDocument.Fill?, modifier: Modifier = Modifier) {
    when (fill) {
        null -> Box(modifier.background(Color.White))
        is PptDocument.SolidFill -> Box(modifier.background(parseHex(fill.hex)))
        is PptDocument.GradientFill -> Box(modifier.drawBehind { drawRect(gradientBrush(fill, size)) })
        is PptDocument.PictureFill -> Box(modifier.background(Color.White)) {
            PictureDecorationView(
                PptDocument.PictureDecoration(fill.bytes, fill.fileName, bounds = null, fillsShape = true),
                Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A DrawingML linear gradient as a brush over [size]. The angle is clockwise from the
 * x axis, 0 running left to right and 90 top to bottom, and the gradient line is
 * stretched so the first and last stops land on the two corners it passes nearest.
 */
private fun gradientBrush(fill: PptDocument.GradientFill, size: Size): Brush {
    val radians = Math.toRadians(fill.angleDegrees.toDouble())
    val dx = cos(radians).toFloat()
    val dy = sin(radians).toFloat()
    val length = abs(size.width * dx) + abs(size.height * dy)
    val center = Offset(size.width / 2f, size.height / 2f)
    val half = Offset(dx * length / 2f, dy * length / 2f)
    return Brush.linearGradient(
        colorStops = fill.stops.map { it.position to parseHex(it.color) }.toTypedArray(),
        start = center - half,
        end = center + half,
    )
}

/** A table, drawn as evenly divided rows of cell text. */
@Composable
private fun TableDecorationView(
    table: PptDocument.TableDecoration,
    modifier: Modifier,
) {
    val colors = KernTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(KernRadius.innerSmall))
            .background(colors.surface.copy(alpha = 0.85f))
            .drawBehind {
                drawRect(color = colors.borderSoft, style = Stroke(width = Hairline))
            },
    ) {
        table.rows.forEach { cells ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                cells.forEach { cell ->
                    Text(
                        text = cell,
                        style = KernType.body.copy(fontSize = TableCellTextSize),
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .drawBehind {
                                drawRect(color = colors.borderSoft.copy(alpha = 0.6f), style = Stroke(width = Hairline))
                            },
                    )
                }
            }
        }
    }
}

/**
 * A labelled dashed outline standing in for a chart, diagram, group or undecodable image.
 * It occupies the shape's real position, so the slide keeps its composition instead of
 * appearing to have lost content.
 */
@Composable
private fun UnsupportedDecorationView(label: String, modifier: Modifier) {
    val colors = KernTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(KernRadius.innerSmall))
            .background(colors.sunken.copy(alpha = 0.35f))
            .drawBehind {
                drawRect(
                    color = colors.borderSoft,
                    style = Stroke(
                        width = Hairline,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(PlaceholderDash, PlaceholderDash)),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = KernType.meta.copy(fontSize = DecorationLabelSize),
            color = colors.textDim,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(6.dp).fillMaxSize(),
        )
    }
}
