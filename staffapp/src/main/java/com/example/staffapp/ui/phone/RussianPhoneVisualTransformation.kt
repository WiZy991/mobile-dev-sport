package com.example.staffapp.ui.phone

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * В стейте — только 10 национальных цифр; на экране — `+7 (XXX) XXX-XX-XX`.
 */
class RussianPhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(10)
        val formatted = formatRussianPhoneMask(digits)
        val digitIndices = formatted.mapIndexedNotNull { i, c ->
            if (!c.isDigit()) return@mapIndexedNotNull null
            if (formatted.startsWith("+7") && i == 1) return@mapIndexedNotNull null
            i
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, digits.length)
                if (digitIndices.isEmpty()) {
                    return formatted.length
                }
                return when {
                    o <= 0 -> digitIndices.first()
                    o >= digitIndices.size -> formatted.length
                    else -> digitIndices[o - 1] + 1
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                val limit = minOf(offset, formatted.length)
                var count = 0
                for (i in 0 until limit) {
                    val c = formatted[i]
                    if (!c.isDigit()) continue
                    if (formatted.startsWith("+7") && i == 1) continue
                    count++
                }
                return count.coerceIn(0, digits.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
