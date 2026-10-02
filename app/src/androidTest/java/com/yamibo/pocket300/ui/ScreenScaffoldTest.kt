package com.yamibo.pocket300.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yamibo.pocket300.ui.theme.LightColors
import com.yamibo.pocket300.ui.theme.PocketShapes
import com.yamibo.pocket300.ui.theme.PocketTypography
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenScaffoldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun failureOffersRetryWithoutDisplayingRawExceptions() {
        var retries = 0
        composeRule.setContent {
            MaterialTheme(colorScheme = LightColors, typography = PocketTypography, shapes = PocketShapes) {
                var state: LoadState<String> by remember { mutableStateOf(LoadState.Failed("IOException: internal details")) }
                LoadContent(state, PaddingValues(), onRetry = {
                    retries++
                    state = LoadState.Loading
                }) { Text(it) }
            }
        }

        composeRule.onNodeWithText("加载失败").assertIsDisplayed()
        composeRule.onNodeWithText("IOException: internal details").assertDoesNotExist()
        composeRule.onNodeWithText("重试").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
        composeRule.onNodeWithText("重试").assertDoesNotExist()
    }

    @Test
    fun readyContentDoesNotShowTheRetryAction() {
        composeRule.setContent {
            MaterialTheme {
                LoadContent(LoadState.Ready("内容"), PaddingValues(), onRetry = {}) { Text(it) }
            }
        }
        composeRule.onNodeWithText("内容").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertDoesNotExist()
    }

    @Test
    fun offlineFailureCanKeepItsActionableMessageWithoutOfferingNetworkRetry() {
        composeRule.setContent {
            MaterialTheme {
                LoadContent<String>(
                    LoadState.Failed("internal details"),
                    PaddingValues(),
                    failureMessage = "离线内容不可用",
                ) { Text(it) }
            }
        }
        composeRule.onNodeWithText("离线内容不可用").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertDoesNotExist()
    }
}
