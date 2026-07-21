package com.lcdcode.moodcairns.backup

import android.util.Base64
import com.lcdcode.moodcairns.BuildConfig
import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.dao.PromptWindowDao
import com.lcdcode.moodcairns.data.db.MoodDatabaseHolder
import com.lcdcode.moodcairns.data.entity.Entry
import com.lcdcode.moodcairns.data.entity.EntryValue
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupSerializer @Inject constructor(
    private val moodHolder: MoodDatabaseHolder,
    private val windowDao: PromptWindowDao,
) {
    private val scaleDao get() = moodHolder.scaleDao()
    private val entryDao get() = moodHolder.entryDao()
    private val tagDao get() = moodHolder.tagDao()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Build the plaintext [BackupFile], AES-GCM encrypt it with a key freshly
     * derived from [pin] + a random per-backup salt, and serialize the
     * envelope. The salt/iter are carried in the envelope so any install —
     * including a fresh one — can decrypt given the same PIN.
     */
    suspend fun exportJson(passphrase: CharArray): String {
        val salt = BackupCrypto.newSalt()
        val iter = BackupCrypto.PBKDF2_ITERATIONS
        val key = BackupCrypto.deriveKey(passphrase, salt, iter)
        try {
            val scales = scaleDao.observeAll().first().map(::toDto)
            val windows = windowDao.observeAll().first().map(::toDto)
            val entries = entryDao.observeAll().first().map(::toDto)
            val tags = tagDao.observeAll().first().map(::toDto)

            val file = BackupFile(
                schemaVersion = BackupFile.CURRENT_VERSION,
                exportedAt = Instant.now().toString(),
                appVersion = runCatching { BuildConfig.VERSION_NAME }.getOrDefault("unknown"),
                scales = scales,
                promptWindows = windows,
                entries = entries,
                tags = tags,
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
     * Parse and decrypt [raw] using [secret] and the salt/iter carried inside the
     * envelope itself. [secret] is the backup passphrase, or - for backups made
     * before passphrases existed - the device PIN used at export time. Throws
     * with a user-readable message on corrupt data or a wrong secret.
     */
    fun parse(raw: String, secret: CharArray): BackupFile {
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

        val key = BackupCrypto.deriveKey(secret, salt, envelope.iter)
        val plaintext = try {
            BackupCrypto.decrypt(key, iv, ct)
        } catch (_: Throwable) {
            error("Could not decrypt backup — wrong passphrase, or file is corrupt")
        } finally {
            key.fill(0)
        }

        val parsed = json.decodeFromString(
            BackupFile.serializer(),
            String(plaintext, Charsets.UTF_8),
        )
        require(parsed.schemaVersion in BackupFile.SUPPORTED_VERSIONS) {
            "Unsupported backup schema version ${parsed.schemaVersion}; " +
                "expected ${BackupFile.SUPPORTED_VERSIONS.first}..${BackupFile.SUPPORTED_VERSIONS.last}"
        }
        return parsed
    }

    fun toEntities(file: BackupFile): BackupEntities = BackupEntities(
        schemaVersion = file.schemaVersion,
        scales = file.scales.map(::fromDto),
        windows = file.promptWindows.map(::fromDto),
        tags = file.tags.mapNotNull(::fromDto),
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
            EntryBundle(entry = entry, values = values, tagIds = e.tagIds)
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
        tagIds = e.tags.map { it.id },
    )

    private fun toDto(t: Tag) = TagDto(
        id = t.id, name = t.name, category = t.category.name, sortOrder = t.sortOrder,
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

    /** Returns null for unknown categories so a hand-edited file cannot abort the import. */
    private fun fromDto(t: TagDto): Tag? {
        val category = runCatching { TagCategory.valueOf(t.category) }.getOrNull() ?: return null
        return Tag(id = t.id, name = t.name, category = category, sortOrder = t.sortOrder)
    }
}

data class BackupEntities(
    val schemaVersion: Int,
    val scales: List<Scale>,
    val windows: List<PromptWindow>,
    val tags: List<Tag>,
    val entries: List<EntryBundle>,
)

data class EntryBundle(
    val entry: Entry,
    val values: List<EntryValue>,
    val tagIds: List<Long>,
)
