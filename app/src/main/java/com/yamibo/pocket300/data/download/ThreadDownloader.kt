package com.yamibo.pocket300.data.download

import com.yamibo.pocket300.api.YamiboThreadDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Executes one complete-thread capture after [ThreadDownloadQueue] has chosen it.
 *
 * [ThreadDownloadManager] and its foreground service own scheduling, while
 * [ThreadDownloadRepository] keeps durable queue state and publishes completed snapshots. This
 * preserves the app's atomic thread-snapshot format.
 */
internal class ThreadDownloader(
    private val store: ThreadDownloadFileStore,
    private val threadPostsSource: ThreadPostsSource,
    private val imageDownloader: PostImageDownloader,
) {
    suspend fun download(
        request: ThreadDownloadRequest,
        checkNotCancelled: () -> Unit,
        onPageFetched: suspend (
            completedPages: Int,
            totalPages: Int,
            latestThread: YamiboThreadDetails,
        ) -> Unit,
        onImageProgress: suspend (
            snapshot: ThreadDownloadSnapshot,
            completedImages: Int,
            totalImages: Int,
            downloadedBytes: Long,
        ) -> Unit,
    ): ThreadDownloadCapture {
        val snapshot = fetchCompleteThreadSnapshot(
            source = threadPostsSource,
            request = request,
            onPageFetched = onPageFetched,
        )
        checkNotCancelled()

        val remoteImageUrls = threadImageUrls(snapshot.posts)
        require(remoteImageUrls.size <= MAX_THREAD_IMAGES) {
            "Thread contains too many downloadable images"
        }
        val staging = withContext(Dispatchers.IO) { store.createStaging(request.key) }
        try {
            val images = ArrayList<ThreadDownloadImage>(remoteImageUrls.size)
            var downloadedBytes = 0L
            onImageProgress(snapshot, 0, remoteImageUrls.size, 0)
            remoteImageUrls.forEachIndexed { index, remoteUrl ->
                checkNotCancelled()
                val imageFile = staging.imageFile(index)
                val referer = snapshot.thread.webUrl.takeIf(String::isNotBlank) ?: request.referer
                val result = imageDownloader.download(
                    PostImageDownloadRequest(remoteUrl, referer),
                    imageFile,
                )
                check(imageFile.isFile && imageFile.length() == result.byteCount) {
                    "Downloaded image size did not match the response"
                }
                downloadedBytes += result.byteCount
                images += ThreadDownloadImage(
                    remoteUrl = remoteUrl,
                    relativePath = "${ThreadDownloadStaging.IMAGE_DIRECTORY_NAME}/${imageFile.name}",
                    byteCount = result.byteCount,
                    sha256 = withContext(Dispatchers.IO) { fileSha256(imageFile) },
                    contentType = result.contentType,
                )
                onImageProgress(snapshot, index + 1, remoteImageUrls.size, downloadedBytes)
            }
            checkNotCancelled()
            return ThreadDownloadCapture(staging, snapshot, images)
        } catch (error: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { store.discard(staging) }
            throw error
        } catch (error: Exception) {
            withContext(Dispatchers.IO) { store.discard(staging) }
            throw error
        }
    }

    private companion object {
        const val MAX_THREAD_IMAGES = 10_000
    }
}

internal data class ThreadDownloadCapture(
    val staging: ThreadDownloadStaging,
    val snapshot: ThreadDownloadSnapshot,
    val images: List<ThreadDownloadImage>,
)
