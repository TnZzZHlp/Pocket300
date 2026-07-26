package com.yamibo.pocket300.data.download

import com.yamibo.pocket300.api.YamiboPollOption
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboPostAttachment
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.api.YamiboPostComment
import com.yamibo.pocket300.api.YamiboThreadDetails
import com.yamibo.pocket300.api.YamiboThreadPoll
import com.yamibo.pocket300.api.YamiboThreadPostsPage
import com.yamibo.pocket300.api.YamiboThreadPostsPagination
import com.yamibo.pocket300.api.YamiboThreadSpecialType
import java.util.Base64

internal val TEST_PNG_BYTES: ByteArray = Base64.getDecoder().decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42Y" +
        "AAAAASUVORK5CYII=",
)

internal fun testAuthor(
    id: Int = 42,
    name: String = "Alice",
): YamiboPostAuthor = YamiboPostAuthor(
    avatarUrl = "https://bbs.yamibo.com/avatar-$id.png",
    groupIconId = "group",
    groupId = 2,
    id = id,
    isAnonymous = false,
    name = name,
)

internal fun testThread(
    threadId: Int = 1000,
    replyCount: Int = 1,
    subject: String = "Offline subject",
): YamiboThreadDetails = YamiboThreadDetails(
    author = testAuthor(),
    createdAt = 1L,
    digestLevel = 1,
    forumId = 300,
    heat = 4,
    hasAttachment = false,
    id = threadId,
    isClosed = false,
    lastPoster = "Bob",
    lastPostAtText = "Today",
    maxPosition = replyCount + 1,
    price = 0,
    readPermission = 0,
    recommendationCount = 3,
    replyCount = replyCount,
    specialType = YamiboThreadSpecialType.NORMAL,
    specialTypeId = 0,
    subject = subject,
    typeId = 7,
    viewCount = 99,
    webUrl = "https://bbs.yamibo.com/thread-$threadId-1-1.html",
)

internal fun testPost(
    threadId: Int = 1000,
    postId: Int = 2000,
    position: Int = 1,
    html: String = "<p>Post $position</p>",
    attachmentUrls: List<String> = emptyList(),
    isOriginalPost: Boolean = position == 1,
): YamiboPost = YamiboPost(
    attachments = attachmentUrls.mapIndexed { index, url ->
        YamiboPostAttachment(
            id = postId * 10 + index,
            filename = "image-$postId-$index.png",
            isImage = true,
            url = url,
        )
    },
    author = testAuthor(id = 40 + position, name = "Author $position"),
    comments = listOf(
        YamiboPostComment(
            author = testAuthor(90 + position, "Commenter $position"),
            createdAtText = "Later",
            id = postId * 100,
            message = "Comment",
            postId = postId,
            threadId = threadId,
        ),
    ),
    createdAt = position.toLong(),
    createdAtText = "Now",
    html = html,
    hasAttachment = attachmentUrls.isNotEmpty(),
    id = postId,
    isOriginalPost = isOriginalPost,
    number = position,
    position = position,
    ratingCount = 2,
    replyCredit = 0,
    status = 0,
    threadId = threadId,
)

internal fun testPoll(): YamiboThreadPoll = YamiboThreadPoll(
    canVote = true,
    expiresAt = 500L,
    maxChoices = 1,
    multiple = false,
    options = listOf(
        YamiboPollOption(
            color = "#fff",
            id = 1,
            percentage = 50.0,
            text = "Option",
            voteCount = 2,
        ),
    ),
    resultsHiddenUntilVote = false,
    voterCount = 4,
)

internal fun testPage(
    thread: YamiboThreadDetails,
    page: Int,
    totalPages: Int,
    posts: List<YamiboPost>,
    pageSize: Int = 2,
    totalPosts: Int = thread.replyCount + 1,
    hasNextPage: Boolean = page < totalPages,
    poll: YamiboThreadPoll? = null,
): YamiboThreadPostsPage = YamiboThreadPostsPage(
    canComment = true,
    pagination = YamiboThreadPostsPagination(
        hasNextPage = hasNextPage,
        page = page,
        pageSize = pageSize,
        totalPages = totalPages,
        totalPosts = totalPosts,
    ),
    poll = poll,
    posts = posts,
    thread = thread,
)

internal fun testRequest(
    thread: YamiboThreadDetails = testThread(),
    requestedAt: Long = 10L,
): ThreadDownloadRequest = ThreadDownloadRequest.create(thread, requestedAt)

internal fun testSnapshot(
    thread: YamiboThreadDetails = testThread(),
    posts: List<YamiboPost> = listOf(
        testPost(thread.id, 2000, 1),
        testPost(thread.id, 2001, 2),
    ),
    poll: YamiboThreadPoll? = null,
    capturedPageCount: Int = 1,
    sourcePageSize: Int = 20,
    sourceTotalPosts: Int = posts.size,
): ThreadDownloadSnapshot = ThreadDownloadSnapshot(
    thread = thread,
    poll = poll,
    posts = posts.sortedWith(THREAD_POST_READING_ORDER),
    capturedPageCount = capturedPageCount,
    sourcePageSize = sourcePageSize,
    sourceTotalPosts = sourceTotalPosts,
)

internal fun stagedImage(
    staging: ThreadDownloadStaging,
    index: Int,
    remoteUrl: String,
    bytes: ByteArray = TEST_PNG_BYTES,
): ThreadDownloadImage {
    val file = staging.imageFile(index)
    file.writeBytes(bytes)
    return ThreadDownloadImage(
        remoteUrl = remoteUrl,
        relativePath = "${ThreadDownloadStaging.IMAGE_DIRECTORY_NAME}/${file.name}",
        byteCount = file.length(),
        sha256 = fileSha256(file),
        contentType = "image/png",
    )
}
