package com.yamibo.pocket300.data.download

import com.yamibo.pocket300.api.YamiboRequestGate
import com.yamibo.pocket300.api.YamiboWafException
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PostImageDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sendsRefererAndUserAgentWhileStreamingExactBytes() = runBlocking {
        var capturedRequest: Request? = null
        val callFactory = OkHttpClient.Builder()
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(TEST_PNG_BYTES.toResponseBody())
                    .build()
            }
            .build()
        val downloader = OkHttpPostImageDownloader(
            cookieJar = CookieJar.NO_COOKIES,
            userAgent = "Pocket300-Test-Agent",
            callFactory = callFactory,
        )
        val destination = temporaryFolder.newFile("image.img")

        val result = downloader.download(
            PostImageDownloadRequest(
                remoteUrl = "https://bbs.yamibo.com/image.png",
                referer = "https://bbs.yamibo.com/thread-1000-1-1.html",
            ),
            destination,
        )

        assertEquals(
            "https://bbs.yamibo.com/thread-1000-1-1.html",
            capturedRequest?.header("Referer"),
        )
        assertEquals("Pocket300-Test-Agent", capturedRequest?.header("User-Agent"))
        assertEquals(TEST_PNG_BYTES.size.toLong(), result.byteCount)
        assertArrayEquals(TEST_PNG_BYTES, destination.readBytes())
    }

    @Test
    fun normalImageDownloadBypassesRequestGate() = runBlocking {
        var gateCalls = 0
        val callFactory = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(TEST_PNG_BYTES.toResponseBody("image/png".toMediaType()))
                    .build()
            }
            .build()
        val downloader = OkHttpPostImageDownloader(
            cookieJar = CookieJar.NO_COOKIES,
            requestGate = YamiboRequestGate {
                gateCalls++
                throw YamiboWafException("gate should not run")
            },
            callFactory = callFactory,
        )
        val destination = temporaryFolder.newFile("direct.png")

        val result = downloader.download(
            PostImageDownloadRequest(
                remoteUrl = "https://bbs.yamibo.com/image.png",
                referer = "https://bbs.yamibo.com/thread-1000-1-1.html",
            ),
            destination,
        )

        assertEquals(0, gateCalls)
        assertEquals(TEST_PNG_BYTES.size.toLong(), result.byteCount)
    }

    @Test
    fun challengedImageDownloadRunsGateOnceAndRetries() = runBlocking {
        var attempts = 0
        var gateCalls = 0
        val callFactory = OkHttpClient.Builder()
            .addInterceptor { chain ->
                attempts++
                if (attempts == 1) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(403)
                        .message("Forbidden")
                        .body("<html><body>browser challenge</body></html>".toResponseBody("text/html".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(TEST_PNG_BYTES.toResponseBody("image/png".toMediaType()))
                        .build()
                }
            }
            .build()
        val downloader = OkHttpPostImageDownloader(
            cookieJar = CookieJar.NO_COOKIES,
            requestGate = YamiboRequestGate { gateCalls++ },
            callFactory = callFactory,
        )
        val destination = temporaryFolder.newFile("challenged.png")

        val result = downloader.download(
            PostImageDownloadRequest(
                remoteUrl = "https://bbs.yamibo.com/image.png",
                referer = "https://bbs.yamibo.com/thread-1000-1-1.html",
            ),
            destination,
        )

        assertEquals(1, gateCalls)
        assertEquals(2, attempts)
        assertArrayEquals(TEST_PNG_BYTES, destination.readBytes())
        assertEquals(TEST_PNG_BYTES.size.toLong(), result.byteCount)
    }
}
