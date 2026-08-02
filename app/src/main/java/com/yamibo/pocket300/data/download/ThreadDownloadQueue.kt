package com.yamibo.pocket300.data.download

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque

/**
 * The observable ordering and execution state for complete-thread downloads.
 *
 * Completed and failed downloads are intentionally not included here: this is only the durable
 * pending queue plus the one task currently handed to the downloader.
 */
data class ThreadDownloadQueueState(
    val isPaused: Boolean = false,
    val isRunning: Boolean = false,
    val activeKey: ThreadDownloadKey? = null,
    val queuedKeys: List<ThreadDownloadKey> = emptyList(),
) {
    val orderedKeys: List<ThreadDownloadKey>
        get() = listOfNotNull(activeKey) + queuedKeys

    fun queuedPosition(key: ThreadDownloadKey): Int? =
        queuedKeys.indexOf(key).takeIf { it >= 0 }?.plus(1)
}

/**
 * A serial, controllable queue modeled after a reader download manager.
 *
 * Persistence remains the responsibility of [ThreadDownloadFileStore]. Keeping scheduling here
 * lets the repository atomically persist a request before it becomes observable to the worker.
 */
internal class ThreadDownloadQueue {
    private val mutex = Mutex()
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)
    private val pending = ArrayDeque<ThreadDownloadQueueEntry>()
    private val _state = MutableStateFlow(ThreadDownloadQueueState())
    private var active: ThreadDownloadQueueEntry? = null
    private var isDispatching = false
    private var nextOrder = 0L
    private var requeueActiveOnFinish = false
    private var activeWasCancelled = false

    val state: StateFlow<ThreadDownloadQueueState> = _state.asStateFlow()

    suspend fun restore(
        entries: List<ThreadDownloadQueueEntry>,
        isPaused: Boolean = false,
    ) {
        mutex.withLock {
            check(active == null) { "Cannot restore a queue with an active download" }
            pending.clear()
            pending.addAll(entries.sortedWith(THREAD_DOWNLOAD_QUEUE_ENTRY_ORDER))
            nextOrder = nextOrderAfter(pending)
            publishStateLocked(paused = isPaused)
            wakeUp.trySend(Unit)
        }
    }

    /** Starts dispatching queued work without changing a user-requested paused state. */
    suspend fun startDispatching(): Boolean = mutex.withLock {
        if (isDispatching) return@withLock false
        isDispatching = true
        publishStateLocked()
        wakeUp.trySend(Unit)
        true
    }

    /**
     * Stops dispatching while preserving queued work. An active cancelled task is returned to the
     * head of the queue by [finish] once its worker observes cancellation.
     */
    suspend fun stopDispatching(): Boolean = mutex.withLock {
        if (!isDispatching) return@withLock false
        isDispatching = false
        if (active != null && !activeWasCancelled) {
            requeueActiveOnFinish = true
        }
        publishStateLocked()
        true
    }

    /** Reserves a durable FIFO position before the caller writes the request to disk. */
    suspend fun reserve(request: ThreadDownloadRequest): ThreadDownloadQueueEntry = mutex.withLock {
        ThreadDownloadQueueEntry(request, nextOrder++).also {
            if (nextOrder < 0) nextOrder = 0L
        }
    }

    suspend fun enqueue(entry: ThreadDownloadQueueEntry): Boolean = mutex.withLock {
        if (active?.request?.key == entry.request.key || pending.any { it.request.key == entry.request.key }) {
            return@withLock false
        }
        pending.addLast(entry)
        nextOrder = maxOf(nextOrder, entry.order + 1).coerceAtLeast(0L)
        publishStateLocked()
        wakeUp.trySend(Unit)
        true
    }

    suspend fun awaitNext(): ThreadDownloadQueueEntry {
        while (true) {
            val next = mutex.withLock {
                if (isDispatching && !state.value.isPaused && active == null && pending.isNotEmpty()) {
                    pending.removeFirst().also {
                        active = it
                        activeWasCancelled = false
                        requeueActiveOnFinish = false
                        publishStateLocked()
                    }
                } else {
                    null
                }
            }
            if (next != null) return next
            wakeUp.receive()
        }
    }

    /**
     * Marks a task as finished and returns it when a pause cancelled the active operation.
     * The caller should publish the returned task as queued again.
     */
    suspend fun finish(
        key: ThreadDownloadKey,
        wasCancelled: Boolean,
    ): ThreadDownloadQueueEntry? = mutex.withLock {
        val completed = active?.takeIf { it.request.key == key } ?: return@withLock null
        active = null
        val shouldRequeue = wasCancelled && requeueActiveOnFinish && !activeWasCancelled
        requeueActiveOnFinish = false
        activeWasCancelled = false
        if (shouldRequeue) pending.addFirst(completed)
        publishStateLocked()
        if (isDispatching && !state.value.isPaused && pending.isNotEmpty()) wakeUp.trySend(Unit)
        completed.takeIf { shouldRequeue }
    }

    /** Pauses queue execution and arranges for an active, cancelled task to be retried first. */
    suspend fun pause(): Boolean = mutex.withLock {
        if (state.value.isPaused) return@withLock false
        val hasActiveDownload = active != null && !activeWasCancelled
        requeueActiveOnFinish = hasActiveDownload
        publishStateLocked(paused = true)
        true
    }

    suspend fun resume(): Boolean = mutex.withLock {
        if (!state.value.isPaused) return@withLock false
        publishStateLocked(paused = false)
        if (isDispatching) wakeUp.trySend(Unit)
        true
    }

    /** Removes a pending task or prevents the active task from being requeued after cancellation. */
    suspend fun cancel(key: ThreadDownloadKey): Boolean = mutex.withLock {
        var removed = false
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().request.key == key) {
                iterator.remove()
                removed = true
            }
        }
        if (active?.request?.key == key) {
            activeWasCancelled = true
            requeueActiveOnFinish = false
            removed = true
        }
        if (removed) publishStateLocked()
        removed
    }

    /** Clears all pending work and prevents the active task from being requeued. */
    suspend fun cancelAll(): List<ThreadDownloadKey> = mutex.withLock {
        val keys = pending.map { it.request.key }.toMutableList()
        active?.request?.key?.let {
            keys += it
            activeWasCancelled = true
            requeueActiveOnFinish = false
        }
        pending.clear()
        publishStateLocked()
        keys
    }

    /**
     * Moves a pending task to the head of the queue and normalizes the durable queue order.
     */
    suspend fun prioritize(key: ThreadDownloadKey): List<ThreadDownloadQueueEntry>? = mutex.withLock {
        val prioritized = pending.firstOrNull { it.request.key == key } ?: return@withLock null
        pending.remove(prioritized)
        pending.addFirst(prioritized)
        val firstPendingOrder = active?.order?.plus(1)?.takeIf { it >= 0 } ?: 0L
        val reordered = pending.mapIndexed { index, entry ->
            entry.copy(order = firstPendingOrder + index)
        }
        pending.clear()
        pending.addAll(reordered)
        nextOrder = nextOrderAfter(pending)
        publishStateLocked()
        reordered
    }

    fun close() {
        wakeUp.close()
    }

    private fun publishStateLocked(paused: Boolean = state.value.isPaused) {
        _state.value = ThreadDownloadQueueState(
            isPaused = paused,
            isRunning = isDispatching,
            activeKey = active?.request?.key,
            queuedKeys = pending.map { it.request.key },
        )
    }

    private fun nextOrderAfter(entries: Collection<ThreadDownloadQueueEntry>): Long {
        val activeEntry = active?.let { sequenceOf(it) } ?: emptySequence()
        val highest = (entries.asSequence() + activeEntry)
            .maxOfOrNull(ThreadDownloadQueueEntry::order) ?: -1L
        return if (highest == Long.MAX_VALUE) 0L else highest + 1L
    }
}

internal data class ThreadDownloadQueueEntry(
    val request: ThreadDownloadRequest,
    val order: Long,
) {
    init {
        require(order >= 0) { "Thread download queue order must not be negative" }
    }
}

private val THREAD_DOWNLOAD_QUEUE_ENTRY_ORDER =
    compareBy<ThreadDownloadQueueEntry>(ThreadDownloadQueueEntry::order)
        .thenBy { it.request.requestedAt }
        .thenBy { it.request.key.threadId }
