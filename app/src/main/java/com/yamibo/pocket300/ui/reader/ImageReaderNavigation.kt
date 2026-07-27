package com.yamibo.pocket300.ui.reader

import kotlin.math.roundToInt

private const val NAVIGATION_ZONE_FRACTION = 1f / 3f

internal enum class ImageReaderTapAction {
    PREVIOUS,
    TOGGLE_CONTROLS,
    NEXT,
    NONE,
}

internal fun imageReaderTapAction(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    mode: ImageReaderMode,
    tapNavigation: Boolean,
): ImageReaderTapAction {
    if (width <= 0f || height <= 0f || x < 0f || y < 0f || x > width || y > height) {
        return ImageReaderTapAction.NONE
    }
    if (!tapNavigation) return ImageReaderTapAction.TOGGLE_CONTROLS

    return when (mode) {
        ImageReaderMode.LEFT_TO_RIGHT -> horizontalTapAction(x, width, reverse = false)
        ImageReaderMode.RIGHT_TO_LEFT -> horizontalTapAction(x, width, reverse = true)
        ImageReaderMode.VERTICAL,
        ImageReaderMode.WEBTOON,
        -> verticalTapAction(x, y, width, height)
    }
}

private fun horizontalTapAction(
    x: Float,
    width: Float,
    reverse: Boolean,
): ImageReaderTapAction {
    val leftAction = if (reverse) ImageReaderTapAction.NEXT else ImageReaderTapAction.PREVIOUS
    val rightAction = if (reverse) ImageReaderTapAction.PREVIOUS else ImageReaderTapAction.NEXT
    return when {
        x <= width * NAVIGATION_ZONE_FRACTION -> leftAction
        x >= width * (1f - NAVIGATION_ZONE_FRACTION) -> rightAction
        else -> ImageReaderTapAction.TOGGLE_CONTROLS
    }
}

private fun verticalTapAction(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
): ImageReaderTapAction = when {
    y <= height * NAVIGATION_ZONE_FRACTION -> ImageReaderTapAction.PREVIOUS
    y >= height * (1f - NAVIGATION_ZONE_FRACTION) -> ImageReaderTapAction.NEXT
    x <= width * NAVIGATION_ZONE_FRACTION -> ImageReaderTapAction.PREVIOUS
    x >= width * (1f - NAVIGATION_ZONE_FRACTION) -> ImageReaderTapAction.NEXT
    else -> ImageReaderTapAction.TOGGLE_CONTROLS
}

internal fun imageReaderPageAfterAction(
    currentIndex: Int,
    pageCount: Int,
    action: ImageReaderTapAction,
): Int {
    if (pageCount <= 0) return 0
    val currentPage = currentIndex.coerceIn(0, pageCount - 1)
    return when (action) {
        ImageReaderTapAction.PREVIOUS -> (currentPage - 1).coerceAtLeast(0)
        ImageReaderTapAction.NEXT -> (currentPage + 1).coerceAtMost(pageCount - 1)
        ImageReaderTapAction.TOGGLE_CONTROLS,
        ImageReaderTapAction.NONE,
        -> currentPage
    }
}

internal fun imageReaderPageFromSlider(value: Float, pageCount: Int): Int =
    if (pageCount <= 0) 0 else value.roundToInt().coerceIn(0, pageCount - 1)

internal fun imageReaderAtFinalPage(page: Int, pageCount: Int): Boolean =
    pageCount > 0 && page == pageCount - 1
