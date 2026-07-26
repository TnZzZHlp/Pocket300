package com.yamibo.pocket300.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PostHtmlRendererTest {
    @Test
    fun resolvesDownloadedImageByNormalizedRemoteUrl() {
        val remote = "https://bbs.yamibo.com/data/attachment/forum/example.jpg"
        val local = "file:/downloads/100/200/image-0001.bin"

        assertEquals(
            local,
            resolvePostImageUrl(
                "/data/attachment/forum/example.jpg",
                mapOf(remote to local),
            ),
        )
    }

    @Test
    fun keepsRemoteImageWhenNoDownloadExists() {
        assertEquals(
            "https://example.com/image.jpg",
            resolvePostImageUrl("https://example.com/image.jpg", emptyMap()),
        )
    }

    @Test
    fun keepsLocalImageSchemesInsteadOfPrefixingForumOrigin() {
        assertEquals(
            "file:/downloads/image.bin",
            normalizePostImageUrl("file:/downloads/image.bin"),
        )
        assertEquals(
            "content://downloads/image",
            normalizePostImageUrl("content://downloads/image"),
        )
    }

    @Test
    fun offlineRenderingNeverFallsBackToUnmappedRemoteImage() {
        assertEquals(
            null,
            resolvePostImageSource(
                source = "https://example.com/smiley.png",
                localImageUrls = emptyMap(),
                allowRemoteImages = false,
            ),
        )
        assertEquals(
            "file:/downloads/smiley.bin",
            resolvePostImageSource(
                source = "https://example.com/smiley.png",
                localImageUrls = mapOf(
                    "https://example.com/smiley.png" to "file:/downloads/smiley.bin",
                ),
                allowRemoteImages = false,
            ),
        )
    }
}
