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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.YamiboThreadSearchType
import com.yamibo.pocket300.data.CustomListDatabase
import com.yamibo.pocket300.data.CustomListRefreshEvents
import com.yamibo.pocket300.data.CustomThreadList
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.Loading
import com.yamibo.pocket300.ui.ScreenScaffold
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
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var state: LoadState<List<CustomThreadList>> by remember { mutableStateOf(LoadState.Loading) }
    LaunchedEffect(reload) {
        state = load { withContext(Dispatchers.IO) { database.getLists() } }
    }
    LaunchedEffect(Unit) {
        CustomListRefreshEvents.refreshedListIds.collect { reload++ }
    }

    ScreenScaffold(
        stringResource(R.string.list_title),
        onRefresh = { reload++ },
        onTopBarDoubleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
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
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onCreate = onCreate,
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
    lists: List<CustomThreadList>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = onCreate) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.custom_list_create), Modifier.padding(start = 8.dp))
            }
        }
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
                items(lists, key = CustomThreadList::id) { list ->
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
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
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
                    }
                }
            }
        }
    }
}

private fun YamiboThreadSearchType.labelResource() = when (this) {
    YamiboThreadSearchType.KEYWORD -> R.string.custom_list_search_keyword
    YamiboThreadSearchType.TITLE -> R.string.custom_list_search_title
    YamiboThreadSearchType.USER_ID -> R.string.custom_list_search_user_id
}
