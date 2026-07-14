package com.yamibo.pocket300.data

data class CustomThreadList(
    val id: Long,
    val name: String,
    val keywords: List<String>,
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

internal fun normalizeCustomListKeywords(value: String): List<String> = value
    .split(Regex("[,，;；\\n]+"))
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(String::lowercase)

