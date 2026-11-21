package com.lcdcode.moodcairns.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class EnvelopeCodecTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val codec = EnvelopeCodec(json)
    private val passphrase = "correct horse".toCharArray()
    private val plaintext = "{\"hello\":\"wörld ✓\"}"

    private fun encrypt(text: String = plaintext) =
        codec.encrypt(text, passphrase.copyOf(), iterations = FAST_ITERATIONS)

    private fun envelope(raw: String) = json.decodeFromString(EncryptedBackup.serializer(), raw)

    private fun withEnvelope(raw: String, change: (EncryptedBackup) -> EncryptedBackup) =
        json.encodeToString(EncryptedBackup.serializer(), change(envelope(raw)))

    private fun importError(block: () -> Unit): ImportError =
        assertThrows(BackupImportException::class.java, block).error

    @Test
    fun `round trip restores the exact plaintext`() {
        val bytes = codec.decrypt(encrypt(), passphrase.copyOf())
        assertEquals(plaintext, String(bytes, Charsets.UTF_8))
    }

    @Test
    fun `every export uses a fresh salt and iv`() {
        val a = envelope(encrypt())
        val b = envelope(encrypt())
        assertTrue(a.salt != b.salt && a.iv != b.iv)
    }

    @Test
    fun `fields are standard padded base64 without line breaks, as Android NO_WRAP wrote`() {
        val e = envelope(encrypt())
        val standard = Regex("^[A-Za-z0-9+/]+={0,2}$")
        listOf(e.salt, e.iv, e.ciphertext).forEach { assertTrue(it, standard.matches(it)) }
        assertEquals(BackupCrypto.SALT_BYTES, Base64.getDecoder().decode(e.salt).size)
        assertEquals(BackupCrypto.IV_BYTES, Base64.getDecoder().decode(e.iv).size)
    }

    @Test
    fun `wrong passphrase is reported as wrong secret or corrupt`() {
        val error = importError { codec.decrypt(encrypt(), "nope".toCharArray()) }
        assertEquals(ImportError.WrongSecretOrCorrupt, error)
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val tampered = withEnvelope(encrypt()) { e ->
            val bytes = Base64.getDecoder().decode(e.ciphertext)
            bytes[0] = (bytes[0].toInt() xor 1).toByte()
            e.copy(ciphertext = Base64.getEncoder().encodeToString(bytes))
        }
        val error = importError { codec.decrypt(tampered, passphrase) }
        assertEquals(ImportError.WrongSecretOrCorrupt, error)
    }

    @Test
    fun `text that is not an envelope is not a backup`() {
        assertEquals(ImportError.NotABackup, importError { codec.decrypt("hello", passphrase) })
    }

    @Test
    fun `unknown kdf is refused`() {
        val raw = withEnvelope(encrypt()) { it.copy(kdf = "MD5") }
        assertTrue(importError { codec.decrypt(raw, passphrase) } is ImportError.Malformed)
    }

    @Test
    fun `zero iterations are refused`() {
        val raw = withEnvelope(encrypt()) { it.copy(iter = 0) }
        assertTrue(importError { codec.decrypt(raw, passphrase) } is ImportError.Malformed)
    }

    @Test(timeout = 5_000)
    fun `absurd iteration counts are refused before any key derivation`() {
        val raw = withEnvelope(encrypt()) { it.copy(iter = Int.MAX_VALUE) }
        assertTrue(importError { codec.decrypt(raw, passphrase) } is ImportError.Malformed)
        val justOver =
            withEnvelope(encrypt()) { it.copy(iter = EnvelopeCodec.MAX_KDF_ITERATIONS + 1) }
        assertTrue(importError { codec.decrypt(justOver, passphrase) } is ImportError.Malformed)
    }

    @Test
    fun `today's iteration count is well inside the limit`() {
        assertTrue(BackupCrypto.PBKDF2_ITERATIONS * 10 <= EnvelopeCodec.MAX_KDF_ITERATIONS)
    }

    @Test
    fun `undecodable base64 is refused`() {
        val raw = withEnvelope(encrypt()) { it.copy(salt = "!!not base64!!") }
        assertTrue(importError { codec.decrypt(raw, passphrase) } is ImportError.Malformed)
    }

    private companion object {
        /** Real exports use 600,000; tests only need the format, not the cost. */
        const val FAST_ITERATIONS = 1_000
    }
}
