package com.yamibo.pocket300.data.download

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ThreadDownloadManifestCodecTest {
    private val codec = ThreadDownloadManifestCodec()

    @Test
    fun requestRoundTripPreservesThreadMetadataAndQueueState() {
        val request = testRequest()

        val pending = codec.decodeRequest(codec.encodeRequest(request))
        val failed = codec.decodeStoredRequest(
            codec.encodeRequest(request, ThreadDownloadRequestState.FAILED),
        )

        assertEquals(request, pending)
        assertEquals(request, failed.request)
        assertEquals(ThreadDownloadRequestState.FAILED, failed.state)
        assertEquals(request.requestedAt, failed.queueOrder)
    }

    @Test
    fun requestRoundTripPreservesExplicitQueueOrderAndReadsLegacyRequests() {
        val request = testRequest(requestedAt = 10L)
        val ordered = codec.decodeStoredRequest(codec.encodeRequest(request, queueOrder = 20L))
        val legacy = JSONObject(codec.encodeRequest(request)).apply {
            remove("queueOrder")
        }

        assertEquals(20L, ordered.queueOrder)
        assertEquals(10L, codec.decodeStoredRequest(legacy.toString()).queueOrder)
    }

    @Test
    fun manifestRoundTripPreservesPollAllPostsPaginationAndImages() {
        val thread = testThread(replyCount = 2)
        val imageUrl = "https://bbs.yamibo.com/image.png"
        val snapshot = testSnapshot(
            thread = thread,
            posts = listOf(
                testPost(thread.id, 2000, 1),
                testPost(thread.id, 2001, 2, "<img data-src='$imageUrl'>"),
                testPost(thread.id, 2002, 3),
            ),
            poll = testPoll(),
            capturedPageCount = 2,
            sourcePageSize = 2,
            sourceTotalPosts = 3,
        )
        val manifest = ThreadDownloadManifest(
            snapshot = snapshot,
            images = listOf(
                ThreadDownloadImage(
                    remoteUrl = imageUrl,
                    relativePath = "images/00001.img",
                    byteCount = TEST_PNG_BYTES.size.toLong(),
                    sha256 = "0".repeat(64),
                    contentType = "image/png",
                ),
            ),
            requestedAt = 10L,
            completedAt = 20L,
        )

        assertEquals(manifest, codec.decodeManifest(codec.encodeManifest(manifest)))
    }

    @Test
    fun missingQueueStateDefaultsToPendingAndUnknownVersionIsRejected() {
        val request = testRequest()
        val legacy = JSONObject(codec.encodeRequest(request)).apply {
            remove("queueState")
        }
        val unsupported = JSONObject(codec.encodeRequest(request))
            .put("version", 99)
            .toString()

        assertEquals(
            ThreadDownloadRequestState.PENDING,
            codec.decodeStoredRequest(legacy.toString()).state,
        )
        assertThrows(IllegalArgumentException::class.java) {
            codec.decodeRequest(unsupported)
        }
    }
}
