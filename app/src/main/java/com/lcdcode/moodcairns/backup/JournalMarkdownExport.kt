package com.lcdcode.moodcairns.backup

import java.io.Closeable
import java.io.FilterOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Plaintext Markdown export of journal pages, laid out for Obsidian and similar tools: one
 * file per day named YYYY-MM-DD.md (the daily-note convention), containing the page exactly
 * as written. Never encrypted; only offered when unsafe exports are allowed.
 */
internal object JournalMarkdown {
    const val MIME_MARKDOWN = "text/markdown"
    const val MIME_ZIP = "application/zip"

    fun fileName(day: LocalDate): String = "$day.md"

    /** The page as a text file: unchanged, but ending in a newline as text files should. */
    fun fileContent(body: String): String = if (body.endsWith("\n")) body else "$body\n"
}

/**
 * Streams pages into a zip of daily Markdown files, one page in memory at a time. Each
 * entry carries the page's last-edited time, so file managers and vaults sort sensibly.
 * Closing finishes the archive but leaves [out] open for its owner to close.
 */
internal class JournalZipWriter(out: OutputStream) : Closeable {
    private val zip = ZipOutputStream(NonClosingOutputStream(out), Charsets.UTF_8)

    fun addPage(day: LocalDate, body: String, updatedAt: Instant) {
        val entry = ZipEntry(JournalMarkdown.fileName(day))
        entry.time = updatedAt.toEpochMilli()
        zip.putNextEntry(entry)
        zip.write(JournalMarkdown.fileContent(body).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    /** Writes the zip directory and releases the compressor. */
    override fun close() = zip.close()
}

/** Passes writes through but turns close() into flush(), so a wrapper cannot close [out]. */
internal class NonClosingOutputStream(out: OutputStream) : FilterOutputStream(out) {
    override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)

    override fun close() = flush()
}
