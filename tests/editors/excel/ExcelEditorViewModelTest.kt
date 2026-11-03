package dev.kern.editors.excel

import android.net.Uri
import android.os.Looper
import dev.kern.shared.find.CellRef
import dev.kern.shared.find.FindMark
import java.io.ByteArrayOutputStream
import java.io.File
import org.apache.poi.xssf.usermodel.XSSFWorkbook
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

/** Find and replace through the spreadsheet ViewModel: every sheet, and formula cells left alone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExcelEditorViewModelTest {

    private lateinit var dir: File

    @Before
    fun makeDir() {
        dir = File("build/tmp/excel-editor-vm-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun removeDir() {
        dir.deleteRecursively()
    }

    /**
     * Sheet "One": A1 "cat", B1 the formula LOWER("CAT"), A2 "cat cat". Sheet "Two": A1 "cat".
     * The grid shows a formula cell's formula, so a search ignoring case matches B1 too.
     */
    private fun open(): ExcelEditorViewModel {
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { wb ->
            val one = wb.createSheet("One")
            one.createRow(0).also { row ->
                row.createCell(0).setCellValue("cat")
                row.createCell(1).also { it.cellFormula = "LOWER(\"CAT\")" }
            }
            one.createRow(1).createCell(0).setCellValue("cat cat")
            wb.createSheet("Two").createRow(0).createCell(0).setCellValue("cat")
            wb.creationHelper.createFormulaEvaluator().evaluateAll()
            wb.write(out)
        }
        val file = File(dir, "book.xlsx").apply { writeBytes(out.toByteArray()) }
        val vm = ExcelEditorViewModel(RuntimeEnvironment.getApplication())
        vm.start(Uri.fromFile(file.absoluteFile).toString())
        waitFor { !vm.loading }
        vm.find.open()
        vm.find.matchCase = false
        vm.find.query = "cat"
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

    @Test
    fun find_searchesEverySheetIncludingFormulas() {
        val vm = open()
        assertEquals(5, vm.matchCount)
        assertEquals(CellRef(0, 0, 0), vm.currentCell)
        assertEquals(FindMark.CURRENT, vm.cellMark(0, 0, 0))
        assertEquals(FindMark.MATCH, vm.cellMark(0, 0, 1))
        assertEquals(FindMark.NONE, vm.cellMark(0, 5, 5))
        repeat(4) { vm.find.next(vm.matchCount) }
        assertEquals(CellRef(1, 0, 0), vm.currentCell)
    }

    @Test
    fun replaceCurrent_refusesAFormulaCell() {
        val vm = open()
        vm.find.replacement = "owl"
        vm.find.next(vm.matchCount)
        assertEquals(CellRef(0, 0, 1), vm.currentCell)
        vm.replaceCurrent()
        assertFalse(vm.dirty)
        assertTrue(vm.find.message!!.isError)
    }

    @Test
    fun replaceAll_skipsFormulasAndReachesOtherSheets() {
        val vm = open()
        vm.find.replacement = "owl"
        vm.replaceAll()
        assertTrue(vm.dirty)
        assertEquals("Replaced 4 matches, skipped 1 formula cell", vm.find.message!!.text)
        assertEquals(listOf("owl", "LOWER(\"CAT\")"), vm.rows[0])
        assertEquals("owl owl", vm.rows[1][0])
        vm.selectSheet(1)
        assertEquals("owl", vm.rows[0][0])
    }

    @Test
    fun formulaCell_followsAnInsertedRowAndColumn() {
        val vm = open()
        vm.select(0, 0)
        vm.addRow()
        vm.addColumn()
        vm.find.replacement = "owl"
        vm.replaceAll()
        assertEquals("Replaced 4 matches, skipped 1 formula cell", vm.find.message!!.text)
        assertEquals("LOWER(\"CAT\")", vm.rows[0][2])
    }

    @Test
    fun editingAFormulaCellByHand_makesItReplaceable() {
        val vm = open()
        vm.select(0, 1)
        vm.editSelected("cat")
        vm.find.replacement = "owl"
        vm.replaceAll()
        assertEquals("Replaced 5 matches", vm.find.message!!.text)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 5L
    }
}
