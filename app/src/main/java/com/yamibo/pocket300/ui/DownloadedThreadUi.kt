package com.yamibo.pocket300.ui

import androidx.compose.runtime.compositionLocalOf
import com.yamibo.pocket300.data.download.DownloadedThread

internal val LocalDownloadedThreadIds = compositionLocalOf<Set<Int>> {
    emptySet()
}

internal fun completedThreadIds(downloads: Iterable<DownloadedThread>): Set<Int> =
    downloads.mapTo(linkedSetOf()) { it.key.threadId }

internal fun shouldShowDownloadedIndicator(
    threadId: Int,
    downloadedThreadIds: Set<Int>,
): Boolean = threadId in downloadedThreadIds
