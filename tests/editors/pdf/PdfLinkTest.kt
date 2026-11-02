package dev.kern.editors.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which PDF link a tap lands on. */
class PdfLinkTest {

    private val web = PdfLink.External(listOf(PageRect(0.1f, 0.1f, 0.5f, 0.15f)), "https://example.org")
    private val jump = PdfLink.GoTo(listOf(PageRect(0.1f, 0.2f, 0.4f, 0.25f), PageRect(0.1f, 0.25f, 0.2f, 0.3f)), 2)
    private val links = listOf(web, jump)

    @Test
    fun aTapInsideALinksBox_findsThatLink() {
        assertEquals(web, links.at(PagePoint(0.3f, 0.12f)))
        assertEquals(jump, links.at(PagePoint(0.15f, 0.28f)))
    }

    @Test
    fun aTapOutsideEveryBox_findsNothing() {
        assertNull(links.at(PagePoint(0.9f, 0.9f)))
        assertNull(links.at(PagePoint(0.3f, 0.28f)))
    }
}
