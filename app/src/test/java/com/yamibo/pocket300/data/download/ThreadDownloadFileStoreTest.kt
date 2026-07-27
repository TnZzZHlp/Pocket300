package com.yamibo.pocket300.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThreadDownloadFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun commitsAndRestoresAllPostsInTextOnlyThread() {
        val root = temporaryFolder.newFolder("downloads")
        val store = ThreadDownloadFileStore(root)
        val request = testRequest()
        val snapshot = testSnapshot(thread = request.thread)
        val staging = store.createStaging(request.key)

        val committed = store.commit(staging, request, snapshot, emptyList(), completedAt = 20L)
        val restored = ThreadDownloadFileStore(root).read(request.key)

        assertEquals(snapshot, committed.snapshot)
        assertEquals(snapshot.posts, restored?.snapshot?.posts)
        assertEquals(snapshot.posts[1], restored?.findPost(snapshot.posts[1].id))
        assertTrue(restored!!.localImageUris.isEmpty())
        assertEquals(listOf(request.key), ThreadDownloadFileStore(root).listCompleted().map { it.key })
    }

    @Test
    fun missingOrModifiedImageInvalidatesTheWholeThread() {
        val root = temporaryFolder.newFolder("downloads")
        val store = ThreadDownloadFileStore(root)
        val imageUrl = "https://bbs.yamibo.com/one.png"
        val thread = testThread()
        val snapshot = testSnapshot(
            thread = thread,
            posts = listOf(
                testPost(thread.id, 2000, 1, "<img data-src='$imageUrl'>"),
                testPost(thread.id, 2001, 2),
            ),
        )
        val request = testRequest(thread)
        val staging = store.createStaging(request.key)
        store.commit(
            staging,
            request,
            snapshot,
            listOf(stagedImage(staging, 0, imageUrl)),
            completedAt = 20L,
        )
        val downloaded = requireNotNull(store.read(request.key))
        downloaded.localImageFiles.getValue(imageUrl).appendBytes(byteArrayOf(1))

        assertTrue(store.inspect(request.key) is StoredThreadDownload.Invalid)
        assertNull(store.read(request.key))
        assertTrue(store.cleanupInvalidDownloads())
        assertFalse(root.resolve(thread.id.toString()).exists())
    }

    @Test
    fun failedRefreshLeavesPreviousSnapshotReadable() {
        val root = temporaryFolder.newFolder("downloads")
        val store = ThreadDownloadFileStore(root)
        val firstThread = testThread(subject = "First")
        val firstRequest = testRequest(firstThread, requestedAt = 10L)
        store.commit(
            store.createStaging(firstRequest.key),
            firstRequest,
            testSnapshot(thread = firstThread),
            emptyList(),
            completedAt = 11L,
        )

        val secondThread = firstThread.copy(subject = "Second")
        val secondRequest = testRequest(secondThread, requestedAt = 20L)
        val requiredImage = "https://bbs.yamibo.com/missing.png"
        val secondSnapshot = testSnapshot(
            thread = secondThread,
            posts = listOf(
                testPost(secondThread.id, 2000, 1, "<img src='$requiredImage'>"),
            ),
        )
        val secondStaging = store.createStaging(secondRequest.key)

        assertThrows(IllegalArgumentException::class.java) {
            store.commit(
                secondStaging,
                secondRequest,
                secondSnapshot,
                emptyList(),
                completedAt = 21L,
            )
        }
        assertEquals("First", store.read(firstRequest.key)?.snapshot?.thread?.subject)
    }

    @Test
    fun successfulRefreshAtomicallyPublishesNewVersionAndCleansOldVersion() {
        val root = temporaryFolder.newFolder("downloads")
        val store = ThreadDownloadFileStore(root)
        val firstThread = testThread(subject = "First")
        val firstRequest = testRequest(firstThread, requestedAt = 10L)
        val first = store.commit(
            store.createStaging(firstRequest.key),
            firstRequest,
            testSnapshot(thread = firstThread),
            emptyList(),
            completedAt = 11L,
        )
        val secondThread = firstThread.copy(subject = "Second", replyCount = 2)
        val secondRequest = testRequest(secondThread, requestedAt = 20L)
        val second = store.commit(
            store.createStaging(secondRequest.key),
            secondRequest,
            testSnapshot(
                thread = secondThread,
                posts = listOf(
                    testPost(secondThread.id, 2000, 1),
                    testPost(secondThread.id, 2001, 2),
                    testPost(secondThread.id, 2002, 3),
                ),
            ),
            emptyList(),
            completedAt = 21L,
        )

        assertNotEquals(first.directory, second.directory)
        assertEquals("Second", store.read(secondRequest.key)?.snapshot?.thread?.subject)
        assertFalse(first.directory.exists())
        assertTrue(second.directory.exists())
    }

    @Test
    fun queueAndFailedStateSurviveStoreRecreation() {
        val root = temporaryFolder.newFolder("downloads")
        val request = testRequest()
        ThreadDownloadFileStore(root).persistFailedRequest(request)

        val restored = ThreadDownloadFileStore(root)

        assertNull(restored.loadQueuedRequest(request.key))
        assertEquals(listOf(request), restored.loadFailedRequests())
        restored.persistRequest(request)
        assertEquals(request, restored.loadQueuedRequest(request.key))
        assertTrue(restored.loadFailedRequests().isEmpty())
    }

    @Test
    fun restoresPendingRequestsByDurableQueueOrder() {
        val root = temporaryFolder.newFolder("downloads")
        val first = testRequest(testThread(threadId = 1000), requestedAt = 20L)
        val second = testRequest(testThread(threadId = 1001), requestedAt = 10L)
        val store = ThreadDownloadFileStore(root)
        store.persistRequest(first, queueOrder = 2L)
        store.persistRequest(second, queueOrder = 1L)

        val restored = ThreadDownloadFileStore(root)

        assertEquals(listOf(second, first), restored.loadQueuedRequests())
        assertEquals(
            listOf(second.key, first.key),
            restored.loadQueuedEntries().map { it.request.key },
        )
    }

    @Test
    fun unsupportedImageNeverCreatesAReadableThread() {
        val root = temporaryFolder.newFolder("downloads")
        val store = ThreadDownloadFileStore(root)
        val url = "https://bbs.yamibo.com/image.svg"
        val thread = testThread()
        val request = testRequest(thread)
        val snapshot = testSnapshot(
            thread = thread,
            posts = listOf(testPost(thread.id, 2000, 1, "<img src='$url'>")),
        )
        val staging = store.createStaging(request.key)

        assertThrows(IllegalArgumentException::class.java) {
            store.commit(
                staging,
                request,
                snapshot,
                listOf(
                    stagedImage(
                        staging,
                        0,
                        url,
                        "<svg xmlns='http://www.w3.org/2000/svg'/>".toByteArray(),
                    ),
                ),
                completedAt = 20L,
            )
        }
        assertNull(store.read(request.key))
    }

    @Test
    fun deleteRemovesCompletedQueueAndStagingProducts() {
        val store = ThreadDownloadFileStore(temporaryFolder.newFolder("downloads"))
        val request = testRequest()
        store.persistRequest(request)
        store.commit(
            store.createStaging(request.key),
            request,
            testSnapshot(thread = request.thread),
            emptyList(),
            completedAt = 20L,
        )

        assertTrue(store.delete(request.key))
        assertNull(store.read(request.key))
        assertNull(store.loadQueuedRequest(request.key))
        assertTrue(store.listCompleted().isEmpty())
    }
}
