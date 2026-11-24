package com.lcdcode.moodcairns.ui.common

/**
 * Like [String.take], but never cuts a surrogate pair in half: emoji and other characters
 * outside the Basic Multilingual Plane are two UTF-16 chars, and keeping only the first
 * would leave a broken character at the end of saved text.
 */
internal fun String.takeWholeChars(maxChars: Int): String {
    if (length <= maxChars) return this
    if (maxChars <= 0) return ""
    val end = if (Character.isHighSurrogate(this[maxChars - 1])) maxChars - 1 else maxChars
    return substring(0, end)
}
