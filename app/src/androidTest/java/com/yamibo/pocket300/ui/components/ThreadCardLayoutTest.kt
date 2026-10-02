package com.yamibo.pocket300.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yamibo.pocket300.data.ReadingHistoryEntry
import com.yamibo.pocket300.ui.LocalDownloadedThreadIds
import com.yamibo.pocket300.ui.LocalReadingHistory
import com.yamibo.pocket300.ui.theme.DarkColors
import com.yamibo.pocket300.ui.theme.PocketShapes
import com.yamibo.pocket300.ui.theme.PocketTypography
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadCardLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readDownloadedCardKeepsMetadataAndResumePositionAtLargeFontScale() {
        var opened = 0
        composeRule.setContent {
            val density = LocalDensity.current
            MaterialTheme(colorScheme = DarkColors, typography = PocketTypography, shapes = PocketShapes) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = 2f),
                    LocalDownloadedThreadIds provides setOf(12),
                    LocalReadingHistory provides mapOf(
                        12 to ReadingHistoryEntry(12, 300, "标题", "作者", "昨天", 24, 0),
                    ),
                ) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        ThreadCardSurface(onClick = { opened++ }) {
                            ThreadCardContent(
                                threadId = 12,
                                subject = "这是一个需要换行显示的中文主题标题",
                                category = "原创",
                                metadata = "很长的作者名称 · 3月14日",
                                activity = "128 回复 · 3,000 浏览",
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("上次读到第 24 楼", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("很长的作者名称 · 3月14日", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("这是一个需要换行显示的中文主题标题")
            .performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }
}
