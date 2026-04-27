package com.moodcairns.backup

import android.util.Base64
import com.moodcairns.BuildConfig
import com.moodcairns.data.dao.EntryDao
import com.moodcairns.data.dao.EntryWithValues
import com.moodcairns.data.dao.PromptWindowDao
import com.moodcairns.data.dao.ScaleDao
import com.moodcairns.data.entity.Entry
import com.moodcairns.data.entity.EntryValue
import com.moodcairns.data.entity.PromptSlot
import com.moodcairns.data.entity.PromptWindow
import com.moodcairns.data.entity.Scale
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupSerializer @Inject constructor(
    private val scaleDao: ScaleDao,
    private val windowDao: PromptWindowDao,
    private val entryDao: EntryDao,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Build the plaintext [BackupFile], AES-GCM encrypt it with a key freshly
     * derived from [pin] + a random per-backup salt, and serialize the
     * envelope. The salt/iter are carried in the envelope so any install —
     * including a fresh one — can decrypt given the same PIN.
     */
    suspend fun exportJson(pin: CharArray): String {
        val salt = BackupCrypto.newSalt()
        val iter = BackupCrypto.PBKDF2_ITERATIONS
        val key = BackupCrypto.deriveKey(pin, salt, iter)
        try {
            val scales = scaleDao.observeAll().first().map(::toDto)
            val windows = windowDao.observeAll().first().map(::toDto)
            val entries = entryDao.observeAll().first().map(::toDto)

            val file = BackupFile(
                schemaVersion = BackupFile.CURRENT_VERSION,
                exportedAt = Instant.now().toString(),
                appVersion = runCatching { BuildConfig.VERSION_NAME }.getOrDefault("unknown"),
                scales = scales,
                promptWindows = windows,
                entries = entries,
            )
            val plaintextJson = json.encodeToString(BackupFile.serializer(), file)
            val encrypted = BackupCrypto.encrypt(key, plaintextJson.toByteArray(Charsets.UTF_8))

            val envelope = EncryptedBackup(
                kdf = BackupCrypto.KDF_ALGORITHM,
                iter = iter,
                salt = b64(salt),
                iv = b64(encrypted.iv),
                ciphertext = b64(encrypted.ciphertext),
            )
            return json.encodeToString(EncryptedBackup.serializer(), envelope)
        } finally {
            key.fill(0)
        }
    }

    /**
     * Parse and decrypt [raw] using [pin] and the salt/iter carried inside the
     * envelope itself. Throws with a user-readable message on corrupt data or
     * wrong PIN.
     */
    fun parse(raw: String, pin: CharArray): BackupFile {
        val envelope = runCatching {
            json.decodeFromString(EncryptedBackup.serializer(), raw)
        }.getOrNull() ?: error("Unrecognized backup file format")

        require(envelope.kdf == BackupCrypto.KDF_ALGORITHM) {
            "Unsupported backup KDF ${envelope.kdf}"
        }
        require(envelope.iter > 0) { "Backup envelope is missing KDF iterations" }

        val salt = runCatching { Base64.decode(envelope.salt, Base64.NO_WRAP) }
            .getOrNull() ?: error("Backup envelope salt is malformed")
        val iv = runCatching { Base64.decode(envelope.iv, Base64.NO_WRAP) }
            .getOrNull() ?: error("Backup envelope IV is malformed")
        val ct = runCatching { Base64.decode(envelope.ciphertext, Base64.NO_WRAP) }
            .getOrNull() ?: error("Backup ciphertext is malformed")

        val key = BackupCrypto.deriveKey(pin, salt, envelope.iter)
        val plaintext = try {
            BackupCrypto.decrypt(key, iv, ct)
        } catch (_: Throwable) {
            error("Could not decrypt backup — wrong PIN, or file is corrupt")
        } finally {
            key.fill(0)
        }

        val parsed = json.decodeFromString(
            BackupFile.serializer(),
            String(plaintext, Charsets.UTF_8),
        )
        require(parsed.schemaVersion == BackupFile.CURRENT_VERSION) {
            "Unsupported backup schema version ${parsed.schemaVersion}; expected ${BackupFile.CURRENT_VERSION}"
        }
        return parsed
    }

    fun toEntities(file: BackupFile): BackupEntities = BackupEntities(
        scales = file.scales.map(::fromDto),
        windows = file.promptWindows.map(::fromDto),
        entries = file.entries.map { e ->
            val entry = Entry(
                id = e.id,
                recordedAt = Instant.parse(e.recordedAt),
                slot = PromptSlot.valueOf(e.slot),
                promptWindowId = e.promptWindowId,
                note = e.note,
            )
            val values = e.values.map { v ->
                EntryValue(entryId = e.id, scaleId = v.scaleId, value = v.value)
            }
            entry to values
        },
    )

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    private fun toDto(s: Scale) = ScaleDto(
        id = s.id, name = s.name, minValue = s.minValue, maxValue = s.maxValue,
        step = s.step, colorArgb = s.colorArgb, isBuiltIn = s.isBuiltIn,
        archived = s.archived, sortOrder = s.sortOrder,
    )

    private fun toDto(w: PromptWindow) = PromptWindowDto(
        id = w.id, label = w.label, slot = w.slot.name,
        startTime = w.startTime.toString(), endTime = w.endTime.toString(),
        enabled = w.enabled,
    )

    private fun toDto(e: EntryWithValues) = EntryDto(
        id = e.entry.id,
        recordedAt = e.entry.recordedAt.toString(),
        slot = e.entry.slot.name,
        promptWindowId = e.entry.promptWindowId,
        note = e.entry.note,
        values = e.values.map { EntryValueDto(scaleId = it.scaleId, value = it.value) },
    )

    private fun fromDto(s: ScaleDto) = Scale(
        id = s.id, name = s.name, minValue = s.minValue, maxValue = s.maxValue,
        step = s.step, colorArgb = s.colorArgb, isBuiltIn = s.isBuiltIn,
        archived = s.archived, sortOrder = s.sortOrder,
    )

    private fun fromDto(w: PromptWindowDto) = PromptWindow(
        id = w.id, label = w.label, slot = PromptSlot.valueOf(w.slot),
        startTime = LocalTime.parse(w.startTime), endTime = LocalTime.parse(w.endTime),
        enabled = w.enabled,
    )
}

data class BackupEntities(
    val scales: List<Scale>,
    val windows: List<PromptWindow>,
    val entries: List<Pair<Entry, List<EntryValue>>>,
)
