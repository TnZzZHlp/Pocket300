package com.yamibo.pocket300.data.download

import com.yamibo.pocket300.api.YAMIBO_ORIGIN
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboThreadDetails
import com.yamibo.pocket300.api.YamiboThreadPoll
import java.io.File
import java.net.URI

data class ThreadDownloadKey(
    val threadId: Int,
) {
    init {
        require(threadId > 0) { "threadId must be positive" }
    }
}

/**
 * A durable queue request. The thread metadata is intentionally captured before enqueueing so a
 * queued or failed download remains identifiable without another network request.
 */
data class ThreadDownloadRequest(
    val thread: YamiboThreadDetails,
    val requestedAt: Long = System.currentTimeMillis(),
) {
    val key: ThreadDownloadKey = ThreadDownloadKey(thread.id)
    val referer: String = thread.webUrl.takeIf(String::isNotBlank)
        ?: "$YAMIBO_ORIGIN/thread-${thread.id}-1-1.html"

    init {
        require(requestedAt >= 0) { "requestedAt must not be negative" }
        requireHttpUrl(referer)
    }

    companion object {
        fun create(
            thread: YamiboThreadDetails,
            requestedAt: Long = System.currentTimeMillis(),
        ): ThreadDownloadRequest = ThreadDownloadRequest(
            thread = thread,
            requestedAt = requestedAt,
        )
    }
}

/**
 * A complete, ordered snapshot of every page fetched for a thread.
 *
 * The source counts are capture metadata rather than strict equality constraints because Discuz
 * can hide or remove posts while a multi-page download is running.
 */
data class ThreadDownloadSnapshot(
    val thread: YamiboThreadDetails,
    val poll: YamiboThreadPoll?,
    val posts: List<YamiboPost>,
    val capturedPageCount: Int,
    val sourcePageSize: Int,
    val sourceTotalPosts: Int,
) {
    val key: ThreadDownloadKey = ThreadDownloadKey(thread.id)

    init {
        require(capturedPageCount > 0) { "capturedPageCount must be positive" }
        require(sourcePageSize > 0) { "sourcePageSize must be positive" }
        require(sourceTotalPosts >= 0) { "sourceTotalPosts must not be negative" }
        require(posts.isNotEmpty()) { "A downloaded thread must contain at least one post" }
        require(posts.all { it.threadId == thread.id }) {
            "Every post in a thread snapshot must belong to that thread"
        }
        require(posts.distinctBy(YamiboPost::id).size == posts.size) {
            "Post IDs in a thread snapshot must be unique"
        }
        require(posts == posts.sortedWith(THREAD_POST_READING_ORDER)) {
            "Posts in a thread snapshot must be in reading order"
        }
    }
}

data class ThreadDownloadImage(
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
        require(SHA_256_PATTERN.matches(sha256)) {
            "sha256 must be a lowercase SHA-256 digest"
        }
    }
}

data class ThreadDownloadManifest(
    val snapshot: ThreadDownloadSnapshot,
    val images: List<ThreadDownloadImage>,
    val requestedAt: Long,
    val completedAt: Long,
    val version: Int = CURRENT_THREAD_DOWNLOAD_MANIFEST_VERSION,
) {
    val key: ThreadDownloadKey = snapshot.key

    init {
        require(version == CURRENT_THREAD_DOWNLOAD_MANIFEST_VERSION) {
            "Unsupported thread download manifest version: $version"
        }
        require(requestedAt >= 0) { "requestedAt must not be negative" }
        require(completedAt >= requestedAt) { "completedAt must not precede requestedAt" }
        require(images.distinctBy(ThreadDownloadImage::remoteUrl).size == images.size) {
            "Downloaded image URLs must be unique"
        }
        require(images.distinctBy(ThreadDownloadImage::relativePath).size == images.size) {
            "Downloaded image paths must be unique"
        }
    }
}

class DownloadedThread internal constructor(
    val manifest: ThreadDownloadManifest,
    val directory: File,
) {
    val key: ThreadDownloadKey get() = manifest.key
    val snapshot: ThreadDownloadSnapshot get() = manifest.snapshot
    val sizeBytes: Long = directory.walkTopDown()
        .filter(File::isFile)
        .sumOf(File::length)
    val localImageFiles: Map<String, File> = manifest.images.associate { image ->
        image.remoteUrl to File(directory, image.relativePath)
    }
    val localImageUris: Map<String, String> = localImageFiles.mapValues { (_, file) ->
        file.toURI().toString()
    }

    fun findPost(postId: Int): YamiboPost? = snapshot.posts.firstOrNull { it.id == postId }

    fun localImageFile(remoteUrl: String): File? = localImageFiles[remoteUrl]

    fun localImageUri(remoteUrl: String): String? = localImageUris[remoteUrl]
}

enum class ThreadDownloadPhase {
    QUEUED,
    FETCHING_PAGES,
    DOWNLOADING_IMAGES,
    COMPLETED,
    FAILED,
}

data class ThreadDownloadProgress(
    val completedPages: Int,
    val totalPages: Int,
    val completedImages: Int,
    val totalImages: Int,
    val downloadedBytes: Long,
) {
    init {
        require(totalPages >= 0) { "totalPages must not be negative" }
        require(completedPages in 0..totalPages) {
            "completedPages must be within totalPages"
        }
        require(totalImages >= 0) { "totalImages must not be negative" }
        require(completedImages in 0..totalImages) {
            "completedImages must be within totalImages"
        }
        require(downloadedBytes >= 0) { "downloadedBytes must not be negative" }
    }
}

data class ThreadDownloadStatus(
    val key: ThreadDownloadKey,
    val phase: ThreadDownloadPhase,
    val thread: YamiboThreadDetails,
    val progress: ThreadDownloadProgress,
    val error: String? = null,
    val completed: DownloadedThread? = null,
    val request: ThreadDownloadRequest? = null,
)

internal const val CURRENT_THREAD_DOWNLOAD_MANIFEST_VERSION = 1
internal val THREAD_POST_READING_ORDER: Comparator<YamiboPost> =
    compareBy<YamiboPost>({ it.position }, { it.number }, { it.id })

private val SHA_256_PATTERN = Regex("[0-9a-f]{64}")

internal fun requireHttpUrl(value: String) {
    val uri = runCatching { URI(value) }.getOrNull()
    require(
        uri != null &&
            (uri.scheme.equals("http", ignoreCase = true) ||
                uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank(),
    ) { "Expected an absolute HTTP(S) URL" }
}
