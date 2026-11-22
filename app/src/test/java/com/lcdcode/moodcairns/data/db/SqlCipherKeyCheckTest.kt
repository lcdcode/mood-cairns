package com.lcdcode.moodcairns.data.db

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SqlCipherKeyCheckTest {

    /**
     * SqlCipherKeyCheck turns a wrong key into false by catching android.database.SQLException,
     * which covers the platform android.database.sqlite exceptions sqlcipher-android throws.
     * If an upgrade adds SQLCipher-specific exception types, this canary fails: check that they
     * still extend android.database.SQLException before updating it.
     */
    @Test
    fun `sqlcipher-android defines no exception types of its own`() {
        val candidates = listOf(
            "net.zetetic.database.sqlcipher.SQLiteException",
            "net.zetetic.database.sqlcipher.SQLiteDatabaseCorruptException",
            "net.zetetic.database.sqlcipher.SQLiteCantOpenDatabaseException",
            "net.zetetic.database.SQLException",
        )
        candidates.forEach { name ->
            try {
                Class.forName(name)
                fail("$name exists; confirm SqlCipherKeyCheck still catches it")
            } catch (_: ClassNotFoundException) {
                // Expected: the platform exception types are used.
            }
        }
    }

    @Test
    fun `the platform corruption exception is caught as SQLException`() {
        assertTrue(
            android.database.SQLException::class.java.isAssignableFrom(
                android.database.sqlite.SQLiteDatabaseCorruptException::class.java,
            ),
        )
    }

    @Test
    fun `the SQLCipher library is on the test classpath`() {
        // Guards the canary above against passing only because the library is missing.
        Class.forName("net.zetetic.database.sqlcipher.SQLiteDatabase")
    }
}
