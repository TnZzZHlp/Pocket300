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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.data.ReadingHistoryDatabase
import com.yamibo.pocket300.data.ReadingHistoryEntry
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.components.LastReadPosition
import com.yamibo.pocket300.ui.components.LocalSearchField
import com.yamibo.pocket300.ui.components.matchesLocalSearch
import com.yamibo.pocket300.ui.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ReadingHistoryScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onThread: (ReadingHistoryEntry) -> Unit,
) {
    val context = LocalContext.current
    val database = remember(context) { ReadingHistoryDatabase.getInstance(context) }
    var reload by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var state: LoadState<List<ReadingHistoryEntry>> by remember { mutableStateOf(LoadState.Loading) }

    fun closeSearch() {
        searchActive = false
        searchQuery = ""
    }

    BackHandler(enabled = searchActive, onBack = ::closeSearch)

    LaunchedEffect(reload) {
        try {
            state = load { withContext(Dispatchers.IO) { database.getAll() } }
        } finally {
            refreshing = false
        }
    }
    ScreenScaffold(
        "阅读历史",
        onBack = if (searchActive) ::closeSearch else onBack,
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
                    label = stringResource(R.string.history_search_label),
                    onQueryChange = { searchQuery = it },
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                LoadContent(state, PaddingValues(0.dp)) { entries ->
                    val filteredEntries = remember(entries, searchQuery) {
                        filterReadingHistoryEntries(entries, searchQuery)
                    }
                    LaunchedEffect(searchQuery) {
                        if (searchQuery.isNotBlank() && filteredEntries.isNotEmpty()) {
                            listState.scrollToItem(0)
                        }
                    }
                    when {
                        entries.isEmpty() -> {
                            EmptyState("还没有阅读记录", "打开主题后会自动记录在这里。")
                        }
                        filteredEntries.isEmpty() -> {
                            EmptyState(
                                stringResource(R.string.history_search_empty_title),
                                stringResource(R.string.history_search_empty_message, searchQuery.trim()),
                            )
                        }
                        else -> {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(filteredEntries, key = { it.threadId }) { entry ->
                                    ReadingHistoryCard(
                                        entry = entry,
                                        onClick = onThread,
                                        modifier = with(sharedTransitionScope) {
                                            Modifier.sharedBounds(
                                                rememberSharedContentState("thread-${entry.threadId}"),
                                                animatedVisibilityScope,
                                            )
                                        },
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

internal fun filterReadingHistoryEntries(
    entries: List<ReadingHistoryEntry>,
    query: String,
): List<ReadingHistoryEntry> = entries.filter { entry ->
    matchesLocalSearch(query, entry.subject, entry.authorName)
}

@Composable
private fun ReadingHistoryCard(
    entry: ReadingHistoryEntry,
    onClick: (ReadingHistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val readAtText = remember(entry.readAt) {
        Instant.ofEpochMilli(entry.readAt).atZone(ZoneId.systemDefault()).format(historyTimeFormatter)
    }
    Card(onClick = { onClick(entry) }, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                entry.subject,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            LastReadPosition(entry.lastReadFloor)
            Text(
                stringResource(R.string.history_thread_metadata, entry.authorName, readAtText),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.lastPostAtText.isNotBlank()) {
                Text("最后回复 ${entry.lastPostAtText}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
