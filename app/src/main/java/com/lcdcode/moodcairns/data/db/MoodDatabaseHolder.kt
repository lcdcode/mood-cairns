package com.lcdcode.moodcairns.data.db

import android.content.Context
import androidx.room.Room
import com.lcdcode.moodcairns.data.dao.EntryDao
import com.lcdcode.moodcairns.data.dao.ScaleDao
import com.lcdcode.moodcairns.data.dao.TagCategoryDao
import com.lcdcode.moodcairns.data.dao.TagDao
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the lifecycle of the SQLCipher-encrypted [MoodDatabase] instance.
 *
 * The DB key (32 random bytes) is supplied by [com.lcdcode.moodcairns.security.LockManager]
 * after a successful unlock. While locked the instance is null and any DAO
 * access throws — guarded by [requireDb] — so accidental read attempts after
 * lock surface as crashes rather than silent failures.
 *
 * Keying and native-library loading are shared with [JournalDatabaseHolder] via
 * [rawKeyPassphrase] and [SqlCipherLibs].
 */
@Singleton
class MoodDatabaseHolder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile private var db: MoodDatabase? = null

    @Synchronized
    fun open(dbKey: ByteArray) {
        if (db != null) return
        SqlCipherLibs.load()
        val factory = SupportOpenHelperFactory(rawKeyPassphrase(dbKey))
        db = Room.databaseBuilder(context, MoodDatabase::class.java, MoodDatabase.NAME)
            .openHelperFactory(factory)
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7,
            )
            .build()
    }

    @Synchronized
    fun close() {
        db?.close()
        db = null
    }

    fun isOpen(): Boolean = db != null

    /**
     * Whether [dbKey] opens the existing database file; true when there is no file yet, since
     * then there is nothing to lose. [open] is lazy and proves nothing about the key, so call
     * this before deleting or persisting anything that depends on the key being right.
     */
    fun keyOpensExistingDatabase(dbKey: ByteArray): Boolean {
        val file = context.getDatabasePath(MoodDatabase.NAME)
        return !file.exists() || SqlCipherKeyCheck.opens(file, dbKey)
    }

    fun scaleDao(): ScaleDao = requireDb().scaleDao()
    fun entryDao(): EntryDao = requireDb().entryDao()
    fun tagDao(): TagDao = requireDb().tagDao()
    fun tagCategoryDao(): TagCategoryDao = requireDb().tagCategoryDao()
    fun database(): MoodDatabase = requireDb()

    private fun requireDb(): MoodDatabase = db
        ?: error("MoodDatabase accessed while locked")
}
