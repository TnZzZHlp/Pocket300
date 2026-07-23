package com.yamibo.pocket300.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive

internal const val CUSTOM_LIST_AUTO_REFRESH_INTERVAL_MILLIS = 10_000L

internal object CustomListRefreshEvents {
    private val mutableRefreshedListIds = MutableSharedFlow<Long>(extraBufferCapacity = 64)

    val refreshedListIds = mutableRefreshedListIds.asSharedFlow()

    fun notifyRefreshed(listId: Long) {
        mutableRefreshedListIds.tryEmit(listId)
    }
}

internal class CustomListAutoRefreshScheduler(
    private val loadLists: suspend () -> List<CustomThreadList>,
    private val refresh: suspend (CustomThreadList) -> Unit,
    private val intervalMillis: Long = CUSTOM_LIST_AUTO_REFRESH_INTERVAL_MILLIS,
    private val waitForNextRefresh: suspend (Long) -> Unit = ::delay,
) {
    suspend fun refreshContinuously() {
        while (currentCoroutineContext().isActive) {
            val lists = try {
                loadLists()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            if (lists.isEmpty()) {
                waitForNextRefresh(intervalMillis)
                continue
            }
            lists.forEach { list ->
                currentCoroutineContext().ensureActive()
                try {
                    refresh(list)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Keep later lists eligible for their next scheduled refresh.
                }
                waitForNextRefresh(intervalMillis)
            }
        }
    }
}
