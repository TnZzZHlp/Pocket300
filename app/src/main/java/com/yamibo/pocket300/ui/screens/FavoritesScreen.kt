package com.yamibo.pocket300.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.YamiboFavoriteThread
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.LocalReadingHistory
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.components.LocalSearchField
import com.yamibo.pocket300.ui.components.ThreadLastReadPosition
import com.yamibo.pocket300.ui.components.matchesLocalSearch
import com.yamibo.pocket300.ui.dimIfRead
import com.yamibo.pocket300.ui.load
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun FavoritesScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onThread: (YamiboFavoriteThread) -> Unit,
) {
    val histories = LocalReadingHistory.current
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

    BackHandler(enabled = searchActive, onBack = ::closeSearch)

    LaunchedEffect(reload) {
        try {
            state = load { api.favorites.getFavoriteThreads() }
        } finally {
            refreshing = false
        }
    }
    ScreenScaffold(
        "收藏",
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
                LoadContent(state, PaddingValues(0.dp)) { favorites ->
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
                            EmptyState("还没有收藏", "在主题页面收藏的内容会显示在这里。")
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
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(filteredFavorites, key = { it.favoriteId }) { favorite ->
                                    Card(
                                        onClick = { onThread(favorite) },
                                        modifier = with(sharedTransitionScope) {
                                            Modifier.sharedBounds(
                                                rememberSharedContentState("thread-${favorite.threadId}"),
                                                animatedVisibilityScope,
                                            )
                                        }.fillMaxWidth().dimIfRead(favorite.threadId, histories),
                                    ) {
                                        Column(
                                            Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(favorite.title, style = MaterialTheme.typography.titleMedium)
                                            favorite.createdAtText.takeIf(String::isNotBlank)?.let {
                                                Text(it, style = MaterialTheme.typography.labelMedium)
                                            }
                                            ThreadLastReadPosition(favorite.threadId)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
