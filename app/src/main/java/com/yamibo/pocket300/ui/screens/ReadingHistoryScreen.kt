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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.data.ReadingHistoryDatabase
import com.yamibo.pocket300.data.ReadingHistoryEntry
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.components.LocalSearchField
import com.yamibo.pocket300.ui.components.ThreadCardContent
import com.yamibo.pocket300.ui.components.ThreadCardSurface
import com.yamibo.pocket300.ui.components.matchesLocalSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val entriesMap by database.entries.collectAsState()
    val entries = remember(entriesMap) {
        entriesMap.values.sortedByDescending(ReadingHistoryEntry::readAt)
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var clearAllPending by remember { mutableStateOf(false) }

    fun closeSearch() {
        searchActive = false
        searchQuery = ""
    }

    BackHandler(enabled = searchActive, onBack = ::closeSearch)

    ScreenScaffold(
        stringResource(R.string.profile_reading_history),
        onBack = if (searchActive) ::closeSearch else onBack,
        onSearch = if (!searchActive && entries.isNotEmpty()) {
            { searchActive = true }
        } else {
            null
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
            } else if (entries.isNotEmpty()) {
                IconButton(onClick = { clearAllPending = true }) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        contentDescription = stringResource(R.string.history_clear_all),
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
                        EmptyState(stringResource(R.string.history_empty_title), message = "")
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
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            items(filteredEntries, key = { it.threadId }) { entry ->
                                ReadingHistoryCard(
                                    entry = entry,
                                    onClick = onThread,
                                    onDelete = { target ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            database.remove(target.threadId)
                                        }
                                    },
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

    if (clearAllPending) {
        AlertDialog(
            onDismissRequest = { clearAllPending = false },
            title = { Text(stringResource(R.string.history_clear_all_title)) },
            text = {
                Text(stringResource(R.string.history_clear_all_message, entries.size))
            },
            dismissButton = {
                TextButton(onClick = { clearAllPending = false }) {
                    Text(stringResource(R.string.logs_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearAllPending = false
                        closeSearch()
                        coroutineScope.launch(Dispatchers.IO) {
                            database.clearAll()
                        }
                    },
                ) {
                    Text(stringResource(R.string.history_clear_confirm))
                }
            },
        )
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
    onDelete: (ReadingHistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val readAtText = remember(entry.readAt) {
        Instant.ofEpochMilli(entry.readAt).atZone(ZoneId.systemDefault()).format(historyTimeFormatter)
    }
    ThreadCardSurface(onClick = { onClick(entry) }, modifier = modifier) {
        ThreadCardContent(
            subject = entry.subject,
            threadId = entry.threadId,
            author = YamiboPostAuthor(entry.authorAvatarUrl, entry.authorId, entry.authorName),
            dateText = stringResource(R.string.history_read_at, readAtText),
            resolveMissingAuthor = true,
            supporting = entry.lastPostAtText.takeIf(String::isNotBlank)?.let {
                stringResource(R.string.history_last_reply, it)
            },
            readingFloor = entry.lastReadFloor,
            headerTrailing = {
                IconButton(
                    onClick = { onDelete(entry) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.history_delete_item),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
