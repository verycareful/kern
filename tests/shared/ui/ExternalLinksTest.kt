package dev.kern.shared.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which links Kern offers to hand to another app, after the reader confirms. */
class ExternalLinksTest {

    @Test
    fun webMailAndPhoneLinks_areOpenable() {
        assertTrue(ExternalLinks.isOpenable("https://example.org/a"))
        assertTrue(ExternalLinks.isOpenable("HTTP://example.org"))
        assertTrue(ExternalLinks.isOpenable("mailto:someone@example.org"))
        assertTrue(ExternalLinks.isOpenable("tel:+100"))
    }

    @Test
    fun otherSchemes_areNotOpenable() {
        assertFalse(ExternalLinks.isOpenable("file:///sdcard/secret.txt"))
        assertFalse(ExternalLinks.isOpenable("javascript:alert(1)"))
        assertFalse(ExternalLinks.isOpenable("content://x/y"))
        assertFalse(ExternalLinks.isOpenable("no-scheme"))
    }
}
