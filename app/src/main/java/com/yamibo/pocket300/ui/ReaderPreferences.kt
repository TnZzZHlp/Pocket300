package com.yamibo.pocket300.ui

import android.content.Context
import androidx.core.content.edit

internal enum class ReaderTone { SYSTEM, PAPER, MINT, NIGHT }

internal data class ReaderPreferences(
    val fontSizeSp: Float = 18f,
    val lineHeightMultiplier: Float = 1.65f,
    val tone: ReaderTone = ReaderTone.SYSTEM,
)

internal class ReaderPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("reader_preferences", Context.MODE_PRIVATE)

    fun load() = ReaderPreferences(
        fontSizeSp = preferences.getFloat("font_size_sp", 18f).coerceIn(14f, 26f),
        lineHeightMultiplier = preferences.getFloat("line_height_multiplier", 1.65f).coerceIn(1.35f, 2f),
        tone = runCatching {
            ReaderTone.valueOf(preferences.getString("tone", ReaderTone.SYSTEM.name).orEmpty())
        }.getOrDefault(ReaderTone.SYSTEM),
    )

    fun save(value: ReaderPreferences) {
        preferences.edit {
            putFloat("font_size_sp", value.fontSizeSp)
            putFloat("line_height_multiplier", value.lineHeightMultiplier)
            putString("tone", value.tone.name)
        }
    }
}
