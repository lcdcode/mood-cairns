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
    fun everySeedScale_hasATranslation_andNoExtras() {
        assertEquals(Seed.scales.map { it.name }.toSet(), SeedNames.scaleNames)
        Seed.scales.forEach { assertNotNull(it.name, SeedNames.scaleRes(it.name, it.isBuiltIn)) }
    }

    @Test
    fun everySeedTag_hasATranslation_andNoExtras() {
        assertEquals(SeedTags.tags.map { it.name }.toSet(), SeedNames.tagNames)
    }

    @Test
    fun everySeedWindow_hasATranslation_andNoExtras() {
        assertEquals(Seed.windows.map { it.label }.toSet(), SeedNames.windowLabels)
    }

    @Test
    fun seedTagNames_areUniqueAcrossCategories() {
        // Tags match on name alone, so a duplicate name would make one ambiguous.
        val names = SeedTags.tags.map { it.name }
        assertEquals(names.distinct(), names)
    }

    @Test
    fun scale_matchesOnlyWhenBuiltIn() {
        assertEquals(
            R.string.seed_scale_happiness,
            SeedNames.scaleRes("Happiness", isBuiltIn = true),
        )
        assertNull(SeedNames.scaleRes("Happiness", isBuiltIn = false))
    }

    @Test
    fun renamedOrCustomRows_keepTheirOwnText() {
        assertNull(SeedNames.tagRes("Zuhause"))
        assertNull(SeedNames.tagRes("home"))
        assertNull(SeedNames.windowRes("Lunch"))
        assertNull(SeedNames.scaleRes("Energy", isBuiltIn = true))
    }
}
