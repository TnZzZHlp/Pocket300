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

    @Test
    fun mapsImageReaderEdgesToPreviousAndNextImages() {
        assertTrue(readerImageTapAction(x = 0f, width = 1000f) == ReaderImageTapAction.PREVIOUS)
        assertTrue(readerImageTapAction(x = 250f, width = 1000f) == ReaderImageTapAction.PREVIOUS)
        assertTrue(readerImageTapAction(x = 750f, width = 1000f) == ReaderImageTapAction.NEXT)
        assertTrue(readerImageTapAction(x = 1000f, width = 1000f) == ReaderImageTapAction.NEXT)
    }

    @Test
    fun mapsImageReaderCenterToControlsAndRejectsInvalidCoordinates() {
        assertTrue(readerImageTapAction(x = 500f, width = 1000f) == ReaderImageTapAction.TOGGLE_CONTROLS)
        assertTrue(readerImageTapAction(x = -1f, width = 1000f) == ReaderImageTapAction.NONE)
        assertTrue(readerImageTapAction(x = 1001f, width = 1000f) == ReaderImageTapAction.NONE)
        assertTrue(readerImageTapAction(x = 0f, width = 0f) == ReaderImageTapAction.NONE)
    }

    @Test
    fun advancesAcrossMoreThanTwoImages() {
        var index = 0
        index = readerImageIndexAfterTap(index, lastIndex = 3, ReaderImageTapAction.NEXT)
        index = readerImageIndexAfterTap(index, lastIndex = 3, ReaderImageTapAction.NEXT)

        assertTrue(index == 2)
    }
}
