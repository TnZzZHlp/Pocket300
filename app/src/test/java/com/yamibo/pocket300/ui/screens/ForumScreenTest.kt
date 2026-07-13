package com.yamibo.pocket300.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumScreenTest {
    @Test
    fun stickyThreadsAccordionIsCollapsedByDefault() {
        assertFalse(STICKY_THREADS_INITIAL_EXPANDED)
    }

    @Test
    fun identifiesThreadsWithPositiveStickyLevel() {
        assertFalse(isStickyThread(0))
        assertTrue(isStickyThread(1))
        assertTrue(isStickyThread(3))
    }
}
