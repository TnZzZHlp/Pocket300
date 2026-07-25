package com.yamibo.pocket300.ui

import android.content.Context
import androidx.core.content.edit
import com.yamibo.pocket300.logging.AppLogger

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
    }.getOrElse { error ->
        AppLogger.warn(TAG, error) { "Invalid saved app theme; using the default theme" }
        AppColorTheme.BEIGE
    }

    fun save(value: AppColorTheme) {
        preferences.edit { putString("color_theme", value.name) }
    }

    private companion object {
        const val TAG = "Preferences"
    }
}
