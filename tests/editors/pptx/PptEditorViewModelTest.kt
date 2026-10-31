package dev.kern.editors.pptx

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import dev.kern.editors.pptx.PptDocument.RunStyle
import dev.kern.editors.pptx.PptDocument.ShapeBounds
import dev.kern.editors.pptx.PptDocument.ShapeRun
import dev.kern.editors.pptx.PptEditorViewModel.DecorationState
import dev.kern.editors.pptx.PptEditorViewModel.ShapeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The change tracking the editor's save is built on: a shape goes into the writer's
 * edit maps only when its text, position or anchor differs from what the file held.
 * The writer's half (an unlisted shape keeps its markup byte for byte) is covered in
 * [PptDocumentTest].
 */
class PptEditorViewModelTest {

    private val bounds = ShapeBounds(10f, 20f, 300f, 60f)

    /** A shape as the editor builds it on load: saved state equal to current state. */
    private fun loaded(): ShapeState {
        val text = PptRichText.toAnnotated(
            listOf(ShapeRun("Hello", RunStyle(bold = true)), ShapeRun(" world")),
            listOf(TextAlign.Center),
        )
        return ShapeState(
            textValue = TextFieldValue(text),
            bounds = bounds,
            savedText = text,
            savedBounds = bounds,
            anchor = "t",
        )
    }

    @Test
    fun loadedShape_isUnchanged() {
        val shape = loaded()
        assertFalse(shape.textChanged)
        assertFalse(shape.boundsChanged)
        assertFalse(shape.anchorChanged)
    }

    @Test
    fun movingTheCaret_doesNotChangeTheShape() {
        val shape = loaded()
        shape.textValue = shape.textValue.copy(selection = TextRange(3))
        assertFalse(shape.textChanged)
    }

    @Test
    fun aKeystrokeReportingTheSameText_doesNotChangeTheShape() {
        val shape = loaded()
        val merged = PptRichText.mergeEdit(shape.textValue.annotatedString, shape.textValue.text)
        shape.textValue = shape.textValue.copy(annotatedString = merged)
        assertFalse(shape.textChanged)
    }

    @Test
    fun typing_changesTheText() {
        val shape = loaded()
        val merged = PptRichText.mergeEdit(shape.textValue.annotatedString, "Hello world!")
        shape.textValue = shape.textValue.copy(annotatedString = merged)
        assertTrue(shape.textChanged)
        assertFalse(shape.boundsChanged)
    }

    @Test
    fun restyling_changesTheText() {
        val shape = loaded()
        shape.textValue = PptRichText.restyleRange(shape.textValue, 6, 11) { it.copy(italic = true) }
        assertTrue(shape.textChanged)
    }

    @Test
    fun realigning_changesTheText() {
        val shape = loaded()
        shape.textValue = PptRichText.alignParagraphs(shape.textValue, TextAlign.Right)
        assertTrue(shape.textChanged)
    }

    @Test
    fun moving_changesOnlyTheBounds() {
        val shape = loaded()
        shape.bounds = bounds.copy(x = 50f)
        assertTrue(shape.boundsChanged)
        assertFalse(shape.textChanged)
        shape.bounds = bounds
        assertFalse("moved back to where the file has it", shape.boundsChanged)
    }

    @Test
    fun anchor_changesOnlyTheAnchor() {
        val shape = loaded()
        shape.anchor = "b"
        assertTrue(shape.anchorChanged)
        assertFalse(shape.textChanged)
    }

    @Test
    fun aShapeTheFileDoesNotHave_isWrittenInFull() {
        val shape = ShapeState(TextFieldValue("Text box"), bounds)
        assertTrue(shape.textChanged)
        assertTrue(shape.boundsChanged)
    }

    @Test
    fun copy_keepsTheSavedStateSoADuplicateIsNotRewritten() {
        val copy = loaded().copy()
        assertFalse(copy.textChanged)
        assertFalse(copy.boundsChanged)
        assertFalse(copy.anchorChanged)
    }

    @Test
    fun copy_ofAnEditedShape_staysEdited() {
        val shape = loaded()
        shape.bounds = bounds.copy(y = 90f)
        shape.anchor = "ctr"
        val copy = shape.copy()
        assertTrue(copy.boundsChanged)
        assertTrue(copy.anchorChanged)
    }

    @Test
    fun decoration_changesOnlyWhenMoved() {
        val decoration = PptDocument.UnsupportedDecoration("Chart or diagram", bounds)
        val state = DecorationState(decoration, bounds)
        assertFalse(state.boundsChanged)
        state.bounds = bounds.copy(width = 120f)
        assertTrue(state.boundsChanged)
        assertTrue("a copy keeps the move", state.copy().boundsChanged)
    }
}
