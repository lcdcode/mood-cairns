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
    private val tagCategoryDao get() = moodHolder.tagCategoryDao()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val contents = BackupContentsDecoder(json)

    /** Encrypted JSON of every scale, window, tag, category, and entry. */
    suspend fun exportJson(passphrase: CharArray): String {
        val file = BackupFile(
            schemaVersion = BackupFile.CURRENT_VERSION,
            exportedAt = Instant.now().toString(),
            appVersion = appVersion(),
            scales = scaleDao.observeAll().first().map(::toDto),
            promptWindows = windowDao.observeAll().first().map(::toDto),
            entries = entryDao.observeAll().first().map(::toDto),
            tags = tagDao.observeAll().first().map(::toDto),
            tagCategories = tagCategoryDao.all().map(::toDto),
        )
        return encryptEnvelope(json.encodeToString(BackupFile.serializer(), file), passphrase)
    }

    /** Encrypted JSON of the tracking setup only: no entries. */
    suspend fun exportConfigJson(passphrase: CharArray): String {
        val file = ConfigFile(
            kind = ConfigFile.KIND,
            configVersion = ConfigFile.CURRENT_VERSION,
            exportedAt = Instant.now().toString(),
            appVersion = appVersion(),
            scales = scaleDao.observeAll().first().map(::toDto),
            promptWindows = windowDao.observeAll().first().map(::toDto),
            tags = tagDao.observeAll().first().map(::toDto),
            tagCategories = tagCategoryDao.all().map(::toDto),
        )
        return encryptEnvelope(json.encodeToString(ConfigFile.serializer(), file), passphrase)
    }

    /**
     * AES-GCM encrypt [plaintext] with a key freshly derived from [passphrase] + a random
     * salt, and serialize the envelope. The salt/iter are carried in the envelope so any
     * install - including a fresh one - can decrypt given the same passphrase.
     */
    private fun encryptEnvelope(plaintext: String, passphrase: CharArray): String {
        val salt = BackupCrypto.newSalt()
        val iter = BackupCrypto.PBKDF2_ITERATIONS
        val key = BackupCrypto.deriveKey(passphrase, salt, iter)
        try {
            val encrypted = BackupCrypto.encrypt(key, plaintext.toByteArray(Charsets.UTF_8))
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
     * Decrypt [raw] as a full backup using [secret] and the salt/iter carried inside the
     * envelope itself. [secret] is the backup passphrase, or - for backups made before
     * passphrases existed - the device PIN used at export time. Throws
     * [BackupImportException] on corrupt data, a wrong secret, or a configuration-only
     * file (which has no entries to restore).
     */
    fun parse(raw: String, secret: CharArray): BackupFile =
        contents.forFullRestore(decryptToObject(raw, secret))

    /**
     * Decrypt [raw] and return just its setup. Accepts a configuration file or a full
     * backup, whose entries are ignored. Throws [BackupImportException] when the file
     * cannot be read or is from an unsupported format version.
     */
    fun parseConfig(raw: String, secret: CharArray): ConfigEntities =
        contents.forConfigImport(decryptToObject(raw, secret))

    private fun decryptToObject(raw: String, secret: CharArray) =
        contents.parseObject(String(decryptEnvelope(raw, secret), Charsets.UTF_8))

    private fun decryptEnvelope(raw: String, secret: CharArray): ByteArray {
        val envelope = runCatching {
            json.decodeFromString(EncryptedBackup.serializer(), raw)
        }.getOrNull() ?: fail(ImportError.NotABackup)

        if (envelope.kdf != BackupCrypto.KDF_ALGORITHM) {
            fail(ImportError.Malformed("Unsupported backup KDF ${envelope.kdf}"))
        }
        if (envelope.iter <= 0) {
            fail(ImportError.Malformed("Backup envelope is missing KDF iterations"))
        }

        val salt = decodeField(envelope.salt, "salt")
        val iv = decodeField(envelope.iv, "IV")
        val ct = decodeField(envelope.ciphertext, "ciphertext")

        val key = BackupCrypto.deriveKey(secret, salt, envelope.iter)
        return try {
            BackupCrypto.decrypt(key, iv, ct)
        } catch (_: Throwable) {
            fail(ImportError.WrongSecretOrCorrupt)
        } finally {
            key.fill(0)
        }
    }

    private fun decodeField(value: String, fieldName: String): ByteArray =
        runCatching { Base64.decode(value, Base64.NO_WRAP) }.getOrNull()
            ?: fail(ImportError.Malformed("Backup envelope $fieldName is malformed"))

    private fun fail(error: ImportError): Nothing = throw BackupImportException(error)

    fun toEntities(file: BackupFile): BackupEntities {
        val categories = resolveCategories(file)
        val resolvedTags = resolveTags(file.tags, categories.mapTo(HashSet()) { it.id })
        return BackupEntities(
            schemaVersion = file.schemaVersion,
            scales = file.scales.map { it.toEntity() },
            windows = file.promptWindows.map { it.toEntity() },
            tags = resolvedTags.tags,
            tagCategories = categories,
            entries = file.entries.map { e -> toBundle(e, resolvedTags.replacedIds) },
        )
    }

    private fun toBundle(e: EntryDto, replacedTagIds: Map<Long, Long>): EntryBundle {
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
        // A tag merged away on import is replaced by its survivor; distinct() drops
        // the duplicate link when the entry already carried both.
        val tagIds = e.tagIds.map { replacedTagIds[it] ?: it }.distinct()
        return EntryBundle(entry = entry, values = values, tagIds = tagIds)
    }

    private fun appVersion(): String =
        runCatching { BuildConfig.VERSION_NAME }.getOrDefault("unknown")

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    private fun toDto(s: Scale) = ScaleDto(
        id = s.id, name = s.name, minValue = s.minValue, maxValue = s.maxValue,
        step = s.step, colorArgb = s.colorArgb, isBuiltIn = s.isBuiltIn,
        archived = s.archived, sortOrder = s.sortOrder, inverted = s.inverted,
        defaultValue = s.defaultValue,
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
        id = t.id, name = t.name, category = t.categoryId, sortOrder = t.sortOrder,
        columnIndex = t.columnIndex,
    )

    private fun toDto(c: TagCategory) =
        TagCategoryDto(id = c.id, label = c.label, sortOrder = c.sortOrder)
}

data class BackupEntities(
    val schemaVersion: Int,
    val scales: List<Scale>,
    val windows: List<PromptWindow>,
    val tags: List<Tag>,
    val entries: List<EntryBundle>,
    val tagCategories: List<TagCategory>,
)

data class EntryBundle(
    val entry: Entry,
    val values: List<EntryValue>,
    val tagIds: List<Long>,
)
