package com.yamibo.pocket300.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.api.GetForumThreadsInput
import com.yamibo.pocket300.api.YamiboForumThreadsPage
import com.yamibo.pocket300.api.YamiboThread
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.components.AutoLoadNextPage
import com.yamibo.pocket300.ui.components.ListFooter
import com.yamibo.pocket300.ui.components.SectionLabel
import com.yamibo.pocket300.ui.components.ThreadCard
import com.yamibo.pocket300.ui.load

private data class ForumContent(val page: YamiboForumThreadsPage, val threads: List<YamiboThread>)


private data class ForumSnapshot(
    val content: ForumContent,
    val pageNumber: Int,
    val selectedTypeId: Int?,
)

private val forumSnapshots = mutableMapOf<Int, ForumSnapshot>()


@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ForumScreen(
    forumId: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onForum: (Int) -> Unit,
    onThread: (YamiboThread) -> Unit,
) {
    val cachedSnapshot = remember(forumId) { forumSnapshots[forumId] }
    var reload by remember { mutableStateOf(0) }
    var pageNumber by rememberSaveable(forumId) { mutableStateOf(cachedSnapshot?.pageNumber ?: 1) }
    var selectedTypeId by rememberSaveable(forumId) { mutableStateOf(cachedSnapshot?.selectedTypeId) }
    var state: LoadState<ForumContent> by remember(forumId) {
        mutableStateOf(cachedSnapshot?.content?.let { LoadState.Ready(it) } ?: LoadState.Loading)
    }
    var restoreCachedPage by remember(forumId) { mutableStateOf(cachedSnapshot != null) }
    var refreshingThreads by remember { mutableStateOf(false) }
    var threadLoadError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(forumId, reload, pageNumber, selectedTypeId) {
        if (restoreCachedPage && reload == 0) {
            restoreCachedPage = false
            return@LaunchedEffect
        }
        val previous = (state as? LoadState.Ready)?.value
        refreshingThreads = pageNumber == 1 && previous != null
        threadLoadError = null
        if (pageNumber == 1 && previous == null) state = LoadState.Loading
        when (val result = load {
            api.threads.getForumThreads(GetForumThreadsInput(forumId, pageNumber, typeId = selectedTypeId))
        }) {
            is LoadState.Ready -> {
                val content = ForumContent(
                    result.value,
                    if (pageNumber == 1) result.value.threads
                    else (previous?.threads.orEmpty() + result.value.threads).distinctBy { it.id },
                )
                state = LoadState.Ready(content)
                forumSnapshots[forumId] = ForumSnapshot(content, pageNumber, selectedTypeId)
            }
            is LoadState.Failed -> if (previous == null) state = result else threadLoadError = result.message
            LoadState.Loading -> Unit
        }
        refreshingThreads = false
    }
    ScreenScaffold(
        modifier = with(sharedTransitionScope) {
            Modifier.sharedBounds(rememberSharedContentState("forum-$forumId"), animatedVisibilityScope)
        },
        title = (state as? LoadState.Ready)?.value?.page?.forum?.name ?: "板块",
        onBack = onBack,
        onRefresh = { pageNumber = 1; reload++ },
    ) { padding ->
        LoadContent(state, padding) { content ->
            val page = content.page
            AutoLoadNextPage(
                listState = listState,
                hasNextPage = page.pagination.hasNextPage,
                onLoadMore = { pageNumber = page.pagination.page + 1 },
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(page.forum.name, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                page.forum.description.ifBlank { "暂无板块简介" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Stat("主题", page.pagination.totalThreads)
                                Stat("帖子", page.forum.postCount)
                                Stat("页码", page.pagination.page)
                            }
                        }
                    }
                }
                if (page.subforums.isNotEmpty()) {
                    item { SectionLabel("子板块") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(page.subforums, key = { it.id }) { subforum ->
                                AssistChip(
                                    onClick = { onForum(subforum.id) },
                                    label = { Text("${subforum.name} · ${subforum.threadCount} 主题") },
                                )
                            }
                        }
                    }
                }
                if (page.threadTypes.isNotEmpty()) {
                    item { SectionLabel("分类") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedTypeId == null,
                                    onClick = { selectedTypeId = null; pageNumber = 1 },
                                    label = { Text("全部") },
                                )
                            }
                            items(page.threadTypes, key = { it.id }) { type ->
                                FilterChip(
                                    selected = selectedTypeId == type.id,
                                    onClick = { selectedTypeId = type.id; pageNumber = 1 },
                                    label = { Text(type.name) },
                                )
                            }
                        }
                    }
                }
                if (refreshingThreads) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                } else {
                    threadLoadError?.let { message ->
                        item {
                            Text(
                                message,
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    items(content.threads, key = { it.id }, contentType = { "thread" }) { thread ->
                        ThreadCard(
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
                            hasNextPage = page.pagination.hasNextPage,
                            onLoadMore = { pageNumber = page.pagination.page + 1 },
                        )
                    }
                }
            }
        }
    }
}

