package com.yamibo.pocket300.data

import com.yamibo.pocket300.api.YamiboThreadDetails
import com.yamibo.pocket300.data.download.ThreadDownloadKey
import com.yamibo.pocket300.data.download.ThreadDownloadPhase
import com.yamibo.pocket300.data.download.ThreadDownloadRepository
import com.yamibo.pocket300.data.download.ThreadDownloadRequest
import com.yamibo.pocket300.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Queues newly discovered custom-list threads without treating an existing manual download as an
 * automatically managed one. Only requests successfully reserved here are recorded for optional
 * cleanup after image reading.
 */
class CustomListAutoDownloadCoordinator(
    private val database: CustomListDatabase,
    private val downloadRepository: ThreadDownloadRepository,
    private val loadThreadDetails: suspend (threadId: Int) -> YamiboThreadDetails,
) {
    suspend fun enqueueNewThreads(list: CustomThreadList, threads: List<CustomListThread>) {
        if (!list.autoDownloadNewThreads || threads.isEmpty()) return
        try {
            downloadRepository.awaitInitialized()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.error(TAG, error) {
                "Could not initialize downloads for automatic custom-list downloads"
            }
            return
        }

        threads.distinctBy(CustomListThread::threadId).forEach { thread ->
            val key = ThreadDownloadKey(thread.threadId)
            val status = downloadRepository.statuses.value[key]
            if (status?.completed != null || status?.phase.isActiveDownload == true) return@forEach

            try {
                val details = loadThreadDetails(thread.threadId)
                require(details.id == thread.threadId) {
                    "Thread details belong to another thread"
                }
                if (downloadRepository.enqueueIfMissing(ThreadDownloadRequest.create(details))) {
                    withContext(Dispatchers.IO) {
                        database.recordAutoDownload(list.id, thread.threadId)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.warn(TAG, error) {
                    "Could not automatically download thread ${thread.threadId} " +
                        "from custom list ${list.id}"
                }
            }
        }
    }

    private val ThreadDownloadPhase?.isActiveDownload: Boolean
        get() = this == ThreadDownloadPhase.QUEUED ||
            this == ThreadDownloadPhase.FETCHING_PAGES ||
            this == ThreadDownloadPhase.DOWNLOADING_IMAGES

    private companion object {
        const val TAG = "CustomListAutoDownload"
    }
}
