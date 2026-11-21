package com.lcdcode.moodcairns.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipInputStream

class JournalMarkdownExportTest {

    private val nov1 = LocalDate.of(2026, 11, 1)
    private val nov2 = LocalDate.of(2026, 11, 2)
    // Even seconds: zip entries store DOS times with two-second resolution.
    private val edited = Instant.parse("2026-11-01T21:30:00Z")

    private fun zip(block: (JournalZipWriter) -> Unit): ByteArray =
        ByteArrayOutputStream().also { out -> JournalZipWriter(out).use(block) }.toByteArray()

    private data class Entry(val name: String, val text: String, val time: Long)

    private fun entries(archive: ByteArray): List<Entry> =
        ZipInputStream(ByteArrayInputStream(archive), Charsets.UTF_8).use { zip ->
            generateSequence { zip.nextEntry }.map { entry ->
                Entry(entry.name, zip.readBytes().toString(Charsets.UTF_8), entry.time)
            }.toList()
        }

    @Test
    fun `file names follow the daily note convention`() {
        assertEquals("2026-11-01.md", JournalMarkdown.fileName(nov1))
    }

    @Test
    fun `file content is the page with a final newline`() {
        assertEquals("# Hi\n", JournalMarkdown.fileContent("# Hi"))
        assertEquals("# Hi\n", JournalMarkdown.fileContent("# Hi\n"))
    }

    @Test
    fun `the zip holds one markdown file per day, in the order written`() {
        val archive = zip { w ->
            w.addPage(nov1, "# Monday\n- [x] walk", edited)
            w.addPage(nov2, "Сегодня **хорошо** 😀", edited)
        }
        assertEquals(
            listOf(
                Entry("2026-11-01.md", "# Monday\n- [x] walk\n", edited.toEpochMilli()),
                Entry("2026-11-02.md", "Сегодня **хорошо** 😀\n", edited.toEpochMilli()),
            ),
            entries(archive),
        )
    }

    @Test
    fun `an empty journal gives a valid empty zip`() {
        assertEquals(emptyList<Entry>(), entries(zip { }))
    }

    @Test
    fun `closing the writer leaves the caller's stream open`() {
        var closed = false
        val out = object : ByteArrayOutputStream() {
            override fun close() {
                closed = true
            }
        }
        JournalZipWriter(out).use { it.addPage(nov1, "x", edited) }
        assertFalse(closed)
        assertTrue(out.size() > 0)
    }
}
