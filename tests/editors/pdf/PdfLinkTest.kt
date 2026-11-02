package dev.kern.editors.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which PDF link a tap lands on, and which links Kern offers to open in another app. */
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

    @Test
    fun webMailAndPhoneLinks_areOpenable() {
        assertTrue(PdfEditorViewModel.isOpenable("https://example.org/a"))
        assertTrue(PdfEditorViewModel.isOpenable("HTTP://example.org"))
        assertTrue(PdfEditorViewModel.isOpenable("mailto:someone@example.org"))
        assertTrue(PdfEditorViewModel.isOpenable("tel:+100"))
    }

    @Test
    fun otherSchemes_areNotOpenable() {
        assertFalse(PdfEditorViewModel.isOpenable("file:///sdcard/secret.txt"))
        assertFalse(PdfEditorViewModel.isOpenable("javascript:alert(1)"))
        assertFalse(PdfEditorViewModel.isOpenable("content://x/y"))
        assertFalse(PdfEditorViewModel.isOpenable("no-scheme"))
    }
}
