package com.yamibo.pocket300.data

import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.api.yamiboAvatarUrl
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ThreadAuthorRepositoryTest {
    @Test fun usesConfirmedUidWithoutFetchingThread() = runBlocking {
        val repository = ThreadAuthorRepository(loadAuthor = { error("Unexpected lookup") })
        val author = repository.resolve(100, YamiboPostAuthor(null, 42, "alice"))!!
        assertEquals(yamiboAvatarUrl(42), author.avatarUrl)
        assertEquals(42, author.id)
        assertEquals(author, repository.resolve(100, null))
    }

    @Test fun normalizesVerifiedLegacyAvatarWithoutGuessingUidFromName() = runBlocking {
        val repository = ThreadAuthorRepository(loadAuthor = { error("Unexpected lookup") })
        val author = repository.resolve(
            100,
            YamiboPostAuthor("https://bbs.yamibo.com/uc_server/avatar.php?uid=42&size=small", null, "alice"),
        )!!
        assertEquals(yamiboAvatarUrl(42), author.avatarUrl)
        assertNull(author.id)
    }

    @Test fun fetchesMissingIdentityByThreadIdAndCachesIt() = runBlocking {
        val requested = mutableListOf<Int>()
        val repository = ThreadAuthorRepository(loadAuthor = {
            requested += it
            YamiboPostAuthor(null, 42, "actual author")
        })
        val author = repository.resolve(100, YamiboPostAuthor(null, null, "old nickname"))!!
        assertEquals("actual author", author.name)
        assertEquals(yamiboAvatarUrl(42), author.avatarUrl)
        assertEquals(author, repository.resolve(100, null))
        assertEquals(listOf(100), requested)
    }

    @Test fun leavesAnonymousAuthorNeutralWhenLookupIsDisabled() = runBlocking {
        val repository = ThreadAuthorRepository(loadAuthor = { error("Unexpected lookup") })
        val author = repository.resolve(100, YamiboPostAuthor(null, 0, "匿名"), allowLookup = false)!!
        assertNull(author.id)
        assertNull(author.avatarUrl)
    }

    @Test fun deduplicatesConcurrentRequestsForTheSameThread() = runBlocking {
        var calls = 0
        val repository = ThreadAuthorRepository(loadAuthor = {
            calls++
            delay(20)
            YamiboPostAuthor(null, 42, "alice")
        })
        val results = List(8) { async { repository.resolve(100, null) } }.awaitAll()
        assertEquals(1, calls)
        assertEquals(1, results.distinct().size)
    }

    @Test fun limitsMissingAuthorLookupsToTwoConcurrentRequests() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val repository = ThreadAuthorRepository(loadAuthor = {
            val current = active.incrementAndGet()
            peak.updateAndGet { previous -> maxOf(previous, current) }
            try {
                delay(20)
                YamiboPostAuthor(null, 42, "alice")
            } finally {
                active.decrementAndGet()
            }
        })
        (1..8).map { async { repository.resolve(it, null) } }.awaitAll()
        assertEquals(2, peak.get())
        assertEquals(0, active.get())
    }

    @Test fun brieflyCachesFailuresThenAllowsRetryWithoutLosingKnownName() = runBlocking {
        var calls = 0
        var now = 0L
        val repository = ThreadAuthorRepository(
            loadAuthor = {
                if (++calls == 1) error("Temporary failure")
                YamiboPostAuthor(null, 42, "alice")
            },
            clock = { now },
        )
        val known = YamiboPostAuthor(null, null, "alice")
        assertEquals(known, repository.resolve(100, known))
        assertNull(repository.resolve(100, null))
        assertEquals(1, calls)
        now = 30_001
        assertEquals(yamiboAvatarUrl(42), repository.resolve(100, known)!!.avatarUrl)
        assertEquals(2, calls)
    }

    @Test fun propagatesCancellationAndDoesNotCacheItAsFailure() = runBlocking {
        var calls = 0
        val repository = ThreadAuthorRepository(loadAuthor = {
            if (++calls == 1) throw CancellationException("Row left composition")
            YamiboPostAuthor(null, 42, "alice")
        })
        try {
            repository.resolve(100, null)
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // A later visible row must be able to retry.
        }
        assertEquals(42, repository.resolve(100, null)!!.id)
        assertEquals(2, calls)
    }

    @Test fun boundsCacheAndEvictsLeastRecentlyUsedThread() = runBlocking {
        val requested = mutableListOf<Int>()
        val repository = ThreadAuthorRepository(
            loadAuthor = {
                requested += it
                YamiboPostAuthor(null, it, "author")
            },
            cacheSize = 2,
        )
        repository.resolve(1, null)
        repository.resolve(2, null)
        repository.resolve(1, null)
        repository.resolve(3, null)
        repository.resolve(1, null)
        repository.resolve(2, null)
        assertEquals(listOf(1, 2, 3, 2), requested)
    }

    @Test fun skipsQueuedLookupWhenAnotherListSuppliesIdentityMeanwhile() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val requested = mutableListOf<Int>()
        val repository = ThreadAuthorRepository(loadAuthor = {
            requested += it
            release.await()
            YamiboPostAuthor(null, it + 10, "author")
        })
        val pending = (1..3).map { id ->
            async(start = CoroutineStart.UNDISPATCHED) { repository.resolve(id, null) }
        }
        val known = repository.resolve(3, YamiboPostAuthor(null, 42, "alice"))
        release.complete(Unit)
        assertEquals(known, pending.awaitAll().last())
        assertEquals(listOf(1, 2), requested)
    }

    @Test fun failedOlderLookupNeverErasesIdentitySuppliedByAnotherList() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val repository = ThreadAuthorRepository(loadAuthor = {
            release.await()
            error("Temporary failure")
        })
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.resolve(100, null) }
        val known = repository.resolve(100, YamiboPostAuthor(null, 42, "alice"))
        release.complete(Unit)
        assertEquals(known, pending.await())
        assertEquals(known, repository.resolve(100, null))
    }

    @Test fun skipsMalformedThreadIdsWithoutBreakingListRendering() = runBlocking {
        val repository = ThreadAuthorRepository(loadAuthor = { error("Unexpected lookup") })
        assertNull(repository.resolve(0, null))
        assertNull(repository.resolve(-1, null))
    }

    @Test fun cachesConfirmedAnonymousResultWithoutInventingAvatar() = runBlocking {
        var calls = 0
        val repository = ThreadAuthorRepository(loadAuthor = {
            calls++
            YamiboPostAuthor(null, null, "匿名")
        })
        assertNull(repository.resolve(100, null)!!.avatarUrl)
        assertNull(repository.resolve(100, null)!!.id)
        assertEquals(1, calls)
    }
}
