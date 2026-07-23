package com.yamibo.pocket300.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.YamiboThreadSearchType
import com.yamibo.pocket300.data.CustomListAutoRefreshScheduler
import com.yamibo.pocket300.data.CustomListDatabase
import com.yamibo.pocket300.data.CustomListRefreshEvents
import com.yamibo.pocket300.data.CustomListRepository
import com.yamibo.pocket300.data.CustomThreadList
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.LocalReadingHistory
import com.yamibo.pocket300.ui.Loading
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ListScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val context = LocalContext.current
    val database = remember(context) { CustomListDatabase.getInstance(context) }
    val repository = remember(database) { CustomListRepository(database, api.search) }
    val refreshScheduler = remember(database, repository) {
        CustomListAutoRefreshScheduler(
            loadLists = { withContext(Dispatchers.IO) { database.getLists() } },
            refresh = { list -> repository.refresh(list) },
        )
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val readThreadIds = LocalReadingHistory.current.keys
    var reload by remember { mutableIntStateOf(0) }
    var refreshingLists by remember { mutableStateOf(false) }
    var state: LoadState<List<CustomListOverviewItem>> by remember { mutableStateOf(LoadState.Loading) }
    LaunchedEffect(reload) {
        state = load {
            withContext(Dispatchers.IO) {
                val threadIdsByList = database.getThreadIdsByList()
                database.getLists().map { list ->
                    CustomListOverviewItem(list, threadIdsByList[list.id].orEmpty())
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        CustomListRefreshEvents.refreshedListIds.collect { reload++ }
    }

    ScreenScaffold(
        stringResource(R.string.list_title),
        onRefresh = {
            if (!refreshingLists) {
                refreshingLists = true
                coroutineScope.launch {
                    try {
                        refreshScheduler.refreshAllLists()
                    } finally {
                        refreshingLists = false
                        reload++
                    }
                }
            }
        },
        isRefreshing = refreshingLists,
        onTopBarDoubleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
        actions = {
            IconButton(onClick = onCreate) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.custom_list_create),
                )
            }
        },
    ) { padding ->
        when (val current = state) {
            LoadState.Loading -> Loading(Modifier.padding(padding))
            is LoadState.Failed -> EmptyState(
                stringResource(R.string.custom_list_load_failed),
                current.message,
                Modifier.padding(padding),
            )

            is LoadState.Ready -> CustomListOverview(
                lists = current.value,
                readThreadIds = readThreadIds,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onOpen = onOpen,
                modifier = Modifier.padding(padding),
                listState = listState,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CustomListOverview(
    lists: List<CustomListOverviewItem>,
    readThreadIds: Set<Int>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState,
) {
    Column(modifier.fillMaxSize()) {
        if (lists.isEmpty()) {
            EmptyState(
                stringResource(R.string.custom_list_empty_title),
                stringResource(R.string.custom_list_empty_message),
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(lists, key = { it.list.id }) { item ->
                    val list = item.list
                    val unreadCount = unreadCustomListThreadCount(item.threadIds, readThreadIds)
                    Card(
                        onClick = { onOpen(list.id) },
                        modifier = with(sharedTransitionScope) {
                            Modifier
                                .fillMaxWidth()
                                .sharedBounds(
                                    rememberSharedContentState("custom-list-${list.id}"),
                                    animatedVisibilityScope,
                                )
                        },
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    list.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(
                                        R.string.custom_list_search_summary,
                                        stringResource(list.searchType.labelResource()),
                                        list.keywords.joinToString(" · "),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(
                                        R.string.custom_list_counts,
                                        list.threadCount,
                                        list.excludedCount,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                modifier = Modifier.padding(start = 16.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        unreadCount.toString(),
                                        style = MaterialTheme.typography.headlineSmall,
                                    )
                                    Text(
                                        stringResource(R.string.custom_list_unread_label),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal data class CustomListOverviewItem(
    val list: CustomThreadList,
    val threadIds: Set<Int>,
)

internal fun unreadCustomListThreadCount(
    threadIds: Set<Int>,
    readThreadIds: Set<Int>,
): Int = threadIds.count { it !in readThreadIds }

private fun YamiboThreadSearchType.labelResource() = when (this) {
    YamiboThreadSearchType.KEYWORD -> R.string.custom_list_search_keyword
    YamiboThreadSearchType.TITLE -> R.string.custom_list_search_title
    YamiboThreadSearchType.USER_ID -> R.string.custom_list_search_user_id
}
