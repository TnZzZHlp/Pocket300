package com.yamibo.pocket300.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.yamibo.pocket300.ui.AppColorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketThemeTest {
    @Test
    fun staticThemesShareTheNeutralSurfaceHierarchy() {
        for (dark in listOf(false, true)) {
            val base = staticColorScheme(AppColorTheme.BEIGE, dark)
            for (theme in AppColorTheme.entries) {
                val colors = staticColorScheme(theme, dark)
                assertEquals(base.background, colors.background)
                assertEquals(base.surfaceContainerLow, colors.surfaceContainerLow)
                assertEquals(base.outlineVariant, colors.outlineVariant)
                assertEquals(colors.primary, colors.surfaceTint)
            }
        }
    }

    @Test
    fun textAndActionsRemainReadableAcrossAllStaticThemes() {
        for (theme in AppColorTheme.entries) {
            for (dark in listOf(false, true)) {
                val colors = staticColorScheme(theme, dark)
                val pairs = listOf(
                    colors.onSurface to colors.surface,
                    colors.onSurfaceVariant to colors.surfaceContainerLow,
                    colors.onSurfaceVariant to colors.surfaceContainerHigh,
                    colors.onSurfaceVariant to colors.surfaceContainerHighest,
                    colors.primary to colors.surfaceContainerLow,
                    colors.onPrimary to colors.primary,
                    colors.onPrimaryContainer to colors.primaryContainer,
                    colors.onSecondaryContainer to colors.secondaryContainer,
                )
                for ((text, surface) in pairs) {
                    val contrast = contrast(text, surface)
                    assertTrue("$theme dark=$dark contrast=$contrast", contrast >= 4.5f)
                }
            }
        }
    }

    @Test
    fun readStateChangesOnlyTheTitleColorAndKeepsItOpaque() {
        for (dark in listOf(false, true)) {
            val colors = staticColorScheme(AppColorTheme.BEIGE, dark)
            assertEquals(colors.onSurface, threadTitleColor(colors, isRead = false))
            assertEquals(colors.onSurfaceVariant, threadTitleColor(colors, isRead = true))
            assertEquals(1f, threadTitleColor(colors, isRead = true).alpha, 0f)
        }
    }

    private fun contrast(text: Color, background: Color): Float {
        val lighter = maxOf(text.luminance(), background.luminance())
        val darker = minOf(text.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
