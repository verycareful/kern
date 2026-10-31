package dev.kern.editors.text

import dev.kern.shared.DocumentFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which files the text editor opens with the Markdown preview. */
class TextEditorRoutingTest {

    @Test
    fun markdownFormat_alwaysPreviews() {
        assertTrue(TextEditorViewModel.isMarkdown(DocumentFormat.MARKDOWN, "document"))
    }

    @Test
    fun textFormat_previewsWhenTheNameSaysMarkdown() {
        assertTrue(TextEditorViewModel.isMarkdown(DocumentFormat.TEXT, "README.md"))
        assertTrue(TextEditorViewModel.isMarkdown(DocumentFormat.TEXT, "notes.MARKDOWN"))
        assertFalse(TextEditorViewModel.isMarkdown(DocumentFormat.TEXT, "notes.txt"))
        assertFalse(TextEditorViewModel.isMarkdown(DocumentFormat.TEXT, "guide.rst"))
        assertFalse(TextEditorViewModel.isMarkdown(DocumentFormat.TEXT, "no-extension"))
    }
}
