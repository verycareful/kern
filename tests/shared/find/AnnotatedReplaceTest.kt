package dev.kern.shared.find

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replacing inside styled text without losing the styles. */
class AnnotatedReplaceTest {

    private val bold = SpanStyle(fontWeight = FontWeight.Bold)
    private val italic = SpanStyle(fontStyle = FontStyle.Italic)

    private fun finder(query: String, regex: Boolean = false) =
        (FindQuery.compile(query, FindOptions(regex = regex)) as FindQuery.Ready).finder

    /** "plain BOLD plain" with BOLD in bold at 6..10. */
    private val sample = buildAnnotatedString {
        append("plain ")
        pushStyle(bold); append("BOLD"); pop()
        append(" plain")
    }

    private fun AnnotatedString.spans() = spanStyles.map { Triple(it.item, it.start, it.end) }

    @Test
    fun replacingAStyledWord_keepsItsStyle() {
        val out = sample.replaceKeepingStyle(6, 10, "strong")
        assertEquals("plain strong plain", out.text)
        assertEquals(listOf(Triple(bold, 6, 12)), out.spans())
    }

    @Test
    fun stylesAfterTheReplacement_shiftByTheChangeInLength() {
        val out = sample.replaceKeepingStyle(0, 5, "x")
        assertEquals("x BOLD plain", out.text)
        assertEquals(listOf(Triple(bold, 2, 6)), out.spans())
    }

    @Test
    fun stylesBeforeTheReplacement_stayPut() {
        val out = sample.replaceKeepingStyle(11, 16, "end")
        assertEquals("plain BOLD end", out.text)
        assertEquals(listOf(Triple(bold, 6, 10)), out.spans())
    }

    @Test
    fun replacingAcrossAStyleBoundary_takesTheFirstCharactersStyle() {
        // "D pl" starts inside the bold run.
        val boldFirst = sample.replaceKeepingStyle(9, 13, "Z")
        assertEquals("plain BOLZain", boldFirst.text)
        assertEquals(listOf(Triple(bold, 6, 10)), boldFirst.spans())
        // "n B" starts in plain text, so the replacement is plain and bold resumes after it.
        val plainFirst = sample.replaceKeepingStyle(4, 7, "Z")
        assertEquals("plaiZOLD plain", plainFirst.text)
        assertEquals(listOf(Triple(bold, 5, 8)), plainFirst.spans())
    }

    @Test
    fun aStyleCoveringOnlyLaterReplacedCharacters_disappears() {
        val text = buildAnnotatedString {
            append("ab")
            pushStyle(italic); append("c"); pop()
            append("d")
        }
        val out = text.replaceKeepingStyle(1, 3, "X")
        assertEquals("aXd", out.text)
        assertEquals(emptyList<Triple<SpanStyle, Int, Int>>(), out.spans())
    }

    @Test
    fun paragraphStylesAndStringAnnotations_followTheText() {
        val text = buildAnnotatedString {
            append("left\nright")
            addStyle(ParagraphStyle(textAlign = TextAlign.Center), 5, 10)
            addStringAnnotation("fontFamily", "Sora", 5, 10)
        }
        val out = text.replaceKeepingStyle(0, 4, "L")
        assertEquals("L\nright", out.text)
        assertEquals(listOf(2 to 7), out.paragraphStyles.map { it.start to it.end })
        assertEquals(TextAlign.Center, out.paragraphStyles.single().item.textAlign)
        val note = out.getStringAnnotations(0, out.length).single()
        assertEquals(listOf("fontFamily", "Sora", 2, 7), listOf(note.tag, note.item, note.start, note.end))
    }

    @Test
    fun replaceAll_keepsEachMatchesStyle() {
        val text = buildAnnotatedString {
            pushStyle(bold); append("cat"); pop()
            append(" and ")
            pushStyle(italic); append("cat"); pop()
        }
        val out = finder("cat").replaceAll(text, "tiger").getOrThrow()
        assertEquals(2, out.count)
        assertEquals("tiger and tiger", out.value.text)
        assertEquals(listOf(Triple(bold, 0, 5), Triple(italic, 10, 15)), out.value.spans())
    }

    @Test
    fun replaceAt_expandsGroupsAndKeepsStyle() {
        val f = finder("(B)(OLD)", regex = true)
        val out = f.replaceAt(sample, f.findAll(sample.text).single(), "\$2\$1").getOrThrow()
        assertEquals("plain OLDB plain", out.text)
        assertEquals(listOf(Triple(bold, 6, 10)), out.spans())
    }

    @Test
    fun replaceAll_badReplacementFailsAndChangesNothing() {
        val result = finder("(B)", regex = true).replaceAll(sample, "\$9")
        assertTrue(result.exceptionOrNull() is InvalidReplacementException)
    }

    @Test
    fun emptyReplacement_deletesTheMatch() {
        val out = sample.replaceKeepingStyle(5, 11, "")
        assertEquals("plainplain", out.text)
        assertEquals(emptyList<Triple<SpanStyle, Int, Int>>(), out.spans())
    }
}
