package com.yamibo.pocket300.data

import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.api.resolveYamiboAvatarUrl
import com.yamibo.pocket300.api.yamiboAvatarUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

internal fun normalizedThreadAuthor(author: YamiboPostAuthor): YamiboPostAuthor {
    val id = author.id?.takeIf { it > 0 }
    return author.copy(
        id = id,
        avatarUrl = id?.let { yamiboAvatarUrl(it) }
            ?: author.avatarUrl?.takeIf(String::isNotBlank)?.let(::resolveYamiboAvatarUrl),
    )
}

/** Resolves only missing identities; image loading and caching stay with Coil. */
internal class ThreadAuthorRepository(
    private val loadAuthor: suspend (Int) -> YamiboPostAuthor,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val cacheSize: Int = 256,
) {
    private data class Entry(val author: YamiboPostAuthor?, val recordedAt: Long)

    private val cache = LinkedHashMap<Int, Entry>(16, 0.75f, true)
    private val locks = Array(32) { Mutex() }
    private val requests = Semaphore(2)

    init {
        require(cacheSize > 0)
    }

    suspend fun resolve(
        threadId: Int,
        knownAuthor: YamiboPostAuthor?,
        allowLookup: Boolean = true,
    ): YamiboPostAuthor? {
        val known = knownAuthor?.let(::normalizedThreadAuthor)
        if (threadId <= 0) return known
        if (known?.avatarUrl != null) {
            remember(threadId, known)
            return known
        }
        if (!allowLookup) return known
        // Fixed striped locks deduplicate a thread without an unbounded map of queued jobs.
        // The caller's coroutine owns the request, so scrolling away cancels pending work.
        return locks[threadId % locks.size].withLock {
            val cached = synchronized(cache) { cache[threadId] }
            if (cached != null && (cached.author != null || clock() - cached.recordedAt < 30_000)) {
                return@withLock cached.author ?: known
            }
            val resolved = requests.withPermit {
                val supplied = synchronized(cache) { cache[threadId]?.author }
                if (supplied != null) supplied else {
                    try {
                        normalizedThreadAuthor(loadAuthor(threadId))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            remember(threadId, resolved) ?: known
        }
    }

    private fun remember(threadId: Int, author: YamiboPostAuthor?): YamiboPostAuthor? =
        synchronized(cache) {
            // A failing older lookup must not erase identity supplied by another list meanwhile.
            val confirmed = author ?: cache[threadId]?.author
            cache[threadId] = Entry(confirmed, clock())
            while (cache.size > cacheSize) cache.remove(cache.keys.first())
            confirmed
        }
}
