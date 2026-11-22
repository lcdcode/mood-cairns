package com.lcdcode.moodcairns.data.db

import android.database.SQLException
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * Tests a SQLCipher key against an existing file without Room. A wrong key surfaces as
 * "file is not a database", which SQLite treats as corruption, and AOSP-derived default
 * corruption handlers delete the file. This check opens read-only with a handler that never
 * deletes anything, so a wrong key only ever yields false.
 */
internal object SqlCipherKeyCheck {

    private val KEEP_FILE_ON_CORRUPTION = DatabaseErrorHandler { _ -> }

    fun opens(file: File, dbKey: ByteArray): Boolean {
        SqlCipherLibs.load()
        val passphrase = rawKeyPassphrase(dbKey)
        return try {
            SQLiteDatabase.openDatabase(
                file.path,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READONLY,
                KEEP_FILE_ON_CORRUPTION,
                null,
            ).use { db ->
                db.rawQuery(PROBE_QUERY, null).use { it.moveToFirst() }
            }
            true
        } catch (_: SQLException) {
            false
        } finally {
            passphrase.fill(0)
        }
    }

    /** Reading the schema forces SQLCipher to decrypt page 1. */
    private const val PROBE_QUERY = "SELECT count(*) FROM sqlite_master"
}
