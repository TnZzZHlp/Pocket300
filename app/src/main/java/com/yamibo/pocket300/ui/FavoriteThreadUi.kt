package com.yamibo.pocket300.ui

import com.yamibo.pocket300.api.YamiboFavoriteThread
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.data.ReadingHistoryEntry

internal fun favoriteThreadAuthor(
    favorite: YamiboFavoriteThread,
    history: ReadingHistoryEntry?,
): YamiboPostAuthor? {
    val author = favorite.author
    if (author.hasAvatarIdentity()) return author
    val saved = history?.takeIf { it.threadId == favorite.threadId }?.let {
        YamiboPostAuthor(it.authorAvatarUrl, it.authorId, it.authorName)
    }
    return saved?.takeIf { it.hasAvatarIdentity() } ?: author ?: saved
}

private fun YamiboPostAuthor?.hasAvatarIdentity(): Boolean =
    (this?.id ?: 0) > 0 || !this?.avatarUrl.isNullOrBlank()
