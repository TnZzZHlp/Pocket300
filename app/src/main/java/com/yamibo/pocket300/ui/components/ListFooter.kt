package com.yamibo.pocket300.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

internal enum class ListFooterState { LOADING, LOAD_MORE, END }

internal fun listFooterState(isLoadingMore: Boolean, hasNextPage: Boolean): ListFooterState = when {
    isLoadingMore -> ListFooterState.LOADING
    hasNextPage -> ListFooterState.LOAD_MORE
    else -> ListFooterState.END
}

@Composable
internal fun ListFooter(
    count: Int,
    hasNextPage: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("已加载 $count 项", style = MaterialTheme.typography.labelMedium)
        when (listFooterState(isLoadingMore, hasNextPage)) {
            ListFooterState.LOADING -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
            ListFooterState.LOAD_MORE -> OutlinedButton(onClick = onLoadMore) {
                Text("加载下一页")
            }
            ListFooterState.END -> Text(
                "已经到底了",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun AutoLoadNextPage(
    listState: LazyListState,
    hasNextPage: Boolean,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, hasNextPage) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            hasNextPage &&
                listState.isScrollInProgress &&
                listState.lastScrolledForward &&
                lastVisibleIndex == layoutInfo.totalItemsCount - 1
        }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad) currentOnLoadMore()
            }
    }
}
