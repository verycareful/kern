package dev.kern.editors.word

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.text.font.FontWeight
import java.io.ByteArrayOutputStream
import java.io.File
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Find and replace through the Word ViewModel: paragraphs keep their formatting, tables are searched too. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordEditorViewModelTest {

    private lateinit var dir: File

    @Before
    fun makeDir() {
        dir = File("build/tmp/word-editor-vm-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun removeDir() {
        dir.deleteRecursively()
    }

    /** Paragraph 0: "The " + bold "cat" + " sat". Block 1: a 1x2 table "cat", "dog". Paragraph 2: "cat cat". */
    private fun open(): WordEditorViewModel {
        val out = ByteArrayOutputStream()
        XWPFDocument().use { doc ->
            doc.createParagraph().also { p ->
                p.createRun().setText("The ")
                p.createRun().also { it.setText("cat"); it.isBold = true }
                p.createRun().setText(" sat")
            }
            doc.createTable(1, 2).getRow(0).also { it.getCell(0).setText("cat"); it.getCell(1).setText("dog") }
            doc.createParagraph().createRun().setText("cat cat")
            doc.write(out)
        }
        val file = File(dir, "doc.docx").apply { writeBytes(out.toByteArray()) }
        val vm = WordEditorViewModel(RuntimeEnvironment.getApplication())
        vm.start(Uri.fromFile(file.absoluteFile).toString())
        waitFor { !vm.loading }
        vm.find.open()
        vm.find.query = "cat"
        vm.find.replacement = "tiger"
        return vm
    }

    private fun waitFor(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
    }

    private fun isBold(vm: WordEditorViewModel, block: Int, start: Int, end: Int): Boolean =
        vm.fields.getValue(block).annotatedString.spanStyles.any {
            it.item.fontWeight == FontWeight.Bold && it.start <= start && it.end >= end
        }

    @Test
    fun find_coversParagraphsAndTableCellsInOrder() {
        val vm = open()
        assertEquals(4, vm.matchCount)
        assertEquals(listOf(0, 1, 2, 2), vm.matches.map { it.key.block })
        assertTrue(vm.matches[1].key.isCell)
    }

    @Test
    fun replaceCurrent_keepsTheReplacedWordsFormatting() {
        val vm = open()
        vm.replaceCurrent()
        assertEquals("The tiger sat", vm.fields.getValue(0).text)
        assertTrue(isBold(vm, 0, 4, 9))
        assertFalse(isBold(vm, 0, 0, 4))
        assertTrue(vm.dirty)
        assertEquals(3, vm.matchCount)
    }

    @Test
    fun replaceAll_keepsFormattingReachesTablesAndUndoesInOneStep() {
        val vm = open()
        vm.replaceAll()
        assertEquals("The tiger sat", vm.fields.getValue(0).text)
        assertTrue(isBold(vm, 0, 4, 9))
        assertEquals(listOf(listOf("tiger", "dog")), vm.tableStates.getValue(1))
        assertEquals("tiger tiger", vm.fields.getValue(2).text)
        assertEquals("Replaced 4 matches", vm.find.message!!.text)
        vm.undo()
        assertEquals("The cat sat", vm.fields.getValue(0).text)
        assertEquals(listOf(listOf("cat", "dog")), vm.tableStates.getValue(1))
        assertEquals("cat cat", vm.fields.getValue(2).text)
    }

    @Test
    fun replaceCurrent_inATableCell() {
        val vm = open()
        vm.find.next(vm.matchCount)
        vm.replaceCurrent()
        assertEquals(listOf(listOf("tiger", "dog")), vm.tableStates.getValue(1))
    }

    @Test
    fun matchCase_isOnByDefault() {
        val vm = open()
        vm.find.query = "Cat"
        assertEquals(0, vm.matchCount)
        vm.find.matchCase = false
        assertEquals(4, vm.matchCount)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 5L
    }
}
