package com.yamibo.pocket300.ui

import android.content.Context
import androidx.core.content.edit

internal enum class AppColorTheme {
    SYSTEM,
    SAKURA,
    VIOLET,
    BLUE,
    GREEN,
}

internal class AppThemePreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("app_theme_preferences", Context.MODE_PRIVATE)

    fun load(): AppColorTheme = runCatching {
        AppColorTheme.valueOf(
            preferences.getString("color_theme", AppColorTheme.SYSTEM.name).orEmpty(),
        )
    }.getOrDefault(AppColorTheme.SYSTEM)

    fun save(value: AppColorTheme) {
        preferences.edit { putString("color_theme", value.name) }
    }
}
