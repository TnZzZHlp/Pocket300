package com.yamibo.pocket300.ui.reader

import android.content.Context
import androidx.core.content.edit
import com.yamibo.pocket300.logging.AppLogger

internal enum class ImageReaderMode {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    VERTICAL,
    WEBTOON,
}

internal enum class ImageReaderScaleType {
    FIT_SCREEN,
    FIT_WIDTH,
}

internal enum class ImageReaderBackground {
    BLACK,
    GRAY,
    WHITE,
}

internal data class ImageReaderPreferences(
    val mode: ImageReaderMode = ImageReaderMode.LEFT_TO_RIGHT,
    val scaleType: ImageReaderScaleType = ImageReaderScaleType.FIT_SCREEN,
    val background: ImageReaderBackground = ImageReaderBackground.BLACK,
    val tapNavigation: Boolean = true,
    val showPageNumber: Boolean = true,
)

internal class ImageReaderPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load() = ImageReaderPreferences(
        mode = enumPreference(MODE_KEY, ImageReaderMode.LEFT_TO_RIGHT),
        scaleType = enumPreference(SCALE_TYPE_KEY, ImageReaderScaleType.FIT_SCREEN),
        background = enumPreference(BACKGROUND_KEY, ImageReaderBackground.BLACK),
        tapNavigation = preferences.getBoolean(TAP_NAVIGATION_KEY, true),
        showPageNumber = preferences.getBoolean(SHOW_PAGE_NUMBER_KEY, true),
    )

    fun save(value: ImageReaderPreferences) {
        preferences.edit {
            putString(MODE_KEY, value.mode.name)
            putString(SCALE_TYPE_KEY, value.scaleType.name)
            putString(BACKGROUND_KEY, value.background.name)
            putBoolean(TAP_NAVIGATION_KEY, value.tapNavigation)
            putBoolean(SHOW_PAGE_NUMBER_KEY, value.showPageNumber)
        }
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T {
        val savedValue = preferences.getString(key, fallback.name).orEmpty()
        return enumValues<T>().firstOrNull { it.name == savedValue } ?: run {
            AppLogger.warn(TAG) { "Invalid saved $key value; using ${fallback.name}" }
            fallback
        }
    }

    private companion object {
        const val TAG = "ImageReaderPreferences"
        const val PREFERENCES_NAME = "image_reader_preferences"
        const val MODE_KEY = "mode"
        const val SCALE_TYPE_KEY = "scale_type"
        const val BACKGROUND_KEY = "background"
        const val TAP_NAVIGATION_KEY = "tap_navigation"
        const val SHOW_PAGE_NUMBER_KEY = "show_page_number"
    }
}
