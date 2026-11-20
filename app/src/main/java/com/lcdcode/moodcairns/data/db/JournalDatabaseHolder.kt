package com.lcdcode.moodcairns.data.db

import android.content.Context
import androidx.room.Room
import com.lcdcode.moodcairns.data.dao.JournalDao
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the lifecycle of the SQLCipher-encrypted [JournalDatabase], mirroring
 * [MoodDatabaseHolder]: open only while unlocked, and any DAO access while locked throws so
 * a missed lock check surfaces loudly instead of silently reading nothing.
 */
@Singleton
class JournalDatabaseHolder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile private var db: JournalDatabase? = null

    /** [journalKey] is zeroed by SupportOpenHelperFactory once handed to SQLCipher. */
    @Synchronized
    fun open(journalKey: ByteArray) {
        if (db != null) {
            journalKey.fill(0)
            return
        }
        SqlCipherLibs.load()
        val factory = SupportOpenHelperFactory(rawKeyPassphrase(journalKey))
        journalKey.fill(0)
        db = Room.databaseBuilder(context, JournalDatabase::class.java, JournalDatabase.NAME)
            .openHelperFactory(factory)
            .build()
    }

    @Synchronized
    fun close() {
        db?.close()
        db = null
    }

    fun isOpen(): Boolean = db != null

    fun journalDao(): JournalDao = requireDb().journalDao()

    fun database(): JournalDatabase = requireDb()

    private fun requireDb(): JournalDatabase = db
        ?: error("JournalDatabase accessed while locked")
}
