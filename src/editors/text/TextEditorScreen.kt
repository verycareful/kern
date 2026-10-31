package dev.kern.editors.text

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kern.editors.markdown.MarkdownPreview
import dev.kern.shared.DocumentFormat
import dev.kern.shared.theme.KernColorScheme
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.PlexMonoFamily
import dev.kern.shared.ui.EditorChrome
import dev.kern.shared.ui.EditorToolbar
import dev.kern.shared.ui.KernIcons
import dev.kern.shared.ui.KernSegmented
import dev.kern.shared.ui.SegmentItem
import dev.kern.shared.ui.ToolbarButton
import dev.kern.shared.ui.ToolbarSeparator
import dev.kern.editors.text.MarkdownFormatting.Inline
import dev.kern.editors.text.MarkdownFormatting.LineMark

private const val MARKDOWN_MIME = "text/markdown"
private const val TEXT_MIME = "text/plain"

/**
 * Editor for Markdown and plain text files. Both edit the raw source; Markdown
 * also has a Preview mode that shows the rendered document, and a toolbar of
 * Markdown edits. Plain text (`.txt`, `.rst`, `.org`) gets the same editor with
 * undo and redo only.
 */
@Composable
fun TextEditorScreen(
    filePath: String?,
    format: DocumentFormat,
    vm: TextEditorViewModel = viewModel(),
) {
    LaunchedEffect(filePath) { vm.start(filePath, format) }
    ResizeForKeyboard()
    val hue = KernTheme.formatColor(if (vm.markdown) DocumentFormat.MARKDOWN else format)
    val editing = vm.mode == TextEditorViewModel.Mode.EDIT

    EditorChrome(
        title = vm.fileName.ifBlank { format.label },
        dirty = vm.dirty,
        loading = vm.loading,
        error = vm.error,
        hue = hue,
        onSave = vm::save,
        exportMimeType = if (vm.markdown) MARKDOWN_MIME else TEXT_MIME,
        exportFileName = vm.fileName.ifBlank { if (vm.markdown) "export.md" else "export.txt" },
        onExportToUri = vm::exportTo,
        toolbar = if (editing) ({ SourceToolbar(vm) }) else null,
    ) { modifier ->
        Column(modifier.background(KernTheme.colors.bg)) {
            if (vm.markdown) ModeSwitch(vm)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (editing) SourceEditor(vm) else PreviewPane(vm)
            }
        }
    }
}

@Composable
private fun ModeSwitch(vm: TextEditorViewModel) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        KernSegmented(
            items = listOf(SegmentItem("Edit", KernIcons.Pen), SegmentItem("Preview", KernIcons.Preview)),
            selectedIndex = if (vm.mode == TextEditorViewModel.Mode.EDIT) 0 else 1,
            onSelect = { if (it == 0) vm.showEditor() else vm.showPreview() },
        )
    }
}

@Composable
private fun SourceEditor(vm: TextEditorViewModel) {
    val colors = KernTheme.colors
    val highlight = remember(colors, vm.markdown) {
        if (vm.markdown) markdownHighlighting(colors) else null
    }
    BasicTextField(
        state = vm.state,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        textStyle = TextStyle(
            fontFamily = PlexMonoFamily,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = colors.text,
        ),
        cursorBrush = SolidColor(colors.accent),
        lineLimits = TextFieldLineLimits.MultiLine(),
        outputTransformation = highlight,
    )
}

