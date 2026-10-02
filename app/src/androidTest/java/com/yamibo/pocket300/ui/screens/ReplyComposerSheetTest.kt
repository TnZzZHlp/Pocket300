package com.yamibo.pocket300.ui.screens

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.POST_COMMENT_MAX_LENGTH
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReplyComposerSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun focusesEditorAndRetainsDraftAfterClosingAndReopening() {
        composeRule.setContent { ComposerContent() }
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
            .performTextReplacement("Unsent reply")
        composeRule.onNodeWithText("发送回帖").assertIsEnabled()
        composeRule.onNodeWithContentDescription("关闭回复编辑器").performClick()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithText("Open composer").performClick()
        composeRule.onNode(hasSetTextAction()).assertTextEquals("Unsent reply")
    }

    @Test
    fun backHidesKeyboardBeforeClosingAndRetainsDraft() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val serviceInfo = automation.serviceInfo
        serviceInfo.flags = serviceInfo.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        composeRule.setContent { ComposerContent(initialDraft = "Unsent reply") }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            automation.windows.none { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }
        composeRule.onNode(hasSetTextAction()).assertTextEquals("Unsent reply")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithText("Open composer").performClick()
        composeRule.onNode(hasSetTextAction()).assertTextEquals("Unsent reply")
    }

    @Test
    fun downwardDragDismissesWithoutLosingDraft() {
        composeRule.setContent { ComposerContent(initialDraft = "Dragged draft") }
        composeRule.onNodeWithText("回复主题").performTouchInput {
            swipeDown(endY = centerY + 900f)
        }
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithText("Open composer").performClick()
        composeRule.onNode(hasSetTextAction()).assertTextEquals("Dragged draft")
    }

    @Test
    fun outsideTapDismissesWithoutLosingDraft() {
        composeRule.setContent { ComposerContent(initialDraft = "Outside tap draft") }
        composeRule.onNode(isDialog()).performTouchInput { click(Offset(centerX, 100f)) }
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithText("Open composer").performClick()
        composeRule.onNode(hasSetTextAction()).assertTextEquals("Outside tap draft")
    }

    @Test
    fun keepsSendAndCloseVisibleWithLongDraftAtLargeFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                ComposerContent(initialDraft = "Long reply\n".repeat(30))
            }
        }
        composeRule.onNodeWithText("发送回帖").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithContentDescription("关闭回复编辑器").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun restoresReplyDraftAfterStateRecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { ComposerContent() }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Restored draft")
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNode(hasSetTextAction()).assertTextEquals("Restored draft")
    }

    @Test
    fun rejectsCommentsOverCharacterLimitAndDisablesBlankSubmission() {
        composeRule.setContent { ComposerContent(maxLength = POST_COMMENT_MAX_LENGTH) }
        composeRule.onNodeWithText("发送回帖").assertIsNotEnabled()
        val maximum = "a".repeat(POST_COMMENT_MAX_LENGTH)
        composeRule.onNode(hasSetTextAction()).performTextReplacement(maximum)
        composeRule.onNodeWithText("200/200").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextReplacement(maximum + "b")
        composeRule.onNode(hasSetTextAction()).assertTextContains(maximum)
        composeRule.onNode(hasSetTextAction()).performTextReplacement("  ")
        composeRule.onNodeWithText("发送回帖").assertIsNotEnabled()
    }

    @Test
    fun locksEditorSubmissionAndCloseWhileSending() {
        var submitting by mutableStateOf(false)
        composeRule.setContent { ComposerContent(initialDraft = "Sending", submitting = submitting) }
        composeRule.runOnIdle { submitting = true }
        composeRule.onNodeWithText("Sending").assertIsNotEnabled()
        composeRule.onNodeWithText("正在发送…").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("关闭回复编辑器").assertIsNotEnabled()
        composeRule.onNodeWithText("回复主题").performTouchInput {
            swipeDown(endY = centerY + 900f)
        }
        composeRule.onNode(isDialog()).performTouchInput { click(Offset(centerX, 100f)) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.onNodeWithText("Sending").assertIsDisplayed().assertIsNotEnabled()
        composeRule.runOnIdle { submitting = false }
        composeRule.onNode(hasSetTextAction()).assertTextEquals("Sending")
        composeRule.onNodeWithText("发送回帖").assertIsEnabled()
    }

    @Test
    fun showsReplyTargetAndPreventsPostingToClosedThread() {
        composeRule.setContent { ComposerContent(initialDraft = "Draft", threadClosed = true) }
        composeRule.onNodeWithText("Test thread").assertIsDisplayed()
        composeRule.onNodeWithText("主题已关闭，无法回帖").assertIsDisplayed()
        composeRule.onNodeWithText("发送回帖").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("关闭回复编辑器").assertIsEnabled()
    }

    @Composable
    private fun ComposerContent(
        initialDraft: String = "",
        submitting: Boolean = false,
        threadClosed: Boolean = false,
        maxLength: Int? = null,
    ) {
        var visible by rememberSaveable { mutableStateOf(true) }
        var draft by rememberSaveable { mutableStateOf(initialDraft) }
        MaterialTheme {
            Button(onClick = { visible = true }) { Text("Open composer") }
            if (visible) {
                ReplyComposerSheet(
                    title = stringResource(R.string.thread_reply_title),
                    target = "Test thread",
                    hint = stringResource(R.string.thread_reply_hint),
                    submitLabel = stringResource(R.string.thread_reply_action),
                    submittingLabel = stringResource(R.string.thread_reply_submitting),
                    draft = draft,
                    submitting = submitting,
                    threadClosed = threadClosed,
                    onDraftChange = { draft = it },
                    onDismiss = { visible = false },
                    onSubmit = {},
                    maxLength = maxLength,
                )
            }
        }
    }
}
