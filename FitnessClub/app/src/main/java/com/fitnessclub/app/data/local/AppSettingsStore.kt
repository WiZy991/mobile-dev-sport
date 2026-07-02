package com.fitnessclub.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsPrefs by preferencesDataStore(name = "app_settings")

enum class ThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun from(value: String?): ThemeMode = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

enum class AppLanguage(val value: String, val tag: String, val label: String) {
    SYSTEM("system", "", "Системный"),
    RUSSIAN("ru", "ru", "Русский");

    companion object {
        fun from(value: String?): AppLanguage = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val languageKey = stringPreferencesKey("app_language")

    val themeMode: Flow<ThemeMode> = context.appSettingsPrefs.data.map { prefs ->
        ThemeMode.from(prefs[themeKey])
    }

    val appLanguage: Flow<AppLanguage> = context.appSettingsPrefs.data.map { prefs ->
        AppLanguage.from(prefs[languageKey])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appSettingsPrefs.edit { prefs ->
            prefs[themeKey] = mode.value
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.appSettingsPrefs.edit { prefs ->
            prefs[languageKey] = language.value
        }
    }
}
