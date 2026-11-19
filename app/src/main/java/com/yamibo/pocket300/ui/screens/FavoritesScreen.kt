package com.yamibo.pocket300.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.YamiboFavoriteThread
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.LocalReadingHistory
import com.yamibo.pocket300.ui.favoriteThreadAuthor
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.components.LocalSearchField
import com.yamibo.pocket300.ui.components.ThreadCardContent
import com.yamibo.pocket300.ui.components.ThreadCardSurface
import com.yamibo.pocket300.ui.components.ThreadListLoadingSkeleton
import com.yamibo.pocket300.ui.components.matchesLocalSearch
import com.yamibo.pocket300.ui.load
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun FavoritesScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    authStateVersion: Int = 0,
    scrollToTopTrigger: Int = 0,
    onThread: (YamiboFavoriteThread) -> Unit,
) {
    var reload by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var state: LoadState<List<YamiboFavoriteThread>> by remember { mutableStateOf(LoadState.Loading) }

    fun closeSearch() {
        searchActive = false
        searchQuery = ""
    }

    val context = LocalContext.current
    var pendingRemoveFavorite by remember { mutableStateOf<YamiboFavoriteThread?>(null) }
    var removingFavoriteId by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = searchActive, onBack = ::closeSearch)

    LaunchedEffect(authStateVersion) {
        if (authStateVersion > 0) reload++
    }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
    }
    LaunchedEffect(reload) {
        try {
            state = load { api.favorites.getFavoriteThreads() }
        } finally {
            refreshing = false
        }
    }
    ScreenScaffold(
        stringResource(R.string.favorites_title),
        onRefresh = { refreshing = true; reload++ },
        isRefreshing = refreshing,
        onSearch = if (searchActive) null else {
            { searchActive = true }
        },
        onTopBarDoubleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
        actions = {
            if (searchActive) {
                IconButton(onClick = ::closeSearch) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.search_close),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(searchActive) {
                LocalSearchField(
                    query = searchQuery,
                    label = stringResource(R.string.favorites_search_label),
                    onQueryChange = { searchQuery = it },
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                LoadContent(
                    state,
                    PaddingValues(0.dp),
                    onRetry = { state = LoadState.Loading; reload++ },
                    loading = { ThreadListLoadingSkeleton() },
                ) { favorites ->
                    val filteredFavorites = remember(favorites, searchQuery) {
                        filterFavoriteThreads(favorites, searchQuery)
                    }
                    LaunchedEffect(searchQuery) {
                        if (searchQuery.isNotBlank() && filteredFavorites.isNotEmpty()) {
                            listState.scrollToItem(0)
                        }
                    }
                    when {
                        favorites.isEmpty() -> {
                            EmptyState(
                                stringResource(R.string.favorites_empty_title),
                                message = stringResource(R.string.favorites_empty_message),
                            )
                        }
                        filteredFavorites.isEmpty() -> {
                            EmptyState(
                                stringResource(R.string.favorites_search_empty_title),
                                stringResource(R.string.favorites_search_empty_message, searchQuery.trim()),
                            )
                        }
                        else -> {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                items(filteredFavorites, key = { it.favoriteId }) { favorite ->
                                    ThreadCardSurface(
                                        onClick = { onThread(favorite) },
                                        modifier = with(sharedTransitionScope) {
                                            Modifier.sharedBounds(
                                                rememberSharedContentState("thread-${favorite.threadId}"),
                                                animatedVisibilityScope,
                                            )
                                        },
                                    ) {
                                        val history = LocalReadingHistory.current[favorite.threadId]
                                        val isRemoving = removingFavoriteId == favorite.favoriteId
                                        ThreadCardContent(
                                            subject = favorite.title,
                                            threadId = favorite.threadId,
                                            supporting = favorite.description.takeIf(String::isNotBlank),
                                            author = favoriteThreadAuthor(favorite, history),
                                            dateText = favorite.createdAtText.takeIf(String::isNotBlank)?.let {
                                                stringResource(R.string.favorite_saved_at, it)
                                            }.orEmpty(),
                                            replyCount = favorite.replyCount,
                                            resolveMissingAuthor = true,
                                            headerTrailing = {
                                                IconButton(
                                                    onClick = { pendingRemoveFavorite = favorite },
                                                    enabled = removingFavoriteId == null,
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    if (isRemoving) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                        )
                                                    } else {
                                                        Icon(
                                                            Icons.Rounded.Favorite,
                                                            contentDescription = stringResource(R.string.favorites_remove_title),
                                                            modifier = Modifier.size(18.dp),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            pendingRemoveFavorite?.let { target ->
                val removeTitle = stringResource(R.string.favorites_remove_title)
                AlertDialog(
                    onDismissRequest = { if (removingFavoriteId == null) pendingRemoveFavorite = null },
                    title = { Text(removeTitle) },
                    text = { Text(stringResource(R.string.favorites_remove_message, target.title)) },
                    confirmButton = {
                        TextButton(
                            enabled = removingFavoriteId == null,
                            onClick = {
                                removingFavoriteId = target.favoriteId
                                coroutineScope.launch {
                                    try {
                                        api.favorites.removeThread(target.favoriteId)
                                        val current = state
                                        if (current is LoadState.Ready) {
                                            state = LoadState.Ready(
                                                current.value.filterNot { it.favoriteId == target.favoriteId },
                                            )
                                        }
                                        pendingRemoveFavorite = null
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            e.message?.takeIf(String::isNotBlank) ?: removeTitle,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } finally {
                                        removingFavoriteId = null
                                    }
                                }
                            },
                        ) {
                            Text(
                                stringResource(R.string.favorites_remove_confirm),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = removingFavoriteId == null,
                            onClick = { pendingRemoveFavorite = null },
                        ) {
                            Text(stringResource(R.string.custom_list_cancel))
                        }
                    },
                )
            }
        }
    }
}

internal fun filterFavoriteThreads(
    favorites: List<YamiboFavoriteThread>,
    query: String,
): List<YamiboFavoriteThread> = favorites.filter { favorite ->
    matchesLocalSearch(query, favorite.title, favorite.description)
}
