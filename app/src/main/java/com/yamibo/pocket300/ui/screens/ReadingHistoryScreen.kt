package com.yamibo.pocket300.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.data.ReadingHistoryDatabase
import com.yamibo.pocket300.data.ReadingHistoryEntry
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.load
import kotlinx.coroutines.Dispatchers
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
    var state: LoadState<List<ReadingHistoryEntry>> by remember { mutableStateOf(LoadState.Loading) }
    LaunchedEffect(reload) {
        state = load { withContext(Dispatchers.IO) { database.getAll() } }
    }
    ScreenScaffold("阅读历史", onBack = onBack, onRefresh = { reload++ }) { padding ->
        LoadContent(state, padding) { entries ->
            if (entries.isEmpty()) {
                EmptyState("还没有阅读记录", "打开主题后会自动记录在这里。")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(entries, key = { it.threadId }) { entry ->
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
            Text(
                "${entry.authorName} · 看到 #${entry.lastReadFloor} · 阅读于 $readAtText",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.lastPostAtText.isNotBlank()) {
                Text("最后回复 ${entry.lastPostAtText}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
