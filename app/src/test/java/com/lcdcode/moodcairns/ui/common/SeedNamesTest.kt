package com.lcdcode.moodcairns.ui.common

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.db.Seed
import com.lcdcode.moodcairns.data.db.SeedTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Built-in rows are recognized by their stored English name. These tests keep the
 * translation table and the seed data from drifting apart.
 */
class SeedNamesTest {

    @Test
    fun every_seed_scale_has_a_translation_and_no_extras() {
        assertEquals(Seed.scales.map { it.name }.toSet(), SeedNames.scaleNames)
        Seed.scales.forEach { assertNotNull(it.name, SeedNames.scaleRes(it.name, it.isBuiltIn)) }
    }

    @Test
    fun every_seed_tag_has_a_translation_and_no_extras() {
        assertEquals(SeedTags.tags.map { it.name }.toSet(), SeedNames.tagNames)
    }

    @Test
    fun every_seed_window_has_a_translation_and_no_extras() {
        assertEquals(Seed.windows.map { it.label }.toSet(), SeedNames.windowLabels)
    }

    @Test
    fun seed_tag_names_are_unique_across_categories() {
        // Tags match on name alone, so a duplicate name would make one ambiguous.
        val names = SeedTags.tags.map { it.name }
        assertEquals(names.distinct(), names)
    }

    @Test
    fun scale_matches_only_when_built_in() {
        assertEquals(
            R.string.seed_scale_happiness,
            SeedNames.scaleRes("Happiness", isBuiltIn = true),
        )
        assertNull(SeedNames.scaleRes("Happiness", isBuiltIn = false))
    }

    @Test
    fun renamed_or_custom_rows_keep_their_own_text() {
        assertNull(SeedNames.tagRes("Zuhause"))
        assertNull(SeedNames.tagRes("home"))
        assertNull(SeedNames.windowRes("Lunch"))
        assertNull(SeedNames.scaleRes("Energy", isBuiltIn = true))
    }
}
