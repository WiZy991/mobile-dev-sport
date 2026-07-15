package com.fitnessclub.app.ui.components

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Синхронизирует цвет статус-бара и контраст системных иконок с хэдером экрана.
 *
 * @param darkIcons `true` — тёмные иконки (для светлого фона), `false` — светлые (для тёмного/оранжевого).
 */
@Composable
fun StatusBarEffect(
    color: Color,
    darkIcons: Boolean = color.luminance() > 0.5f,
    navigationKey: Any? = null,
) {
    val view = LocalView.current
    androidx.compose.runtime.key(navigationKey) {
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                @Suppress("DEPRECATION")
                window.statusBarColor = color.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
            }
        }
    }
}
