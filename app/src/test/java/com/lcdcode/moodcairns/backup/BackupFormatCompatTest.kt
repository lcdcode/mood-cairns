package com.lcdcode.moodcairns.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the backup schema's backward compatibility: a v1 export (no tags, no
 * per-entry tagIds) must keep parsing under the v2 model via serialization
 * defaults, and a v2 file must round-trip its tags.
 */
class BackupFormatCompatTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun versionConstants_coverV1AndV2() {
        assertEquals(2, BackupFile.CURRENT_VERSION)
        assertTrue(1 in BackupFile.SUPPORTED_VERSIONS)
        assertTrue(2 in BackupFile.SUPPORTED_VERSIONS)
    }

    @Test
    fun v1Json_parsesWithEmptyTagDefaults() {
        val v1 = """
            {
              "schemaVersion": 1,
              "exportedAt": "2026-01-01T00:00:00Z",
              "appVersion": "1.0",
              "scales": [
                {"id": 1, "name": "Happiness", "minValue": 1, "maxValue": 10, "step": 1.0,
                 "colorArgb": 0, "isBuiltIn": true, "archived": false, "sortOrder": 0}
              ],
              "promptWindows": [],
              "entries": [
                {"id": 5, "recordedAt": "2026-01-01T08:00:00Z", "slot": "MANUAL",
                 "promptWindowId": null, "note": "old entry",
                 "values": [{"scaleId": 1, "value": 7.0}]}
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString(BackupFile.serializer(), v1)

        assertEquals(1, parsed.schemaVersion)
        assertTrue(parsed.tags.isEmpty())
        assertEquals(1, parsed.entries.size)
        assertTrue(parsed.entries.single().tagIds.isEmpty())
        assertFalse(parsed.scales.single().inverted)
    }

    @Test
    fun scaleWithoutInvertedKey_defaultsToFalse() {
        val preInverseScale = """
            {"id": 3, "name": "Pain", "minValue": 1, "maxValue": 10, "step": 1.0,
             "colorArgb": 0, "isBuiltIn": true, "archived": false, "sortOrder": 4}
        """.trimIndent()

        val parsed = json.decodeFromString(ScaleDto.serializer(), preInverseScale)

        assertFalse(parsed.inverted)
    }

    @Test
    fun invertedScale_roundTrips() {
        val original = ScaleDto(
            id = 7, name = "Calm", minValue = -5, maxValue = 5, step = 1.0f,
            colorArgb = 0x6BAA75, isBuiltIn = false, archived = false, sortOrder = 5,
            inverted = true,
        )

        val decoded = json.decodeFromString(
            ScaleDto.serializer(),
            json.encodeToString(ScaleDto.serializer(), original),
        )

        assertEquals(original, decoded)
        assertTrue(decoded.inverted)
    }

    @Test
    fun v2File_roundTripsTagsAndLinks() {
        val original = BackupFile(
            schemaVersion = BackupFile.CURRENT_VERSION,
            exportedAt = "2026-01-01T00:00:00Z",
            appVersion = "1.1",
            scales = emptyList(),
            promptWindows = emptyList(),
            entries = listOf(
                EntryDto(
                    id = 5, recordedAt = "2026-01-01T08:00:00Z", slot = "MANUAL",
                    promptWindowId = null, note = null,
                    values = emptyList(),
                    tagIds = listOf(1, 3),
                ),
            ),
            tags = listOf(
                TagDto(id = 1, name = "Home", category = "PLACE", sortOrder = 0),
                TagDto(id = 3, name = "Alone", category = "PERSON", sortOrder = 4),
            ),
        )

        val decoded = json.decodeFromString(
            BackupFile.serializer(),
            json.encodeToString(BackupFile.serializer(), original),
        )

        assertEquals(original, decoded)
        assertEquals(listOf(1L, 3L), decoded.entries.single().tagIds)
    }
}
