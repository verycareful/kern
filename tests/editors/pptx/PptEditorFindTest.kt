package dev.kern.editors.pptx

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.text.font.FontWeight
import java.io.ByteArrayOutputStream
import java.io.File
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Find and replace through the PowerPoint ViewModel: every slide, formatting kept, one undo step. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PptEditorFindTest {

    private lateinit var dir: File

    @Before
    fun makeDir() {
        dir = File("build/tmp/ppt-editor-vm-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun removeDir() {
        dir.deleteRecursively()
    }

    /** Slide 1: one text box, "The " + bold "cat". Slide 2: "no match". Slide 3: "cat cat". */
    private fun open(): PptEditorViewModel {
        val out = ByteArrayOutputStream()
        XMLSlideShow().use { show ->
            show.createSlide().createTextBox().addNewTextParagraph().also { p ->
                p.addNewTextRun().setText("The ")
                p.addNewTextRun().also { it.setText("cat"); it.isBold = true }
            }
            show.createSlide().createTextBox().addNewTextParagraph().addNewTextRun().setText("no match")
            show.createSlide().createTextBox().addNewTextParagraph().addNewTextRun().setText("cat cat")
            show.write(out)
        }
        val file = File(dir, "deck.pptx").apply { writeBytes(out.toByteArray()) }
        val vm = PptEditorViewModel(RuntimeEnvironment.getApplication())
        vm.start(Uri.encode(Uri.fromFile(file.absoluteFile).toString()))
        waitFor { !vm.loading }
        vm.find.open()
        vm.find.query = "cat"
        vm.find.replacement = "owl"
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

    private fun text(vm: PptEditorViewModel, slide: Int) = vm.slideStates[slide].shapes.joinToString("|") { it.textValue.text }

    @Test
    fun find_searchesEverySlide_andRevealSwitchesSlide() {
        val vm = open()
        assertEquals(3, vm.matchCount)
        vm.find.next(vm.matchCount)
        vm.revealCurrentMatch()
        assertEquals(2, vm.currentSlide)
    }

    @Test
    fun replaceCurrent_keepsBold() {
        val vm = open()
        vm.replaceCurrent()
        val shape = vm.slideStates[0].shapes.first { it.textValue.text.isNotBlank() }
        // A new POI text box starts with an empty paragraph, so the text follows a newline.
        assertEquals("\nThe owl", shape.textValue.text)
        val owl = shape.textValue.text.indexOf("owl")
        assertTrue(shape.textValue.annotatedString.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == owl && it.end == owl + 3 })
        assertTrue(vm.dirty)
    }

    @Test
    fun replaceAll_coversEverySlideAndUndoesInOneStep() {
        val vm = open()
        vm.replaceAll()
        assertTrue(text(vm, 0).contains("The owl"))
        assertTrue(text(vm, 2).contains("owl owl"))
        assertEquals("Replaced 3 matches", vm.find.message!!.text)
        vm.undo()
        assertTrue(text(vm, 0).contains("The cat"))
        assertTrue(text(vm, 2).contains("cat cat"))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 5L
    }
}
