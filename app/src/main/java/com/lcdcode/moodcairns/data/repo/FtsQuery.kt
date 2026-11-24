package com.lcdcode.moodcairns.data.repo

// Splits like the unicode61 tokenizer: letters, digits and combining marks form terms.
private val NON_TERM = Regex("[^\\p{L}\\p{N}\\p{M}]+")

internal const val MAX_QUERY_TERMS = 16

/**
 * Turns free text typed in the search box into a safe FTS4 MATCH expression: every term is
 * a quoted prefix query (`"word*"`), and terms are ANDed. Quoting matters because SQLCipher
 * is built with the enhanced query syntax, where bare AND, OR, NOT, NEAR and parentheses are
 * operators. Since terms contain only letters, digits and marks, no input can reach the
 * query grammar. Returns "" when there is nothing to search for.
 */
internal fun buildFtsQuery(raw: String): String =
    raw.split(NON_TERM)
        .filter(String::isNotEmpty)
        .take(MAX_QUERY_TERMS)
        .joinToString(" ") { "\"$it*\"" }
