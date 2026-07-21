package com.lcdcode.moodcairns.ui.tags

import com.lcdcode.moodcairns.data.entity.TagCategory

val TagCategory.displayName: String
    get() = when (this) {
        TagCategory.PLACE -> "Places"
        TagCategory.PERSON -> "People"
        TagCategory.ACTIVITY -> "Activities"
    }
