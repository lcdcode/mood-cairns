package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.db.SeedTags
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.LocalTime

/**
 * Turns the decrypted JSON of a backup or configuration file into data, deciding which
 * kind it is and whether this app version can read it. Uses no Android APIs, so JVM
 * tests cover it. Every failure throws [BackupImportException].
 *
 * Versions are checked before decoding, so a newer file whose shape changed is
 * reported as unsupported rather than as damaged.
 */
internal class BackupContentsDecoder(private val json: Json) {

    fun parseObject(plaintext: String): JsonObject =
        runCatching { json.parseToJsonElement(plaintext) as? JsonObject }.getOrNull()
            ?: fail(ImportError.Malformed("Backup contents are not a JSON object"))

    /** A full backup to restore. A configuration file is refused: it has no entries. */
    fun forFullRestore(contents: JsonObject): BackupFile {
        if (isConfigFile(contents)) fail(ImportError.ConfigOnlyFile)
        return decodeBackupFile(contents)
    }

    /** The setup in a configuration file, or in a full backup with its entries ignored. */
    fun forConfigImport(contents: JsonObject): ConfigEntities = when {
        isConfigFile(contents) -> toConfigEntities(decodeConfigFile(contents))
        ConfigFile.ENTRIES_KEY in contents -> toConfigEntities(decodeBackupFile(contents))
        else -> fail(ImportError.NotABackup)
    }

    private fun decodeBackupFile(contents: JsonObject): BackupFile {
        val version = intField(contents, SCHEMA_VERSION_KEY)
        if (version !in BackupFile.SUPPORTED_VERSIONS) {
            fail(ImportError.UnsupportedVersion(version))
        }
        return decode(contents) { json.decodeFromJsonElement(BackupFile.serializer(), it) }
    }

    private fun decodeConfigFile(contents: JsonObject): ConfigFile {
        val version = intField(contents, ConfigFile.VERSION_KEY)
        if (version !in ConfigFile.SUPPORTED_VERSIONS) {
            fail(ImportError.UnsupportedVersion(version, fromConfigFile = true))
        }
        return decode(contents) { json.decodeFromJsonElement(ConfigFile.serializer(), it) }
    }

    private fun <T> decode(contents: JsonObject, decoder: (JsonObject) -> T): T =
        runCatching { decoder(contents) }
            .getOrElse { fail(ImportError.Malformed(it.message ?: "Unreadable backup contents")) }

    private fun intField(contents: JsonObject, key: String): Int =
        (contents[key] as? JsonPrimitive)?.intOrNull
            ?: fail(ImportError.Malformed("Backup is missing $key"))

    /** The setup in a full backup. v1 backups predate tags, so they get the seed tags. */
    private fun toConfigEntities(file: BackupFile): ConfigEntities {
        val categories = resolveCategories(file)
        val tags = if (file.schemaVersion < FIRST_VERSION_WITH_TAGS) {
            SeedTags.tags
        } else {
            resolveTags(file.tags, categories.mapTo(HashSet()) { it.id }).tags
        }
        return ConfigEntities(
            scales = file.scales.map(::validScale),
            tagCategories = categories,
            tags = tags,
            windows = file.promptWindows.map(::validWindow),
        )
    }

    private fun toConfigEntities(file: ConfigFile): ConfigEntities {
        val categories = resolveCategories(file.tagCategories)
        return ConfigEntities(
            scales = file.scales.map(::validScale),
            tagCategories = categories,
            tags = resolveTags(file.tags, categories.mapTo(HashSet()) { it.id }).tags,
            windows = file.promptWindows.map(::validWindow),
        )
    }

    /** Rejects a scale the editor could never save, since an import cannot ask the user. */
    private fun validScale(dto: ScaleDto): Scale {
        val scale = dto.toEntity()
        val range = scale.minValue.toFloat()..scale.maxValue.toFloat()
        val valid = scale.name.isNotBlank() &&
            scale.minValue < scale.maxValue &&
            scale.step.isFinite() && scale.step > 0f &&
            (scale.defaultValue == null || scale.defaultValue in range)
        if (!valid) fail(ImportError.Malformed("Invalid scale \"${dto.name}\""))
        return scale
    }

    /**
     * Applies the window editor's rules: a window that ends before it starts would never
     * fire, and the editor could not fix it without changing its times.
     */
    private fun validWindow(dto: PromptWindowDto): PromptWindow =
        runCatching { dto.toEntity() }.getOrNull()
            ?.takeIf { it.label.isNotBlank() && it.startTime.isBefore(it.endTime) }
            ?: fail(ImportError.Malformed("Invalid prompt window \"${dto.label}\""))

    private companion object {
        const val SCHEMA_VERSION_KEY = "schemaVersion"

        /** Matches the full import, which seeds default tags for v1 files. */
        const val FIRST_VERSION_WITH_TAGS = 2
    }
}

/**
 * True when the decrypted [contents] is a configuration-only file. The marker sits inside
 * the authenticated ciphertext, so it cannot be altered without the passphrase.
 */
internal fun isConfigFile(contents: JsonObject): Boolean =
    (contents[ConfigFile.KIND_KEY] as? JsonPrimitive)?.content == ConfigFile.KIND

internal fun ScaleDto.toEntity() = Scale(
    id = id, name = name, minValue = minValue, maxValue = maxValue,
    step = step, colorArgb = colorArgb, isBuiltIn = isBuiltIn,
    archived = archived, sortOrder = sortOrder, inverted = inverted,
    defaultValue = defaultValue,
)

/** Throws on an unknown slot or an unparseable time. */
internal fun PromptWindowDto.toEntity() = PromptWindow(
    id = id, label = label, slot = PromptSlot.valueOf(slot),
    startTime = LocalTime.parse(startTime), endTime = LocalTime.parse(endTime),
    enabled = enabled,
)

private fun fail(error: ImportError): Nothing = throw BackupImportException(error)
