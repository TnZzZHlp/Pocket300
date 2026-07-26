package com.yamibo.pocket300.data.download

import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboPostAttachment
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.api.YamiboPostComment
import com.yamibo.pocket300.api.YamiboThreadDetails
import com.yamibo.pocket300.api.YamiboThreadSpecialType
import org.json.JSONArray
import org.json.JSONObject

enum class PostDownloadRequestState {
    PENDING,
    FAILED,
}

data class StoredPostDownloadRequest(
    val request: PostDownloadRequest,
    val state: PostDownloadRequestState,
)

class PostDownloadManifestCodec {
    fun encodeRequest(
        request: PostDownloadRequest,
        state: PostDownloadRequestState = PostDownloadRequestState.PENDING,
    ): String = JSONObject()
        .put("version", CURRENT_POST_DOWNLOAD_MANIFEST_VERSION)
        .put("queueState", state.name)
        .put("requestedAt", request.requestedAt)
        .put("referer", request.referer)
        .put("hasText", request.hasText)
        .put("thread", encodeThread(request.snapshot.thread))
        .put("post", encodePost(request.snapshot.post))
        .put("remoteImageUrls", JSONArray(request.remoteImageUrls))
        .toString()

    fun decodeRequest(json: String): PostDownloadRequest =
        decodeStoredRequest(json).request

    fun decodeStoredRequest(json: String): StoredPostDownloadRequest {
        val root = JSONObject(json)
        requireVersion(root)
        val state = root.optString(
            "queueState",
            PostDownloadRequestState.PENDING.name,
        ).let(PostDownloadRequestState::valueOf)
        return StoredPostDownloadRequest(
            request = PostDownloadRequest(
                snapshot = PostDownloadSnapshot(
                    thread = decodeThread(root.getJSONObject("thread")),
                    post = decodePost(root.getJSONObject("post")),
                ),
                remoteImageUrls = root.getJSONArray("remoteImageUrls").strings(),
                hasText = root.getBoolean("hasText"),
                referer = root.getString("referer"),
                requestedAt = root.getLong("requestedAt"),
            ),
            state = state,
        )
    }

    fun encodeManifest(manifest: PostDownloadManifest): String = JSONObject()
        .put("version", manifest.version)
        .put("requestedAt", manifest.requestedAt)
        .put("completedAt", manifest.completedAt)
        .put("hasText", manifest.hasText)
        .put("thread", encodeThread(manifest.snapshot.thread))
        .put("post", encodePost(manifest.snapshot.post))
        .put(
            "images",
            JSONArray().apply {
                manifest.images.forEach { image ->
                    put(
                        JSONObject()
                            .put("remoteUrl", image.remoteUrl)
                            .put("relativePath", image.relativePath)
                            .put("byteCount", image.byteCount)
                            .put("sha256", image.sha256)
                            .putNullable("contentType", image.contentType),
                    )
                }
            },
        )
        .toString()

    fun decodeManifest(json: String): PostDownloadManifest {
        val root = JSONObject(json)
        requireVersion(root)
        val images = root.getJSONArray("images").objects().map { image ->
            PostDownloadImage(
                remoteUrl = image.getString("remoteUrl"),
                relativePath = image.getString("relativePath"),
                byteCount = image.getLong("byteCount"),
                sha256 = image.getString("sha256"),
                contentType = image.optionalString("contentType"),
            )
        }
        return PostDownloadManifest(
            snapshot = PostDownloadSnapshot(
                thread = decodeThread(root.getJSONObject("thread")),
                post = decodePost(root.getJSONObject("post")),
            ),
            hasText = root.getBoolean("hasText"),
            images = images,
            requestedAt = root.getLong("requestedAt"),
            completedAt = root.getLong("completedAt"),
            version = root.getInt("version"),
        )
    }

    private fun requireVersion(root: JSONObject) {
        require(root.getInt("version") == CURRENT_POST_DOWNLOAD_MANIFEST_VERSION) {
            "Unsupported post download manifest version: ${root.getInt("version")}"
        }
    }
}

private fun encodeThread(thread: YamiboThreadDetails): JSONObject = JSONObject()
    .put("author", encodeAuthor(thread.author))
    .put("createdAt", thread.createdAt)
    .put("digestLevel", thread.digestLevel)
    .put("forumId", thread.forumId)
    .put("heat", thread.heat)
    .put("hasAttachment", thread.hasAttachment)
    .put("id", thread.id)
    .put("isClosed", thread.isClosed)
    .put("lastPoster", thread.lastPoster)
    .put("lastPostAtText", thread.lastPostAtText)
    .put("maxPosition", thread.maxPosition)
    .put("price", thread.price)
    .put("readPermission", thread.readPermission)
    .put("recommendationCount", thread.recommendationCount)
    .put("replyCount", thread.replyCount)
    .put("specialType", thread.specialType.name)
    .put("specialTypeId", thread.specialTypeId)
    .put("subject", thread.subject)
    .putNullable("typeId", thread.typeId)
    .put("viewCount", thread.viewCount)
    .put("webUrl", thread.webUrl)

