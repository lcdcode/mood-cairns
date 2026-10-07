package com.lcdcode.moodcairns.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.entity.Tag

/**
 * Translated display names for the built-in scales, tags, and prompt windows.
 *
 * Those rows keep their canonical English name in the database (see Seed.kt), which
 * identifies them, so every install and backup is recognized without a schema change.
 * A row the user has renamed no longer matches and shows the user's own text.
 *
 * Seed tag names are unique across categories, so tags match on name alone and stay
 * translated if moved to another category. Built-in scales also require isBuiltIn,
 * since they cannot be renamed and users cannot create a duplicate name.
 */
object SeedNames {

    private val scales: Map<String, Int> = mapOf(
        "Happiness" to R.string.seed_scale_happiness,
        "Anxiety" to R.string.seed_scale_anxiety,
        "Stress" to R.string.seed_scale_stress,
        "Boredom" to R.string.seed_scale_boredom,
        "Pain" to R.string.seed_scale_pain,
    )

    private val tags: Map<String, Int> = mapOf(
        "Home" to R.string.seed_tag_home,
        "Work" to R.string.seed_tag_work,
        "School" to R.string.seed_tag_school,
        "Commute" to R.string.seed_tag_commute,
        "Outdoors" to R.string.seed_tag_outdoors,
        "Travel" to R.string.seed_tag_travel,
        "Family" to R.string.seed_tag_family,
        "Friends" to R.string.seed_tag_friends,
        "Partner" to R.string.seed_tag_partner,
        "Coworkers" to R.string.seed_tag_coworkers,
        "Alone" to R.string.seed_tag_alone,
        "Exercise" to R.string.seed_tag_exercise,
        "Socializing" to R.string.seed_tag_socializing,
        "Reading" to R.string.seed_tag_reading,
        "Gaming" to R.string.seed_tag_gaming,
        "Chores" to R.string.seed_tag_chores,
        "Rest" to R.string.seed_tag_rest,
        "Joy" to R.string.seed_tag_joy,
        "Excited" to R.string.seed_tag_excited,
        "Grateful" to R.string.seed_tag_grateful,
        "Content" to R.string.seed_tag_content,
        "Calm" to R.string.seed_tag_calm,
        "Surprise" to R.string.seed_tag_surprise,
        "Frustrated" to R.string.seed_tag_frustrated,
        "Lonely" to R.string.seed_tag_lonely,
        "Sadness" to R.string.seed_tag_sadness,
        "Fear" to R.string.seed_tag_fear,
        "Anger" to R.string.seed_tag_anger,
        "Disgust" to R.string.seed_tag_disgust,
    )

    private val windows: Map<String, Int> = mapOf(
        "Morning" to R.string.seed_window_morning,
        "Evening" to R.string.seed_window_evening,
    )

    @StringRes
    fun scaleRes(name: String, isBuiltIn: Boolean): Int? = if (isBuiltIn) scales[name] else null

    @StringRes
    fun tagRes(name: String): Int? = tags[name]

    @StringRes
    fun windowRes(label: String): Int? = windows[label]

    internal val scaleNames: Set<String> get() = scales.keys
    internal val tagNames: Set<String> get() = tags.keys
    internal val windowLabels: Set<String> get() = windows.keys
}

@Composable
fun Scale.displayName(): String =
    SeedNames.scaleRes(name, isBuiltIn)?.let { stringResource(it) } ?: name

@Composable
fun Tag.displayName(): String = SeedNames.tagRes(name)?.let { stringResource(it) } ?: name

@Composable
fun PromptWindow.displayLabel(): String =
    SeedNames.windowRes(label)?.let { stringResource(it) } ?: label

/** The scale's display name as a [UiText] format arg, for text built outside composition. */
fun Scale.displayNameArg(): Any =
    SeedNames.scaleRes(name, isBuiltIn)?.let { UiText.Res(it) } ?: name

/** A stored window label in [context]'s language, e.g. for a notification title. */
fun windowDisplayLabel(context: Context, label: String): String =
    SeedNames.windowRes(label)?.let(context::getString) ?: label
