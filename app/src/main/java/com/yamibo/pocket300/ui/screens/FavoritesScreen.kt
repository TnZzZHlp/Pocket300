package com.yamibo.pocket300.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.api.YamiboFavoriteThread
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.LocalReadingHistory
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.load
import com.yamibo.pocket300.ui.dimIfRead
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
    var state: LoadState<List<YamiboFavoriteThread>> by remember { mutableStateOf(LoadState.Loading) }
    LaunchedEffect(reload) { state = load { api.favorites.getFavoriteThreads() } }
    ScreenScaffold(
        "收藏",
        onRefresh = { reload++ },
        isRefreshing = state is LoadState.Loading,
        onTopBarDoubleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
    ) { padding ->
        LoadContent(state, padding) { favorites ->
            if (favorites.isEmpty()) {
                EmptyState("还没有收藏", "在主题页面收藏的内容会显示在这里。")
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(favorites, key = { it.favoriteId }) { favorite ->
                        Card(
                            onClick = { onThread(favorite) },
                            modifier = with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    rememberSharedContentState("thread-${favorite.threadId}"),
                                    animatedVisibilityScope,
                                )
                            }.fillMaxWidth().dimIfRead(favorite.threadId, histories),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(favorite.title, style = MaterialTheme.typography.titleMedium)
                                favorite.createdAtText.takeIf(String::isNotBlank)?.let {
                                    Text(it, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

