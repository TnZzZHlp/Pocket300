package com.yamibo.pocket300.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.data.download.DownloadedThread
import com.yamibo.pocket300.data.download.ThreadDownloadKey
import com.yamibo.pocket300.data.download.ThreadDownloadPhase
import com.yamibo.pocket300.data.download.ThreadDownloadRepository
import com.yamibo.pocket300.data.download.ThreadDownloadStatus
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.Loading
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.components.LocalSearchField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val downloadSearchTermSeparator = Regex("\\s+")
private val defaultDownloadSizeUnits = arrayOf("KB", "MB", "GB", "TB")

internal data class DownloadListItem(
    val key: ThreadDownloadKey,
    val subject: String,
    val author: String,
    val postCount: Int,
    val imageCount: Int,
    val completedPages: Int,
    val totalPages: Int,
    val completedImages: Int,
    val sizeBytes: Long,
    val downloadedAt: Long,
    val phase: ThreadDownloadPhase,
    val queuePosition: Int? = null,
    val queuePaused: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun DownloadsScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onOpen: (DownloadedThread) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        ThreadDownloadRepository.getInstance(context.applicationContext)
    }
    val statuses by repository.statuses.collectAsState()
    val queueState by repository.queueState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val deleteFailedMessage = stringResource(R.string.downloads_delete_failed)
    val retryFailedMessage = stringResource(R.string.downloads_retry_failed)
    var initialized by remember(repository) { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<DownloadListItem?>(null) }
    var deleteAllPending by remember { mutableStateOf(false) }

    fun closeSearch() {
        searchActive = false
        searchQuery = ""
    }

    BackHandler(enabled = searchActive, onBack = ::closeSearch)

    LaunchedEffect(repository) {
        repository.awaitInitialized()
        initialized = true
    }

    val allItems = remember(statuses, queueState) {
        statuses.values.map { status ->
            status.toDownloadListItem(
                queuePosition = queueState.queuedPosition(status.key),
                queuePaused = queueState.isPaused && status.phase == ThreadDownloadPhase.QUEUED,
            )
        }
    }
    val downloadsByKey = remember(statuses) {
        statuses.values.mapNotNull(ThreadDownloadStatus::completed)
            .associateBy(DownloadedThread::key)
    }
    val visibleItems = remember(allItems, queueState.orderedKeys, searchQuery) {
        filterAndSortDownloads(allItems, searchQuery, queueState.orderedKeys)
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && visibleItems.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.downloads_title),
        onBack = if (searchActive) ::closeSearch else onBack,
        onSearch = if (!searchActive && allItems.isNotEmpty()) {
            { searchActive = true }
        } else {
            null
        },
        onTopBarDoubleClick = {
            coroutineScope.launch {
                if (visibleItems.isNotEmpty()) listState.animateScrollToItem(0)
            }
        },
        actions = {
            if (searchActive) {
                IconButton(onClick = ::closeSearch) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.downloads_cancel),
                    )
                }
            }
            if (queueState.orderedKeys.isNotEmpty()) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            if (queueState.isPaused) {
                                repository.resumeDownloads()
                            } else {
                                repository.pauseDownloads()
                            }
                        }
                    },
                ) {
                    Icon(
                        if (queueState.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = stringResource(
                            if (queueState.isPaused) {
                                R.string.downloads_resume
                            } else {
                                R.string.downloads_pause
                            },
                        ),
                    )
                }
            }
            IconButton(
                enabled = allItems.isNotEmpty(),
                onClick = { deleteAllPending = true },
            ) {
                Icon(
                    Icons.Rounded.DeleteSweep,
                    contentDescription = stringResource(R.string.downloads_delete_all),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(searchActive) {
                LocalSearchField(
                    query = searchQuery,
                    label = stringResource(R.string.downloads_search_label),
                    onQueryChange = { searchQuery = it },
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    !initialized -> Loading()

                    allItems.isEmpty() -> {
                        EmptyState(
                            stringResource(R.string.downloads_empty_title),
                            stringResource(R.string.downloads_empty_message),
                        )
                    }

                    visibleItems.isEmpty() -> {
                        EmptyState(
                            stringResource(R.string.downloads_search_empty_title),
                            stringResource(
                                R.string.downloads_search_empty_message,
                                searchQuery.trim(),
                            ),
                        )
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = visibleItems,
                                key = { it.key.threadId },
                            ) { item ->
                                val completed = item.phase == ThreadDownloadPhase.COMPLETED
                                val cardModifier = if (completed) {
                                    with(sharedTransitionScope) {
                                        Modifier.sharedBounds(
                                            rememberSharedContentState(
                                                threadSharedContentKey(item.key.threadId),
                                            ),
                                            animatedVisibilityScope,
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                                DownloadCard(
                                    item = item,
                                    modifier = cardModifier,
                                    onOpen = {
                                        downloadsByKey[item.key]?.let(onOpen)
                                    },
                                    onDelete = { pendingDelete = item },
                                    onPrioritize = if (
                                        item.phase == ThreadDownloadPhase.QUEUED &&
                                        item.queuePosition != null &&
                                        item.queuePosition > 1
                                    ) {
                                        {
                                            coroutineScope.launch {
                                                repository.prioritize(item.key)
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    onRetry = {
                                        coroutineScope.launch {
                                            try {
                                                if (!repository.retry(item.key)) {
                                                    Toast.makeText(
                                                        context,
                                                        retryFailedMessage,
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (_: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    retryFailedMessage,
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
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

    pendingDelete?.let { item ->
        val isCompleted = item.phase == ThreadDownloadPhase.COMPLETED
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    stringResource(
                        if (isCompleted) {
                            R.string.downloads_delete_title
                        } else {
                            R.string.downloads_remove_task_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (isCompleted) {
                            R.string.downloads_delete_message
                        } else {
                            R.string.downloads_remove_task_message
                        },
                    ),
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.downloads_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        coroutineScope.launch {
                            try {
                                repository.delete(item.key)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    deleteFailedMessage,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (isCompleted) {
                                R.string.downloads_confirm_delete
                            } else {
                                R.string.downloads_confirm_remove
                            },
                        ),
                    )
                }
            },
        )
    }

    if (deleteAllPending) {
        AlertDialog(
            onDismissRequest = { deleteAllPending = false },
            title = { Text(stringResource(R.string.downloads_delete_all_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.downloads_delete_all_message,
                        allItems.size,
                    ),
                )
            },
            dismissButton = {
                TextButton(onClick = { deleteAllPending = false }) {
                    Text(stringResource(R.string.downloads_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteAllPending = false
                        closeSearch()
                        coroutineScope.launch {
                            try {
                                repository.deleteAll()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    deleteFailedMessage,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.downloads_confirm_delete))
                }
            },
        )
    }
}

@Composable
private fun DownloadCard(
    item: DownloadListItem,
    modifier: Modifier,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onPrioritize: (() -> Unit)?,
    onRetry: () -> Unit,
) {
    val timePattern = stringResource(R.string.downloads_time_pattern)
    val byteFormat = stringResource(R.string.downloads_size_bytes_format)
    val sizeValueFormat = stringResource(R.string.downloads_size_value_format)
    val sizeUnits = stringArrayResource(R.array.downloads_size_units)
    val eventTime = remember(item.downloadedAt, timePattern) {
        formatDownloadTime(item.downloadedAt, pattern = timePattern)
    }
    val completed = item.phase == ThreadDownloadPhase.COMPLETED
    val openDescription = stringResource(R.string.downloads_open_item, item.subject)
    val actionModifier = if (completed) {
        Modifier
            .clickable(onClick = onOpen)
            .semantics { contentDescription = openDescription }
    } else {
        Modifier
    }

    Card(modifier = modifier.fillMaxWidth().then(actionModifier)) {
        Column(
            Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.subject,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.phase == ThreadDownloadPhase.FAILED) {
                    IconButton(onClick = onRetry) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.downloads_retry),
                        )
                    }
                }
                onPrioritize?.let { prioritize ->
                    IconButton(onClick = prioritize) {
                        Icon(
                            Icons.Rounded.VerticalAlignTop,
                            contentDescription = stringResource(R.string.downloads_prioritize),
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(
                            if (completed) {
                                R.string.downloads_delete
                            } else {
                                R.string.downloads_cancel_download
                            },
                        ),
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.downloads_item_metadata,
                    item.author,
                    item.postCount,
                    item.imageCount,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = downloadStatusText(item),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = if (item.phase == ThreadDownloadPhase.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (completed) {
                Text(
                    text = formatDownloadSize(
                        sizeBytes = item.sizeBytes,
                        byteFormat = byteFormat,
                        valueFormat = sizeValueFormat,
                        units = sizeUnits,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    if (completed) {
                        R.string.downloads_saved_at
                    } else {
                        R.string.downloads_requested_at
                    },
                    eventTime,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun downloadStatusText(item: DownloadListItem): String = when (item.phase) {
    ThreadDownloadPhase.QUEUED -> when {
        item.queuePaused -> stringResource(R.string.downloads_status_paused)
        item.queuePosition != null -> stringResource(
            R.string.downloads_status_queued_position,
            item.queuePosition,
        )
        else -> stringResource(R.string.downloads_status_queued)
    }
    ThreadDownloadPhase.FETCHING_PAGES -> {
        if (item.totalPages > 0) {
            stringResource(
                R.string.downloads_status_fetching_pages_progress,
                item.completedPages,
                item.totalPages,
            )
        } else {
            stringResource(R.string.downloads_status_fetching_pages)
        }
    }
    ThreadDownloadPhase.DOWNLOADING_IMAGES -> {
        if (item.imageCount > 0) {
            stringResource(
                R.string.downloads_status_downloading_images,
                item.completedImages,
                item.imageCount,
            )
        } else {
            stringResource(R.string.downloads_status_saving)
        }
    }
    ThreadDownloadPhase.FAILED -> stringResource(R.string.downloads_status_failed)
    ThreadDownloadPhase.COMPLETED -> stringResource(R.string.downloads_status_completed)
}

internal fun filterAndSortDownloads(
    downloads: List<DownloadListItem>,
    query: String,
    queueOrder: List<ThreadDownloadKey> = emptyList(),
): List<DownloadListItem> {
    val terms = query
        .trim()
        .takeIf(String::isNotEmpty)
        ?.split(downloadSearchTermSeparator)
        .orEmpty()
    val queueIndexes = queueOrder.withIndex().associate { (index, key) -> key to index }
    return downloads
        .filter { item ->
            val searchable = "${item.subject} ${item.author}"
            terms.all { term -> searchable.contains(term, ignoreCase = true) }
        }
        .sortedWith(
            compareBy<DownloadListItem> { item -> queueIndexes[item.key] ?: Int.MAX_VALUE }
                .thenByDescending(DownloadListItem::downloadedAt)
                .thenBy { it.key.threadId },
        )
}

internal fun formatDownloadSize(
    sizeBytes: Long,
    byteFormat: String = "%d B",
    valueFormat: String = "%.1f %s",
    units: Array<String> = defaultDownloadSizeUnits,
    locale: Locale = Locale.ROOT,
): String {
    require(units.isNotEmpty()) { "At least one download size unit is required" }
    val safeSize = sizeBytes.coerceAtLeast(0)
    if (safeSize < 1_024) return byteFormat.format(locale, safeSize)
    var value = safeSize.toDouble()
    var unitIndex = -1
    do {
        value /= 1_024
        unitIndex++
    } while (value >= 1_024 && unitIndex < units.lastIndex)
    return valueFormat.format(locale, value, units[unitIndex])
}

internal fun formatDownloadTime(
    downloadedAt: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    pattern: String = "yyyy-MM-dd HH:mm",
): String = Instant.ofEpochMilli(downloadedAt)
    .atZone(zoneId)
    .format(DateTimeFormatter.ofPattern(pattern))

internal fun threadSharedContentKey(threadId: Int): String = "thread-$threadId"

private fun ThreadDownloadStatus.toDownloadListItem(
    queuePosition: Int?,
    queuePaused: Boolean,
) = DownloadListItem(
    key = key,
    subject = thread.subject,
    author = thread.author.name,
    postCount = completed?.snapshot?.posts?.size ?: (thread.replyCount + 1).coerceAtLeast(1),
    imageCount = progress.totalImages,
    completedPages = progress.completedPages,
    totalPages = progress.totalPages,
    completedImages = progress.completedImages,
    sizeBytes = completed?.sizeBytes ?: progress.downloadedBytes,
    downloadedAt = completed?.manifest?.completedAt ?: request?.requestedAt ?: 0L,
    phase = phase,
    queuePosition = queuePosition,
    queuePaused = queuePaused,
)
