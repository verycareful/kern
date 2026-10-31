package dev.kern.editors.text

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** A text file must save back byte for byte unless its text was edited. */
class TextDocumentTest {

    private fun roundTrip(bytes: ByteArray) {
        val doc = TextDocument.decode(bytes)
        assertArrayEquals(bytes, doc.encode())
    }

    @Test
    fun utf8_withoutBom_roundTrips() = roundTrip("# Title\n\nCafé 😀 naïve\n".toByteArray(Charsets.UTF_8))

    @Test
    fun utf8_withBom_keepsTheBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hello\n".toByteArray()
        val doc = TextDocument.decode(bytes)
        assertEquals("hello\n", doc.text)
        assertArrayEquals(bytes, doc.encode())
    }

    @Test
    fun utf16le_withBom_decodesAndRoundTrips() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "a\r\nb".toByteArray(Charsets.UTF_16LE)
        val doc = TextDocument.decode(bytes)
        assertEquals("a\nb", doc.text)
        assertArrayEquals(bytes, doc.encode())
    }

    @Test
    fun crlf_isShownAsLf_andRestoredOnSave() {
        val bytes = "one\r\ntwo\r\n".toByteArray()
        val doc = TextDocument.decode(bytes)
        assertEquals("one\ntwo\n", doc.text)
        assertEquals(TextDocument.LineEnding.CRLF, doc.lineEnding)
        assertArrayEquals("one\r\ntwo\r\nthree\r\n".toByteArray(), doc.encode("one\ntwo\nthree\n"))
    }

    @Test
    fun oldMacCr_isShownAsLf_andRestoredOnSave() {
        val doc = TextDocument.decode("a\rb\r".toByteArray())
        assertEquals("a\nb\n", doc.text)
        assertArrayEquals("a\rb\r".toByteArray(), doc.encode())
    }

    @Test
    fun invalidUtf8_fallsBackToLatin1_andKeepsEveryByte() {
        val bytes = byteArrayOf('h'.code.toByte(), 0xE9.toByte(), 'l'.code.toByte(), 0xFF.toByte(), '\n'.code.toByte())
        val doc = TextDocument.decode(bytes)
        assertEquals(Charsets.ISO_8859_1, doc.charset)
        assertEquals("hélÿ\n", doc.text)
        assertArrayEquals(bytes, doc.encode())
    }

    @Test
    fun emptyFile_roundTrips() = roundTrip(ByteArray(0))

    @Test
    fun lineEnding_followsTheMajority() {
        assertEquals(TextDocument.LineEnding.LF, TextDocument.detectLineEnding("a\nb\r\nc\n"))
        assertEquals(TextDocument.LineEnding.CRLF, TextDocument.detectLineEnding("a\r\nb\r\nc\n"))
        assertEquals(TextDocument.LineEnding.LF, TextDocument.detectLineEnding("no breaks"))
    }

    @Test
    fun noTrailingNewline_staysWithout() = roundTrip("last line".toByteArray())
}
