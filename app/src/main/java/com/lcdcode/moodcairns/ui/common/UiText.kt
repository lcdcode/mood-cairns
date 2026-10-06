package com.lcdcode.moodcairns.ui.common

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * User-facing text produced outside composition (ViewModels, services). It holds a
 * resource reference instead of a resolved String, so it is rendered in whatever
 * language is active when it is displayed, not when it was created.
 *
 * Format args may themselves be [UiText]; they are resolved first, which lets a
 * message wrap another (e.g. "Import failed: %1$s" around a specific reason).
 */
sealed interface UiText {

    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** [args] defaults to just [count], the common "%1$d items" case. */
    data class Plural(
        @PluralsRes val id: Int,
        val count: Int,
        val args: List<Any> = listOf(count),
    ) : UiText

    fun resolve(context: Context): String = resolve(context.resources)

    fun resolve(resources: Resources): String = when (this) {
        is Res -> resources.getString(id, *resolveArgs(args, resources))
        is Plural -> resources.getQuantityString(id, count, *resolveArgs(args, resources))
    }

    companion object {
        /** [id] formatted with [cause]'s message (or class name) as its only arg. */
        fun withDetail(@StringRes id: Int, cause: Throwable): UiText =
            Res(id, listOf(cause.message ?: cause.javaClass.simpleName))
    }
}

@Composable
@ReadOnlyComposable
fun UiText.asString(): String {
    // Reading the configuration subscribes to it, so a locale change re-resolves.
    LocalConfiguration.current
    return resolve(LocalContext.current.resources)
}

private fun resolveArgs(args: List<Any>, resources: Resources): Array<Any> =
    args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()
