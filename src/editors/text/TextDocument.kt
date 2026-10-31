package dev.kern.editors.text

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * A plain text file split into the text the editor shows and the byte-level
 * details needed to write it back as it came: the character set, a leading byte
 * order mark, and the line separator. The editor always works with `\n`; [encode]
 * restores the file's own separator, so an unedited file saves byte for byte.
 */
data class TextDocument(
    val text: String,
    val charset: Charset,
    val bom: ByteArray,
    val lineEnding: LineEnding,
) {
    enum class LineEnding(val separator: String) { LF("\n"), CRLF("\r\n"), CR("\r") }

    /** Bytes for [editedText] in this document's encoding, separator and BOM. */
    fun encode(editedText: String = text): ByteArray {
        val restored = if (lineEnding == LineEnding.LF) editedText else editedText.replace("\n", lineEnding.separator)
        return bom + restored.toByteArray(charset)
    }

    override fun equals(other: Any?): Boolean =
        other is TextDocument && text == other.text && charset == other.charset &&
            bom.contentEquals(other.bom) && lineEnding == other.lineEnding

    override fun hashCode(): Int = ((text.hashCode() * 31 + charset.hashCode()) * 31 + bom.contentHashCode()) * 31 +
        lineEnding.hashCode()

    companion object {
        private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        private val UTF16LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        private val UTF16BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

        /**
         * Decodes [bytes]. A BOM names the encoding; without one the bytes are read
         * as UTF-8 when they are valid UTF-8 and as ISO-8859-1 otherwise, which maps
         * every byte to one character, so even a file in an unknown single-byte
         * encoding survives an edit unchanged outside the edited text.
         */
        fun decode(bytes: ByteArray): TextDocument {
            val (charset, bom) = when {
                bytes.startsWith(UTF8_BOM) -> Charsets.UTF_8 to UTF8_BOM
                bytes.startsWith(UTF16LE_BOM) -> Charsets.UTF_16LE to UTF16LE_BOM
                bytes.startsWith(UTF16BE_BOM) -> Charsets.UTF_16BE to UTF16BE_BOM
                isValidUtf8(bytes) -> Charsets.UTF_8 to ByteArray(0)
                else -> Charsets.ISO_8859_1 to ByteArray(0)
            }
            val raw = String(bytes, bom.size, bytes.size - bom.size, charset)
            val ending = detectLineEnding(raw)
            val text = when (ending) {
                LineEnding.LF -> raw
                LineEnding.CRLF -> raw.replace("\r\n", "\n")
                LineEnding.CR -> raw.replace('\r', '\n')
            }
            return TextDocument(text, charset, bom, ending)
        }

        /** The most common separator in [raw]; `\n` when the text has no line break. */
        internal fun detectLineEnding(raw: String): LineEnding {
            var crlf = 0
            var lf = 0
            var cr = 0
            var i = 0
            while (i < raw.length) {
                when (raw[i]) {
                    '\r' -> if (i + 1 < raw.length && raw[i + 1] == '\n') { crlf++; i++ } else cr++
                    '\n' -> lf++
                }
                i++
            }
            return when {
                crlf > lf && crlf >= cr -> LineEnding.CRLF
                cr > lf && cr > crlf -> LineEnding.CR
                else -> LineEnding.LF
            }
        }

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

        private fun isValidUtf8(bytes: ByteArray): Boolean = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            true
        } catch (_: CharacterCodingException) {
            false
        }
    }
}
