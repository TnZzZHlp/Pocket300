package com.yamibo.pocket300.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomListSelectionTest {
    @Test
    fun togglesIndividualThreadSelection() {
        assertEquals(setOf(100, 200), toggleThreadSelection(setOf(100), 200))
        assertEquals(setOf(200), toggleThreadSelection(setOf(100, 200), 100))
    }

    @Test
    fun selectsEveryDisplayedThread() {
        assertEquals(
            setOf(100, 200, 300),
            toggleAllDisplayedThreads(setOf(100), listOf(200, 300)),
        )
    }

    @Test
    fun clearsDisplayedThreadsWithoutChangingHiddenSelection() {
        assertEquals(
            setOf(100),
            toggleAllDisplayedThreads(setOf(100, 200, 300), listOf(200, 300)),
        )
    }
}
