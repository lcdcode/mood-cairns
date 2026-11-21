package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.repo.JournalRepository
import kotlinx.coroutines.flow.first
import java.io.OutputStream
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Reads journal pages for the plaintext Markdown export. */
@Singleton
class JournalMarkdownExporter @Inject constructor(private val repo: JournalRepository) {

    /** Days that have a page, newest first. Reads only the short columns, never bodies. */
    suspend fun days(): List<LocalDate> = repo.observeMeta().first().map { it.day }

    /** Streams every page into a zip on [out] (not closed); returns how many were written. */
    suspend fun exportAll(out: OutputStream): Int = JournalZipWriter(out).use { zip ->
        var pages = 0
        repo.forEachDoc { doc ->
            zip.addPage(doc.day, doc.body, doc.updatedAt)
            pages++
        }
        pages
    }

    /** The Markdown file content for [day], or null when that day has no page. */
    suspend fun pageMarkdown(day: LocalDate): String? =
        repo.load(day)?.body?.let(JournalMarkdown::fileContent)
}
