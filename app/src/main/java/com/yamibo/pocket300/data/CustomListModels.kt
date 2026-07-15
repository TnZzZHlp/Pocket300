package com.yamibo.pocket300.data

import com.yamibo.pocket300.api.YamiboThreadSearchType

data class CustomThreadList(
    val id: Long,
    val name: String,
    val keywords: List<String>,
    val searchType: YamiboThreadSearchType,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long?,
    val threadCount: Int,
    val excludedCount: Int,
)

data class CustomListThread(
    val listId: Long,
    val threadId: Int,
    val forumId: Int,
    val forumName: String,
    val subject: String,
    val authorName: String,
    val createdAtText: String,
    val excerpt: String?,
    val replyCount: Int,
    val viewCount: Int,
    val webUrl: String,
)

internal fun parseCustomListSearchType(value: String): YamiboThreadSearchType =
    runCatching { YamiboThreadSearchType.valueOf(value.uppercase()) }
        .getOrDefault(YamiboThreadSearchType.TITLE)

internal fun normalizeCustomListKeywords(value: String): List<String> = value
    .lineSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(String::lowercase)
    .toList()

