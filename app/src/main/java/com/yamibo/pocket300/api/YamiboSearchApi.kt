package com.yamibo.pocket300.api

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.ceil

enum class YamiboSearchScope { SITE, FORUM }
enum class YamiboThreadSearchType { KEYWORD, TITLE, USER_ID }

data class SearchSiteThreadsInput(
    val keyword: String,
    val page: Int = 1,
    val searchId: Int? = null,
    val type: YamiboThreadSearchType = YamiboThreadSearchType.TITLE,
)
data class SearchForumThreadsInput(
    val keyword: String,
    val forumId: Int,
    val page: Int = 1,
    val searchId: Int? = null,
    val type: YamiboThreadSearchType = YamiboThreadSearchType.TITLE,
)

data class YamiboSearchAuthor(val avatarUrl: String?, val id: Int?, val name: String)
data class YamiboSearchForum(val id: Int, val name: String, val webUrl: String)
data class YamiboSearchThread(
    val author: YamiboSearchAuthor,
    val createdAtText: String,
    val excerpt: String?,
    val forum: YamiboSearchForum,
    val id: Int,
    val imageUrls: List<String>,
    val replyCount: Int,
    val subject: String,
    val viewCount: Int,
    val webUrl: String,
)

data class YamiboSearchPagination(
    val hasNextPage: Boolean,
    val page: Int,
    val pageSize: Int,
    val searchId: Int?,
    val totalPages: Int,
    val totalThreads: Int,
)

data class YamiboSearchPage(
    val forumId: Int?,
    val keyword: String,
    val pagination: YamiboSearchPagination,
    val scope: YamiboSearchScope,
    val threads: List<YamiboSearchThread>,
)

enum class YamiboSearchErrorCode {
    INVALID_KEYWORD, RATE_LIMITED, SEARCH_EXPIRED, INVALID_RESPONSE, NETWORK_ERROR, SERVER_ERROR,
}

