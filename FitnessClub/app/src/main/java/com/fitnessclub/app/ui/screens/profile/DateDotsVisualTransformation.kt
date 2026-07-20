package com.fitnessclub.app.ui.screens.profile

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * В стейте — только цифры (до 8); на экране — `дд.мм.гггг`.
 */
class DateDotsVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(8)
        val formatted = formatDateDots(digits)
        val digitIndices = formatted.mapIndexedNotNull { i, c ->
            if (c.isDigit()) i else null
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, digits.length)
                if (digitIndices.isEmpty()) return formatted.length
                return when {
                    o <= 0 -> 0
                    o >= digitIndices.size -> formatted.length
                    else -> digitIndices[o - 1] + 1
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                val limit = minOf(offset, formatted.length)
                var count = 0
                for (i in 0 until limit) {
                    if (formatted[i].isDigit()) count++
                }
                return count.coerceIn(0, digits.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), mapping)
    }

    companion object {
        fun formatDateDots(digits: String): String {
            val d = digits.filter { it.isDigit() }.take(8)
            return buildString {
                d.forEachIndexed { i, c ->
                    if (i == 2 || i == 4) append('.')
                    append(c)
                }
            }
        }
    }
}
