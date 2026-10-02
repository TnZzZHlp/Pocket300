package com.yamibo.pocket300.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.data.ThreadAuthorRepository
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadAuthorUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun neverShowsPreviousThreadsAuthorWhileNewIdentityIsLoading() {
        var threadId by mutableIntStateOf(100)
        val repository = ThreadAuthorRepository(loadAuthor = { awaitCancellation() })
        composeRule.setContent {
            CompositionLocalProvider(LocalThreadAuthors provides repository) {
                val known = if (threadId == 100) YamiboPostAuthor(null, 42, "alice") else null
                val author = rememberThreadAuthor(threadId, known, allowLookup = true)
                Text(author?.name ?: "unknown")
            }
        }
        composeRule.onNodeWithText("alice").assertIsDisplayed()
        composeRule.runOnIdle { threadId = 101 }
        composeRule.onNodeWithText("unknown").assertIsDisplayed()
        composeRule.onNodeWithText("alice").assertDoesNotExist()
    }
}
