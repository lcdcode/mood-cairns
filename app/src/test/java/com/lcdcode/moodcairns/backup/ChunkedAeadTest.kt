package com.lcdcode.moodcairns.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/** Attacks on the chunked stream itself: every rearrangement must fail authentication. */
class ChunkedAeadTest {

    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val header = "test header".toByteArray()
    private val prefix = ByteArray(ChunkedAead.NONCE_PREFIX_BYTES) { 7 }
    private val segment = CHUNK + ChunkedAead.TAG_BYTES

    private fun encrypt(plain: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ChunkEncryptingOutputStream(out, key, header, prefix, CHUNK).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun decrypt(
        ciphertext: ByteArray,
        aad: ByteArray = header,
        withKey: SecretKeySpec = key,
    ): ByteArray {
        val input = ByteArrayInputStream(ciphertext)
        val stream = ChunkDecryptingInputStream(input, withKey, aad, prefix, CHUNK)
        val plain = stream.readBytes()
        assertTrue("stream must end on its authenticated last chunk", stream.isComplete)
        return plain
    }

    private fun failure(block: () -> Unit): ImportError =
        assertThrows(BackupImportException::class.java, block).error

    private fun bytes(size: Int) = Random(size).nextBytes(size)

    private fun ByteArray.flipped(index: Int) =
        copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }

    @Test
    fun `round trips at every interesting length`() {
        for (size in listOf(0, 1, CHUNK - 1, CHUNK, CHUNK + 1, 2 * CHUNK, 2 * CHUNK + 1, 1_000)) {
            assertArrayEquals("size $size", bytes(size), decrypt(encrypt(bytes(size))))
        }
    }

    @Test
    fun `an empty stream is a single tag-only chunk`() {
        assertEquals(ChunkedAead.TAG_BYTES, encrypt(ByteArray(0)).size)
    }

    @Test
    fun `a full final chunk is still sealed as last`() {
        // Exactly two chunks of data: no extra empty chunk is needed.
        assertEquals(2 * segment, encrypt(bytes(2 * CHUNK)).size)
    }

    @Test
    fun `flipping any byte fails authentication`() {
        val ciphertext = encrypt(bytes(3 * CHUNK + 5))
        for (index in listOf(0, segment - 1, segment + 3, ciphertext.size - 1)) {
            val tampered = ciphertext.flipped(index)
            assertEquals(ImportError.WrongSecretOrCorrupt, failure { decrypt(tampered) })
        }
    }

    @Test
    fun `dropping the last chunk at a chunk boundary is detected`() {
        val ciphertext = encrypt(bytes(3 * CHUNK))
        val cut = ciphertext.copyOf(2 * segment)
        assertEquals(ImportError.WrongSecretOrCorrupt, failure { decrypt(cut) })
    }

    @Test
    fun `cutting a chunk in half is detected`() {
        val ciphertext = encrypt(bytes(3 * CHUNK))
        val halfChunk = ciphertext.copyOf(segment + 20)
        assertEquals(ImportError.WrongSecretOrCorrupt, failure { decrypt(halfChunk) })
    }

    @Test
    fun `losing everything after the first few bytes is malformed`() {
        val ciphertext = encrypt(bytes(CHUNK))
        assertTrue(failure { decrypt(ciphertext.copyOf(5)) } is ImportError.Malformed)
    }

    @Test
    fun `swapping two chunks is detected`() {
        val ciphertext = encrypt(bytes(3 * CHUNK + 1))
        val swapped = ciphertext.copyOf()
        System.arraycopy(ciphertext, segment, swapped, 0, segment)
        System.arraycopy(ciphertext, 0, swapped, segment, segment)
        assertEquals(ImportError.WrongSecretOrCorrupt, failure { decrypt(swapped) })
    }

    @Test
    fun `repeating a chunk is detected`() {
        val ciphertext = encrypt(bytes(2 * CHUNK + 1))
        val repeated = ciphertext.copyOfRange(0, segment) + ciphertext
        assertEquals(ImportError.WrongSecretOrCorrupt, failure { decrypt(repeated) })
    }

    @Test
    fun `data appended after the last chunk is detected`() {
        val ciphertext = encrypt(bytes(CHUNK + 3)) + bytes(10)
        assertEquals(ImportError.WrongSecretOrCorrupt, failure { decrypt(ciphertext) })
    }

    @Test
    fun `a different header or key fails authentication`() {
        val ciphertext = encrypt(bytes(40))
        assertEquals(
            ImportError.WrongSecretOrCorrupt,
            failure { decrypt(ciphertext, aad = "other header".toByteArray()) },
        )
        val otherKey = SecretKeySpec(ByteArray(32) { 9 }, "AES")
        val error = failure { decrypt(ciphertext, withKey = otherKey) }
        assertEquals(ImportError.WrongSecretOrCorrupt, error)
    }

    @Test
    fun `nonces differ per chunk and by last flag`() {
        val nonces = listOf(
            ChunkedAead.nonce(prefix, 0, last = false),
            ChunkedAead.nonce(prefix, 1, last = false),
            ChunkedAead.nonce(prefix, 1, last = true),
        )
        assertEquals(3, nonces.map { it.toList() }.distinct().size)
        assertTrue(nonces.all { it.size == BackupCrypto.IV_BYTES })
    }

    private companion object {
        const val CHUNK = 16
    }
}
