package com.yamibo.pocket300.ui.screens

import org.junit.Assert.assertEquals
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

    @Test
    fun togglesCollapsedCategoryWithoutAffectingOtherCategories() {
        val collapsed = toggleCollapsedCategory(setOf(10), 20)
        assertEquals(setOf(10, 20), collapsed)

        val reopened = toggleCollapsedCategory(collapsed, 10)
        assertEquals(setOf(20), reopened)
    }
}
