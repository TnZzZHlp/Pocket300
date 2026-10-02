package com.yamibo.pocket300.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.yamibo.pocket300.ui.ReaderTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderColorsTest {
    @Test
    fun systemTonePreservesTheSelectedAppTheme() {
        assertSame(LightColors, readerColorScheme(ReaderTone.SYSTEM, LightColors))
        assertSame(DarkColors, readerColorScheme(ReaderTone.SYSTEM, DarkColors))
    }

    @Test
    fun explicitReaderTonesDoNotDependOnTheAppLightDarkMode() {
        for (tone in listOf(ReaderTone.PAPER, ReaderTone.MINT, ReaderTone.NIGHT)) {
            val light = readerColorScheme(tone, LightColors)
            val dark = readerColorScheme(tone, DarkColors)
            assertEquals(light.background, dark.background)
            assertEquals(light.surfaceContainerHigh, dark.surfaceContainerHigh)
            assertEquals(light.onSurfaceVariant, dark.onSurfaceVariant)
        }
    }

    @Test
    fun quotedTextStaysReadableOnEveryReaderBackground() {
        for (base in listOf(LightColors, DarkColors)) {
            for (tone in ReaderTone.entries) {
                val colors = readerColorScheme(tone, base)
                val pairs = listOf(
                    colors.onSurface to colors.surface,
                    colors.onSurfaceVariant to colors.surfaceContainerHigh,
                    colors.primary to colors.surfaceContainerHigh,
                )
                for ((text, surface) in pairs) {
                    assertTrue("$tone", contrast(text, surface) >= 4.5f)
                }
            }
        }
    }

    private fun contrast(text: Color, background: Color): Float =
        (maxOf(text.luminance(), background.luminance()) + 0.05f) /
            (minOf(text.luminance(), background.luminance()) + 0.05f)
}
