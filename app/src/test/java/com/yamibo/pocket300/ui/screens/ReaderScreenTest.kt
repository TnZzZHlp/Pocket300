package com.yamibo.pocket300.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScreenTest {
    @Test
    fun reusesMatchingPostContentWhenOpeningReader() {
        assertFalse(needsReaderContentLoad(1000, 2000, threadId = 1000, postId = 2000))
        assertTrue(needsReaderContentLoad(null, null, threadId = 1000, postId = 2000))
        assertTrue(needsReaderContentLoad(1000, 2001, threadId = 1000, postId = 2000))
    }
}
