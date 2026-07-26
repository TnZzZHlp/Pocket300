package com.yamibo.pocket300.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PostDownloadFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun commitsAndRestoresTextOnlyPostWithoutImageFiles() {
        val root = temporaryFolder.newFolder("downloads")
        val store = PostDownloadFileStore(root)
        val request = testDownloadRequest()
        val staging = store.createStaging(request.key)

        val committed = store.commit(staging, request, emptyList(), completedAt = 20L)
        val restored = PostDownloadFileStore(root).read(request.key)

        assertEquals(request.snapshot, committed.snapshot)
        assertNotNull(restored)
        assertTrue(restored!!.manifest.hasText)
        assertFalse(restored.manifest.hasImages)
        assertTrue(restored.localImageUris.isEmpty())
        assertEquals(listOf(request.key), PostDownloadFileStore(root).listCompleted().map { it.key })
    }

    @Test
    fun partialImageFailureNeverCreatesAReadableFinalPostOrManifest() {
        val root = temporaryFolder.newFolder("downloads")
        val store = PostDownloadFileStore(root)
        val request = testDownloadRequest(
            html = "<img src='one'><img src='two'>",
            imageUrls = listOf(
                "https://bbs.yamibo.com/one.png",
                "https://bbs.yamibo.com/two.png",
            ),
            hasText = false,
        )
        val staging = store.createStaging(request.key)
        val first = stagedImage(staging, 0, request.remoteImageUrls.first())

        assertThrows(IllegalArgumentException::class.java) {
            store.commit(staging, request, listOf(first), completedAt = 20L)
        }

        assertNull(store.read(request.key))
        assertFalse(root.manifestFile(request.key.threadId, request.key.postId).exists())
        assertFalse(staging.directory.resolve("manifest.json").exists())
    }

    @Test
    fun missingImageInvalidatesPreviouslyCompletedPost() {
        val root = temporaryFolder.newFolder("downloads")
        val store = PostDownloadFileStore(root)
        val request = testDownloadRequest(
            html = "<img src='one'>",
            imageUrls = listOf("https://bbs.yamibo.com/one.png"),
            hasText = false,
        )
        val staging = store.createStaging(request.key)
        store.commit(
            staging,
            request,
            listOf(stagedImage(staging, 0, request.remoteImageUrls.single())),
            completedAt = 20L,
        )
        val downloaded = requireNotNull(store.read(request.key))
        downloaded.localImageFiles.values.single().delete()

        assertTrue(store.inspect(request.key) is StoredPostDownload.Invalid)
        assertNull(store.read(request.key))
        assertTrue(store.listCompleted().isEmpty())
        assertTrue(store.cleanupInvalidDownloads())
        assertFalse(root.resolve("${request.key.threadId}/${request.key.postId}").exists())
    }

    @Test
    fun modifiedImageDigestAndCorruptManifestAreNeverReportedAsComplete() {
        val root = temporaryFolder.newFolder("downloads")
        val store = PostDownloadFileStore(root)
        val first = testDownloadRequest(
            threadId = 1000,
            postId = 2000,
            html = "<img>",
            imageUrls = listOf("https://bbs.yamibo.com/one.png"),
            hasText = false,
        )
        val firstStaging = store.createStaging(first.key)
        store.commit(
            firstStaging,
            first,
            listOf(stagedImage(firstStaging, 0, first.remoteImageUrls.single())),
            completedAt = 20L,
        )
        requireNotNull(store.read(first.key)).localImageFiles.values.single()
            .appendBytes(byteArrayOf(1))

        assertTrue(store.inspect(first.key) is StoredPostDownload.Invalid)

        val second = testDownloadRequest(threadId = 1001, postId = 2001)
        val secondStaging = store.createStaging(second.key)
        store.commit(secondStaging, second, emptyList(), completedAt = 20L)
        root.manifestFile(second.key.threadId, second.key.postId).writeText("{broken")

        assertTrue(store.inspect(second.key) is StoredPostDownload.Invalid)
        assertTrue(store.listCompleted().isEmpty())
    }

    @Test
    fun deleteRemovesManifestImagesAndQueuedRequest() {
        val root = temporaryFolder.newFolder("downloads")
        val store = PostDownloadFileStore(root)
        val request = testDownloadRequest()
        store.persistRequest(request)
        val staging = store.createStaging(request.key)
        store.commit(staging, request, emptyList(), completedAt = 20L)

        assertTrue(store.delete(request.key))

        assertNull(store.read(request.key))
        assertNull(store.loadQueuedRequest(request.key))
        assertTrue(store.listCompleted().isEmpty())
    }

    @Test
    fun queueRequestSurvivesStoreRecreation() {
        val root = temporaryFolder.newFolder("downloads")
        val request = testDownloadRequest()
        PostDownloadFileStore(root).persistRequest(request)

        val restored = PostDownloadFileStore(root).loadQueuedRequests()

        assertEquals(listOf(request), restored)
    }

    @Test
    fun failedQueueStateIsDurableAndCanReturnToPending() {
        val root = temporaryFolder.newFolder("downloads")
        val store = PostDownloadFileStore(root)
        val request = testDownloadRequest()

        store.persistFailedRequest(request)

        assertNull(store.loadQueuedRequest(request.key))
        assertTrue(store.loadQueuedRequests().isEmpty())
        assertEquals(listOf(request), store.loadFailedRequests())

        store.persistRequest(request)

        assertEquals(request, store.loadQueuedRequest(request.key))
        assertEquals(listOf(request), store.loadQueuedRequests())
        assertTrue(store.loadFailedRequests().isEmpty())
    }

    @Test
    fun deleteAllRepairsInternalDirectoryPaths() {
        val root = temporaryFolder.newFolder("downloads")
        val store = PostDownloadFileStore(root)
        val stagingRoot = root.resolve(".staging")
        assertTrue(stagingRoot.deleteRecursively())
        stagingRoot.writeText("not a directory")

        assertTrue(store.deleteAll())

        assertTrue(stagingRoot.isDirectory)
        assertTrue(root.resolve(".queue").isDirectory)
        assertTrue(store.createStaging(testDownloadRequest().key).directory.isDirectory)
    }

    @Test
    fun formatsWithoutGuaranteedReaderDecodersAreRejectedBeforeCommit() {
        val root = temporaryFolder.newFolder("downloads")
        val store = PostDownloadFileStore(root)
        val unsupportedImages = listOf(
            "<svg xmlns='http://www.w3.org/2000/svg'></svg>".toByteArray(),
            byteArrayOf(
                0x00,
                0x00,
                0x00,
                0x18,
                'f'.code.toByte(),
                't'.code.toByte(),
                'y'.code.toByte(),
                'p'.code.toByte(),
                'a'.code.toByte(),
                'v'.code.toByte(),
                'i'.code.toByte(),
                'f'.code.toByte(),
            ),
        )

        unsupportedImages.forEachIndexed { index, bytes ->
            val request = testDownloadRequest(
                threadId = 1100 + index,
                postId = 2100 + index,
                html = "<img>",
                imageUrls = listOf("https://bbs.yamibo.com/image-$index"),
                hasText = false,
            )
            val staging = store.createStaging(request.key)

            assertThrows(IllegalArgumentException::class.java) {
                store.commit(
                    staging,
                    request,
                    listOf(stagedImage(staging, 0, request.remoteImageUrls.single(), bytes)),
                    completedAt = 20L,
                )
            }
            assertNull(store.read(request.key))
        }
    }

    @Test
    fun deviceDecoderRejectionPreventsACompletedDownload() {
        val root = temporaryFolder.newFolder("downloads")
        val store = PostDownloadFileStore(
            rootDirectory = root,
            decoderValidator = PostDownloadImageDecoderValidator { false },
        )
        val request = testDownloadRequest(
            html = "<img>",
            imageUrls = listOf("https://bbs.yamibo.com/image.png"),
            hasText = false,
        )
        val staging = store.createStaging(request.key)

        assertThrows(IllegalArgumentException::class.java) {
            store.commit(
                staging,
                request,
                listOf(stagedImage(staging, 0, request.remoteImageUrls.single())),
                completedAt = 20L,
            )
        }
        assertNull(store.read(request.key))
    }
}
