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

/** Where a decoration goes when the file declares no geometry for it, in slide points. */
private val FallbackDecorationBounds = PptDocument.ShapeBounds(80f, 80f, 400f, 240f)

private val DecorationLabelSize = 13.sp
private val TableCellTextSize = 11.sp

/**
 * The read-only layer of a slide: pictures, tables, and outlines standing in for shapes
 * the editor cannot draw yet.
 *
 * Drawn beneath the editable text so a text box always wins a tap. [scale] is the canvas
 * scale, used to divide hairlines and keep their apparent weight constant as the slide
 * is zoomed.
 */
@Composable
fun SlideDecorationLayer(
    decorations: List<PptDocument.SlideDecoration>,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    if (decorations.isEmpty()) return
    Box(modifier) {
        decorations.forEach { decoration ->
            val bounds = decoration.bounds ?: FallbackDecorationBounds
            val placed = Modifier
                .offset(x = bounds.x.dp, y = bounds.y.dp)
                .size(width = bounds.width.dp, height = bounds.height.dp)
            when (decoration) {
                is PptDocument.PictureDecoration -> PictureDecorationView(decoration, scale, placed)
                is PptDocument.TableDecoration -> TableDecorationView(decoration, scale, placed)
                is PptDocument.UnsupportedDecoration -> UnsupportedDecorationView(decoration.label, scale, placed)
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
    scale: Float,
    modifier: Modifier,
) {
    val bitmap = remember(picture.bytes) {
        runCatching { BitmapFactory.decodeByteArray(picture.bytes, 0, picture.bytes.size)?.asImageBitmap() }
            .getOrNull()
    }
    if (bitmap == null) {
        // A format Android cannot decode, EMF and WMF being the common ones in a deck
        // authored on Windows. Saying so beats an empty rectangle.
        UnsupportedDecorationView("Unsupported image", scale, modifier)
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

/** A table, drawn as evenly divided rows of cell text. */
@Composable
private fun TableDecorationView(
    table: PptDocument.TableDecoration,
    scale: Float,
    modifier: Modifier,
) {
    val colors = KernTheme.colors
    val hairline = (1f / scale).dp
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(KernRadius.innerSmall))
            .background(colors.surface.copy(alpha = 0.85f))
            .drawBehind {
                drawRect(color = colors.borderSoft, style = Stroke(width = 1f / scale))
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
                                drawRect(color = colors.borderSoft.copy(alpha = 0.6f), style = Stroke(width = hairline.toPx()))
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
private fun UnsupportedDecorationView(label: String, scale: Float, modifier: Modifier) {
    val colors = KernTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(KernRadius.innerSmall))
            .background(colors.sunken.copy(alpha = 0.35f))
            .drawBehind {
                val dash = 6f / scale
                drawRect(
                    color = colors.borderSoft,
                    style = Stroke(
                        width = 1f / scale,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
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
