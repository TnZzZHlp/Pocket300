package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.api.YamiboThreadDetails
import com.yamibo.pocket300.api.YamiboThreadSpecialType
import com.yamibo.pocket300.data.download.DownloadedThread
import com.yamibo.pocket300.data.download.ThreadDownloadImage
import com.yamibo.pocket300.data.download.ThreadDownloadManifest
import com.yamibo.pocket300.data.download.ThreadDownloadSnapshot
import com.yamibo.pocket300.ui.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReaderScreenTest {
    @Test
    fun reusesMatchingPostContentWhenOpeningReader() {
        assertFalse(needsReaderContentLoad(1000, 2000, threadId = 1000, postId = 2000))
        assertTrue(needsReaderContentLoad(null, null, threadId = 1000, postId = 2000))
        assertTrue(needsReaderContentLoad(1000, 2001, threadId = 1000, postId = 2000))
    }

    @Test
    fun offlineOnlyReaderNeverCallsNetwork() = runBlocking {
        val downloaded = readerContent(source = ReaderContentSource.DOWNLOAD)
        var networkCalled = false
        val states = mutableListOf<LoadState<ReaderContent>>()

        loadReaderContentLocalFirst(
            threadId = 1000,
            postId = 2000,
            initialContent = null,
            offlineOnly = true,
            offlineUnavailableMessage = "offline unavailable",
            loadDownloaded = { downloaded },
            loadNetwork = {
                networkCalled = true
                readerContent()
            },
            onState = states::add,
        )

        assertEquals(1, states.size)
        assertSame(downloaded, (states.single() as LoadState.Ready).value)
        assertFalse(networkCalled)
    }

    @Test
    fun showsDownloadedPostBeforeBackgroundRefreshCompletes() = runBlocking {
        val downloaded = readerContent(source = ReaderContentSource.DOWNLOAD)
        val fresh = readerContent().copy(
            post = readerContent().post.copy(html = "<p>fresh</p>"),
        )
        val releaseNetwork = CompletableDeferred<ReaderContent>()
        val networkStarted = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val states = mutableListOf<LoadState<ReaderContent>>()

        val job = async {
            loadReaderContentLocalFirst(
                threadId = 1000,
                postId = 2000,
                initialContent = null,
                offlineOnly = false,
                offlineUnavailableMessage = "offline unavailable",
                loadDownloaded = {
                    events += "read-local"
                    downloaded
                },
                loadNetwork = {
                    events += "start-network"
                    networkStarted.complete(Unit)
                    releaseNetwork.await()
                },
                onState = {
                    states += it
                    if (it is LoadState.Ready && it.value === downloaded) {
                        events += "emit-local"
                    }
                },
            )
        }

        networkStarted.await()
        assertFalse(job.isCompleted)
        assertEquals(listOf("read-local", "emit-local", "start-network"), events)
        assertEquals(1, states.size)
        assertSame(downloaded, (states.single() as LoadState.Ready).value)

        releaseNetwork.complete(fresh)
        job.await()

        assertEquals(2, states.size)
        assertEquals("<p>fresh</p>", (states.last() as LoadState.Ready).value.post.html)
    }

    @Test
    fun keepsDownloadedPostWhenBackgroundRefreshFails() = runBlocking {
        val downloaded = readerContent(source = ReaderContentSource.DOWNLOAD)
        val states = mutableListOf<LoadState<ReaderContent>>()

        loadReaderContentLocalFirst(
            threadId = 1000,
            postId = 2000,
            initialContent = null,
            offlineOnly = false,
            offlineUnavailableMessage = "offline unavailable",
            loadDownloaded = { downloaded },
            loadNetwork = { error("network unavailable") },
            onState = states::add,
        )

        assertEquals(1, states.size)
        assertSame(downloaded, (states.single() as LoadState.Ready).value)
    }

    @Test
    fun freshReaderContentKeepsDownloadedImageFiles() = runBlocking {
        val live = readerContent()
        val downloaded = readerContent(
            source = ReaderContentSource.DOWNLOAD,
            localImages = mapOf(
                "https://example.com/page.jpg" to "file:/downloads/page.bin",
            ),
        )
        val states = mutableListOf<LoadState<ReaderContent>>()

        loadReaderContentLocalFirst(
            threadId = 1000,
            postId = 2000,
            initialContent = live,
            offlineOnly = false,
            offlineUnavailableMessage = "offline unavailable",
            loadDownloaded = { downloaded },
            loadNetwork = { live },
            onState = states::add,
        )

        assertSame(downloaded, (states.first() as LoadState.Ready).value)
        val refreshed = (states.last() as LoadState.Ready).value
        assertEquals(downloaded.localImageUrls, refreshed.localImageUrls)
        assertEquals(ReaderContentSource.NETWORK, refreshed.source)
    }

    @Test
    fun staleDownloadedInitialContentReloadsNetworkWhenLocalProductIsMissing() = runBlocking {
        val stale = readerContent(source = ReaderContentSource.DOWNLOAD)
        val live = readerContent(source = ReaderContentSource.NETWORK)
        var networkCalled = false
        val states = mutableListOf<LoadState<ReaderContent>>()

        loadReaderContentLocalFirst(
            threadId = 1000,
            postId = 2000,
            initialContent = stale,
            offlineOnly = false,
            offlineUnavailableMessage = "offline unavailable",
            loadDownloaded = { null },
            loadNetwork = {
                networkCalled = true
                live
            },
            onState = states::add,
        )

        assertTrue(networkCalled)
        assertSame(LoadState.Loading, states.first())
        assertEquals(live, (states.last() as LoadState.Ready).value)
    }

    @Test
    fun reusableLiveContentSurvivesRefreshFailureWithoutStaleLocalFiles() = runBlocking {
        val reusable = readerContent(
            localImages = mapOf(
                "https://example.com/page.jpg" to "file:/deleted/page.bin",
            ),
        )
        val states = mutableListOf<LoadState<ReaderContent>>()

        loadReaderContentLocalFirst(
            threadId = 1000,
            postId = 2000,
            initialContent = reusable,
            offlineOnly = false,
            offlineUnavailableMessage = "offline unavailable",
            loadDownloaded = { null },
            loadNetwork = { error("network unavailable") },
            onState = states::add,
        )

        val retained = (states.single() as LoadState.Ready).value
        assertEquals(reusable.post, retained.post)
        assertTrue(retained.localImageUrls.isEmpty())
        assertEquals(ReaderContentSource.NETWORK, retained.source)
    }

    @Test
    fun offlineOnlyReaderReportsMissingDownloadWithoutCallingNetwork() = runBlocking {
        var networkCalled = false
        val states = mutableListOf<LoadState<ReaderContent>>()

        loadReaderContentLocalFirst(
            threadId = 1000,
            postId = 2000,
            initialContent = null,
            offlineOnly = true,
            offlineUnavailableMessage = "offline unavailable",
            loadDownloaded = { null },
            loadNetwork = {
                networkCalled = true
                readerContent()
            },
            onState = states::add,
        )

        assertEquals("offline unavailable", (states.single() as LoadState.Failed).message)
        assertFalse(networkCalled)
    }

    @Test
    fun usesBlockingNetworkFailureOnlyWhenNoLocalOrReusableContentExists() = runBlocking {
        val states = mutableListOf<LoadState<ReaderContent>>()

        loadReaderContentLocalFirst(
            threadId = 1000,
            postId = 2000,
            initialContent = null,
            offlineOnly = false,
            offlineUnavailableMessage = "offline unavailable",
            loadDownloaded = { null },
            loadNetwork = { error("network unavailable") },
            onState = states::add,
        )

        assertSame(LoadState.Loading, states.first())
        assertEquals("network unavailable", (states.last() as LoadState.Failed).message)
    }

    @Test(expected = CancellationException::class)
    fun backgroundRefreshDoesNotSwallowCancellation() = runBlocking {
        loadReaderContentLocalFirst(
            threadId = 1000,
            postId = 2000,
            initialContent = null,
            offlineOnly = false,
            offlineUnavailableMessage = "offline unavailable",
            loadDownloaded = {
                readerContent(source = ReaderContentSource.DOWNLOAD)
            },
            loadNetwork = { throw CancellationException("cancelled") },
            onState = {},
        )
    }

    @Test
    fun removedDownloadClearsLocalFilesForOnlineReader() {
        val current = readerContent(
            source = ReaderContentSource.DOWNLOAD,
            localImages = mapOf(
                "https://example.com/page.jpg" to "file:/deleted/page.bin",
            ),
        )

        val state = reconcileReaderDownloadedContent(
            current = current,
            downloaded = null,
            offlineOnly = false,
            offlineUnavailableMessage = "offline unavailable",
        )

        val content = (state as LoadState.Ready).value
        assertTrue(content.localImageUrls.isEmpty())
        assertEquals(ReaderContentSource.NETWORK, content.source)
    }

    @Test
    fun removedDownloadMakesStrictOfflineReaderUnavailable() {
        val state = reconcileReaderDownloadedContent(
            current = readerContent(source = ReaderContentSource.DOWNLOAD),
            downloaded = null,
            offlineOnly = true,
            offlineUnavailableMessage = "offline unavailable",
        )

        assertEquals("offline unavailable", (state as LoadState.Failed).message)
    }

    @Test
    fun textOnlyReplacementClearsOldDownloadedImageFiles() {
        val current = readerContent(
            localImages = mapOf(
                "https://example.com/page.jpg" to "file:/deleted/page.bin",
            ),
        )
        val textOnlyDownload = readerContent(source = ReaderContentSource.DOWNLOAD)

        val state = reconcileReaderDownloadedContent(
            current = current,
            downloaded = textOnlyDownload,
            offlineOnly = false,
            offlineUnavailableMessage = "offline unavailable",
        )

        val content = (state as LoadState.Ready).value
        assertTrue(content.localImageUrls.isEmpty())
        assertEquals(ReaderContentSource.NETWORK, content.source)
    }

    @Test
    fun resolvesReaderImagesToDownloadedFilesInOrder() {
        assertEquals(
            listOf("file:/downloads/first.bin", "https://example.com/second.jpg"),
            resolveReaderImageUrls(
                listOf(
                    "https://example.com/first.jpg",
                    "https://example.com/second.jpg",
                ),
                mapOf("https://example.com/first.jpg" to "file:/downloads/first.bin"),
            ),
        )
    }

    @Test
    fun offlineReaderOmitsImagesThatAreNotStoredLocally() {
        assertEquals(
            listOf("file:/downloads/first.bin"),
            resolveReaderImageUrls(
                remoteImageUrls = listOf(
                    "https://example.com/first.jpg",
                    "https://example.com/missing.jpg",
                ),
                localImageUrls = mapOf(
                    "https://example.com/first.jpg" to "file:/downloads/first.bin",
                ),
                allowRemoteImages = false,
            ),
        )
    }

    @Test
    fun downloadedThreadReaderContentSelectsRequestedFloorAndSharesThreadImages() {
        val original = readerContent()
        val reply = original.post.copy(
            id = 2001,
            isOriginalPost = false,
            number = 2,
            position = 2,
        )
        val downloaded = DownloadedThread(
            manifest = ThreadDownloadManifest(
                snapshot = ThreadDownloadSnapshot(
                    thread = original.thread.copy(replyCount = 1, maxPosition = 2),
                    poll = null,
                    posts = listOf(original.post, reply),
                    capturedPageCount = 1,
                    sourcePageSize = 20,
                    sourceTotalPosts = 2,
                ),
                images = listOf(
                    ThreadDownloadImage(
                        remoteUrl = "https://example.com/page.jpg",
                        relativePath = "images/0001.img",
                        byteCount = 1,
                        sha256 = "0".repeat(64),
                        contentType = "image/jpeg",
                    ),
                ),
                requestedAt = 1L,
                completedAt = 2L,
            ),
            directory = File("downloads"),
        )

        val result = requireNotNull(downloaded.toReaderContent(reply.id))

        assertEquals(reply, result.post)
        assertEquals(downloaded.snapshot.thread, result.thread)
        assertEquals(downloaded.localImageUris, result.localImageUrls)
        assertEquals(ReaderContentSource.DOWNLOAD, result.source)
        assertNull(downloaded.toReaderContent(postId = 9999))
    }

    private fun readerContent(
        source: ReaderContentSource = ReaderContentSource.NETWORK,
        localImages: Map<String, String> = emptyMap(),
    ): ReaderContent {
        val author = YamiboPostAuthor(
            avatarUrl = null,
            groupIconId = null,
            groupId = null,
            id = 10,
            isAnonymous = false,
            name = "Author",
        )
        return ReaderContent(
            thread = YamiboThreadDetails(
                author = author,
                createdAt = 1L,
                digestLevel = 0,
                forumId = 300,
                heat = 0,
                hasAttachment = localImages.isNotEmpty(),
                id = 1000,
                isClosed = false,
                lastPoster = "Author",
                lastPostAtText = "today",
                maxPosition = 1,
                price = 0,
                readPermission = 0,
                recommendationCount = 0,
                replyCount = 0,
                specialType = YamiboThreadSpecialType.NORMAL,
                specialTypeId = 0,
                subject = "Subject",
                typeId = null,
                viewCount = 1,
                webUrl = "https://bbs.yamibo.com/thread-1000-1-1.html",
            ),
            post = YamiboPost(
                attachments = emptyList(),
                author = author,
                comments = emptyList(),
                createdAt = 1L,
                createdAtText = "today",
                html = "<p>Text</p>",
                hasAttachment = localImages.isNotEmpty(),
                id = 2000,
                isOriginalPost = true,
                number = 1,
                position = 1,
                ratingCount = 0,
                replyCredit = 0,
                status = 0,
                threadId = 1000,
            ),
            localImageUrls = localImages,
            source = source,
        )
    }
}
