package com.yamibo.pocket300.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadTypographyTest {
    @Test
    fun threadContentUsesTheComfortableLargeBodyStyle() {
        val appTypography = Typography()
        val typography = threadTypography(appTypography)

        assertEquals(appTypography.bodyLarge, typography.body)
        assertEquals(appTypography.titleMedium, typography.heading)
    }

    @Test
    fun appTypographyUsesTheSharedContentScale() {
        assertEquals(16.sp, PocketTypography.bodyLarge.fontSize)
        assertEquals(14.sp, PocketTypography.bodyMedium.fontSize)
        assertEquals(28.sp, PocketTypography.bodyLarge.lineHeight)
        assertEquals(20.sp, PocketTypography.bodyMedium.lineHeight)
        assertEquals(12.sp, PocketTypography.labelSmall.fontSize)
    }
}
