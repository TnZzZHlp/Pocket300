package com.yamibo.pocket300.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Pocket300AppTest {
    @Test
    fun reservesBottomNavigationSpaceOnTopLevelScreen() {
        assertEquals(80.dp, navigationContentBottomPadding(true, 80.dp))
    }

    @Test
    fun releasesBottomNavigationSpaceAsSoonAsDetailScreenOpens() {
        assertEquals(0.dp, navigationContentBottomPadding(false, 80.dp))
    }

    @Test
    fun scrollsToTopOnlyWhenReselectingCurrentTab() {
        assertTrue(shouldScrollTabToTopOnClick(currentRoute = "home", targetRoute = "home"))
        assertFalse(shouldScrollTabToTopOnClick(currentRoute = "home", targetRoute = "favorites"))
        assertFalse(shouldScrollTabToTopOnClick(currentRoute = "thread/{threadId}", targetRoute = "home"))
    }

    @Test
    fun hidesFavoritesAndEmptyListTabsWhenUnauthenticated() {
        assertTrue(isTabVisible("home", isAuthenticated = false, hasLocalCustomLists = false))
        assertFalse(isTabVisible("list", isAuthenticated = false, hasLocalCustomLists = false))
        assertFalse(isTabVisible("favorites", isAuthenticated = false, hasLocalCustomLists = false))
        assertTrue(isTabVisible("profile", isAuthenticated = false, hasLocalCustomLists = false))

        assertTrue(isTabVisible("list", isAuthenticated = false, hasLocalCustomLists = true))
        assertFalse(isTabVisible("favorites", isAuthenticated = false, hasLocalCustomLists = true))

        assertTrue(isTabVisible("favorites", isAuthenticated = true, hasLocalCustomLists = false))
        assertTrue(isTabVisible("list", isAuthenticated = true, hasLocalCustomLists = false))
    }

    @Test
    fun redirectsUnauthorizedRoutesBasedOnAuthAndLocalCustomLists() {
        assertTrue(shouldRedirectUnauthorizedRoute("favorites", isAuthenticated = false, hasLocalCustomLists = true))
        assertTrue(shouldRedirectUnauthorizedRoute("search?forumId={forumId}", isAuthenticated = false, hasLocalCustomLists = true))
        assertTrue(shouldRedirectUnauthorizedRoute("custom-list/new", isAuthenticated = false, hasLocalCustomLists = true))
        assertTrue(shouldRedirectUnauthorizedRoute("custom-list/{listId}/edit", isAuthenticated = false, hasLocalCustomLists = true))
        assertTrue(shouldRedirectUnauthorizedRoute("ratings/{threadId}/{postId}", isAuthenticated = false, hasLocalCustomLists = true))

        assertTrue(shouldRedirectUnauthorizedRoute("list", isAuthenticated = false, hasLocalCustomLists = false))
        assertTrue(shouldRedirectUnauthorizedRoute("custom-list/{listId}", isAuthenticated = false, hasLocalCustomLists = false))
        assertFalse(shouldRedirectUnauthorizedRoute("list", isAuthenticated = false, hasLocalCustomLists = true))
        assertFalse(shouldRedirectUnauthorizedRoute("custom-list/{listId}", isAuthenticated = false, hasLocalCustomLists = true))

        assertFalse(shouldRedirectUnauthorizedRoute("home", isAuthenticated = false, hasLocalCustomLists = false))
        assertFalse(shouldRedirectUnauthorizedRoute("profile", isAuthenticated = false, hasLocalCustomLists = false))
        assertFalse(shouldRedirectUnauthorizedRoute("forum/{forumId}", isAuthenticated = false, hasLocalCustomLists = false))
        assertFalse(shouldRedirectUnauthorizedRoute("thread/{threadId}?floor={floor}&postId={postId}&page={page}&favoriteId={favoriteId}&offline={offline}", isAuthenticated = false, hasLocalCustomLists = false))
        assertFalse(shouldRedirectUnauthorizedRoute("history", isAuthenticated = false, hasLocalCustomLists = false))
        assertFalse(shouldRedirectUnauthorizedRoute("downloads", isAuthenticated = false, hasLocalCustomLists = false))
        assertFalse(shouldRedirectUnauthorizedRoute("settings", isAuthenticated = false, hasLocalCustomLists = false))
        assertFalse(shouldRedirectUnauthorizedRoute("logs", isAuthenticated = false, hasLocalCustomLists = false))
        assertFalse(shouldRedirectUnauthorizedRoute("favorites", isAuthenticated = true, hasLocalCustomLists = false))
    }
}
