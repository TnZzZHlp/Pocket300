package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.api.YamiboFavoriteThread
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesScreenTest {
    private val favorites = listOf(
        YamiboFavoriteThread(1, 101, "Summer Story", "百合短篇", "昨天"),
        YamiboFavoriteThread(2, 102, "冬日物语", "Drama collection", "今天"),
    )

    @Test
    fun filtersFavoritesByTitleIgnoringCaseAndWhitespace() {
        assertEquals(listOf(favorites[0]), filterFavoriteThreads(favorites, "  summer  "))
    }

    @Test
    fun filtersFavoritesByDescription() {
        assertEquals(listOf(favorites[1]), filterFavoriteThreads(favorites, "drama"))
    }

    @Test
    fun requiresEverySearchTermToMatchFavoriteContent() {
        assertEquals(listOf(favorites[0]), filterFavoriteThreads(favorites, "Summer 百合"))
        assertEquals(emptyList<YamiboFavoriteThread>(), filterFavoriteThreads(favorites, "Summer Drama"))
    }

    @Test
    fun blankFavoriteQueryKeepsEveryFavorite() {
        assertEquals(favorites, filterFavoriteThreads(favorites, "  "))
    }
}
