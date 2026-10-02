package com.yamibo.pocket300.ui

import com.yamibo.pocket300.api.YamiboFavoriteThread
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.data.ReadingHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteThreadUiTest {
    private val favorite = YamiboFavoriteThread(9, 100, "标题", "", "昨天")
    private val history = ReadingHistoryEntry(100, 300, "标题", "alice", "", 12, 1, authorId = 42)

    @Test fun reusesSavedIdentityWhenFavoriteHasOnlyAuthorName() {
        val author = favoriteThreadAuthor(favorite.copy(author = YamiboPostAuthor(null, null, "alice")), history)!!
        assertEquals(42, author.id)
    }

    @Test fun prefersIdentityReturnedByFavoritesApi() {
        val current = YamiboPostAuthor(null, 43, "alice")
        assertEquals(current, favoriteThreadAuthor(favorite.copy(author = current), history))
    }

    @Test fun neverBorrowsIdentityFromAnotherThread() {
        assertNull(favoriteThreadAuthor(favorite, history.copy(threadId = 101)))
    }

    @Test fun keepsKnownNameWhileWaitingForMissingIdentity() {
        val author = YamiboPostAuthor(null, null, "alice")
        assertEquals(author, favoriteThreadAuthor(favorite.copy(author = author), null))
    }
}
