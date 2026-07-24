package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.data.CustomListThread
import java.time.DateTimeException
import java.time.LocalDate

internal enum class ThreadReadFilter { ALL, READ, UNREAD }

internal enum class ThreadPublicationOrder { NEWEST_FIRST, OLDEST_FIRST }

internal fun toggleThreadSelection(selectedThreadIds: Set<Int>, threadId: Int): Set<Int> =
    if (threadId in selectedThreadIds) selectedThreadIds - threadId
    else selectedThreadIds + threadId

internal fun toggleAllDisplayedThreads(
    selectedThreadIds: Set<Int>,
    displayedThreadIds: Collection<Int>,
): Set<Int> {
    val displayed = displayedThreadIds.toSet()
    return if (displayed.isNotEmpty() && selectedThreadIds.containsAll(displayed)) {
        selectedThreadIds - displayed
    } else {
        selectedThreadIds + displayed
    }
}

internal fun filterAndSortCustomListThreads(
    threads: List<CustomListThread>,
    readThreadIds: Set<Int>,
    readFilter: ThreadReadFilter,
    publicationOrder: ThreadPublicationOrder,
    today: LocalDate = LocalDate.now(),
): List<CustomListThread> {
    val filtered = threads.filter { thread ->
        when (readFilter) {
            ThreadReadFilter.ALL -> true
            ThreadReadFilter.READ -> thread.threadId in readThreadIds
            ThreadReadFilter.UNREAD -> thread.threadId !in readThreadIds
        }
    }
    val comparator = compareBy<CustomListThread>(
        { publicationDate(it.createdAtText, today).toEpochDay() },
        CustomListThread::threadId,
    )
    return filtered.sortedWith(
        if (publicationOrder == ThreadPublicationOrder.OLDEST_FIRST) comparator
        else comparator.reversed(),
    )
}

private fun publicationDate(text: String, today: LocalDate): LocalDate {
    val normalized = text.trim()
    when {
        normalized.startsWith("今天") -> return today
        normalized.startsWith("昨天") -> return today.minusDays(1)
        normalized.startsWith("前天") -> return today.minusDays(2)
    }

    val parts = Regex(
        """(?<!\d)(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})(?:\s*[日号])?""",
    )
        .find(normalized)
        ?.groupValues
    if (parts != null) {
        safeDate(parts[1].toInt(), parts[2].toInt(), parts[3].toInt())?.let { return it }
    }

    val monthDay = Regex(
        """(?<!\d)(\d{1,2})\s*[-/.月]\s*(\d{1,2})(?:\s*[日号])?""",
    )
        .find(normalized)
        ?.groupValues
    if (monthDay != null) {
        val month = monthDay[1].toInt()
        val day = monthDay[2].toInt()
        safeDate(today.year, month, day)?.let { candidate ->
            return if (candidate.isAfter(today)) candidate.minusYears(1) else candidate
        }
    }

    // Discuz thread IDs are increasing; callers use the ID as a stable tie-breaker for
    // relative or unrecognized date labels.
    return LocalDate.MIN
}

private fun safeDate(year: Int, month: Int, day: Int): LocalDate? = try {
    LocalDate.of(year, month, day)
} catch (_: DateTimeException) {
    null
}
