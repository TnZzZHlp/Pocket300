package com.yamibo.pocket300.data.download

import com.yamibo.pocket300.api.YAMIBO_ORIGIN
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboThreadDetails
import java.io.File
import java.net.URI

data class PostDownloadKey(
    val threadId: Int,
    val postId: Int,
) {
    init {
        require(threadId > 0) { "threadId must be positive" }
        require(postId > 0) { "postId must be positive" }
    }
}

data class PostDownloadSnapshot(
    val thread: YamiboThreadDetails,
    val post: YamiboPost,
) {
    val key: PostDownloadKey = PostDownloadKey(thread.id, post.id)

    init {
        require(post.threadId == thread.id) {
            "Post ${post.id} belongs to thread ${post.threadId}, not thread ${thread.id}"
        }
    }
}

/**
 * A fully self-contained request persisted before work is put on the in-process queue.
 *
 * A post may contain both readable text and images. Those capabilities are deliberately
 * represented independently rather than as an exclusive content type.
 */
data class PostDownloadRequest(
    val snapshot: PostDownloadSnapshot,
    val remoteImageUrls: List<String>,
    val hasText: Boolean = postHtmlHasReadableText(snapshot.post.html),
    val referer: String = snapshot.thread.webUrl.takeIf(String::isNotBlank)
        ?: "$YAMIBO_ORIGIN/thread-${snapshot.thread.id}-1-1.html",
    val requestedAt: Long = System.currentTimeMillis(),
) {
    val key: PostDownloadKey = snapshot.key
    val hasImages: Boolean get() = remoteImageUrls.isNotEmpty()

    init {
        require(requestedAt >= 0) { "requestedAt must not be negative" }
        require(remoteImageUrls.distinct() == remoteImageUrls) {
            "remoteImageUrls must be distinct and retain reading order"
        }
        require(hasText || remoteImageUrls.isNotEmpty()) {
            "A downloadable post must contain readable text or at least one image"
        }
        remoteImageUrls.forEach(::requireHttpUrl)
        requireHttpUrl(referer)
    }

    companion object {
        fun create(
            thread: YamiboThreadDetails,
            post: YamiboPost,
            remoteImageUrls: List<String>,
            hasText: Boolean = postHtmlHasReadableText(post.html),
            requestedAt: Long = System.currentTimeMillis(),
        ): PostDownloadRequest = PostDownloadRequest(
            snapshot = PostDownloadSnapshot(thread, post),
            remoteImageUrls = remoteImageUrls.distinct(),
            hasText = hasText,
            requestedAt = requestedAt,
        )
    }
}

data class PostDownloadImage(
    val remoteUrl: String,
    val relativePath: String,
    val byteCount: Long,
    val sha256: String,
    val contentType: String?,
) {
    init {
        requireHttpUrl(remoteUrl)
        require(relativePath.isNotBlank()) { "relativePath must not be blank" }
        require(byteCount > 0) { "byteCount must be positive" }
        require(SHA_256_PATTERN.matches(sha256)) { "sha256 must be a lowercase SHA-256 digest" }
    }
}

data class PostDownloadManifest(
    val snapshot: PostDownloadSnapshot,
    val hasText: Boolean,
    val images: List<PostDownloadImage>,
    val requestedAt: Long,
    val completedAt: Long,
    val version: Int = CURRENT_POST_DOWNLOAD_MANIFEST_VERSION,
) {
    val key: PostDownloadKey = snapshot.key
    val hasImages: Boolean get() = images.isNotEmpty()

    init {
        require(version == CURRENT_POST_DOWNLOAD_MANIFEST_VERSION) {
            "Unsupported post download manifest version: $version"
        }
        require(requestedAt >= 0) { "requestedAt must not be negative" }
        require(completedAt >= requestedAt) { "completedAt must not precede requestedAt" }
        require(images.map(PostDownloadImage::remoteUrl).distinct().size == images.size) {
            "Downloaded image URLs must be unique"
        }
        require(images.map(PostDownloadImage::relativePath).distinct().size == images.size) {
            "Downloaded image paths must be unique"
        }
        require(hasText || images.isNotEmpty()) {
            "A completed post must contain readable text or at least one image"
        }
    }
}

class DownloadedPost internal constructor(
    val manifest: PostDownloadManifest,
    val directory: File,
) {
    val key: PostDownloadKey get() = manifest.key
    val snapshot: PostDownloadSnapshot get() = manifest.snapshot
    val sizeBytes: Long = directory.walkTopDown()
        .filter(File::isFile)
        .sumOf(File::length)
    val localImageFiles: Map<String, File> = manifest.images.associate { image ->
        image.remoteUrl to File(directory, image.relativePath)
    }
    val localImageUris: Map<String, String> = localImageFiles.mapValues { (_, file) ->
        file.toURI().toString()
    }

    fun localImageFile(remoteUrl: String): File? = localImageFiles[remoteUrl]

    fun localImageUri(remoteUrl: String): String? = localImageUris[remoteUrl]
}

enum class PostDownloadPhase {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

data class PostDownloadProgress(
    val completedImages: Int,
    val totalImages: Int,
    val downloadedBytes: Long,
) {
    init {
        require(totalImages >= 0) { "totalImages must not be negative" }
        require(completedImages in 0..totalImages) { "completedImages must be within totalImages" }
        require(downloadedBytes >= 0) { "downloadedBytes must not be negative" }
    }
}

data class PostDownloadStatus(
    val key: PostDownloadKey,
    val phase: PostDownloadPhase,
    val snapshot: PostDownloadSnapshot,
    val hasText: Boolean,
    val hasImages: Boolean,
    val progress: PostDownloadProgress,
    val error: String? = null,
    val completed: DownloadedPost? = null,
    val request: PostDownloadRequest? = null,
)

internal const val CURRENT_POST_DOWNLOAD_MANIFEST_VERSION = 1
private val SHA_256_PATTERN = Regex("[0-9a-f]{64}")
private val HTML_TAG_PATTERN = Regex("<[^>]+>")
private val HTML_ENTITY_PATTERN = Regex("&(?:nbsp|#160);", RegexOption.IGNORE_CASE)

internal fun postHtmlHasReadableText(html: String): Boolean =
    HTML_ENTITY_PATTERN.replace(HTML_TAG_PATTERN.replace(html, " "), " ")
        .any { !it.isWhitespace() }

internal fun requireHttpUrl(value: String) {
    val uri = runCatching { URI(value) }.getOrNull()
    require(
        uri != null &&
            (uri.scheme.equals("http", ignoreCase = true) ||
                uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank(),
    ) { "Expected an absolute HTTP(S) URL" }
}
