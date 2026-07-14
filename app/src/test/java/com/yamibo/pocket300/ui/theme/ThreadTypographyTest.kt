package com.yamibo.pocket300.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadTypographyTest {
    @Test
    fun headingAndBodyShareTheSameBaseSize() {
        val typography = threadTypography(Typography())

        assertEquals(typography.body.fontSize, typography.heading.fontSize)
        assertEquals(typography.body.lineHeight, typography.heading.lineHeight)
    }

    @Test
    fun appTypographyUsesTheSharedContentScale() {
        assertEquals(16.sp, PocketTypography.bodyLarge.fontSize)
        assertEquals(24.sp, PocketTypography.bodyLarge.lineHeight)
        assertEquals(PocketTypography.bodyLarge.fontSize, PocketTypography.titleMedium.fontSize)
    }
}
