package com.yamibo.pocket300.ui

import android.text.Html
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.util.LruCache
import android.webkit.CookieManager
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.POCKET300_USER_AGENT
import com.yamibo.pocket300.api.YAMIBO_ORIGIN
import com.yamibo.pocket300.logging.AppLogger

private sealed interface PostHtmlPart {
    data class Text(val value: String, val url: String? = null) : PostHtmlPart
    data class Image(val url: String) : PostHtmlPart
}

private sealed interface PostRenderPart {
    data class Inline(val parts: List<PostHtmlPart>) : PostRenderPart
    data class Image(val url: String) : PostRenderPart
}

private val postHtmlCache = LruCache<String, List<PostHtmlPart>>(64)

@Composable
internal fun PostHtml(
    html: String,
    threadId: Int,
    attachmentUrls: List<String>,
    onLink: (String) -> Unit,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    localImageUrls: Map<String, String> = emptyMap(),
    allowRemoteImages: Boolean = true,
) {
    val parts = remember(html, attachmentUrls) { postHtmlParts(html, attachmentUrls) }
    val renderParts = remember(parts) { groupPostHtmlParts(parts) }
    val imageCount = remember(renderParts) {
        renderParts.count { it is PostRenderPart.Image }
    }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var imageNumber = 0
            renderParts.forEach { part ->
                when (part) {
                    is PostRenderPart.Inline -> PostInlineHtml(
                        parts = part.parts,
                        threadId = threadId,
                        onLink = onLink,
                        style = textStyle,
                        localImageUrls = localImageUrls,
                        allowRemoteImages = allowRemoteImages,
                    )
                    is PostRenderPart.Image -> {
                        imageNumber++
                        val url = resolvePostImageSource(
                            part.url,
                            localImageUrls,
                            allowRemoteImages,
                        )
                        var failed by remember(url) { mutableStateOf(false) }
                        if (url == null) {
                            Text(
                                stringResource(R.string.reader_offline_image_unavailable),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val request = rememberPostImageRequest(url, threadId)
                            if (failed) {
                                Text(
                                    stringResource(R.string.reader_image_load_error),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                AsyncImage(
                                    model = request,
                                    contentDescription = stringResource(
                                        R.string.reader_image_description,
                                        imageNumber,
                                        imageCount,
                                    ),
                                    contentScale = ContentScale.FillWidth,
                                    onError = { failed = true },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun postImageUrls(html: String, attachmentUrls: List<String>): List<String> =
    postHtmlParts(html, attachmentUrls)
        .filterIsInstance<PostHtmlPart.Image>()
        .filterNot { isSmileyImage(it.url) }
        .map { normalizePostImageUrl(it.url) }
        .distinct()

private fun postHtmlParts(html: String, attachmentUrls: List<String>): List<PostHtmlPart> {
    val htmlParts = postHtmlCache.get(html) ?: parsePostHtml(html).also { postHtmlCache.put(html, it) }
    val embeddedUrls = htmlParts.filterIsInstance<PostHtmlPart.Image>()
        .map { normalizePostImageUrl(it.url) }
        .toSet()
    return htmlParts + attachmentUrls
        .filterNot { normalizePostImageUrl(it) in embeddedUrls }
        .map(PostHtmlPart::Image)
}

private fun groupPostHtmlParts(parts: List<PostHtmlPart>): List<PostRenderPart> {
    val result = mutableListOf<PostRenderPart>()
    val inline = mutableListOf<PostHtmlPart>()
    fun flushInline() {
        if (inline.isNotEmpty()) result += PostRenderPart.Inline(inline.toList())
        inline.clear()
    }
    parts.forEach { part ->
        if (part is PostHtmlPart.Image && !isSmileyImage(part.url)) {
            flushInline()
            result += PostRenderPart.Image(part.url)
        } else {
            inline += part
        }
    }
    flushInline()
    return result
}

@Composable
private fun PostInlineHtml(
    parts: List<PostHtmlPart>,
    threadId: Int,
    onLink: (String) -> Unit,
    style: TextStyle,
    localImageUrls: Map<String, String>,
    allowRemoteImages: Boolean,
) {
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    val text = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            when (part) {
                is PostHtmlPart.Text -> if (part.url == null) {
                    append(part.value)
                } else {
                    pushLink(
                        LinkAnnotation.Url(
                            part.url,
                            styles = TextLinkStyles(style = linkStyle),
                            linkInteractionListener = { onLink(part.url) },
                        ),
                    )
                    append(part.value)
                    pop()
                }
                is PostHtmlPart.Image -> appendInlineContent("smiley-$index", "表情")
            }
        }
    }
    val inlineContent = buildMap {
        parts.forEachIndexed { index, part ->
            if (part is PostHtmlPart.Image) {
                val resolved = resolvePostImageSource(
                    part.url,
                    localImageUrls,
                    allowRemoteImages,
                ) ?: return@forEachIndexed
                put(
                    "smiley-$index",
                    InlineTextContent(
                        Placeholder(
                            width = style.lineHeight,
                            height = style.lineHeight,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        AsyncImage(
                            model = rememberPostImageRequest(
                                resolved,
                                threadId,
                            ),

                            contentDescription = "表情",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
        }
    }
    Text(text = text, inlineContent = inlineContent, style = style)
}

@Composable
internal fun rememberPostImageRequest(url: String, threadId: Int): ImageRequest {
    val context = LocalContext.current
    val userAgent = POCKET300_USER_AGENT
    return remember(url, threadId, userAgent) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .apply {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val cookie = CookieManager.getInstance().getCookie(url)
                    if (!cookie.isNullOrBlank()) addHeader("Cookie", cookie)
                    addHeader("Referer", "$YAMIBO_ORIGIN/forum.php?mod=viewthread&tid=$threadId")
                    addHeader("User-Agent", userAgent)
                }
            }
            .build()
            .also {
                AppLogger.verbose("PostImage") {
                    "Prepared image request with User-Agent: $userAgent"
                }
            }
    }
}

private fun isSmileyImage(source: String): Boolean = normalizePostImageUrl(source)
    .substringAfter(YAMIBO_ORIGIN)
    .trimStart('/')
    .startsWith("static/image/smiley/", ignoreCase = true)

@Suppress("DEPRECATION")
private fun parsePostHtml(html: String): List<PostHtmlPart> {
    val spanned = Html.fromHtml(resolveDiscuzImageSources(html), Html.FROM_HTML_MODE_LEGACY) as Spanned
    val images = spanned.getSpans(0, spanned.length, ImageSpan::class.java)
        .sortedBy(spanned::getSpanStart)
    val parts = mutableListOf<PostHtmlPart>()
    var cursor = 0
    images.forEach { image ->
        addPostText(parts, spanned, cursor, spanned.getSpanStart(image))
        image.source?.takeIf(String::isNotBlank)?.let { parts += PostHtmlPart.Image(it) }
        cursor = spanned.getSpanEnd(image)
    }
    addPostText(parts, spanned, cursor, spanned.length)
    return parts
}

private val imageTagPattern = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val sourceAttributePattern = Regex(
    """\bsrc\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""",
    RegexOption.IGNORE_CASE,
)

private fun resolveDiscuzImageSources(html: String): String = imageTagPattern.replace(html) { match ->
    val tag = match.value
    val source = listOf("zoomfile", "file", "data-src", "data-original")
        .firstNotNullOfOrNull { readHtmlAttribute(tag, it) }
        ?.takeIf { it.isNotBlank() }
        ?: return@replace tag
    val resolvedSource = "src=\"${source.replace("\"", "&quot;")}\""
    if (sourceAttributePattern.containsMatchIn(tag)) {
        sourceAttributePattern.replaceFirst(tag, resolvedSource)
    } else {
        tag.replaceFirst("<img", "<img $resolvedSource", ignoreCase = true)
    }
}

private fun readHtmlAttribute(tag: String, name: String): String? {
    val pattern = Regex(
        """(?:^|\s)${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
        RegexOption.IGNORE_CASE,
    )
    val match = pattern.find(tag) ?: return null
    return match.groupValues.drop(1).firstOrNull(String::isNotEmpty)
}

private fun addPostText(parts: MutableList<PostHtmlPart>, spanned: Spanned, start: Int, end: Int) {
    if (start >= end) return
    val boundaries = buildSet {
        add(start)
        add(end)
        spanned.getSpans(start, end, URLSpan::class.java).forEach { span ->
            add(spanned.getSpanStart(span).coerceIn(start, end))
            add(spanned.getSpanEnd(span).coerceIn(start, end))
        }
    }.sorted()
    boundaries.zipWithNext().forEach { (from, to) ->
        var text = spanned.subSequence(from, to).toString().replace('\uFFFC'.toString(), "")
        if (from == start) text = text.trimStart()
        if (to == end) text = text.trimEnd()
        if (text.isNotEmpty()) {
            val url = spanned.getSpans(from, to, URLSpan::class.java).firstOrNull()?.url
            parts += PostHtmlPart.Text(text, url)
        }
    }
}

internal fun normalizePostImageUrl(source: String): String {
    val value = source.trim().replace("&amp;", "&")
    return when {
        value.startsWith("file:", ignoreCase = true) ||
            value.startsWith("content:", ignoreCase = true) ||
            value.startsWith("android.resource:", ignoreCase = true) -> value
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "$YAMIBO_ORIGIN$value"
        value.startsWith("http://bbs.yamibo.com/") -> value.replaceFirst("http://", "https://")
        value.startsWith("http://") || value.startsWith("https://") -> value
        else -> "$YAMIBO_ORIGIN/${value.trimStart('/')}"
    }
}

internal fun resolvePostImageUrl(
    source: String,
    localImageUrls: Map<String, String>,
): String = requireNotNull(
    resolvePostImageSource(source, localImageUrls, allowRemoteImages = true),
)

internal fun resolvePostImageSource(
    source: String,
    localImageUrls: Map<String, String>,
    allowRemoteImages: Boolean,
): String? {
    val normalized = normalizePostImageUrl(source)
    return localImageUrls[normalized]
        ?: localImageUrls[source]
        ?: normalized.takeIf { allowRemoteImages || !it.isRemotePostImageUrl() }
}

private fun String.isRemotePostImageUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

@Suppress("DEPRECATION")
internal fun plainText(html: String): String =
    Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
