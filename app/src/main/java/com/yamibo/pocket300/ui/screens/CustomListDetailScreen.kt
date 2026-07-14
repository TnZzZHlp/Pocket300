package com.yamibo.pocket300.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.data.CustomListDatabase
import com.yamibo.pocket300.data.CustomListRepository
import com.yamibo.pocket300.data.CustomListSyncProgress
import com.yamibo.pocket300.data.CustomListThread
import com.yamibo.pocket300.data.CustomThreadList
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.Loading
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CustomListDetailScreen(
    listId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onThread: (CustomListThread) -> Unit,
) {
    val context = LocalContext.current
    val database = remember(context) { CustomListDatabase.getInstance(context) }
    val repository = remember(database) { CustomListRepository(database, api.search) }
    val scope = rememberCoroutineScope()
    val syncFailedMessage = stringResource(R.string.custom_list_sync_failed)
    var list by remember(listId) { mutableStateOf<CustomThreadList?>(null) }
    var threads by remember(listId) { mutableStateOf<List<CustomListThread>>(emptyList()) }
    var loading by remember(listId) { mutableStateOf(true) }
    var syncing by remember(listId) { mutableStateOf(false) }
    var progress by remember(listId) { mutableStateOf<CustomListSyncProgress?>(null) }
    var error by remember(listId) { mutableStateOf<String?>(null) }

    suspend fun loadLocal(): CustomThreadList? = withContext(Dispatchers.IO) {
        database.getList(listId).also { loaded ->
            val loadedThreads = if (loaded == null) emptyList() else database.getThreads(listId)
            withContext(Dispatchers.Main) {
                list = loaded
                threads = loadedThreads
                loading = false
            }
        }
    }

    suspend fun sync(target: CustomThreadList) {
        if (syncing) return
        syncing = true
        error = null
        progress = null
        runCatching {
            repository.refresh(target) { progress = it }
        }.onFailure {
            error = it.message ?: syncFailedMessage
        }
        loadLocal()
        syncing = false
        progress = null
    }

    LaunchedEffect(listId) {
        val loaded = loadLocal()
        if (loaded != null && loaded.lastSyncedAt == null) sync(loaded)
    }

    ScreenScaffold(
        title = list?.name ?: stringResource(R.string.list_title),
        onBack = onBack,
        onRefresh = if (syncing || list == null) null else ({ scope.launch { sync(list ?: return@launch) } }),
        onSettings = onEdit,
    ) { padding ->
        when {
            loading -> Loading(Modifier.padding(padding))
            list == null -> EmptyState(
                stringResource(R.string.custom_list_not_found),
                stringResource(R.string.custom_list_not_found_message),
                Modifier.padding(padding),
            )
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                if (syncing) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.padding(4.dp))
                        Text(
                            progress?.let {
                                stringResource(
                                    R.string.custom_list_sync_progress,
                                    it.keywordIndex,
                                    it.keywordCount,
                                    it.keyword,
                                    it.page,
                                    it.totalPages ?: it.page,
                                )
                            } ?: stringResource(R.string.custom_list_sync_starting),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (threads.isEmpty() && !syncing) {
                    EmptyState(
                        stringResource(R.string.custom_list_no_threads),
                        stringResource(R.string.custom_list_no_threads_message),
                        Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(threads, key = CustomListThread::threadId) { thread ->
                            CustomListThreadCard(
                                thread = thread,
                                onClick = onThread,
                                onExclude = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            database.excludeThread(listId, thread.threadId)
                                        }
                                        threads = threads.filterNot { it.threadId == thread.threadId }
                                        list = list?.copy(
                                            threadCount = (list?.threadCount ?: 1).minus(1).coerceAtLeast(0),
                                            excludedCount = (list?.excludedCount ?: 0) + 1,
                                        )
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

@Composable
private fun CustomListThreadCard(
    thread: CustomListThread,
    onClick: (CustomListThread) -> Unit,
    onExclude: () -> Unit,
) {
    Card(onClick = { onClick(thread) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(
                Modifier.weight(1f).padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    thread.forumName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    thread.subject,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
                    stringResource(
                        R.string.custom_list_thread_metadata,
                        thread.authorName,
                        thread.createdAtText,
                        thread.replyCount,
                        thread.viewCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Box(Modifier.padding(4.dp)) {
                IconButton(onClick = onExclude) {
                    Icon(
                        Icons.Rounded.Block,
                        contentDescription = stringResource(R.string.custom_list_exclude_thread),
                    )
                }
            }
        }
    }
}