@Composable
private fun PreviewPane(vm: TextEditorViewModel) {
    val rendered = vm.preview
    if (rendered == null) {
        Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = KernTheme.colors.accent)
        }
    } else {
        MarkdownPreview(rendered, KernTheme.colors, Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceToolbar(vm: TextEditorViewModel) {
    val undo = vm.state.undoState
    EditorToolbar(Modifier.imePadding()) {
        ToolbarButton(KernIcons.Undo, "Undo", vm::undo, enabled = undo.canUndo)
        ToolbarButton(KernIcons.Redo, "Redo", vm::redo, enabled = undo.canRedo)
        if (!vm.markdown) return@EditorToolbar
        ToolbarSeparator()
        ToolbarButton(KernIcons.Bold, "Bold", { vm.apply { t, s, e -> MarkdownFormatting.inline(t, s, e, Inline.BOLD) } })
        ToolbarButton(KernIcons.Italic, "Italic", { vm.apply { t, s, e -> MarkdownFormatting.inline(t, s, e, Inline.ITALIC) } })
        ToolbarButton(KernIcons.Strikethrough, "Strikethrough", {
            vm.apply { t, s, e -> MarkdownFormatting.inline(t, s, e, Inline.STRIKETHROUGH) }
        })
        ToolbarButton(KernIcons.Code, "Inline code", { vm.apply { t, s, e -> MarkdownFormatting.inline(t, s, e, Inline.CODE) } })
        ToolbarSeparator()
        ToolbarButton(KernIcons.Heading, "Heading", { vm.apply(MarkdownFormatting::heading) })
        ToolbarButton(KernIcons.Bullet, "Bulleted list", { vm.apply { t, s, e -> MarkdownFormatting.lineMark(t, s, e, LineMark.BULLET) } })
        ToolbarButton(KernIcons.NumberedList, "Numbered list", {
            vm.apply { t, s, e -> MarkdownFormatting.lineMark(t, s, e, LineMark.NUMBERED) }
        })
        ToolbarButton(KernIcons.Checklist, "Task list", { vm.apply { t, s, e -> MarkdownFormatting.lineMark(t, s, e, LineMark.TASK) } })
        ToolbarButton(KernIcons.Quote, "Quote", { vm.apply { t, s, e -> MarkdownFormatting.lineMark(t, s, e, LineMark.QUOTE) } })
        ToolbarSeparator()
        ToolbarButton(KernIcons.Link, "Link", { vm.apply(MarkdownFormatting::link) })
        ToolbarButton(KernIcons.Image, "Image", { vm.apply(MarkdownFormatting::image) })
        ToolbarButton(KernIcons.CodeBlock, "Code block", { vm.apply(MarkdownFormatting::codeBlock) })
        ToolbarButton(KernIcons.Table, "Table", { vm.apply(MarkdownFormatting::table) })
        ToolbarButton(KernIcons.Rule, "Horizontal rule", { vm.apply(MarkdownFormatting::horizontalRule) })
    }
}

/** Colours the Markdown syntax in the source; see [MarkdownHighlighter]. */
private fun markdownHighlighting(colors: KernColorScheme): OutputTransformation {
    val styles = MarkdownHighlighter.Kind.entries.associateWith { it.style(colors) }
    return OutputTransformation {
        for (span in MarkdownHighlighter.spans(asCharSequence())) {
            addStyle(styles.getValue(span.kind), span.start, span.end)
        }
    }
}

private fun MarkdownHighlighter.Kind.style(colors: KernColorScheme): SpanStyle = when (this) {
    MarkdownHighlighter.Kind.HEADING -> SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold)
    MarkdownHighlighter.Kind.STRONG -> SpanStyle(fontWeight = FontWeight.Bold)
    MarkdownHighlighter.Kind.EMPHASIS -> SpanStyle(fontStyle = FontStyle.Italic)
    MarkdownHighlighter.Kind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    MarkdownHighlighter.Kind.CODE, MarkdownHighlighter.Kind.CODE_BLOCK -> SpanStyle(color = colors.textMid, background = colors.sunken)
    MarkdownHighlighter.Kind.QUOTE -> SpanStyle(color = colors.textMid, fontStyle = FontStyle.Italic)
    MarkdownHighlighter.Kind.LIST_MARKER, MarkdownHighlighter.Kind.RULE -> SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold)
    MarkdownHighlighter.Kind.LINK, MarkdownHighlighter.Kind.URL -> SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)
    MarkdownHighlighter.Kind.MATH -> SpanStyle(color = colors.textMid)
    MarkdownHighlighter.Kind.HTML -> SpanStyle(color = colors.textDim)
}

/**
 * Resizes the window for the keyboard while this editor is open, so the toolbar
 * and the caret stay above it; the window's previous mode is restored on leaving.
 */
@Composable
private fun ResizeForKeyboard() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.softInputMode
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { window.setSoftInputMode(previous) }
    }
}
