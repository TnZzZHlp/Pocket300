package com.yamibo.pocket300.ui

import com.yamibo.pocket300.api.YAMIBO_ORIGIN
import java.net.URI

internal sealed interface PostLinkTarget {
    data class Thread(
        val id: Int,
        val postId: Int? = null,
        val page: Int? = null,
    ) : PostLinkTarget
    data class Forum(val id: Int) : PostLinkTarget
    data class External(val url: String) : PostLinkTarget
}

internal fun resolvePostLink(rawUrl: String): PostLinkTarget {
    val url = normalizeLinkUrl(rawUrl)
    val uri = runCatching { URI(url) }.getOrNull()
    val isYamibo = uri?.host?.lowercase()?.let { it == "yamibo.com" || it.endsWith(".yamibo.com") } == true
    if (isYamibo) {
        val query = uri.rawQuery.orEmpty().replace("&amp;", "&")
        val postId = queryParameter(query, "pid")
            ?: Regex("^pid(\\d+)$", RegexOption.IGNORE_CASE)
                .matchEntire(uri.rawFragment.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        val threadId = queryParameter(query, "tid")
            // Discuz permanent links to a floor use ptid instead of tid.
            ?: queryParameter(query, "ptid").takeIf { postId != null }
            ?: Regex("(?:^|/)thread-(\\d+)(?:-|\\.|/|$)", RegexOption.IGNORE_CASE)
                .find(uri.path.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        if (threadId != null) {
            return PostLinkTarget.Thread(
                id = threadId,
                postId = postId,
                page = queryParameter(query, "page"),
            )
        }

        val forumId = queryParameter(query, "fid")
            ?: Regex("(?:^|/)forum-(\\d+)(?:-|\\.|/|$)", RegexOption.IGNORE_CASE)
                .find(uri.path.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        if (forumId != null) return PostLinkTarget.Forum(forumId)
    }
    return PostLinkTarget.External(url)
}

private fun normalizeLinkUrl(rawUrl: String): String {
    val value = rawUrl.trim().replace("&amp;", "&")
    return when {
        value.startsWith("//") -> "https:$value"
        runCatching { URI(value).scheme != null }.getOrDefault(false) -> value
        value.startsWith("/") -> "$YAMIBO_ORIGIN$value"
        else -> "$YAMIBO_ORIGIN/${value.trimStart('/')}"
    }
}

private fun queryParameter(query: String, name: String): Int? = query
    .split('&')
    .firstNotNullOfOrNull { item ->
        val (key, value) = item.split('=', limit = 2).let { it.first() to it.getOrElse(1) { "" } }
        value.toIntOrNull()?.takeIf { key.equals(name, ignoreCase = true) }
    }
