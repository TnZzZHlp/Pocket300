package com.yamibo.pocket300.ui.viewmodels

import com.yamibo.pocket300.api.GetThreadPostsInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadViewModelTest {
    @Test
    fun doesNotReloadSameRequestAfterReturningFromReader() {
        val tracker = ThreadPostsRequestTracker()
        val input = GetThreadPostsInput(threadId = 1000, page = 2, authorId = 42)

        assertTrue(tracker.shouldLoad(input))
        assertFalse(tracker.shouldLoad(input))

        tracker.invalidate()
        assertTrue(tracker.shouldLoad(input))
    }
}
