package com.yamibo.pocket300.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.YamiboApi
import com.yamibo.pocket300.data.ReadingHistoryDatabase
import com.yamibo.pocket300.data.ReadingHistoryEntry
import com.yamibo.pocket300.ui.screens.CustomListDetailScreen
import com.yamibo.pocket300.ui.screens.CustomListEditorScreen
import com.yamibo.pocket300.ui.screens.FavoritesScreen
import com.yamibo.pocket300.ui.screens.ForumIndexScreen
import com.yamibo.pocket300.ui.screens.ForumScreen
import com.yamibo.pocket300.ui.screens.ListScreen
import com.yamibo.pocket300.ui.screens.ProfileScreen
import com.yamibo.pocket300.ui.screens.ReaderContent
import com.yamibo.pocket300.ui.screens.ReaderScreen
import com.yamibo.pocket300.ui.screens.RatingsScreen
import com.yamibo.pocket300.ui.screens.ReadingHistoryScreen
import com.yamibo.pocket300.ui.screens.SearchScreen
import com.yamibo.pocket300.ui.screens.SettingsScreen
import com.yamibo.pocket300.ui.screens.ThreadScreen
import com.yamibo.pocket300.ui.theme.PocketTheme

internal val api = YamiboApi()

private data class Tab(val route: String, @param:StringRes val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", R.string.tab_forum, Icons.Rounded.Forum),
    Tab("list", R.string.tab_list, Icons.AutoMirrored.Rounded.List),
    Tab("favorites", R.string.tab_favorites, Icons.Rounded.Favorite),
    Tab("profile", R.string.tab_profile, Icons.Rounded.AccountCircle),
)

private data class PendingThreadNavigation(val route: String, val history: ReadingHistoryEntry)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Pocket300App() {
    val context = LocalContext.current
    val themePreferencesStore = remember(context) { AppThemePreferencesStore(context) }
    val historyDatabase = remember(context) { ReadingHistoryDatabase.getInstance(context) }
    val readingHistory by historyDatabase.entries.collectAsState()
    var colorTheme by rememberSaveable { mutableStateOf(themePreferencesStore.load()) }

    PocketTheme(colorTheme = colorTheme) {
        val navController = rememberNavController()
        var authStateVersion by rememberSaveable { mutableIntStateOf(0) }
        var readerContent by remember { mutableStateOf<ReaderContent?>(null) }
        val entry by navController.currentBackStackEntryAsState()
        val route = entry?.destination?.route.orEmpty()
        val isTopLevel = tabs.any { it.route == route }
        var pendingThread by remember { mutableStateOf<PendingThreadNavigation?>(null) }

        fun openThread(threadId: Int, route: String) {
            val history = readingHistory[threadId]
            if (history != null && history.lastReadFloor > 1) {
                pendingThread = PendingThreadNavigation(route, history)
            } else {
                navController.navigate(route)
            }
        }

        CompositionLocalProvider(LocalReadingHistory provides readingHistory) {
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
                FavoritesScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onThread = {
                        openThread(
                            it.threadId,
                            "thread/${it.threadId}?favoriteId=${it.favoriteId}",
                        )
                    },
                )
            }
            composable("list") {
                ListScreen(
                    onCreate = { navController.navigate("custom-list/new") },
                    onOpen = { navController.navigate("custom-list/$it") },
                )
            }
            composable("custom-list/new") {
                CustomListEditorScreen(
                    listId = null,
                    onBack = navController::navigateUp,
                    onSaved = { listId ->
                        navController.navigate("custom-list/$listId") {
                            popUpTo("custom-list/new") { inclusive = true }
                        }
                    },
                    onDeleted = navController::navigateUp,
                )
            }
            composable(
                route = "custom-list/{listId}",
                arguments = listOf(navArgument("listId") { type = NavType.LongType }),
            ) { backStack ->
                val listId = backStack.arguments?.getLong("listId") ?: return@composable
                CustomListDetailScreen(
                    listId = listId,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onBack = navController::navigateUp,
                    onEdit = { navController.navigate("custom-list/$listId/edit") },
                    onThread = { openThread(it.threadId, "thread/${it.threadId}") },
                )
            }
            composable(
                route = "custom-list/{listId}/edit",
                arguments = listOf(navArgument("listId") { type = NavType.LongType }),
            ) { backStack ->
                CustomListEditorScreen(
                    listId = backStack.arguments?.getLong("listId") ?: return@composable,
                    onBack = navController::navigateUp,
                    onSaved = { navController.navigateUp() },
                    onDeleted = { navController.popBackStack("list", inclusive = false) },
                )
            }
            composable("history") {
                ReadingHistoryScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onBack = navController::navigateUp,
                    onThread = {
                        openThread(it.threadId, "thread/${it.threadId}")
                    },
                )
            }
            composable("profile") {
                ProfileScreen(
                    onAuthStateChanged = { authStateVersion++ },
                    onHistory = { navController.navigate("history") },
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
                    onThread = { openThread(it.id, "thread/${it.id}") },
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
                    onThread = { openThread(it.id, "thread/${it.id}") },
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
                    onReader = { content, page ->
                        readerContent = content
                        navController.navigate(
                            "reader/${content.thread.id}/${content.post.id}/$page",
                        )
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
                    initialContent = readerContent,
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
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        }
        pendingThread?.let { pending ->
            AlertDialog(
                onDismissRequest = { pendingThread = null },
                title = { Text(stringResource(R.string.thread_resume_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.thread_resume_message,
                            pending.history.lastReadFloor,
                        ),
                    )
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingThread = null
                            navController.navigate(pending.route)
                        },
                    ) { Text(stringResource(R.string.thread_start_from_beginning)) }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingThread = null
                            navController.navigate(
                                routeWithLastReadFloor(
                                    pending.route,
                                    pending.history.lastReadFloor,
                                ),
                            )
                        },
                    ) { Text(stringResource(R.string.thread_jump_to_last_read)) }
                },
            )
        }
      }
        }
        }
    }
}
