package com.yamibo.pocket300.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Test
    fun doesNotFocusQueryWhenReturningToSearchResults() {
        lateinit var searchVisible: MutableState<Boolean>

        composeRule.setContent {
            searchVisible = remember { mutableStateOf(true) }
            if (searchVisible.value) {
                SharedTransitionLayout {
                    val sharedTransitionScope = this
                    AnimatedVisibility(visible = true) {
                        SearchScreen(
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = this,
                            onBack = {},
                            onThread = {},
                        )
                    }
                }
            }
        }

        composeRule.runOnIdle { searchVisible.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { searchVisible.value = true }

        composeRule.onNodeWithTag(SEARCH_QUERY_FIELD_TEST_TAG).assertIsNotFocused()
    }
}
