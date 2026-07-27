package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.api.YamiboThreadDetails
import com.yamibo.pocket300.data.CustomListThread
import com.yamibo.pocket300.data.download.ThreadDownloadPhase
import kotlinx.coroutines.CancellationException

internal enum class CustomListBulkDownloadAction {
    SKIP,
    RETRY,
    PREPARE,
}

internal data class CustomListBulkDownloadProgress(
    val completedCount: Int,
    val totalCount: Int,
)

internal data class CustomListBulkDownloadResult(
    val queuedCount: Int,
    val skippedCount: Int,
    val failedThreadIds: Set<Int>,
) {
    val failedCount: Int get() = failedThreadIds.size
}

internal fun customListBulkDownloadAction(
    phase: ThreadDownloadPhase?,
    hasCompletedDownload: Boolean,
): CustomListBulkDownloadAction {
    if (hasCompletedDownload) return CustomListBulkDownloadAction.SKIP
    return when (phase) {
        null -> CustomListBulkDownloadAction.PREPARE
        ThreadDownloadPhase.FAILED -> CustomListBulkDownloadAction.RETRY
        ThreadDownloadPhase.QUEUED,
        ThreadDownloadPhase.FETCHING_PAGES,
        ThreadDownloadPhase.DOWNLOADING_IMAGES,
        ThreadDownloadPhase.COMPLETED,
        -> CustomListBulkDownloadAction.SKIP
    }
}

internal fun selectedCustomListThreadsInDisplayOrder(
    displayedThreads: List<CustomListThread>,
    selectedThreadIds: Set<Int>,
): List<CustomListThread> = displayedThreads
    .filter { it.threadId in selectedThreadIds }
    .distinctBy(CustomListThread::threadId)

internal suspend fun enqueueCustomListThreadDownloads(
    threads: List<CustomListThread>,
    actionFor: (threadId: Int) -> CustomListBulkDownloadAction,
    retry: suspend (threadId: Int) -> Boolean,
    loadThreadDetails: suspend (threadId: Int) -> YamiboThreadDetails,
    enqueueIfMissing: suspend (thread: YamiboThreadDetails) -> Boolean,
    onProgress: (completedCount: Int, totalCount: Int) -> Unit = { _, _ -> },
    onFailure: (threadId: Int, failure: Exception) -> Unit = { _, _ -> },
): CustomListBulkDownloadResult {
    val targets = threads.distinctBy(CustomListThread::threadId)
    var queuedCount = 0
    var skippedCount = 0
    val failedThreadIds = linkedSetOf<Int>()

    suspend fun prepare(threadId: Int): Boolean {
        val details = loadThreadDetails(threadId)
        require(details.id == threadId) { "Thread details belong to another thread" }
        return enqueueIfMissing(details)
    }

    targets.forEachIndexed { index, thread ->
        try {
            when (actionFor(thread.threadId)) {
                CustomListBulkDownloadAction.SKIP -> skippedCount++
                CustomListBulkDownloadAction.RETRY -> {
                    val queued = retry(thread.threadId) || prepare(thread.threadId)
                    if (queued) queuedCount++ else skippedCount++
                }
                CustomListBulkDownloadAction.PREPARE -> {
                    val queued = prepare(thread.threadId)
                    if (queued) queuedCount++ else skippedCount++
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            failedThreadIds += thread.threadId
            onFailure(thread.threadId, failure)
        } finally {
            onProgress(index + 1, targets.size)
        }
    }

    return CustomListBulkDownloadResult(
        queuedCount = queuedCount,
        skippedCount = skippedCount,
        failedThreadIds = failedThreadIds,
    )
}
