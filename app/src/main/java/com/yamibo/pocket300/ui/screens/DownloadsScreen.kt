package com.yamibo.pocket300.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.data.download.DownloadedPost
import com.yamibo.pocket300.data.download.PostDownloadKey
import com.yamibo.pocket300.data.download.PostDownloadPhase
import com.yamibo.pocket300.data.download.PostDownloadRepository
import com.yamibo.pocket300.data.download.PostDownloadStatus
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
    val key: PostDownloadKey,
    val subject: String,
    val author: String,
    val floor: Int,
    val isOriginalPost: Boolean,
    val hasText: Boolean,
    val imageCount: Int,
    val completedImages: Int,
    val sizeBytes: Long,
    val downloadedAt: Long,
    val phase: PostDownloadPhase,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsScreen(
    onBack: () -> Unit,
    onOpen: (DownloadedPost) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        PostDownloadRepository.getInstance(context.applicationContext)
    }
    val statuses by repository.statuses.collectAsState()
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

    val allItems = remember(statuses) {
        statuses.values.map(PostDownloadStatus::toDownloadListItem)
    }
    val downloadsByKey = remember(statuses) {
        statuses.values.mapNotNull(PostDownloadStatus::completed)
            .associateBy(DownloadedPost::key)
    }
    val visibleItems = remember(allItems, searchQuery) {
        filterAndSortDownloads(
            allItems,
            searchQuery,
        )
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
                    !initialized -> {
                        Loading()
                    }

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
                                key = { "${it.key.threadId}-${it.key.postId}" },
                            ) { item ->
                                DownloadCard(
                                    item = item,
                                    onOpen = {
                                        downloadsByKey[item.key]?.let(onOpen)
                                    },
                                    onDelete = { pendingDelete = item },
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
        val isCompleted = item.phase == PostDownloadPhase.COMPLETED
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
                    Text(stringResource(R.string.downloads_confirm_delete))
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
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    val contentType = when {
        item.imageCount > 0 && item.hasText ->
            stringResource(R.string.downloads_mixed_post, item.imageCount)
        item.imageCount > 0 -> stringResource(R.string.downloads_image_post, item.imageCount)
        else -> stringResource(R.string.downloads_text_post)
    }
    val floor = if (item.isOriginalPost) {
        stringResource(R.string.reader_original_post)
    } else {
        stringResource(R.string.reader_floor, item.floor)
    }
    val timePattern = stringResource(R.string.downloads_time_pattern)
    val byteFormat = stringResource(R.string.downloads_size_bytes_format)
    val sizeValueFormat = stringResource(R.string.downloads_size_value_format)
    val sizeUnits = stringArrayResource(R.array.downloads_size_units)
    val eventTime = remember(item.downloadedAt, timePattern) {
        formatDownloadTime(item.downloadedAt, pattern = timePattern)
    }
    val openDescription = stringResource(R.string.downloads_open)
    val completed = item.phase == PostDownloadPhase.COMPLETED
    val cardModifier = if (completed) {
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = openDescription }
    } else {
        Modifier.fillMaxWidth()
    }

    Card(
        onClick = onOpen,
        enabled = completed,
        modifier = cardModifier,
    ) {
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
                if (item.phase == PostDownloadPhase.FAILED) {
                    IconButton(onClick = onRetry) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.downloads_retry),
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.downloads_delete),
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.downloads_item_metadata,
                    floor,
                    item.author,
                    contentType,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = when (item.phase) {
                    PostDownloadPhase.QUEUED -> stringResource(R.string.downloads_status_queued)
                    PostDownloadPhase.DOWNLOADING -> {
                        if (item.imageCount > 0) {
                            stringResource(
                                R.string.downloads_status_downloading,
                                item.completedImages,
                                item.imageCount,
                            )
                        } else {
                            stringResource(R.string.downloads_status_saving)
                        }
                    }
                    PostDownloadPhase.FAILED -> stringResource(R.string.downloads_status_failed)
                    PostDownloadPhase.COMPLETED -> formatDownloadSize(
                        sizeBytes = item.sizeBytes,
                        byteFormat = byteFormat,
                        valueFormat = sizeValueFormat,
                        units = sizeUnits,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (item.phase == PostDownloadPhase.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
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

internal fun filterAndSortDownloads(
    downloads: List<DownloadListItem>,
    query: String,
): List<DownloadListItem> {
    val terms = query.trim()
        .takeIf(String::isNotEmpty)
        ?.split(downloadSearchTermSeparator)
        .orEmpty()
    return downloads
        .filter { item ->
            terms.all { term ->
                item.subject.contains(term, ignoreCase = true) ||
                    item.author.contains(term, ignoreCase = true)
            }
        }
        .sortedWith(
            compareByDescending<DownloadListItem>(DownloadListItem::downloadedAt)
                .thenByDescending { it.key.threadId }
                .thenByDescending { it.key.postId },
        )
}

internal fun formatDownloadSize(
    sizeBytes: Long,
    byteFormat: String = "%d B",
    valueFormat: String = "%.1f %s",
    units: Array<String> = defaultDownloadSizeUnits,
): String {
    require(units.isNotEmpty()) { "At least one download size unit is required" }
    val safeBytes = sizeBytes.coerceAtLeast(0)
    if (safeBytes < 1_024) return String.format(Locale.ROOT, byteFormat, safeBytes)

    var value = safeBytes.toDouble()
    var unitIndex = -1
    while (value >= 1_024 && unitIndex < units.lastIndex) {
        value /= 1_024
        unitIndex++
    }
    return String.format(Locale.ROOT, valueFormat, value, units[unitIndex])
}

internal fun formatDownloadTime(
    downloadedAt: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    pattern: String = "yyyy-MM-dd HH:mm",
): String = Instant.ofEpochMilli(downloadedAt)
    .atZone(zoneId)
    .format(DateTimeFormatter.ofPattern(pattern))

private fun PostDownloadStatus.toDownloadListItem() = DownloadListItem(
    key = key,
    subject = snapshot.thread.subject,
    author = snapshot.post.author.name,
    floor = snapshot.post.number,
    isOriginalPost = snapshot.post.isOriginalPost,
    hasText = hasText,
    imageCount = progress.totalImages,
    completedImages = progress.completedImages,
    sizeBytes = completed?.sizeBytes ?: progress.downloadedBytes,
    downloadedAt = completed?.manifest?.completedAt ?: request?.requestedAt ?: 0L,
    phase = phase,
)
