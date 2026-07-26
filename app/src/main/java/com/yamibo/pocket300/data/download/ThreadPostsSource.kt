package com.yamibo.pocket300.data.download

import com.yamibo.pocket300.api.GetThreadPostsInput
import com.yamibo.pocket300.api.YAMIBO_ORIGIN
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboPostsApi
import com.yamibo.pocket300.api.YamiboThreadPostsPage

fun interface ThreadPostsSource {
    suspend fun getPage(threadId: Int, page: Int): YamiboThreadPostsPage
}

class YamiboThreadPostsSource(
    private val postsApi: YamiboPostsApi,
) : ThreadPostsSource {
    override suspend fun getPage(threadId: Int, page: Int): YamiboThreadPostsPage =
        postsApi.getThreadPosts(
            GetThreadPostsInput(
                threadId = threadId,
                page = page,
                authorId = null,
            ),
        )
}

/**
 * Extracts every non-smiley image a downloaded thread needs, preserving first reading occurrence.
 * Discuz lazy-image attributes take precedence over placeholder `src` values.
 */
internal fun threadImageUrls(posts: List<YamiboPost>): List<String> = buildList {
    val seen = linkedSetOf<String>()
    posts.forEach { post ->
        downloadablePostImageUrls(
            html = post.html,
            attachmentUrls = post.attachments.filter { it.isImage }.map { it.url },
        ).forEach { url ->
            if (seen.add(url)) add(url)
        }
    }
}

/**
 * Canonical image-key extraction shared by downloading and UI rendering.
 */
internal fun downloadablePostImageUrls(
    html: String,
    attachmentUrls: List<String>,
): List<String> {
    val embedded = html.imageTagSources()
        .mapNotNull(::normalizableRemoteImageUrl)
        .distinct()
    val embeddedSet = embedded.toSet()
    return (embedded + attachmentUrls
        .mapNotNull(::normalizableRemoteImageUrl)
        .filterNot { it in embeddedSet })
        .filterNot(::isThreadSmileyUrl)
        .distinct()
}

private val IMAGE_TAG_PATTERN = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val IMAGE_SOURCE_ATTRIBUTES =
    listOf("zoomfile", "file", "data-src", "data-original", "src")

private fun String.imageTagSources(): List<String> = IMAGE_TAG_PATTERN.findAll(this)
    .mapNotNull { match ->
        IMAGE_SOURCE_ATTRIBUTES.firstNotNullOfOrNull { name ->
            readHtmlAttribute(match.value, name)?.takeIf(String::isNotBlank)
        }
    }
    .toList()

private fun readHtmlAttribute(tag: String, name: String): String? {
    val pattern = Regex(
        """(?:^|\s)${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
        RegexOption.IGNORE_CASE,
    )
    val match = pattern.find(tag) ?: return null
    return match.groupValues.drop(1).firstOrNull(String::isNotEmpty)
}

internal fun normalizeThreadImageUrl(source: String): String {
    val value = source.trim().replace("&amp;", "&")
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "$YAMIBO_ORIGIN$value"
        value.startsWith("http://bbs.yamibo.com/", ignoreCase = true) ->
            value.replaceFirst("http://", "https://", ignoreCase = true)
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) -> value
        else -> "$YAMIBO_ORIGIN/${value.trimStart('/')}"
    }
}

private fun normalizableRemoteImageUrl(source: String): String? = runCatching {
    normalizeThreadImageUrl(source).also(::requireHttpUrl)
}.getOrNull()

private fun isThreadSmileyUrl(url: String): Boolean =
    url.substringAfter(YAMIBO_ORIGIN)
        .trimStart('/')
        .startsWith("static/image/smiley/", ignoreCase = true)
