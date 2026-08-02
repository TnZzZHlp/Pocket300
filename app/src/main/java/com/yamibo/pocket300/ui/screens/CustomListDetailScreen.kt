package com.yamibo.pocket300.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.GetThreadPostsInput
import com.yamibo.pocket300.data.CustomListAutoDownloadCoordinator
import com.yamibo.pocket300.data.CustomListDatabase
import com.yamibo.pocket300.data.CustomListRefreshEvents
import com.yamibo.pocket300.data.CustomListRefreshMode
import com.yamibo.pocket300.data.CustomListRepository
import com.yamibo.pocket300.data.CustomListSyncProgress
import com.yamibo.pocket300.data.CustomListThread
import com.yamibo.pocket300.data.CustomThreadList
import com.yamibo.pocket300.data.ReadingHistoryDatabase
import com.yamibo.pocket300.data.download.ThreadDownloadKey
import com.yamibo.pocket300.data.download.ThreadDownloadManager
import com.yamibo.pocket300.data.download.ThreadDownloadRequest
import com.yamibo.pocket300.logging.AppLogger
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.Loading
import com.yamibo.pocket300.ui.LocalReadingHistory
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.components.ThreadCardTitle
import com.yamibo.pocket300.ui.components.ThreadLastReadPosition
import com.yamibo.pocket300.ui.dimIfRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun CustomListDetailScreen(
    listId: Long,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onThread: (CustomListThread) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val database = remember(context) { CustomListDatabase.getInstance(context) }
    val historyDatabase = remember(context) { ReadingHistoryDatabase.getInstance(context) }
    val downloadRepository = remember(context) {
        ThreadDownloadManager.getInstance(context.applicationContext)
    }
    val autoDownloadCoordinator = remember(database, downloadRepository) {
        CustomListAutoDownloadCoordinator(
            database = database,
            downloadRepository = downloadRepository,
            loadThreadDetails = { threadId ->
                api.posts.getThreadPosts(GetThreadPostsInput(threadId)).thread
            },
        )
    }
    val repository = remember(database, autoDownloadCoordinator) {
        CustomListRepository(
            database = database,
            searchApi = api.search,
            onNewThreadsForAutoDownload = autoDownloadCoordinator::enqueueNewThreads,
        )
    }
    val downloadStatuses by downloadRepository.statuses.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val histories = LocalReadingHistory.current
    val syncFailedMessage = stringResource(R.string.custom_list_sync_failed)
    val bulkActionFailedMessage = stringResource(R.string.custom_list_bulk_action_failed)
    var list by remember(listId) { mutableStateOf<CustomThreadList?>(null) }
    var threads by remember(listId) { mutableStateOf<List<CustomListThread>>(emptyList()) }
    var loading by remember(listId) { mutableStateOf(true) }
    var syncing by remember(listId) { mutableStateOf(false) }
    var progress by remember(listId) { mutableStateOf<CustomListSyncProgress?>(null) }
    var error by remember(listId) { mutableStateOf<String?>(null) }
    var refreshMenuExpanded by remember(listId) { mutableStateOf(false) }
    var selectionMode by remember(listId) { mutableStateOf(false) }
    var selectedThreadIds by remember(listId) { mutableStateOf(emptySet<Int>()) }
    var selectionActionsMenuExpanded by remember(listId) { mutableStateOf(false) }
    var applyingSelectionAction by remember(listId) { mutableStateOf(false) }
    var downloadRepositoryReady by remember(downloadRepository) { mutableStateOf(false) }
    var bulkDownloadProgress by remember(listId) {
        mutableStateOf<CustomListBulkDownloadProgress?>(null)
    }
    var readFilter by rememberSaveable(listId) { mutableStateOf(ThreadReadFilter.UNREAD) }
    var publicationOrder by rememberSaveable(listId) {
        mutableStateOf(ThreadPublicationOrder.NEWEST_FIRST)
    }
    val displayedThreads = filterAndSortCustomListThreads(
        threads = threads,
        readThreadIds = histories.keys,
        readFilter = readFilter,
        publicationOrder = publicationOrder,
    )
    val displayedThreadIds = displayedThreads.map(CustomListThread::threadId)
    val selectedThreads = selectedCustomListThreadsInDisplayOrder(
        displayedThreads,
        selectedThreadIds,
    )
    val allDisplayedThreadsSelected = displayedThreadIds.isNotEmpty() &&
        selectedThreadIds.containsAll(displayedThreadIds)
    val hasDownloadableSelectedThread = selectedThreads.any { thread ->
        val status = downloadStatuses[ThreadDownloadKey(thread.threadId)]
        customListBulkDownloadAction(
            phase = status?.phase,
            hasCompletedDownload = status?.completed != null,
        ) != CustomListBulkDownloadAction.SKIP
    }

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

    suspend fun performSync(
        target: CustomThreadList,
        mode: CustomListRefreshMode = CustomListRefreshMode.REGULAR,
    ) {
        error = null
        progress = null
        runCatching {
            repository.refresh(target, mode) { progress = it }
        }.onFailure { failure ->
            if (failure is CancellationException) throw failure
            AppLogger.warn(TAG, failure) {
                "Custom list $listId refresh failed; mode=$mode"
            }
            error = failure.message ?: syncFailedMessage
        }
        loadLocal()
        syncing = false
        progress = null
    }

    suspend fun sync(
        target: CustomThreadList,
        mode: CustomListRefreshMode = CustomListRefreshMode.REGULAR,
    ) {
        if (syncing) return
        syncing = true
        performSync(target, mode)
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedThreadIds = emptySet()
        selectionActionsMenuExpanded = false
    }

    fun toggleSelection(threadId: Int) {
        selectedThreadIds = toggleThreadSelection(selectedThreadIds, threadId)
    }

    fun startSelection(threadId: Int) {
        selectionMode = true
        selectedThreadIds = selectedThreadIds + threadId
    }

    fun markSelectedRead() {
        val targets = selectedThreads.filter { it.threadId !in histories }
        if (targets.isEmpty() || applyingSelectionAction) return
        applyingSelectionAction = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { historyDatabase.markRead(targets) }
                exitSelectionMode()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                AppLogger.error(TAG, failure) {
                    "Could not mark ${targets.size} selected custom-list threads as read"
                }
                error = failure.message ?: bulkActionFailedMessage
            } finally {
                applyingSelectionAction = false
            }
        }
    }

    fun excludeSelected() {
        val targetIds = selectedThreads.map(CustomListThread::threadId)
        if (targetIds.isEmpty() || applyingSelectionAction) return
        applyingSelectionAction = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { database.excludeThreads(listId, targetIds) }
                loadLocal()
                exitSelectionMode()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                AppLogger.error(TAG, failure) {
                    "Could not exclude ${targetIds.size} selected threads from custom list $listId"
                }
                error = failure.message ?: bulkActionFailedMessage
            } finally {
                applyingSelectionAction = false
            }
        }
    }

    fun downloadSelected() {
        val targets = selectedThreads
        if (
            targets.isEmpty() ||
            applyingSelectionAction ||
            !downloadRepositoryReady
        ) {
            return
        }
        applyingSelectionAction = true
        bulkDownloadProgress = CustomListBulkDownloadProgress(0, targets.size)
        error = null
        scope.launch {
            try {
                downloadRepository.awaitInitialized()
                val result = enqueueCustomListThreadDownloads(
                    threads = targets,
                    actionFor = { threadId ->
                        val status = downloadRepository.statuses.value[ThreadDownloadKey(threadId)]
                        customListBulkDownloadAction(
                            phase = status?.phase,
                            hasCompletedDownload = status?.completed != null,
                        )
                    },
                    retry = { threadId ->
                        downloadRepository.retry(ThreadDownloadKey(threadId))
                    },
                    enqueueIfMissing = { thread ->
                        downloadRepository.enqueueIfMissing(
                            ThreadDownloadRequest.create(thread),
                        )
                    },
                    onProgress = { completedCount, totalCount ->
                        bulkDownloadProgress = CustomListBulkDownloadProgress(
                            completedCount,
                            totalCount,
                        )
                    },
                    onFailure = { threadId, failure ->
                        AppLogger.warn(TAG, failure) {
                            "Could not prepare custom-list thread $threadId for download"
                        }
                    },
                )
                val message = when {
                    result.failedCount > 0 -> resources.getString(
                        R.string.custom_list_download_selected_partial,
                        result.queuedCount,
                        result.skippedCount,
                        result.failedCount,
                    )
                    result.skippedCount > 0 -> resources.getString(
                        R.string.custom_list_download_selected_queued_with_skipped,
                        result.queuedCount,
                        result.skippedCount,
                    )
                    else -> resources.getString(
                        R.string.custom_list_download_selected_queued,
                        result.queuedCount,
                    )
                }
                Toast.makeText(
                    context,
                    message,
                    if (result.failedCount > 0) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
                ).show()
                if (result.failedCount == 0) {
                    exitSelectionMode()
                } else {
                    selectedThreadIds = result.failedThreadIds
                    error = message
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                AppLogger.error(TAG, failure) {
                    "Could not prepare ${targets.size} selected custom-list downloads"
                }
                error = failure.message ?: bulkActionFailedMessage
            } finally {
                bulkDownloadProgress = null
                applyingSelectionAction = false
            }
        }
    }

    BackHandler(enabled = selectionMode) {
        if (!applyingSelectionAction) exitSelectionMode()
    }

    LaunchedEffect(listId) {
        val loaded = loadLocal()
        if (loaded != null && loaded.lastSyncedAt == null) sync(loaded)
    }

    LaunchedEffect(downloadRepository) {
        try {
            downloadRepository.awaitInitialized()
            downloadRepositoryReady = true
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            AppLogger.error(TAG, failure) {
                "Could not initialize downloads for custom list $listId"
            }
            error = failure.message ?: bulkActionFailedMessage
        }
    }

    LaunchedEffect(listId) {
        CustomListRefreshEvents.refreshedListIds
            .filter { it == listId }
            .collect { loadLocal() }
    }

    LaunchedEffect(displayedThreadIds) {
        selectedThreadIds = selectedThreadIds.intersect(displayedThreadIds.toSet())
    }

    ScreenScaffold(
        modifier = with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState("custom-list-$listId"),
                animatedVisibilityScope,
            )
        },
        title = if (selectionMode) {
            stringResource(R.string.custom_list_selected_count, selectedThreadIds.size)
        } else {
            list?.name ?: stringResource(R.string.list_title)
        },
        onBack = when {
            selectionMode && applyingSelectionAction -> null
            selectionMode -> ::exitSelectionMode
            else -> onBack
        },
        onRefresh = if (selectionMode) null else list?.let { target ->
            {
                if (!syncing) {
                    syncing = true
                    scope.launch { performSync(target) }
                }
            }
        },
        isRefreshing = syncing,
        onSettings = if (selectionMode) null else onEdit,
        onTopBarDoubleClick = { scope.launch { listState.animateScrollToItem(0) } },
        actions = {
            if (selectionMode) {
                IconButton(
                    enabled = displayedThreadIds.isNotEmpty() && !applyingSelectionAction,
                    onClick = {
                        selectedThreadIds = toggleAllDisplayedThreads(
                            selectedThreadIds,
                            displayedThreadIds,
                        )
                    },
                ) {
                    Icon(
                        Icons.Rounded.SelectAll,
                        contentDescription = stringResource(
                            if (allDisplayedThreadsSelected) {
                                R.string.custom_list_clear_selection
                            } else {
                                R.string.custom_list_select_all
                            },
                        ),
                    )
                }
                IconButton(
                    enabled = downloadRepositoryReady &&
                        hasDownloadableSelectedThread &&
                        !applyingSelectionAction,
                    onClick = ::downloadSelected,
                ) {
                    val currentProgress = bulkDownloadProgress
                    if (currentProgress == null) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = stringResource(
                                R.string.custom_list_download_selected,
                            ),
                        )
                    } else {
                        val description = stringResource(
                            R.string.custom_list_download_preparing_progress,
                            currentProgress.completedCount,
                            currentProgress.totalCount,
                        )
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .semantics { contentDescription = description },
                            strokeWidth = 2.dp,
                        )
                    }
                }
                Box {
                    IconButton(
                        enabled = selectedThreads.isNotEmpty() && !applyingSelectionAction,
                        onClick = { selectionActionsMenuExpanded = true },
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(
                                R.string.custom_list_more_actions,
                            ),
                        )
                    }
                    DropdownMenu(
                        expanded = selectionActionsMenuExpanded,
                        onDismissRequest = { selectionActionsMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.custom_list_mark_selected_read))
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.DoneAll, contentDescription = null)
                            },
                            enabled = selectedThreads.any { it.threadId !in histories },
                            onClick = {
                                selectionActionsMenuExpanded = false
                                markSelectedRead()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.custom_list_exclude_selected))
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Block, contentDescription = null)
                            },
                            onClick = {
                                selectionActionsMenuExpanded = false
                                excludeSelected()
                            },
                        )
                    }
                }
            } else {
                Box {
                    IconButton(
                        enabled = !syncing && list != null,
                        onClick = { refreshMenuExpanded = true },
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.custom_list_more_actions),
                        )
                    }
                    DropdownMenu(
                        expanded = refreshMenuExpanded,
                        onDismissRequest = { refreshMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.custom_list_select_threads)) },
                            enabled = displayedThreads.isNotEmpty(),
                            onClick = {
                                refreshMenuExpanded = false
                                selectionMode = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.custom_list_full_refresh)) },
                            onClick = {
                                refreshMenuExpanded = false
                                scope.launch {
                                    sync(
                                        list ?: return@launch,
                                        CustomListRefreshMode.FULL,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            loading -> Loading(Modifier.padding(padding))
            list == null -> EmptyState(
                stringResource(R.string.custom_list_not_found),
                stringResource(R.string.custom_list_not_found_message),
                Modifier.padding(padding),
            )

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (syncing) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    Column(Modifier.weight(1f)) {
                        CustomListDisplayControls(
                            readFilter = readFilter,
                            onReadFilterChange = { readFilter = it },
                            publicationOrder = publicationOrder,
                            onPublicationOrderChange = { publicationOrder = it },
                        )
                        if (displayedThreads.isEmpty() && !syncing) {
                            EmptyState(
                                stringResource(R.string.custom_list_filter_empty_title),
                                stringResource(R.string.custom_list_filter_empty_message),
                                Modifier.weight(1f),
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(
                                    displayedThreads,
                                    key = CustomListThread::threadId
                                ) { thread ->
                                    CustomListThreadCard(
                                        thread = thread,
                                        onClick = onThread,
                                        selectionMode = selectionMode,
                                        selected = thread.threadId in selectedThreadIds,
                                        onToggleSelection = {
                                            toggleSelection(thread.threadId)
                                        },
                                        onStartSelection = {
                                            startSelection(thread.threadId)
                                        },
                                        interactionEnabled = !applyingSelectionAction,
                                        modifier = with(sharedTransitionScope) {
                                            Modifier.sharedBounds(
                                                rememberSharedContentState("thread-${thread.threadId}"),
                                                animatedVisibilityScope,
                                            )
                                        },
                                        onExclude = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    database.excludeThread(listId, thread.threadId)
                                                }
                                                threads = threads.filterNot {
                                                    it.threadId == thread.threadId
                                                }
                                                list = list?.copy(
                                                    threadCount = (list?.threadCount ?: 1)
                                                        .minus(1).coerceAtLeast(0),
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
    }
}

private const val TAG = "CustomListScreen"

@Composable
private fun CustomListDisplayControls(
    readFilter: ThreadReadFilter,
    onReadFilterChange: (ThreadReadFilter) -> Unit,
    publicationOrder: ThreadPublicationOrder,
    onPublicationOrderChange: (ThreadPublicationOrder) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.custom_list_read_filter),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = readFilter == ThreadReadFilter.UNREAD,
                    onClick = { onReadFilterChange(ThreadReadFilter.UNREAD) },
                    label = { Text(stringResource(R.string.custom_list_unread_only)) },
                )
            }
            item {
                FilterChip(
                    selected = readFilter == ThreadReadFilter.READ,
                    onClick = { onReadFilterChange(ThreadReadFilter.READ) },
                    label = { Text(stringResource(R.string.custom_list_read_only)) },
                )
            }
            item {
                FilterChip(
                    selected = readFilter == ThreadReadFilter.ALL,
                    onClick = { onReadFilterChange(ThreadReadFilter.ALL) },
                    label = { Text(stringResource(R.string.custom_list_read_all)) },
                )
            }
        }
        Text(
            stringResource(R.string.custom_list_publication_order),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = publicationOrder == ThreadPublicationOrder.NEWEST_FIRST,
                    onClick = { onPublicationOrderChange(ThreadPublicationOrder.NEWEST_FIRST) },
                    label = { Text(stringResource(R.string.custom_list_newest_first)) },
                )
            }
            item {
                FilterChip(
                    selected = publicationOrder == ThreadPublicationOrder.OLDEST_FIRST,
                    onClick = { onPublicationOrderChange(ThreadPublicationOrder.OLDEST_FIRST) },
                    label = { Text(stringResource(R.string.custom_list_oldest_first)) },
                )
            }
        }
    }
}

@Composable
private fun CustomListThreadCard(
    thread: CustomListThread,
    onClick: (CustomListThread) -> Unit,
    onExclude: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    interactionEnabled: Boolean,
    onToggleSelection: () -> Unit,
    onStartSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val histories = LocalReadingHistory.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .dimIfRead(thread.threadId, histories)
            .combinedClickable(
                enabled = interactionEnabled,
                onClick = {
                    if (selectionMode) onToggleSelection() else onClick(thread)
                },
                onLongClick = {
                    if (selectionMode) onToggleSelection() else onStartSelection()
                },
            ),
        colors = if (selected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    enabled = interactionEnabled,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(
                        start = if (selectionMode) 8.dp else 16.dp,
                        top = 16.dp,
                        bottom = 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    thread.forumName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                ThreadCardTitle(
                    subject = thread.subject,
                    threadId = thread.threadId,
                    maxLines = 2,
                )
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
                ThreadLastReadPosition(thread.threadId)
            }
            if (!selectionMode) {
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
}
