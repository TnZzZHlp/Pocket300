package com.yamibo.pocket300.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yamibo.pocket300.api.YamiboApi
import com.yamibo.pocket300.ui.screens.FavoritesScreen
import com.yamibo.pocket300.ui.screens.ForumIndexScreen
import com.yamibo.pocket300.ui.screens.ForumScreen
import com.yamibo.pocket300.ui.screens.ProfileScreen
import com.yamibo.pocket300.ui.screens.ReaderScreen
import com.yamibo.pocket300.ui.screens.RatingsScreen
import com.yamibo.pocket300.ui.screens.ReadingHistoryScreen
import com.yamibo.pocket300.ui.screens.SearchScreen
import com.yamibo.pocket300.ui.screens.SettingsScreen
import com.yamibo.pocket300.ui.screens.ThreadScreen
import com.yamibo.pocket300.ui.theme.PocketTheme

internal val api = YamiboApi()

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "论坛", Icons.Rounded.Forum),
    Tab("history", "历史", Icons.Rounded.History),
    Tab("favorites", "收藏", Icons.Rounded.Favorite),
    Tab("profile", "我的", Icons.Rounded.AccountCircle),
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Pocket300App() {
    val context = LocalContext.current
    val themePreferencesStore = remember(context) { AppThemePreferencesStore(context) }
    var colorTheme by rememberSaveable { mutableStateOf(themePreferencesStore.load()) }

    PocketTheme(colorTheme = colorTheme) {
        val navController = rememberNavController()
        var authStateVersion by rememberSaveable { mutableIntStateOf(0) }
        val entry by navController.currentBackStackEntryAsState()
        val route = entry?.destination?.route.orEmpty()
        val isTopLevel = tabs.any { it.route == route }

        SharedTransitionLayout {
      val sharedTransitionScope = this
      Box(Modifier.fillMaxSize()) {
        NavHost(navController, startDestination = "home", modifier = Modifier.fillMaxSize()) {
            composable("home") {
                ForumIndexScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    authStateVersion = authStateVersion,
                    onForum = { navController.navigate("forum/${it.id}") },
                    onSearch = { navController.navigate("search") },
                )
            }
            composable("favorites") {
                FavoritesScreen {
                    navController.navigate("thread/${it.threadId}?favoriteId=${it.favoriteId}")
                }
            }
            composable("history") {
                ReadingHistoryScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onThread = {
                        navController.navigate("thread/${it.threadId}?floor=${it.lastReadFloor}")
                    },
                )
            }
            composable("profile") {
                ProfileScreen(
                    onAuthStateChanged = { authStateVersion++ },
                    onSettings = { navController.navigate("settings") },
                )
            }
            composable("settings") {
                SettingsScreen(
                    colorTheme = colorTheme,
                    onColorThemeChange = { updated ->
                        colorTheme = updated
                        themePreferencesStore.save(updated)
                    },
                    onBack = navController::navigateUp,
                )
            }
            composable("search") {
                SearchScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onBack = navController::navigateUp,
                    onThread = { navController.navigate("thread/${it.id}") },
                )
            }
            composable(
                route = "forum/{forumId}",
                arguments = listOf(navArgument("forumId") { type = NavType.IntType }),
            ) { backStack ->
                ForumScreen(
                    forumId = backStack.arguments?.getInt("forumId") ?: return@composable,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onBack = navController::navigateUp,
                    onForum = { navController.navigate("forum/$it") },
                    onThread = { navController.navigate("thread/${it.id}") },
                )
            }
            composable(
                route = "thread/{threadId}?floor={floor}&postId={postId}&page={page}&favoriteId={favoriteId}",
                arguments = listOf(
                    navArgument("threadId") { type = NavType.IntType },
                    navArgument("floor") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("postId") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("page") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("favoriteId") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { backStack ->
                ThreadScreen(
                    threadId = backStack.arguments?.getInt("threadId") ?: return@composable,
                    initialFloor = backStack.arguments?.getInt("floor") ?: 0,
                    initialPostId = backStack.arguments?.getInt("postId") ?: 0,
                    initialPage = backStack.arguments?.getInt("page") ?: 0,
                    initialFavoriteId = backStack.arguments?.getInt("favoriteId") ?: 0,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onBack = navController::navigateUp,
                    onForum = { navController.navigate("forum/$it") },
                    onRatings = { threadId, postId -> navController.navigate("ratings/$threadId/$postId") },
                    onReader = { threadId, postId, page ->
                        navController.navigate("reader/$threadId/$postId/$page")
                    },
                    onThread = {
                        navController.navigate(
                            "thread/${it.id}?postId=${it.postId ?: 0}&page=${it.page ?: 0}",
                        )
                    },
                )
            }
            composable(
                route = "reader/{threadId}/{postId}/{page}",
                arguments = listOf(
                    navArgument("threadId") { type = NavType.IntType },
                    navArgument("postId") { type = NavType.IntType },
                    navArgument("page") { type = NavType.IntType },
                ),
            ) { backStack ->
                ReaderScreen(
                    threadId = backStack.arguments?.getInt("threadId") ?: return@composable,
                    postId = backStack.arguments?.getInt("postId") ?: return@composable,
                    initialPage = backStack.arguments?.getInt("page") ?: 1,
                    onBack = navController::navigateUp,
                    onForum = { navController.navigate("forum/$it") },
                    onThread = {
                        navController.navigate(
                            "thread/${it.id}?postId=${it.postId ?: 0}&page=${it.page ?: 0}",
                        )
                    },
                )
            }
            composable(
                route = "ratings/{threadId}/{postId}",
                arguments = listOf(
                    navArgument("threadId") { type = NavType.IntType },
                    navArgument("postId") { type = NavType.IntType },
                ),
            ) { backStack ->
                RatingsScreen(
                    threadId = backStack.arguments?.getInt("threadId") ?: return@composable,
                    postId = backStack.arguments?.getInt("postId") ?: return@composable,
                    onBack = navController::navigateUp,
                )
            }
        }
        AnimatedVisibility(
            visible = isTopLevel,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
          NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
      }
        }
    }
}
