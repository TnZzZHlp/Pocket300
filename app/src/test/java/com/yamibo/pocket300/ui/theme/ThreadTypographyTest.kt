package com.yamibo.pocket300.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadTypographyTest {
    @Test
    fun threadContentUsesTheStandardMediumBodyStyle() {
        val appTypography = Typography()
        val typography = threadTypography(appTypography)

        assertEquals(appTypography.bodyMedium, typography.body)
        assertEquals(appTypography.titleMedium, typography.heading)
    }

    @Test
    fun appTypographyUsesTheSharedContentScale() {
        assertEquals(14.sp, PocketTypography.bodyLarge.fontSize)
        assertEquals(14.sp, PocketTypography.bodyMedium.fontSize)
        assertEquals(22.sp, PocketTypography.bodyLarge.lineHeight)
        assertEquals(20.sp, PocketTypography.bodyMedium.lineHeight)
    }
}
