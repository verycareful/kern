package dev.kern.editors.epub

import android.net.Uri
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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

/** Find and replace through the EPUB ViewModel: every chapter is searched and edited. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpubEditorViewModelTest {

    private lateinit var dir: File

    @Before
    fun makeDir() {
        dir = File("build/tmp/epub-editor-vm-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun removeDir() {
        dir.deleteRecursively()
    }

    private fun chapter(body: String) =
        """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>c</title></head><body>$body</body></html>"""

    /** Chapter 1: "A cat." and "No match." Chapter 2: "cat and cat". */
    private fun book(): ByteArray {
        val entries = listOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "OEBPS/content.opf" to """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Book</dc:title></metadata><manifest><item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="ch2.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""",
            "OEBPS/ch1.xhtml" to chapter("<p>A cat.</p><p>No match.</p>"),
            "OEBPS/ch2.xhtml" to chapter("<p>cat and cat</p>"),
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, text) in entries) {
                val data = text.toByteArray()
                zip.putNextEntry(
                    ZipEntry(name).apply {
                        if (name == "mimetype") {
                            method = ZipEntry.STORED
                            size = data.size.toLong()
                            compressedSize = data.size.toLong()
                            crc = CRC32().apply { update(data) }.value
                        }
                    },
                )
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun open(): EpubEditorViewModel {
        val file = File(dir, "book.epub").apply { writeBytes(book()) }
        val vm = EpubEditorViewModel(RuntimeEnvironment.getApplication())
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

    private fun texts(vm: EpubEditorViewModel, chapter: Int): List<String> {
        vm.goToChapter(chapter)
        return vm.currentBlocks.map { vm.blockText(it) }
    }

    @Test
    fun find_searchesEveryChapterInOrder() {
        val vm = open()
        assertEquals(3, vm.matchCount)
        assertEquals(listOf(0, 1, 1), vm.matches.map { it.key.chapter })
    }

    @Test
    fun replaceCurrent_editsAChapterThatIsNotOnScreen() {
        val vm = open()
        vm.find.next(vm.matchCount)
        assertEquals(0, vm.currentChapter)
        vm.replaceCurrent()
        assertTrue(vm.dirty)
        assertEquals("owl and cat", texts(vm, 1).single())
        assertEquals("A cat.", texts(vm, 0).first())
    }

    @Test
    fun replaceAll_coversEveryChapter() {
        val vm = open()
        vm.replaceAll()
        assertEquals("Replaced 3 matches", vm.find.message!!.text)
        assertEquals("A owl.", texts(vm, 0).first())
        assertEquals("owl and owl", texts(vm, 1).single())
    }

    @Test
    fun replacingBackToTheOriginal_clearsTheEdit() {
        val vm = open()
        vm.replaceCurrent()
        assertTrue(vm.dirty)
        vm.find.query = "owl"
        vm.find.replacement = "cat"
        vm.replaceCurrent()
        assertFalse(vm.dirty)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 5L
    }
}
