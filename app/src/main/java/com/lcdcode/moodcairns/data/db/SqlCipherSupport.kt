package com.lcdcode.moodcairns.data.db

private val HEX = "0123456789abcdef".toCharArray()

/**
 * SQLCipher's raw-key passphrase form, `x'<64 hex chars>'`. It makes SQLCipher use the
 * 32-byte CSPRNG key directly as the page-encryption key instead of running its per-open
 * PBKDF2 (about 300 ms on phones), which adds nothing for a key that is already
 * full-entropy. SupportOpenHelperFactory zeroes the returned array after the native handoff.
 */
internal fun rawKeyPassphrase(dbKey: ByteArray): ByteArray {
    val hex = StringBuilder(dbKey.size * 2 + 3).apply {
        append("x'")
        for (b in dbKey) {
            val v = b.toInt() and 0xFF
            append(HEX[v ushr 4])
            append(HEX[v and 0x0F])
        }
        append('\'')
    }
    return hex.toString().toByteArray(Charsets.US_ASCII)
}

/** Loads SQLCipher's native library once per process; safe to call from any thread. */
internal object SqlCipherLibs {
    @Volatile private var loaded = false

    // sqlcipher-android 4.6.x dropped SQLiteDatabase.loadLibs; System.loadLibrary replaces it.
    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            System.loadLibrary("sqlcipher")
            loaded = true
        }
    }
}