class YamiboSearchException(
    val code: YamiboSearchErrorCode,
    message: String,
    val retryAfterMillis: Long? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

data class ParseSearchPageContext(
    val forumId: Int?,
    val page: Int,
    val responseUrl: String,
    val scope: YamiboSearchScope,
    val keywordOverride: String? = null,
)

class YamiboSearchApi(private val client: YamiboClient) {
    suspend fun searchSiteThreads(input: SearchSiteThreadsInput): YamiboSearchPage =
        search(input.keyword, input.page, input.searchId, input.type, YamiboSearchScope.SITE, null)

    suspend fun searchForumThreads(input: SearchForumThreadsInput): YamiboSearchPage =
        search(input.keyword, input.page, input.searchId, input.type, YamiboSearchScope.FORUM, input.forumId)

    private suspend fun search(
        rawKeyword: String,
        page: Int,
        searchId: Int?,
        type: YamiboThreadSearchType,
        scope: YamiboSearchScope,
        forumId: Int?,
    ): YamiboSearchPage {
        val keyword = rawKeyword.trim()
        if (keyword.isEmpty()) throw YamiboSearchException(
            YamiboSearchErrorCode.INVALID_KEYWORD,
            "请输入搜索关键字"
        )
        if (type == YamiboThreadSearchType.USER_ID && keyword.toIntOrNull()?.takeIf { it > 0 } == null) {
            throw YamiboSearchException(
                YamiboSearchErrorCode.INVALID_KEYWORD,
                "请输入有效的用户 ID"
            )
        }
        require(page > 0) { "page must be a positive integer" }
        searchId?.let { require(it > 0) { "searchId must be a positive integer" } }
        forumId?.let { require(it > 0) { "forumId must be a positive integer" } }

        var response = if (searchId != null) {
            requestCached(page, searchId)
        } else {
            request(
                buildThreadSearchParameters(keyword, type, scope, forumId),
            )
        }
        if (searchId == null && page > 1) {
            val created = parseSearchPage(
                response.html,
                ParseSearchPageContext(
                    forumId,
                    1,
                    response.url,
                    scope,
                    keyword.takeIf { type == YamiboThreadSearchType.USER_ID },
                )
            )
            val createdId = created.pagination.searchId ?: return created
            response = requestCached(page, createdId)
        }
        val result = parseSearchPage(
            response.html,
            ParseSearchPageContext(
                forumId,
                page,
                response.url,
                scope,
                keyword.takeIf { type == YamiboThreadSearchType.USER_ID },
            )
        )
        if (searchId != null && result.keyword != keyword) {
            throw YamiboSearchException(
                YamiboSearchErrorCode.SEARCH_EXPIRED,
                "搜索结果已过期，请重新搜索"
            )
        }
        return result
    }

    private suspend fun requestCached(page: Int, id: Int) = request(
        mapOf(
            "ascdesc" to "desc", "mobile" to "2", "mod" to "forum", "orderby" to "dateline",
            "page" to page.toString(), "searchid" to id.toString(), "searchsubmit" to "yes",
        ),
    )

    private suspend fun request(parameters: Map<String, String>): YamiboPageResponse = try {
        client.requestPage("/search.php", parameters)
    } catch (error: YamiboApiException) {
        throw YamiboSearchException(
            error.code.toSearchCode(),
            error.message ?: "百合会请求失败",
            cause = error
        )
    }
}

internal fun buildThreadSearchParameters(
    query: String,
    type: YamiboThreadSearchType,
    scope: YamiboSearchScope,
    forumId: Int?,
): Map<String, String> = buildMap {
    put("mobile", "2")
    put("mod", if (scope == YamiboSearchScope.FORUM) "curforum" else "forum")
    forumId?.let { put("srhfid", it.toString()) }
    when (type) {
        YamiboThreadSearchType.KEYWORD -> {
            put("srchtxt", query)
            put("srchtype", "fulltext")
        }
        YamiboThreadSearchType.TITLE -> {
            put("srchtxt", query)
            put("srchtype", "title")
        }
        YamiboThreadSearchType.USER_ID -> put("srchuid", query)
    }
    put("searchsubmit", "yes")
}

fun parseSearchPage(html: String, context: ParseSearchPageContext): YamiboSearchPage {
    throwForSearchTip(html)
    val keyword = context.keywordOverride ?: run {
        val input = requiredSearchMatch(
            html,
            Regex("""(<input(?=[^>]*\bname=["']srchtxt["'])[^>]*>)""", RegexOption.IGNORE_CASE),
            "搜索框",
        )
        searchText(
            requiredSearchMatch(
                input,
                Regex("""\bvalue=["']([^"']*)["']""", RegexOption.IGNORE_CASE),
                "搜索关键字"
            )
        )
    }
    val total = searchCount(
        requiredSearchMatch(
            html,
            Regex("""相关内容\s*([\d,]+)\s*个""", RegexOption.IGNORE_CASE),
            "结果总数"
        ), "结果总数"
    )
    val threads = splitSearchItems(html).map(::parseSearchThread)
    if (total > 0 && threads.isEmpty()) searchInvalid("百合会搜索结果数量与主题列表不一致")
    val explicitPages = Regex("""title=["']共\s*(\d+)\s*页["']""", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.get(1)
    val totalPages = explicitPages?.let { searchCount(it, "总页数") } ?: ceil(total / 20.0).toInt()
    return YamiboSearchPage(
        context.forumId,
        keyword,
        YamiboSearchPagination(
            context.page < totalPages,
            context.page,
            20,
            parseSearchId(html, context.responseUrl),
            totalPages,
            total
        ),
        context.scope,
        threads,
    )
}

private fun parseSearchThread(item: String): YamiboSearchThread {
    val id = positiveSearchCount(
        requiredSearchMatch(
            item,
            Regex("""viewthread(?:&amp;|&)tid=(\d+)""", RegexOption.IGNORE_CASE),
            "tid"
        ), "tid"
    )
    val rawUid = Regex(
        """space(?:&amp;|&)uid=(\d+)""",
        RegexOption.IGNORE_CASE
    ).find(item)?.groupValues?.get(1)
    val uid = rawUid?.let { searchCount(it, "uid") }?.takeIf { it > 0 }
    val authorRaw =
        Regex("""<a[^>]*class=["']mmc["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
            .find(item)?.groupValues?.get(1)
            ?: Regex(
                """<div[^>]*class=["']muser["'][^>]*>[\s\S]*?<h3[^>]*>([\s\S]*?)</h3>""",
                RegexOption.IGNORE_CASE
            )
                .find(item)?.groupValues?.get(1)
            ?: searchInvalid("百合会搜索结果缺少有效的作者")
    val authorName = searchText(authorRaw)
    val avatar = Regex(
        """<a[^>]*class=["']mimg["'][^>]*>\s*<img[^>]*src=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
        .find(item)?.groupValues?.get(1)
    val created = searchText(
        requiredSearchMatch(
            item,
            Regex(
                """<span[^>]*class=["']mtime["'][^>]*>([\s\S]*?)</span>""",
                RegexOption.IGNORE_CASE
            ),
            "发布时间"
        )
    )
    val subject = searchText(
        requiredSearchMatch(
            item,
            Regex(
                """<div[^>]*class=["'][^"']*\bthreadlist_tit\b[^"']*["'][^>]*>([\s\S]*?)</div>""",
                RegexOption.IGNORE_CASE
            ),
            "标题"
        )
    )
    val excerptRaw = Regex(
        """<div[^>]*class=["'][^"']*\bthreadlist_mes\b[^"']*["'][^>]*>([\s\S]*?)</div>""",
        RegexOption.IGNORE_CASE
    )
        .find(item)?.groupValues?.get(1).orEmpty()
    val forumId = positiveSearchCount(
        requiredSearchMatch(
            item,
            Regex("""forumdisplay(?:&amp;|&)fid=(\d+)""", RegexOption.IGNORE_CASE),
            "fid"
        ), "fid"
    )
    val forumName = searchText(
        requiredSearchMatch(
            item,
            Regex(
                """<a[^>]*href=["'][^"']*forumdisplay(?:&amp;|&)fid=\d+[^"']*["'][^>]*>([\s\S]*?)</a>""",
                RegexOption.IGNORE_CASE
            ),
            "板块名"
        )
    ).removePrefix("#")
    val views = searchCount(
        requiredSearchMatch(
            item,
            Regex("""class=["']dm-eye-fill["'][^>]*></i>\s*([\d,]+)""", RegexOption.IGNORE_CASE),
            "浏览数"
        ), "浏览数"
    )
    val replies = searchCount(
        requiredSearchMatch(
            item,
            Regex("""class=["']dm-chat-s-fill["'][^>]*></i>\s*([\d,]+)""", RegexOption.IGNORE_CASE),
            "回复数"
        ), "回复数"
    )
    val images = Regex(
        """<div[^>]*class=["'][^"']*\bthreadlist_imgs\d*\b[^"']*["'][^>]*>[\s\S]*?</div>""",
        RegexOption.IGNORE_CASE
    )
        .findAll(item).flatMap { container ->
            Regex("""<img[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .findAll(container.value).map { normalizeSearchUrl(it.groupValues[1]) }
        }.toList()
    if (listOf(
            authorName,
            created,
            subject,
            forumName
        ).any(String::isEmpty)
    ) searchInvalid("百合会搜索结果包含空的必要文本字段")
    return YamiboSearchThread(
        YamiboSearchAuthor(avatar?.let(::normalizeSearchUrl), uid, authorName),
        created,
        searchText(excerptRaw).ifEmpty { null },
        YamiboSearchForum(
            forumId,
            forumName,
            "$YAMIBO_ORIGIN/forum.php?mod=forumdisplay&fid=$forumId&mobile=2"
        ),
        id,
        images,
        replies,
        subject,
        views,
        "$YAMIBO_ORIGIN/forum.php?mod=viewthread&tid=$id&mobile=2",
    )
}

private fun splitSearchItems(html: String): List<String> {
    val starts = Regex("""<li\s+class=["']list["']\s*>""", RegexOption.IGNORE_CASE).findAll(html)
        .map { it.range.first }.toList()
    return starts.mapIndexed { index, start ->
        val end = starts.getOrNull(index + 1) ?: listOf(
            html.indexOf("<div class=\"pg\">", start),
            html.indexOf("<div id=\"mask\"", start),
            html.length
        )
            .filter { it >= 0 }.min()
        html.substring(start, end)
    }
}

private fun parseSearchId(html: String, responseUrl: String): Int? {
    val raw = runCatching {
        URI(responseUrl).rawQuery.orEmpty().split('&').firstNotNullOfOrNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.firstOrNull() == "searchid") decodeSearchQuery(parts.getOrElse(1) { "" }) else null
        }
    }.getOrElse { searchInvalid("百合会搜索返回了无效的页面地址") }
        ?: Regex("""searchid=(\d+)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        ?: return null
    return positiveSearchCount(raw, "searchid")
}

@Suppress("DEPRECATION")
private fun decodeSearchQuery(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())

private fun throwForSearchTip(html: String) {
    val tip =
        Regex("""<div[^>]*class=["']jump_c["'][^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1) ?: return
    val message = searchText(
        Regex(
            """<p[^>]*>([\s\S]*?)</p>""",
            RegexOption.IGNORE_CASE
        ).find(tip)?.groupValues?.get(1) ?: tip
    )
    when {
        "10 秒内只能进行一次搜索" in message -> throw YamiboSearchException(
            YamiboSearchErrorCode.RATE_LIMITED,
            message,
            10_000
        )

        "搜索指定的主题不存在" in message || "搜索结果已过期" in message -> throw YamiboSearchException(
            YamiboSearchErrorCode.SEARCH_EXPIRED,
            message
        )

        else -> throw YamiboSearchException(
            YamiboSearchErrorCode.SERVER_ERROR,
            message.ifEmpty { "百合会搜索服务返回了错误" })
    }
}

private fun requiredSearchMatch(value: String, regex: Regex, field: String): String =
    regex.find(value)?.groupValues?.getOrNull(1)?.takeIf(String::isNotEmpty)
        ?: searchInvalid("百合会搜索结果缺少有效的 $field")

private fun searchText(value: String): String = decodeSearchEntities(
    value.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]*>"""), "").replace("\r", ""),
).replace(Regex("""[ \t]+\n"""), "\n").replace(Regex("""\n[ \t]+"""), "\n").trim()

private fun decodeSearchEntities(value: String): String =
    Regex("""&(?:#(\d+)|#x([\da-f]+)|([a-z]+));""", RegexOption.IGNORE_CASE)
        .replace(value) { match ->
            val decimal = match.groupValues[1]
            val hex = match.groupValues[2]
            if (decimal.isNotEmpty() || hex.isNotEmpty()) {
                runCatching {
                    (if (decimal.isNotEmpty()) decimal.toInt() else hex.toInt(16)).let(
                        Character::toChars
                    ).concatToString()
                }.getOrDefault(match.value)
            } else mapOf(
                "amp" to "&",
                "apos" to "'",
                "gt" to ">",
                "lt" to "<",
                "nbsp" to "\u00a0",
                "quot" to "\""
            )[match.groupValues[3].lowercase()] ?: match.value
        }

private fun normalizeSearchUrl(raw: String): String {
    val value = decodeSearchEntities(raw.trim())
    if (value.startsWith("//")) return "https:$value"
    return URI("$YAMIBO_ORIGIN/").resolve(value).toString()
}

private fun searchCount(raw: String, field: String): Int =
    raw.replace(",", "").toIntOrNull()?.takeIf { it >= 0 }
        ?: searchInvalid("百合会搜索结果包含无效的 $field")

private fun positiveSearchCount(raw: String, field: String) =
    searchCount(raw, field).takeIf { it > 0 }
        ?: searchInvalid("百合会搜索结果包含无效的 $field")

private fun searchInvalid(message: String): Nothing =
    throw YamiboSearchException(YamiboSearchErrorCode.INVALID_RESPONSE, message)

private fun YamiboApiErrorCode.toSearchCode() = when (this) {
    YamiboApiErrorCode.INVALID_RESPONSE -> YamiboSearchErrorCode.INVALID_RESPONSE
    YamiboApiErrorCode.NETWORK_ERROR -> YamiboSearchErrorCode.NETWORK_ERROR
    YamiboApiErrorCode.SERVER_ERROR -> YamiboSearchErrorCode.SERVER_ERROR
}
