package com.fitnessclub.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Поле анкеты без цветной подсветки фокуса: оранжевый бренд-цвет читается как ошибка.
 * Ошибка (isError) остаётся красной.
 */
@Composable
fun calmTextFieldColors(containerColor: Color? = null): TextFieldColors {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val container = containerColor ?: MaterialTheme.colorScheme.surfaceContainerHighest
    return TextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        focusedIndicatorColor = neutral,
        unfocusedIndicatorColor = neutral,
        focusedLabelColor = neutral,
        unfocusedLabelColor = neutral,
        focusedLeadingIconColor = neutral,
        focusedTrailingIconColor = neutral,
        cursorColor = MaterialTheme.colorScheme.onSurface,
    )
}
