package com.yamibo.pocket300.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yamibo.pocket300.api.YamiboSearchThread
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.Loading
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.components.AutoLoadNextPage
import com.yamibo.pocket300.ui.components.ListFooter
import com.yamibo.pocket300.ui.viewmodels.SearchContent
import com.yamibo.pocket300.ui.viewmodels.SearchViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun SearchScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onThread: (YamiboSearchThread) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    ScreenScaffold(
        "搜索",
        onBack = onBack,
        onTopBarDoubleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.weight(1f),
                    label = { Text("搜索主题") },
                    placeholder = { Text("输入关键字") },
                    singleLine = true,
                )
                Button(onClick = viewModel::submit) { Text("搜索") }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (val current = viewModel.state) {
                    null -> EmptyState("搜索主题", "输入关键字开始搜索。")
                    LoadState.Loading -> Loading()
                    is LoadState.Failed -> EmptyState("搜索失败", current.message)
                    is LoadState.Ready -> SearchResults(
                        content = current.value,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onThread = onThread,
                        onLoadMore = viewModel::loadMore,
                        listState = listState,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchResults(
    content: SearchContent,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onThread: (YamiboSearchThread) -> Unit,
    onLoadMore: () -> Unit,
    listState: LazyListState,
) {
    if (content.threads.isEmpty()) {
        EmptyState("没有搜索结果", "没有找到与“${content.page.keyword}”相关的主题。")
        return
    }
    AutoLoadNextPage(
        listState = listState,
        hasNextPage = content.page.pagination.hasNextPage,
        onLoadMore = onLoadMore,

    )
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(content.threads, key = { it.id }) { thread ->
            SearchThreadCard(
                thread = thread,
                onClick = onThread,
                modifier = with(sharedTransitionScope) {
                    Modifier.sharedBounds(
                        rememberSharedContentState("thread-${thread.id}"),
                        animatedVisibilityScope,
                    )
                },
            )
        }
        item {
            ListFooter(
                count = content.threads.size,
                hasNextPage = content.page.pagination.hasNextPage,
                onLoadMore = onLoadMore,
            )
        }
    }
}

@Composable
private fun SearchThreadCard(
    thread: YamiboSearchThread,
    onClick: (YamiboSearchThread) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = { onClick(thread) }, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(thread.forum.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(thread.subject, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            thread.excerpt?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${thread.author.name} · ${thread.createdAtText} · ${thread.replyCount} 回复 · ${thread.viewCount} 浏览",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
