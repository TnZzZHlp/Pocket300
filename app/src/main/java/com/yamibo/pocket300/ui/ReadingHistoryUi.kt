package com.yamibo.pocket300.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.yamibo.pocket300.data.ReadingHistoryEntry

internal const val READ_THREAD_ALPHA = 0.62f

internal val LocalReadingHistory = staticCompositionLocalOf<Map<Int, ReadingHistoryEntry>> {
    emptyMap()
}

internal fun threadAlpha(hasReadingHistory: Boolean): Float =
    if (hasReadingHistory) READ_THREAD_ALPHA else 1f

internal fun Modifier.dimIfRead(threadId: Int, histories: Map<Int, ReadingHistoryEntry>): Modifier =
    alpha(threadAlpha(threadId in histories))

internal fun routeWithLastReadFloor(route: String, floor: Int): String {
    val separator = if ('?' in route) '&' else '?'
    return "$route${separator}floor=${floor.coerceAtLeast(1)}"
}
