package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.repo.JournalReplaceTarget
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.InputStream
import java.time.LocalDate

/**
 * Streams a journal backup into [target], replacing every page. Pages are decrypted,
 * validated and inserted one at a time inside [target]'s transaction, so memory stays small.
 * A refused file, a crossed limit or cancellation throws before the transaction commits,
 * leaving the current journal exactly as it was.
 */
internal class JournalRestore(
    private val target: JournalReplaceTarget,
    private val limits: JournalBackupLimits = JournalBackupLimits.DEFAULT,
) {
    /**
     * Restores from [raw], which this closes, and returns the number of pages. [today]
     * anchors the accepted date range. Throws [BackupImportException] for a refused file.
     */
    suspend fun restore(raw: InputStream, secret: CharArray, today: LocalDate): Int {
        val limited = LimitedInputStream(raw, limits.maxFileBytes) {
            ImportError.TooLarge(limits.maxMegabytes)
        }
        return BufferedInputStream(limited, READ_BUFFER_BYTES).use { input ->
            closingOnCancel(input) { readInto(input, secret, today) }
        }
    }

    private suspend fun readInto(input: InputStream, secret: CharArray, today: LocalDate): Int =
        // Reads the header and metadata: a wrong passphrase or file fails here, before the
        // transaction starts.
        JournalBackupReader(input, secret, limits).use { backup ->
            val validator = JournalPageValidator(today)
            var pages = 0
            target.replaceAll { insert ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val page = backup.nextPage() ?: break
                    insert(validator.toDoc(page))
                    pages++
                }
                backup.requireComplete()
            }
            pages
        }

    private companion object {
        const val READ_BUFFER_BYTES = 8 * 1024
    }
}

/**
 * Runs [block], closing [stream] if the coroutine is cancelled meanwhile. Cancellation alone
 * cannot interrupt a read blocked on a stalled provider; closing the stream makes that read
 * fail, which rolls the restore back instead of holding the journal's write lock forever.
 * A failure caused that way is reported as the cancellation it really is.
 */
private suspend fun <T> closingOnCancel(stream: Closeable, block: suspend () -> T): T =
    coroutineScope {
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                stream.close()
            }
        }
        try {
            block()
        } catch (e: BackupImportException) {
            ensureActive()
            throw e
        } finally {
            watcher.cancel()
        }
    }
