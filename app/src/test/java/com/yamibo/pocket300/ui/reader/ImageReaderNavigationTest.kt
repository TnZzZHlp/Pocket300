package com.yamibo.pocket300.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageReaderNavigationTest {
    @Test
    fun horizontalTapZonesFollowReadingDirection() {
        assertEquals(
            ImageReaderTapAction.PREVIOUS,
            tap(x = 100f, y = 500f, mode = ImageReaderMode.LEFT_TO_RIGHT),
        )
        assertEquals(
            ImageReaderTapAction.NEXT,
            tap(x = 900f, y = 500f, mode = ImageReaderMode.LEFT_TO_RIGHT),
        )
        assertEquals(
            ImageReaderTapAction.NEXT,
            tap(x = 100f, y = 500f, mode = ImageReaderMode.RIGHT_TO_LEFT),
        )
        assertEquals(
            ImageReaderTapAction.PREVIOUS,
            tap(x = 900f, y = 500f, mode = ImageReaderMode.RIGHT_TO_LEFT),
        )
    }

    @Test
    fun verticalTapZonesUseMihonStyleLNavigation() {
        assertEquals(
            ImageReaderTapAction.PREVIOUS,
            tap(x = 500f, y = 100f, mode = ImageReaderMode.VERTICAL),
        )
        assertEquals(
            ImageReaderTapAction.NEXT,
            tap(x = 500f, y = 900f, mode = ImageReaderMode.VERTICAL),
        )
        assertEquals(
            ImageReaderTapAction.PREVIOUS,
            tap(x = 100f, y = 500f, mode = ImageReaderMode.WEBTOON),
        )
        assertEquals(
            ImageReaderTapAction.NEXT,
            tap(x = 900f, y = 500f, mode = ImageReaderMode.WEBTOON),
        )
        assertEquals(
            ImageReaderTapAction.TOGGLE_CONTROLS,
            tap(x = 500f, y = 500f, mode = ImageReaderMode.VERTICAL),
        )
    }

    @Test
    fun disabledTapNavigationOnlyTogglesControls() {
        assertEquals(
            ImageReaderTapAction.TOGGLE_CONTROLS,
            imageReaderTapAction(
                x = 0f,
                y = 0f,
                width = 1000f,
                height = 1000f,
                mode = ImageReaderMode.LEFT_TO_RIGHT,
                tapNavigation = false,
            ),
        )
    }

    @Test
    fun invalidTapCoordinatesDoNothing() {
        assertEquals(
            ImageReaderTapAction.NONE,
            tap(x = -1f, y = 500f, mode = ImageReaderMode.LEFT_TO_RIGHT),
        )
        assertEquals(
            ImageReaderTapAction.NONE,
            imageReaderTapAction(
                x = 0f,
                y = 0f,
                width = 0f,
                height = 1000f,
                mode = ImageReaderMode.VERTICAL,
                tapNavigation = true,
            ),
        )
    }

    @Test
    fun pageNavigationAndSliderValuesStayWithinBounds() {
        assertEquals(
            0,
            imageReaderPageAfterAction(0, pageCount = 4, ImageReaderTapAction.PREVIOUS),
        )
        assertEquals(
            3,
            imageReaderPageAfterAction(3, pageCount = 4, ImageReaderTapAction.NEXT),
        )
        assertEquals(
            2,
            imageReaderPageAfterAction(1, pageCount = 4, ImageReaderTapAction.NEXT),
        )
        assertEquals(0, imageReaderPageFromSlider(-10f, pageCount = 4))
        assertEquals(2, imageReaderPageFromSlider(1.6f, pageCount = 4))
        assertEquals(3, imageReaderPageFromSlider(20f, pageCount = 4))
    }

    @Test
    fun identifiesOnlyTheLastAvailableImageAsTheReadingCompletionPoint() {
        assertTrue(imageReaderAtFinalPage(page = 2, pageCount = 3))
        assertFalse(imageReaderAtFinalPage(page = 1, pageCount = 3))
        assertFalse(imageReaderAtFinalPage(page = 0, pageCount = 0))
    }

    private fun tap(
        x: Float,
        y: Float,
        mode: ImageReaderMode,
    ) = imageReaderTapAction(
        x = x,
        y = y,
        width = 1000f,
        height = 1000f,
        mode = mode,
        tapNavigation = true,
    )
}
