package com.yamibo.pocket300.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive

internal const val CUSTOM_LIST_AUTO_REFRESH_BETWEEN_LISTS_MILLIS = 10_000L
private const val MAX_FOREGROUND_AUTO_REFRESH_CHECK_DELAY_MILLIS = 60_000L
private const val MILLIS_PER_HOUR = 60 * 60 * 1_000L

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
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val betweenListDelayMillis: Long = CUSTOM_LIST_AUTO_REFRESH_BETWEEN_LISTS_MILLIS,
    private val waitForNextRefresh: suspend (Long) -> Unit = ::delay,
) {
    suspend fun refreshContinuously() {
        while (currentCoroutineContext().isActive) {
            refreshDueLists()
            waitForNextRefresh(nextRefreshCheckDelay())
        }
    }

    suspend fun refreshAllLists() {
        refreshAllLists(loadListsOrEmpty())
    }

    suspend fun refreshAllLists(lists: List<CustomThreadList>) {
        refreshLists(lists)
    }

    suspend fun refreshDueLists() {
        val dueLists = loadListsOrEmpty().filter { it.isAutoRefreshDue(nowMillis()) }
        refreshLists(dueLists)
    }

    private suspend fun refreshLists(lists: List<CustomThreadList>) {
        lists.forEachIndexed { index, list ->
            currentCoroutineContext().ensureActive()
            try {
                refresh(list)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Continue refreshing the remaining lists.
            }
            if (index < lists.lastIndex) {
                waitForNextRefresh(betweenListDelayMillis)
            }
        }
    }

    private suspend fun nextRefreshCheckDelay(): Long {
        val now = nowMillis()
        val nextDueIn = loadListsOrEmpty()
            .minOfOrNull { it.millisUntilAutoRefresh(now) }
            ?: MAX_FOREGROUND_AUTO_REFRESH_CHECK_DELAY_MILLIS
        return nextDueIn.coerceIn(
            betweenListDelayMillis,
            MAX_FOREGROUND_AUTO_REFRESH_CHECK_DELAY_MILLIS,
        )
    }

    private suspend fun loadListsOrEmpty(): List<CustomThreadList> = try {
        loadLists()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun CustomThreadList.isAutoRefreshDue(now: Long): Boolean =
    millisUntilAutoRefresh(now) == 0L

internal fun CustomThreadList.millisUntilAutoRefresh(now: Long): Long {
    val lastSynced = lastSyncedAt ?: return 0
    val intervalMillis = autoRefreshIntervalHours.coerceAtLeast(1).toLong() * MILLIS_PER_HOUR
    return (lastSynced + intervalMillis - now).coerceAtLeast(0)
}
