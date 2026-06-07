package dev.kern.editors.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class EpubDocumentTest {

    private val container = """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>
    """.trimIndent()

    private val opf = """
        <?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Test Book</dc:title></metadata>
          <manifest>
            <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
            <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
            <item id="c2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine toc="ncx"><itemref idref="c1"/><itemref idref="c2"/></spine>
        </package>
    """.trimIndent()

    private val ncx = """
        <?xml version="1.0"?>
        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
          <navMap>
            <navPoint id="n1"><navLabel><text>First Chapter</text></navLabel><content src="ch1.xhtml"/></navPoint>
            <navPoint id="n2"><navLabel><text>Second Chapter</text></navLabel><content src="ch2.xhtml"/></navPoint>
          </navMap>
        </ncx>
    """.trimIndent()

    private val ch1 = """
        <?xml version="1.0"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><head><title>c1</title></head>
        <body><h1>Chapter One</h1><p>Hello world.</p><p>Second para.</p></body></html>
    """.trimIndent()

    private val ch2 = """
        <?xml version="1.0"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><head><title>c2</title></head>
        <body><p>Chapter two text.</p></body></html>
    """.trimIndent()

    private fun standardEntries(): List<Pair<String, ByteArray>> = listOf(
        "mimetype" to "application/epub+zip".toByteArray(),
        "META-INF/container.xml" to container.toByteArray(),
        "OEBPS/content.opf" to opf.toByteArray(),
        "OEBPS/toc.ncx" to ncx.toByteArray(),
        "OEBPS/ch1.xhtml" to ch1.toByteArray(),
        "OEBPS/ch2.xhtml" to ch2.toByteArray(),
    )

    private fun zipBytes(entries: List<Pair<String, ByteArray>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((name, data) in entries) {
                if (name == "mimetype") {
                    zos.putNextEntry(ZipEntry("mimetype").apply {
                        method = ZipEntry.STORED
                        size = data.size.toLong()
                        compressedSize = data.size.toLong()
                        crc = CRC32().apply { update(data) }.value
                    })
                } else {
                    zos.putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
                }
                zos.write(data)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun read_parsesTitleSpineTocAndBlocks() {
        val parsed = EpubDocument.read(zipBytes(standardEntries()))
        assertEquals("Test Book", parsed.title)
        assertEquals(2, parsed.chapters.size)
        assertEquals("First Chapter", parsed.chapters[0].title)
        assertEquals("Second Chapter", parsed.chapters[1].title)

        val blocks = parsed.chapters[0].blocks
        assertEquals("Chapter One", blocks[0].text)
        assertEquals(EpubDocument.Style.TITLE, blocks[0].style)
        assertEquals("Hello world.", blocks[1].text)
        assertEquals(EpubDocument.Style.BODY, blocks[1].style)
        assertEquals("Second para.", blocks[2].text)
    }

    @Test
    fun applyEdits_changesOnlyTheEditedBlockAndKeepsMimetypeStoredFirst() {
        val edited = EpubDocument.applyEditsAndSerialize(
            zipBytes(standardEntries()),
            mapOf((0 to 1) to "Hello, edited."),
        )

        val parsed = EpubDocument.read(edited)
        assertEquals("Hello, edited.", parsed.chapters[0].blocks[1].text)
        assertEquals("Chapter One", parsed.chapters[0].blocks[0].text)      // untouched block
        assertEquals("Chapter two text.", parsed.chapters[1].blocks[0].text) // untouched chapter

        // mimetype must remain the first entry and STORED for a valid EPUB.
        ZipInputStream(ByteArrayInputStream(edited)).use { zis ->
            val first = requireNotNull(zis.nextEntry) { "empty archive" }
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED.toLong(), first.method.toLong())
            assertEquals("application/epub+zip", String(zis.readBytes()))
        }
    }

    @Test
    fun read_rejectsDrmProtectedEpub() {
        val drmXml = """
            <?xml version="1.0"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <EncryptedData xmlns="http://www.w3.org/2001/04/xmlenc#">
                <EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes256-cbc"/>
              </EncryptedData>
            </encryption>
        """.trimIndent()
        val drm = zipBytes(standardEntries() + ("META-INF/encryption.xml" to drmXml.toByteArray()))
        try {
            EpubDocument.read(drm)
            fail("expected EpubException for DRM-protected EPUB")
        } catch (e: EpubDocument.EpubException) {
            assertTrue(e.message!!.contains("DRM"))
        }
    }
}
