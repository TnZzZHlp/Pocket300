package com.yamibo.pocket300.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.GetForumThreadsInput
import com.yamibo.pocket300.api.YamiboForumThreadSort
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
import kotlinx.coroutines.launch

private data class ForumContent(
    val page: YamiboForumThreadsPage,
    val threads: List<YamiboThread>,
    val isLoadingMore: Boolean = false,
)


private data class ForumSnapshot(
    val content: ForumContent,
    val pageNumber: Int,
    val selectedTypeId: Int?,
    val sort: YamiboForumThreadSort,
)

private val forumSnapshots = mutableMapOf<Int, ForumSnapshot>()

internal const val STICKY_THREADS_INITIAL_EXPANDED = false

internal fun isStickyThread(stickyLevel: Int): Boolean = stickyLevel > 0


@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ForumScreen(
    forumId: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onForum: (Int) -> Unit,
    onSearch: () -> Unit,
    onThread: (YamiboThread) -> Unit,
) {
    val cachedSnapshot = remember(forumId) { forumSnapshots[forumId] }
    var reload by remember { mutableStateOf(0) }
    var pageNumber by rememberSaveable(forumId) { mutableStateOf(cachedSnapshot?.pageNumber ?: 1) }
    var selectedTypeId by rememberSaveable(forumId) { mutableStateOf(cachedSnapshot?.selectedTypeId) }
    var sort by rememberSaveable(forumId) {
        mutableStateOf(cachedSnapshot?.sort ?: YamiboForumThreadSort.LATEST_REPLY)
    }
    var state: LoadState<ForumContent> by remember(forumId) {
        mutableStateOf(cachedSnapshot?.content?.let { LoadState.Ready(it) } ?: LoadState.Loading)
    }
    var restoreCachedPage by remember(forumId) { mutableStateOf(cachedSnapshot != null) }
    var refreshingThreads by remember { mutableStateOf(false) }
    var threadLoadError by remember { mutableStateOf<String?>(null) }
    var stickyThreadsExpanded by rememberSaveable(forumId) {
        mutableStateOf(STICKY_THREADS_INITIAL_EXPANDED)
    }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(forumId, reload, pageNumber, selectedTypeId, sort) {
        if (restoreCachedPage && reload == 0) {
            restoreCachedPage = false
            return@LaunchedEffect
        }
        val previous = (state as? LoadState.Ready)?.value
        refreshingThreads = pageNumber == 1 && previous != null
        threadLoadError = null
        if (pageNumber == 1 && previous == null) {
            state = LoadState.Loading
        } else if (pageNumber > 1 && previous != null) {
            state = LoadState.Ready(previous.copy(isLoadingMore = true))
        }
        when (val result = load {
            api.threads.getForumThreads(
                GetForumThreadsInput(
                    forumId,
                    pageNumber,
                    typeId = selectedTypeId,
                    sort = sort,
                )
            )
        }) {
            is LoadState.Ready -> {
                val content = ForumContent(
                    result.value,
                    if (pageNumber == 1) result.value.threads
                    else (previous?.threads.orEmpty() + result.value.threads).distinctBy { it.id },
                )
                state = LoadState.Ready(content)
                forumSnapshots[forumId] = ForumSnapshot(content, pageNumber, selectedTypeId, sort)
            }

            is LoadState.Failed -> if (previous == null) {
                state = result
            } else {
                state = LoadState.Ready(previous.copy(isLoadingMore = false))
                threadLoadError = result.message
            }

            LoadState.Loading -> Unit
        }
        refreshingThreads = false
    }
    ScreenScaffold(
        modifier = with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState("forum-$forumId"),
                animatedVisibilityScope
            )
        },
        title = (state as? LoadState.Ready)?.value?.page?.forum?.name ?: "板块",
        onBack = onBack,
        onSearch = onSearch,
        onRefresh = { refreshingThreads = true; pageNumber = 1; reload++ },
        isRefreshing = refreshingThreads,
        onTopBarDoubleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
        actions = {
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Sort,
                        contentDescription = stringResource(R.string.forum_sort),
                        tint = if (sort != YamiboForumThreadSort.LATEST_REPLY) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                ForumSortMenu(
                    expanded = sortMenuExpanded,
                    sort = sort,
                    onDismiss = { sortMenuExpanded = false },
                    onSort = {
                        sort = it
                        pageNumber = 1
                        sortMenuExpanded = false
                    },
                )
            }
        },
    ) { padding ->
        LoadContent(state, padding) { content ->
            val page = content.page
            AutoLoadNextPage(
                listState = listState,
                hasNextPage = page.pagination.hasNextPage && !content.isLoadingMore,
                onLoadMore = { pageNumber = page.pagination.page + 1 },
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    item { SectionLabel(stringResource(R.string.forum_filter_category)) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedTypeId == null,
                                    onClick = { selectedTypeId = null; pageNumber = 1 },
                                    label = { Text(stringResource(R.string.forum_filter_all)) },
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
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                } else {
                    threadLoadError?.let { message ->
                        item {
                            Text(
                                message,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    val (stickyThreads, regularThreads) = content.threads.partition {
                        isStickyThread(it.stickyLevel)
                    }
                    if (stickyThreads.isNotEmpty()) {
                        item(key = "sticky-threads", contentType = "sticky-threads") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                ElevatedCard(
                                    onClick = { stickyThreadsExpanded = !stickyThreadsExpanded },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            stringResource(R.string.forum_sticky_threads, stickyThreads.size),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Icon(
                                            imageVector = if (stickyThreadsExpanded) {
                                                Icons.Rounded.ExpandLess
                                            } else {
                                                Icons.Rounded.ExpandMore
                                            },
                                            contentDescription = stringResource(
                                                if (stickyThreadsExpanded) {
                                                    R.string.forum_collapse_sticky_threads
                                                } else {
                                                    R.string.forum_expand_sticky_threads
                                                }
                                            ),
                                        )
                                    }
                                }
                                AnimatedVisibility(
                                    visible = stickyThreadsExpanded,
                                    enter = expandVertically(expandFrom = Alignment.Top),
                                    exit = shrinkVertically(shrinkTowards = Alignment.Top),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        stickyThreads.forEach { thread ->
                                            ForumThreadCard(
                                                thread = thread,
                                                onThread = onThread,
                                                sharedTransitionScope = sharedTransitionScope,
                                                animatedVisibilityScope = animatedVisibilityScope,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    items(regularThreads, key = { it.id }, contentType = { "thread" }) { thread ->
                        ForumThreadCard(
                            thread = thread,
                            onThread = onThread,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                    item {
                        ListFooter(
                            count = content.threads.size,
                            hasNextPage = page.pagination.hasNextPage,
                            isLoadingMore = content.isLoadingMore,
                            onLoadMore = { pageNumber = page.pagination.page + 1 },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForumSortMenu(
    expanded: Boolean,
    sort: YamiboForumThreadSort,
    onDismiss: () -> Unit,
    onSort: (YamiboForumThreadSort) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.forum_sort),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        YamiboForumThreadSort.entries.forEach { option ->
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            when (option) {
                                YamiboForumThreadSort.LATEST_REPLY -> R.string.forum_sort_latest_reply
                                YamiboForumThreadSort.POPULAR -> R.string.forum_sort_popular
                                YamiboForumThreadSort.DIGEST -> R.string.forum_sort_digest
                                YamiboForumThreadSort.NEWEST -> R.string.forum_sort_newest
                            }
                        )
                    )
                },
                onClick = { onSort(option) },
                trailingIcon = {
                    if (sort == option) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ForumThreadCard(
    thread: YamiboThread,
    onThread: (YamiboThread) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
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
