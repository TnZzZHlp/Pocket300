package com.yamibo.pocket300.data.download

import com.yamibo.pocket300.api.AndroidCookieJar
import com.yamibo.pocket300.api.POCKET300_USER_AGENT
import com.yamibo.pocket300.api.Pocket300UserAgentInterceptor
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class PostImageDownloadRequest(
    val remoteUrl: String,
    val referer: String,
)

data class PostImageDownloadResult(
    val byteCount: Long,
    val contentType: String?,
)

fun interface PostImageDownloader {
    suspend fun download(
        request: PostImageDownloadRequest,
        destination: File,
    ): PostImageDownloadResult
}

/**
 * Authenticated image transport. Android's persistent CookieManager-backed jar is shared with
 * the API transport, while Referer and the Pocket300 browser User-Agent are sent for Discuz
 * attachment authorization.
 */
class OkHttpPostImageDownloader(
    cookieJar: CookieJar = AndroidCookieJar(),
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val userAgent: String = POCKET300_USER_AGENT,
    private val callFactory: Call.Factory = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(Pocket300UserAgentInterceptor(userAgent))
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build(),
) : PostImageDownloader {
    override suspend fun download(
        request: PostImageDownloadRequest,
        destination: File,
    ): PostImageDownloadResult = suspendCancellableCoroutine { continuation ->
        val url = request.remoteUrl.toHttpUrlOrNull()
        if (url == null || url.scheme !in setOf("http", "https")) {
            continuation.resumeWith(
                Result.failure(IllegalArgumentException("Image URL must use HTTP(S)")),
            )
            return@suspendCancellableCoroutine
        }
        val referer = request.referer.toHttpUrlOrNull()
        if (referer == null || referer.scheme !in setOf("http", "https")) {
            continuation.resumeWith(
                Result.failure(IllegalArgumentException("Image Referer must use HTTP(S)")),
            )
            return@suspendCancellableCoroutine
        }
        destination.parentFile?.let { parent ->
            if (!parent.isDirectory && !parent.mkdirs()) {
                continuation.resumeWith(
                    Result.failure(IOException("Could not create image download directory")),
                )
                return@suspendCancellableCoroutine
            }
        }
        val call = callFactory.newCall(
            Request.Builder()
                .url(url)
                .header("Referer", request.referer)
                .header("User-Agent", userAgent)
                .build(),
        )
        continuation.invokeOnCancellation {
            call.cancel()
            destination.delete()
        }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    destination.delete()
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!response.isSuccessful) {
                                throw IOException(
                                    "Image request failed with HTTP ${response.code}",
                                )
                            }
                            val body = response.body
                            val declaredLength = body.contentLength()
                            if (declaredLength > MAX_IMAGE_BYTES) {
                                throw IOException("Image exceeds the 100 MiB size limit")
                            }
                            val byteCount = FileOutputStream(destination).use { output ->
                                var total = 0L
                                body.byteStream().use { input ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        if (total > MAX_IMAGE_BYTES) {
                                            throw IOException("Image exceeds the 100 MiB size limit")
                                        }
                                        output.write(buffer, 0, count)
                                    }
                                }
                                output.fd.sync()
                                total
                            }
                            if (byteCount <= 0) throw IOException("Image response body was empty")
                            PostImageDownloadResult(
                                byteCount = byteCount,
                                contentType = body.contentType()?.toString(),
                            )
                        }
                    }
                    if (result.isFailure) {
                        destination.delete()
                    }
                    continuation.resumeWith(result)
                }
            },
        )
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
        const val MAX_IMAGE_BYTES = 100L * 1024L * 1024L
    }
}
