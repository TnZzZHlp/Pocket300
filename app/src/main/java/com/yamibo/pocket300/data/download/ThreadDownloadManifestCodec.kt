package com.yamibo.pocket300.data.download

import com.yamibo.pocket300.api.YamiboPollOption
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboPostAttachment
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.api.YamiboPostComment
import com.yamibo.pocket300.api.YamiboThreadDetails
import com.yamibo.pocket300.api.YamiboThreadPoll
import com.yamibo.pocket300.api.YamiboThreadSpecialType
import org.json.JSONArray
import org.json.JSONObject

enum class ThreadDownloadRequestState {
    PENDING,
    FAILED,
}

data class StoredThreadDownloadRequest(
    val request: ThreadDownloadRequest,
    val state: ThreadDownloadRequestState,
)

class ThreadDownloadManifestCodec {
    fun encodeRequest(
        request: ThreadDownloadRequest,
        state: ThreadDownloadRequestState = ThreadDownloadRequestState.PENDING,
    ): String = JSONObject()
        .put("version", CURRENT_THREAD_DOWNLOAD_MANIFEST_VERSION)
        .put("queueState", state.name)
        .put("requestedAt", request.requestedAt)
        .put("thread", encodeThread(request.thread))
        .toString()

    fun decodeRequest(json: String): ThreadDownloadRequest =
        decodeStoredRequest(json).request

    fun decodeStoredRequest(json: String): StoredThreadDownloadRequest {
        val root = JSONObject(json)
        requireVersion(root)
        val state = root.optString(
            "queueState",
            ThreadDownloadRequestState.PENDING.name,
        ).let(ThreadDownloadRequestState::valueOf)
        return StoredThreadDownloadRequest(
            request = ThreadDownloadRequest(
                thread = decodeThread(root.getJSONObject("thread")),
                requestedAt = root.getLong("requestedAt"),
            ),
            state = state,
        )
    }

    fun encodeManifest(manifest: ThreadDownloadManifest): String = JSONObject()
        .put("version", manifest.version)
        .put("requestedAt", manifest.requestedAt)
        .put("completedAt", manifest.completedAt)
        .put(
            "snapshot",
            JSONObject()
                .put("thread", encodeThread(manifest.snapshot.thread))
                .putNullable("poll", manifest.snapshot.poll?.let(::encodePoll))
                .put(
                    "posts",
                    JSONArray().apply {
                        manifest.snapshot.posts.forEach { put(encodePost(it)) }
                    },
                )
                .put("capturedPageCount", manifest.snapshot.capturedPageCount)
                .put("sourcePageSize", manifest.snapshot.sourcePageSize)
                .put("sourceTotalPosts", manifest.snapshot.sourceTotalPosts),
        )
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

    fun decodeManifest(json: String): ThreadDownloadManifest {
        val root = JSONObject(json)
        requireVersion(root)
        val snapshot = root.getJSONObject("snapshot")
        return ThreadDownloadManifest(
            snapshot = ThreadDownloadSnapshot(
                thread = decodeThread(snapshot.getJSONObject("thread")),
                poll = snapshot.optionalObject("poll")?.let(::decodePoll),
                posts = snapshot.getJSONArray("posts").objects().map(::decodePost),
                capturedPageCount = snapshot.getInt("capturedPageCount"),
                sourcePageSize = snapshot.getInt("sourcePageSize"),
                sourceTotalPosts = snapshot.getInt("sourceTotalPosts"),
            ),
            images = root.getJSONArray("images").objects().map { image ->
                ThreadDownloadImage(
                    remoteUrl = image.getString("remoteUrl"),
                    relativePath = image.getString("relativePath"),
                    byteCount = image.getLong("byteCount"),
                    sha256 = image.getString("sha256"),
                    contentType = image.optionalString("contentType"),
                )
            },
            requestedAt = root.getLong("requestedAt"),
            completedAt = root.getLong("completedAt"),
            version = root.getInt("version"),
        )
    }

    private fun requireVersion(root: JSONObject) {
        require(root.getInt("version") == CURRENT_THREAD_DOWNLOAD_MANIFEST_VERSION) {
            "Unsupported thread download manifest version: ${root.getInt("version")}"
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

private fun encodePoll(poll: YamiboThreadPoll): JSONObject = JSONObject()
    .put("canVote", poll.canVote)
    .putNullable("expiresAt", poll.expiresAt)
    .put("maxChoices", poll.maxChoices)
    .put("multiple", poll.multiple)
    .put(
        "options",
        JSONArray().apply {
            poll.options.forEach { option ->
                put(
                    JSONObject()
                        .putNullable("color", option.color)
                        .put("id", option.id)
                        .put("percentage", option.percentage)
                        .put("text", option.text)
                        .put("voteCount", option.voteCount),
                )
            }
        },
    )
    .put("resultsHiddenUntilVote", poll.resultsHiddenUntilVote)
    .put("voterCount", poll.voterCount)

private fun decodePoll(value: JSONObject): YamiboThreadPoll = YamiboThreadPoll(
    canVote = value.getBoolean("canVote"),
    expiresAt = value.optionalLong("expiresAt"),
    maxChoices = value.getInt("maxChoices"),
    multiple = value.getBoolean("multiple"),
    options = value.getJSONArray("options").objects().map { option ->
        YamiboPollOption(
            color = option.optionalString("color"),
            id = option.getInt("id"),
            percentage = option.getDouble("percentage"),
            text = option.getString("text"),
            voteCount = option.getInt("voteCount"),
        )
    },
    resultsHiddenUntilVote = value.getBoolean("resultsHiddenUntilVote"),
    voterCount = value.getInt("voterCount"),
)

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun JSONObject.optionalObject(key: String): JSONObject? =
    if (!has(key) || isNull(key)) null else getJSONObject(key)

private fun JSONObject.optionalString(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)

private fun JSONObject.optionalInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)

private fun JSONObject.optionalLong(key: String): Long? =
    if (!has(key) || isNull(key)) null else getLong(key)

private fun JSONArray.objects(): List<JSONObject> = buildList(length()) {
    repeat(length()) { index -> add(getJSONObject(index)) }
}
