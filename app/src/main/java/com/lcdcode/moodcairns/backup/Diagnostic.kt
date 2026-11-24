package com.lcdcode.moodcairns.backup

import java.util.Locale

/**
 * Exception text made safe to show and log. Built only through [of], which escapes control,
 * format (such as bidi overrides), separator and lone surrogate characters, and cuts the
 * result to [MAX_CHARS] plus an ellipsis. Reads at most that much of the input.
 */
@JvmInline
value class Diagnostic private constructor(val text: String) {

    override fun toString(): String = text

    companion object {
        const val MAX_CHARS = 120
        private const val ELLIPSIS = "…"
        private const val BMP_ESCAPE = "\\u%04X"
        private const val SUPPLEMENTARY_ESCAPE = "\\U%08X"

        private val UNSAFE_TYPES = setOf(
            Character.CONTROL,
            Character.FORMAT,
            Character.LINE_SEPARATOR,
            Character.PARAGRAPH_SEPARATOR,
            Character.SURROGATE,
        ).map { it.toInt() }.toSet()

        fun of(raw: String): Diagnostic = Diagnostic(sanitize(raw))

        fun of(cause: Throwable): Diagnostic = of(cause.message ?: cause.javaClass.simpleName)

        private fun sanitize(raw: String): String {
            val out = StringBuilder()
            var offset = 0
            while (offset < raw.length) {
                val codePoint = raw.codePointAt(offset)
                val piece = render(codePoint)
                if (out.length + piece.length > MAX_CHARS) return out.append(ELLIPSIS).toString()
                out.append(piece)
                offset += Character.charCount(codePoint)
            }
            return out.toString()
        }

        private fun render(codePoint: Int): String = when {
            codePoint == '\n'.code -> "\\n"
            codePoint == '\r'.code -> "\\r"
            codePoint == '\t'.code -> "\\t"
            Character.getType(codePoint) in UNSAFE_TYPES -> escape(codePoint)
            else -> String(Character.toChars(codePoint))
        }

        private fun escape(codePoint: Int): String =
            if (Character.isBmpCodePoint(codePoint)) {
                BMP_ESCAPE.format(Locale.ROOT, codePoint)
            } else {
                SUPPLEMENTARY_ESCAPE.format(Locale.ROOT, codePoint)
            }
    }
}
