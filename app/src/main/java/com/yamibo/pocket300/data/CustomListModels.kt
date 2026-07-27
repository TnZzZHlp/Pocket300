package com.yamibo.pocket300.data

import com.yamibo.pocket300.api.YamiboThreadSearchType
import com.yamibo.pocket300.logging.AppLogger

const val DEFAULT_CUSTOM_LIST_AUTO_REFRESH_INTERVAL_HOURS = 24
const val DEFAULT_CUSTOM_LIST_AUTO_DOWNLOAD_NEW_THREADS = false
const val DEFAULT_CUSTOM_LIST_AUTO_DELETE_AFTER_IMAGE_READING = false

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
    val autoRefreshIntervalHours: Int = DEFAULT_CUSTOM_LIST_AUTO_REFRESH_INTERVAL_HOURS,
    val autoDownloadNewThreads: Boolean = DEFAULT_CUSTOM_LIST_AUTO_DOWNLOAD_NEW_THREADS,
    val autoDeleteAfterImageReading: Boolean =
        DEFAULT_CUSTOM_LIST_AUTO_DELETE_AFTER_IMAGE_READING,
)

/**
 * The first sync establishes the list's baseline. Automatically downloading every historical
 * result when a list is created would be surprising, so only later additions are eligible.
 */
internal fun CustomThreadList.shouldAutoDownloadAddedThreads(addedThreadCount: Int): Boolean =
    autoDownloadNewThreads && lastSyncedAt != null && addedThreadCount > 0

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
        .getOrElse { error ->
            AppLogger.warn("CustomListDatabase", error) {
                "Invalid saved custom list search type; using title search"
            }
            YamiboThreadSearchType.TITLE
        }

internal fun normalizeCustomListKeywords(value: String): List<String> = value
    .lineSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(String::lowercase)
    .toList()

