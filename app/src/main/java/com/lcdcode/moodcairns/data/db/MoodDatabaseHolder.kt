package com.lcdcode.moodcairns.data.db

import android.content.Context
import androidx.room.Room
import com.lcdcode.moodcairns.data.dao.EntryDao
import com.lcdcode.moodcairns.data.dao.ScaleDao
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
 * SQLCipher's native libs are loaded lazily on first open; the call is cheap to
 * repeat and the library guards against double-loading.
 */
@Singleton
class MoodDatabaseHolder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile private var db: MoodDatabase? = null

    @Synchronized
    fun open(dbKey: ByteArray) {
        if (db != null) return
        loadNativeLibs()
        // Use SQLCipher's raw-key form ("x'HEX'") so the 32-byte CSPRNG key is
        // used directly as the page-encryption key — bypassing SQLCipher's
        // per-open PBKDF2 (≈300 ms on phones) which adds no security here since
        // our key is already full-entropy. SupportOpenHelperFactory zeroes the
        // array after handing it to the native side.
        val factory = SupportOpenHelperFactory(toRawKeyPassphrase(dbKey))
        db = Room.databaseBuilder(context, MoodDatabase::class.java, MoodDatabase.NAME)
            .openHelperFactory(factory)
            .build()
    }

    @Synchronized
    fun close() {
        db?.close()
        db = null
    }

    fun isOpen(): Boolean = db != null

    fun scaleDao(): ScaleDao = requireDb().scaleDao()
    fun entryDao(): EntryDao = requireDb().entryDao()
    fun database(): MoodDatabase = requireDb()

    private fun requireDb(): MoodDatabase = db
        ?: error("MoodDatabase accessed while locked")

    private fun toRawKeyPassphrase(dbKey: ByteArray): ByteArray {
        val hex = StringBuilder(dbKey.size * 2 + 3).apply {
            append("x'")
            for (b in dbKey) {
                val v = b.toInt() and 0xFF
                append(HEX[v ushr 4]); append(HEX[v and 0x0F])
            }
            append('\'')
        }
        return hex.toString().toByteArray(Charsets.US_ASCII)
    }

    companion object {
        private val HEX = "0123456789abcdef".toCharArray()

        @Volatile private var librariesLoaded = false

        /**
         * Idempotently pull SQLCipher's native libraries in.
         * sqlcipher-android 4.6.x dropped the wrapper `SQLiteDatabase.loadLibs`;
         * a direct System.loadLibrary call is the supported replacement.
         */
        private fun loadNativeLibs() {
            if (librariesLoaded) return
            synchronized(Companion) {
                if (librariesLoaded) return
                System.loadLibrary("sqlcipher")
                librariesLoaded = true
            }
        }
    }
}
