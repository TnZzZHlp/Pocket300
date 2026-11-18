package com.yamibo.pocket300.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun navigationContentBottomPadding(isTopLevel: Boolean, navigationBarPadding: Dp): Dp =
    if (isTopLevel) navigationBarPadding else 0.dp

internal fun shouldScrollTabToTopOnClick(currentRoute: String, targetRoute: String): Boolean =
    currentRoute == targetRoute

internal fun isTabVisible(
    route: String,
    isAuthenticated: Boolean,
    hasLocalCustomLists: Boolean,
): Boolean = when (route) {
    "favorites" -> isAuthenticated
    "list" -> isAuthenticated || hasLocalCustomLists
    else -> true
}

internal fun shouldRedirectUnauthorizedRoute(
    currentRoute: String,
    isAuthenticated: Boolean,
    hasLocalCustomLists: Boolean,
): Boolean {
    if (isAuthenticated || currentRoute.isEmpty()) return false
    return when {
        currentRoute == "favorites" -> true
        currentRoute.startsWith("search") -> true
        currentRoute == "custom-list/new" -> true
        currentRoute.startsWith("custom-list/") && currentRoute.endsWith("/edit") -> true
        currentRoute.startsWith("ratings/") -> true
        !hasLocalCustomLists &&
            (currentRoute == "list" || currentRoute.startsWith("custom-list/")) -> true
        else -> false
    }
}
