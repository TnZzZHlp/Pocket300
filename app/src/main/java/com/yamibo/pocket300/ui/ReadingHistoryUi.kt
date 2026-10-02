package com.yamibo.pocket300.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.yamibo.pocket300.data.ReadingHistoryEntry

internal val LocalReadingHistory = staticCompositionLocalOf<Map<Int, ReadingHistoryEntry>> {
    emptyMap()
}

internal fun lastReadFloor(
    threadId: Int,
    histories: Map<Int, ReadingHistoryEntry>,
): Int? = histories[threadId]?.lastReadFloor

internal fun routeWithLastReadFloor(route: String, floor: Int): String {
    val separator = if ('?' in route) '&' else '?'
    return "$route${separator}floor=${floor.coerceAtLeast(1)}"
}
