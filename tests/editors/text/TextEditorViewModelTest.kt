package dev.kern.editors.text

import android.net.Uri
import android.os.Looper
import androidx.compose.foundation.ExperimentalFoundationApi
import dev.kern.shared.DocumentFormat
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Load, edit, undo and save through the editor's ViewModel against a real file; which files get the preview. */
@OptIn(ExperimentalFoundationApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextEditorViewModelTest {

    private lateinit var dir: File

    @Before
    fun makeDir() {
        dir = File("build/tmp/text-editor-vm-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun removeDir() {
        dir.deleteRecursively()
    }

    private fun open(name: String, bytes: ByteArray, format: DocumentFormat): Pair<TextEditorViewModel, File> {
        val file = File(dir, name).apply { writeBytes(bytes) }
        val vm = TextEditorViewModel(RuntimeEnvironment.getApplication())
        vm.start(Uri.encode(Uri.fromFile(file.absoluteFile).toString()), format)
        waitFor { !vm.loading }
        return vm to file
    }

    /** Runs the main looper until [done] holds; the ViewModel's file work runs on background threads. */
    private fun waitFor(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
    }

    private fun save(vm: TextEditorViewModel) {
        var result: Boolean? = null
        vm.save { ok, _ -> result = ok }
        waitFor { result != null }
        assertEquals(true, result)
    }

    @Test
    fun markdownFile_opensInPreview_unchanged() {
        val (vm, _) = open("readme.md", "# Title\r\n\r\nBody\r\n".toByteArray(), DocumentFormat.MARKDOWN)
        assertEquals(null, vm.error)
        assertTrue(vm.markdown)
        assertEquals(TextEditorViewModel.Mode.PREVIEW, vm.mode)
        assertEquals("# Title\n\nBody\n", vm.state.text.toString())
        assertFalse(vm.dirty)
        waitFor { vm.preview != null }
        assertTrue(vm.preview!!.body.contains("<h1 id=\"title\">Title</h1>"))
    }

    @Test
    fun plainTextNamedMarkdown_getsThePreview_plainTextDoesNot() {
        assertTrue(open("notes.md", "x".toByteArray(), DocumentFormat.TEXT).first.markdown)
        val (plain, _) = open("notes.txt", "x".toByteArray(), DocumentFormat.TEXT)
        assertFalse(plain.markdown)
        assertEquals(TextEditorViewModel.Mode.EDIT, plain.mode)
    }

    @Test
    fun toolbarEdit_marksDirty_andSaveKeepsTheFilesLineEndings() {
        val (vm, file) = open("doc.md", "one\r\ntwo\r\n".toByteArray(), DocumentFormat.MARKDOWN)
        vm.showEditor()
        vm.apply { t, _, _ -> MarkdownFormatting.inline(t, 0, 3, MarkdownFormatting.Inline.BOLD) }
        assertEquals("**one**\ntwo\n", vm.state.text.toString())
        assertTrue(vm.dirty)
        save(vm)
        assertFalse(vm.dirty)
        assertArrayEquals("**one**\r\ntwo\r\n".toByteArray(), file.readBytes())
    }

    @Test
    fun toolbarEdit_canBeUndone_backToClean() {
        val (vm, _) = open("doc.md", "word".toByteArray(), DocumentFormat.MARKDOWN)
        vm.apply { t, _, _ -> MarkdownFormatting.inline(t, 0, 4, MarkdownFormatting.Inline.ITALIC) }
        assertTrue(vm.dirty)
        assertTrue(vm.state.undoState.canUndo)
        vm.undo()
        assertEquals("word", vm.state.text.toString())
        assertFalse(vm.dirty)
        assertTrue(vm.state.undoState.canRedo)
    }

    @Test
    fun freshlyLoadedFile_hasNothingToUndo() {
        val (vm, _) = open("a.txt", "text".toByteArray(), DocumentFormat.TEXT)
        assertFalse(vm.state.undoState.canUndo)
    }

    @Test
    fun preview_rendersTheEditedText() {
        val (vm, _) = open("doc.md", "first".toByteArray(), DocumentFormat.MARKDOWN)
        waitFor { vm.preview != null }
        vm.showEditor()
        vm.apply { t, _, _ -> MarkdownFormatting.heading(t, 0, 0) }
        vm.showPreview()
        waitFor { vm.preview?.body?.contains("<h1") == true }
        assertNotNull(vm.preview)
    }

    @Test
    fun missingFile_reportsAnError() {
        val vm = TextEditorViewModel(RuntimeEnvironment.getApplication())
        vm.start(Uri.encode(Uri.fromFile(File(dir, "absent.md").absoluteFile).toString()), DocumentFormat.MARKDOWN)
        waitFor { !vm.loading }
        assertNotNull(vm.error)
    }

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

    private fun openForFind(text: String): TextEditorViewModel =
        open("find.txt", text.toByteArray(), DocumentFormat.TEXT).first.also { it.find.open() }

    @Test
    fun find_listsSourceMatchesOnlyWhileOpen() {
        val vm = openForFind("cat Cat cat")
        vm.find.query = "cat"
        assertEquals(2, vm.matchCount)
        vm.find.matchCase = false
        assertEquals(3, vm.matchCount)
        vm.find.close()
        assertEquals(0, vm.matchCount)
    }

    @Test
    fun find_markdownPreviewCannotReplaceAndGreysTheToggles() {
        val (vm, _) = open("doc.md", "cat".toByteArray(), DocumentFormat.MARKDOWN)
        assertFalse(vm.canReplace)
        assertFalse(vm.supportsOptions)
        vm.showEditor()
        assertTrue(vm.canReplace)
        assertTrue(vm.supportsOptions)
    }

    @Test
    fun preview_countsWhatTheWebViewReportsWhileTheBarHasAQuery() {
        val (vm, _) = open("doc.md", "cat".toByteArray(), DocumentFormat.MARKDOWN)
        vm.find.query = "cat"
        assertEquals("", vm.previewQuery)
        vm.find.open()
        assertEquals("cat", vm.previewQuery)
        vm.onPreviewFindResult(4)
        assertEquals(4, vm.matchCount)
        vm.find.query = ""
        assertEquals(0, vm.matchCount)
    }

    @Test
    fun replaceCurrent_replacesOneMatchAsOneUndoStep() {
        val vm = openForFind("cat cat cat")
        vm.find.query = "cat"
        vm.find.replacement = "dog"
        vm.find.next(vm.matchCount)
        vm.replaceCurrent()
        assertEquals("cat dog cat", vm.state.text.toString())
        assertTrue(vm.dirty)
        assertEquals(2, vm.matchCount)
        vm.undo()
        assertEquals("cat cat cat", vm.state.text.toString())
        assertFalse(vm.dirty)
    }

    @Test
    fun replaceAll_replacesEveryMatchAsOneUndoStepAndReportsTheCount() {
        val vm = openForFind("a1 b22 c333")
        vm.find.regex = true
        vm.find.query = "([a-z])(\\d+)"
        vm.find.replacement = "\$2\$1"
        vm.replaceAll()
        assertEquals("1a 22b 333c", vm.state.text.toString())
        assertEquals("Replaced 3 matches", vm.find.message?.text)
        vm.undo()
        assertEquals("a1 b22 c333", vm.state.text.toString())
    }

    @Test
    fun badReplacement_changesNothingAndShowsTheError() {
        val vm = openForFind("ab ab")
        vm.find.regex = true
        vm.find.query = "(a)b"
        vm.find.replacement = "\$5"
        vm.replaceAll()
        assertEquals("ab ab", vm.state.text.toString())
        assertTrue(vm.find.message!!.isError)
        vm.replaceCurrent()
        assertEquals("ab ab", vm.state.text.toString())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 5L
    }
}