private fun decodeThread(value: JSONObject): YamiboThreadDetails = YamiboThreadDetails(
    author = decodeAuthor(value.getJSONObject("author")),
    createdAt = value.getLong("createdAt"),
    digestLevel = value.getInt("digestLevel"),
    forumId = value.getInt("forumId"),
    heat = value.getInt("heat"),
    hasAttachment = value.getBoolean("hasAttachment"),
    id = value.getInt("id"),
    isClosed = value.getBoolean("isClosed"),
    lastPoster = value.getString("lastPoster"),
    lastPostAtText = value.getString("lastPostAtText"),
    maxPosition = value.getInt("maxPosition"),
    price = value.getInt("price"),
    readPermission = value.getInt("readPermission"),
    recommendationCount = value.getInt("recommendationCount"),
    replyCount = value.getInt("replyCount"),
    specialType = runCatching {
        YamiboThreadSpecialType.valueOf(value.getString("specialType"))
    }.getOrDefault(YamiboThreadSpecialType.UNKNOWN),
    specialTypeId = value.getInt("specialTypeId"),
    subject = value.getString("subject"),
    typeId = value.optionalInt("typeId"),
    viewCount = value.getInt("viewCount"),
    webUrl = value.getString("webUrl"),
)

private fun encodePost(post: YamiboPost): JSONObject = JSONObject()
    .put(
        "attachments",
        JSONArray().apply {
            post.attachments.forEach { attachment ->
                put(
                    JSONObject()
                        .put("id", attachment.id)
                        .put("filename", attachment.filename)
                        .put("isImage", attachment.isImage)
                        .put("url", attachment.url),
                )
            }
        },
    )
    .put("author", encodeAuthor(post.author))
    .put(
        "comments",
        JSONArray().apply {
            post.comments.forEach { comment ->
                put(
                    JSONObject()
                        .put("author", encodeAuthor(comment.author))
                        .put("createdAtText", comment.createdAtText)
                        .put("id", comment.id)
                        .put("message", comment.message)
                        .put("postId", comment.postId)
                        .put("threadId", comment.threadId),
                )
            }
        },
    )
    .put("createdAt", post.createdAt)
    .put("createdAtText", post.createdAtText)
    .put("html", post.html)
    .put("hasAttachment", post.hasAttachment)
    .put("id", post.id)
    .put("isOriginalPost", post.isOriginalPost)
    .put("number", post.number)
    .put("position", post.position)
    .put("ratingCount", post.ratingCount)
    .put("replyCredit", post.replyCredit)
    .put("status", post.status)
    .put("threadId", post.threadId)

private fun decodePost(value: JSONObject): YamiboPost = YamiboPost(
    attachments = value.getJSONArray("attachments").objects().map { attachment ->
        YamiboPostAttachment(
            id = attachment.getInt("id"),
            filename = attachment.getString("filename"),
            isImage = attachment.getBoolean("isImage"),
            url = attachment.getString("url"),
        )
    },
    author = decodeAuthor(value.getJSONObject("author")),
    comments = value.getJSONArray("comments").objects().map { comment ->
        YamiboPostComment(
            author = decodeAuthor(comment.getJSONObject("author")),
            createdAtText = comment.getString("createdAtText"),
            id = comment.getInt("id"),
            message = comment.getString("message"),
            postId = comment.getInt("postId"),
            threadId = comment.getInt("threadId"),
        )
    },
    createdAt = value.getLong("createdAt"),
    createdAtText = value.getString("createdAtText"),
    html = value.getString("html"),
    hasAttachment = value.getBoolean("hasAttachment"),
    id = value.getInt("id"),
    isOriginalPost = value.getBoolean("isOriginalPost"),
    number = value.getInt("number"),
    position = value.getInt("position"),
    ratingCount = value.getInt("ratingCount"),
    replyCredit = value.getInt("replyCredit"),
    status = value.getInt("status"),
    threadId = value.getInt("threadId"),
)

private fun encodeAuthor(author: YamiboPostAuthor): JSONObject = JSONObject()
    .putNullable("avatarUrl", author.avatarUrl)
    .putNullable("groupIconId", author.groupIconId)
    .putNullable("groupId", author.groupId)
    .putNullable("id", author.id)
    .put("isAnonymous", author.isAnonymous)
    .put("name", author.name)

private fun decodeAuthor(value: JSONObject): YamiboPostAuthor = YamiboPostAuthor(
    avatarUrl = value.optionalString("avatarUrl"),
    groupIconId = value.optionalString("groupIconId"),
    groupId = value.optionalInt("groupId"),
    id = value.optionalInt("id"),
    isAnonymous = value.getBoolean("isAnonymous"),
    name = value.getString("name"),
)

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun JSONObject.optionalString(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)

private fun JSONObject.optionalInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)

private fun JSONArray.strings(): List<String> = buildList(length()) {
    repeat(length()) { index -> add(getString(index)) }
}

private fun JSONArray.objects(): List<JSONObject> = buildList(length()) {
    repeat(length()) { index -> add(getJSONObject(index)) }
}
