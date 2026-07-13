package com.yamibo.pocket300.ui

import android.content.Context
import androidx.core.content.edit

internal enum class AppColorTheme {
    SYSTEM,
    BEIGE,
    VIOLET,
    BLUE,
    GREEN,
}

internal class AppThemePreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("app_theme_preferences", Context.MODE_PRIVATE)

    fun load(): AppColorTheme = runCatching {
        when (val savedTheme = preferences.getString("color_theme", AppColorTheme.BEIGE.name).orEmpty()) {
            "SAKURA" -> AppColorTheme.BEIGE
            else -> AppColorTheme.valueOf(savedTheme)
        }
    }.getOrDefault(AppColorTheme.BEIGE)

    fun save(value: AppColorTheme) {
        preferences.edit { putString("color_theme", value.name) }
    }
}
