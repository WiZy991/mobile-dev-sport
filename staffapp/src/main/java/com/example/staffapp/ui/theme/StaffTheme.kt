package com.example.staffapp.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = StaffPrimary,
    onPrimary = StaffOnPrimary,
    primaryContainer = StaffPrimaryDark,
    secondary = StaffAccentBlue,
    background = StaffBackground,
    onBackground = StaffOnBackground,
    surface = StaffSurface,
    surfaceVariant = Color(0xFFEEEEEE),
    onSurface = StaffOnSurface,
    onSurfaceVariant = StaffOnSurfaceVariant,
    error = StaffError,
)

@Composable
fun StaffTheme(content: @Composable () -> Unit) {
    val colorScheme = LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = StaffShapes,
        content = content,
    )
}
