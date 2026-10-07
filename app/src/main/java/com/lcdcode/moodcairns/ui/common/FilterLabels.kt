package com.lcdcode.moodcairns.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lcdcode.moodcairns.R

/**
 * Label for a filter row's reset chip: "All" while nothing is filtered (a state
 * indicator), "Clear" once something is (an action).
 */
@Composable
fun allOrClearLabel(unfiltered: Boolean): String =
    stringResource(if (unfiltered) R.string.common_all else R.string.common_clear)
