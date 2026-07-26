package com.yamibo.pocket300.data.download

import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboPostAttachment
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.api.YamiboPostComment
import com.yamibo.pocket300.api.YamiboThreadDetails
import com.yamibo.pocket300.api.YamiboThreadSpecialType
import java.io.File
import java.util.Base64

internal val TEST_PNG_BYTES: ByteArray = Base64.getDecoder().decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42Y" +
        "AAAAASUVORK5CYII=",
)

internal fun testDownloadRequest(
    threadId: Int = 1000,
    postId: Int = 2000,
    html: String = "<p>Hello offline reader</p>",
    imageUrls: List<String> = emptyList(),
    hasText: Boolean = postHtmlHasReadableText(html),
    requestedAt: Long = 10L,
): PostDownloadRequest {
    val author = YamiboPostAuthor(
        avatarUrl = "https://bbs.yamibo.com/avatar.png",
        groupIconId = "group",
        groupId = 2,
        id = 42,
        isAnonymous = false,
        name = "Alice",
    )
    val thread = YamiboThreadDetails(
        author = author,
        createdAt = 1L,
        digestLevel = 1,
        forumId = 300,
        heat = 4,
        hasAttachment = imageUrls.isNotEmpty(),
        id = threadId,
        isClosed = false,
        lastPoster = "Bob",
        lastPostAtText = "Today",
        maxPosition = 12,
        price = 0,
        readPermission = 0,
        recommendationCount = 3,
        replyCount = 11,
        specialType = YamiboThreadSpecialType.NORMAL,
        specialTypeId = 0,
        subject = "Offline subject",
        typeId = 7,
        viewCount = 99,
        webUrl = "https://bbs.yamibo.com/thread-$threadId-1-1.html",
    )
    val post = YamiboPost(
        attachments = imageUrls.mapIndexed { index, url ->
            YamiboPostAttachment(
                id = index + 1,
                filename = "image-$index.png",
                isImage = true,
                url = url,
            )
        },
        author = author,
        comments = listOf(
            YamiboPostComment(
                author = author.copy(id = 43, name = "Commenter"),
                createdAtText = "Later",
                id = 9,
                message = "Comment",
                postId = postId,
                threadId = threadId,
            ),
        ),
        createdAt = 2L,
        createdAtText = "Now",
        html = html,
        hasAttachment = imageUrls.isNotEmpty(),
        id = postId,
        isOriginalPost = true,
        number = 1,
        position = 1,
        ratingCount = 2,
        replyCredit = 0,
        status = 0,
        threadId = threadId,
    )
    return PostDownloadRequest(
        snapshot = PostDownloadSnapshot(thread, post),
        remoteImageUrls = imageUrls,
        hasText = hasText,
        referer = thread.webUrl,
        requestedAt = requestedAt,
    )
}

internal fun stagedImage(
    staging: PostDownloadStaging,
    index: Int,
    remoteUrl: String,
    bytes: ByteArray = TEST_PNG_BYTES,
): PostDownloadImage {
    val file = staging.imageFile(index)
    file.writeBytes(bytes)
    return PostDownloadImage(
        remoteUrl = remoteUrl,
        relativePath = "${PostDownloadStaging.IMAGE_DIRECTORY_NAME}/${file.name}",
        byteCount = file.length(),
        sha256 = fileSha256(file),
        contentType = "image/png",
    )
}

internal fun File.manifestFile(threadId: Int, postId: Int): File =
    resolve("$threadId/$postId/manifest.json")
