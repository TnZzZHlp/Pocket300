package com.yamibo.pocket300.data.download

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PostDownloadManifestCodecTest {
    private val codec = PostDownloadManifestCodec()

    @Test
    fun requestRoundTripPreservesCompleteThreadPostSnapshotAndImageOrder() {
        val request = testDownloadRequest(
            imageUrls = listOf(
                "https://bbs.yamibo.com/one.png",
                "https://bbs.yamibo.com/two.png",
            ),
        )

        val decoded = codec.decodeRequest(codec.encodeRequest(request))

        assertEquals(request, decoded)
        assertTrue(decoded.hasText)
        assertTrue(decoded.hasImages)
    }

    @Test
    fun manifestRoundTripPreservesTextAndImageCapabilitiesIndependently() {
        val request = testDownloadRequest(
            html = "<p>Text plus image</p>",
            imageUrls = listOf("https://bbs.yamibo.com/one.png"),
        )
        val image = PostDownloadImage(
            remoteUrl = request.remoteImageUrls.single(),
            relativePath = "images/0001.img",
            byteCount = TEST_PNG_BYTES.size.toLong(),
            sha256 = "0".repeat(64),
            contentType = "image/png",
        )
        val manifest = PostDownloadManifest(
            snapshot = request.snapshot,
            hasText = true,
            images = listOf(image),
            requestedAt = request.requestedAt,
            completedAt = 20L,
        )

        val decoded = codec.decodeManifest(codec.encodeManifest(manifest))

        assertEquals(manifest, decoded)
        assertTrue(decoded.hasText)
        assertTrue(decoded.hasImages)
    }

    @Test
    fun textOnlyRequestDoesNotPretendToHaveImages() {
        val request = testDownloadRequest()

        val decoded = codec.decodeRequest(codec.encodeRequest(request))

        assertTrue(decoded.hasText)
        assertFalse(decoded.hasImages)
        assertEquals(emptyList<String>(), decoded.remoteImageUrls)
    }

    @Test
    fun requestQueueStateRoundTripsAndLegacyRequestsDefaultToPending() {
        val request = testDownloadRequest()
        val failed = codec.decodeStoredRequest(
            codec.encodeRequest(request, PostDownloadRequestState.FAILED),
        )
        val legacyObject = JSONObject(codec.encodeRequest(request))
        legacyObject.remove("queueState")
        val legacy = legacyObject.toString()

        assertEquals(PostDownloadRequestState.FAILED, failed.state)
        assertEquals(request, failed.request)
        assertEquals(
            PostDownloadRequestState.PENDING,
            codec.decodeStoredRequest(legacy).state,
        )
    }

    @Test
    fun rejectsMetadataOnlyRequestAndUnsupportedManifestVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            testDownloadRequest(html = "<br>", hasText = false)
        }
        val encoded = JSONObject(codec.encodeRequest(testDownloadRequest()))
            .put("version", 99)
            .toString()

        assertThrows(IllegalArgumentException::class.java) {
            codec.decodeRequest(encoded)
        }
    }
}
