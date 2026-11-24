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
    fun sqlcipher_android_defines_no_exception_types_of_its_own() {
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
    fun the_platform_corruption_exception_is_caught_as_sqlexception() {
        assertTrue(
            android.database.SQLException::class.java.isAssignableFrom(
                android.database.sqlite.SQLiteDatabaseCorruptException::class.java,
            ),
        )
    }

    @Test
    fun the_sqlcipher_library_is_on_the_test_classpath() {
        // Guards the canary above against passing only because the library is missing.
        Class.forName("net.zetetic.database.sqlcipher.SQLiteDatabase")
    }
}
