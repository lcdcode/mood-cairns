package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.db.SeedTags
import com.lcdcode.moodcairns.data.entity.TagCategory
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins how decrypted contents are classified: a configuration file must never be
 * restorable as a full backup (by this or any older app version), and files from a
 * newer format are refused as unsupported before their shape is decoded.
 */
class BackupContentsDecoderTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val decoder = BackupContentsDecoder(json)

    @Test
    fun configFile_doesNotDecodeAsBackupFile_soOlderAppsCannotRestoreIt() {
        // Older versions decode the plaintext straight into BackupFile. The missing
        // required "entries" key is what stops them from wiping entries.
        val text = json.encodeToString(ConfigFile.serializer(), configFile())

        assertThrows(SerializationException::class.java) {
            json.decodeFromString(BackupFile.serializer(), text)
        }
    }

    @Test
    fun fullRestore_refusesAConfigFile() {
        val error = importError { decoder.forFullRestore(encode(configFile())) }

        assertEquals(ImportError.ConfigOnlyFile, error)
    }

    @Test
    fun fullRestore_stillReadsABackup() {
        val backup = backupFile()

        assertEquals(backup, decoder.forFullRestore(encode(backup)))
    }

    @Test
    fun configImport_readsAConfigFile() {
        val entities = decoder.forConfigImport(encode(configFile()))

        assertEquals(listOf("Sleep"), entities.scales.map { it.name })
        assertEquals(listOf("Gym"), entities.tags.map { it.name })
        assertEquals(listOf("Morning"), entities.windows.map { it.label })
        assertEquals(TagCategory.OTHER_ID, entities.tagCategories.last().id)
    }

    @Test
    fun configImport_takesOnlyTheSetupFromAFullBackup() {
        val entities = decoder.forConfigImport(encode(backupFile()))

        assertEquals(listOf("Sleep"), entities.scales.map { it.name })
        assertEquals(listOf("Gym"), entities.tags.map { it.name })
    }

    @Test
    fun configImport_fromAV1Backup_usesTheSeedTags() {
        val entities = decoder.forConfigImport(encode(backupFile(version = 1)))

        assertEquals(SeedTags.tags, entities.tags)
    }

    @Test
    fun newerConfigVersion_isUnsupported_evenIfItsShapeChanged() {
        val future = JsonObject(
            mapOf(
                ConfigFile.KIND_KEY to JsonPrimitive(ConfigFile.KIND),
                ConfigFile.VERSION_KEY to JsonPrimitive(ConfigFile.CURRENT_VERSION + 1),
                "scales" to JsonPrimitive("a shape this version cannot read"),
            ),
        )

        val error = importError { decoder.forConfigImport(future) }

        assertEquals(ImportError.UnsupportedVersion(ConfigFile.CURRENT_VERSION + 1), error)
    }

    @Test
    fun newerBackupVersion_isUnsupportedForConfigImport() {
        val future = encode(backupFile(version = BackupFile.CURRENT_VERSION + 1))

        val error = importError { decoder.forConfigImport(future) }

        assertEquals(ImportError.UnsupportedVersion(BackupFile.CURRENT_VERSION + 1), error)
    }

    @Test
    fun configImport_ofSomethingElse_isNotABackup() {
        val error = importError { decoder.forConfigImport(JsonObject(emptyMap())) }

        assertEquals(ImportError.NotABackup, error)
    }

    @Test
    fun invalidScales_areRejected() {
        val invalid = listOf(
            sleep().copy(name = " "),
            sleep().copy(minValue = 5, maxValue = 5),
            sleep().copy(step = 0f),
            sleep().copy(step = -1f),
            sleep().copy(defaultValue = 11f),
        )
        for (scale in invalid) {
            val error = importError {
                decoder.forConfigImport(encode(configFile(scales = listOf(scale))))
            }
            assertTrue("$scale should be rejected, got $error", error is ImportError.Malformed)
        }
    }

    @Test
    fun windowWithUnknownSlot_isMalformed() {
        val badWindow = morning().copy(slot = "NOON")

        val error = importError {
            decoder.forConfigImport(encode(configFile(windows = listOf(badWindow))))
        }

        assertTrue(error is ImportError.Malformed)
    }

    @Test
    fun blankTagNames_areDropped() {
        val tags = listOf(gym(), gym().copy(id = 2, name = "  "))

        val entities = decoder.forConfigImport(encode(configFile(tags = tags)))

        assertEquals(listOf("Gym"), entities.tags.map { it.name })
    }

    @Test
    fun kindMarker_mustBeTheExactString() {
        assertTrue(isConfigFile(encode(configFile())))
        assertFalse(isConfigFile(encode(backupFile())))
        assertFalse(isConfigFile(JsonObject(mapOf(ConfigFile.KIND_KEY to JsonPrimitive("other")))))
        assertFalse(isConfigFile(JsonObject(mapOf(ConfigFile.KIND_KEY to JsonObject(emptyMap())))))
    }

    @Test
    fun parseObject_rejectsNonObjects() {
        val error = importError { decoder.parseObject("[1, 2]") }

        assertTrue(error is ImportError.Malformed)
    }

    // ---- helpers ----

    private fun importError(block: () -> Unit): ImportError =
        assertThrows(BackupImportException::class.java) { block() }.error

    private fun encode(file: ConfigFile): JsonObject =
        json.parseToJsonElement(json.encodeToString(ConfigFile.serializer(), file)).jsonObject

    private fun encode(file: BackupFile): JsonObject =
        json.parseToJsonElement(json.encodeToString(BackupFile.serializer(), file)).jsonObject

    private fun sleep() = ScaleDto(
        id = 1, name = "Sleep", minValue = 1, maxValue = 10, step = 1f, colorArgb = 0,
        isBuiltIn = false, archived = false, sortOrder = 0,
    )

    private fun gym() = TagDto(id = 1, name = "Gym", category = "ACTIVITY", sortOrder = 0)

    private fun morning() = PromptWindowDto(
        id = 1, label = "Morning", slot = "MORNING", startTime = "08:00", endTime = "10:00",
        enabled = true,
    )

    private fun categories() = listOf(
        TagCategoryDto(id = "ACTIVITY", sortOrder = 0),
        TagCategoryDto(id = TagCategory.OTHER_ID, sortOrder = 1),
    )

    private fun configFile(
        scales: List<ScaleDto> = listOf(sleep()),
        tags: List<TagDto> = listOf(gym()),
        windows: List<PromptWindowDto> = listOf(morning()),
    ) = ConfigFile(
        kind = ConfigFile.KIND,
        configVersion = ConfigFile.CURRENT_VERSION,
        exportedAt = "2026-11-12T00:00:00Z",
        appVersion = "test",
        scales = scales,
        promptWindows = windows,
        tags = tags,
        tagCategories = categories(),
    )

    private fun backupFile(version: Int = BackupFile.CURRENT_VERSION) = BackupFile(
        schemaVersion = version,
        exportedAt = "2026-11-12T00:00:00Z",
        appVersion = "test",
        scales = listOf(sleep()),
        promptWindows = listOf(morning()),
        entries = listOf(
            EntryDto(
                id = 1, recordedAt = "2026-11-12T08:00:00Z", slot = "MORNING",
                promptWindowId = 1, note = null, values = emptyList(),
            ),
        ),
        tags = listOf(gym()),
        tagCategories = categories(),
    )
}
