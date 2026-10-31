package dev.kern.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading the bundled notices index, and the hand-kept list of bundled-asset licences it is built from. */
class NoticesTest {

    @Test
    fun parse_readsEveryField() {
        val notices = Notices.parse("org.jsoup\tjsoup (1.23.2)\tThe MIT License\thttps://jsoup.org/\tab12,cd34\n")
        assertEquals(
            listOf(Notice("org.jsoup", "jsoup (1.23.2)", "The MIT License", "https://jsoup.org/", listOf("ab12", "cd34"))),
            notices,
        )
    }

    @Test
    fun parse_skipsBlankAndShortLines_andAllowsNoUrlOrText() {
        val notices = Notices.parse("\nshort\tline\nKaTeX\tMaths\tMIT\t\t\n")
        assertEquals(listOf(Notice("KaTeX", "Maths", "MIT", "", emptyList())), notices)
    }

    @Test
    fun textPath_pointsIntoTheTextsFolder() {
        assertEquals("notices/texts/ab12.txt", Notices.textPath("ab12"))
    }

    @Test
    fun paragraphs_splitAtBlankLines() {
        val text = "MIT License\r\n\r\nCopyright (c) 2026\n  \nPermission is hereby granted\nto any person.\n"
        assertEquals(
            listOf("MIT License", "Copyright (c) 2026", "Permission is hereby granted\nto any person."),
            Notices.paragraphs(text),
        )
    }

    @Test
    fun bundledAssetIndex_isWellFormed_andEveryFileExists() {
        val lines = File("licenses/index.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(lines.size >= 10)
        for (line in lines) {
            val fields = line.split('\t')
            assertEquals(line, 5, fields.size)
            assertTrue(fields[0], File("licenses/${fields[0]}").isFile)
            assertTrue(line, fields[1].isNotBlank() && fields[3].isNotBlank())
        }
    }

    @Test
    fun everyLicenceFileInTheRepo_isListedOrUsedByTheBuild() {
        val listed = File("licenses/index.tsv").readLines().filter { !it.startsWith("#") }.map { it.substringBefore('\t') }.toSet()
        val byBuild = File("licenses/libraries").listFiles().orEmpty().map { "libraries/${it.name}" } +
            listOf("common/Apache-2.0.txt")
        val all = File("licenses").walkTopDown().filter { it.isFile && it.name != "index.tsv" }
            .map { it.relativeTo(File("licenses")).path }.toList()
        for (path in all) assertTrue("$path is not in licenses/index.tsv", path in listed || path in byBuild)
    }
}
