package com.yamibo.pocket300.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onTopBarDoubleClick: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) = Scaffold(
    modifier = modifier,
    topBar = {
        TopAppBar(
            modifier = if (onTopBarDoubleClick == null) {
                Modifier
            } else {
                Modifier.pointerInput(onTopBarDoubleClick) {
                    detectTapGestures(onDoubleTap = { onTopBarDoubleClick() })
                }
            },
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            },
            actions = {
                if (onSearch != null) IconButton(onSearch) { Icon(Icons.Rounded.Search, "搜索") }
                if (onRefresh != null) IconButton(onRefresh) { Icon(Icons.Rounded.Refresh, "刷新") }
                if (onSettings != null) {
                    IconButton(onSettings) {
                        Icon(Icons.Rounded.Settings, stringResource(R.string.settings_title))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
    },
    content = content,
)

@Composable
internal fun <T> LoadContent(state: LoadState<T>, padding: PaddingValues, content: @Composable (T) -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding)) {
        when (state) {
            LoadState.Loading -> Loading()
            is LoadState.Failed -> EmptyState("加载失败", state.message)
            is LoadState.Ready -> content(state.value)
        }
    }
}

@Composable
internal fun Loading(modifier: Modifier = Modifier) = Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
}

@Composable
internal fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) = Box(
    modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal suspend fun <T> load(block: suspend () -> T): LoadState<T> = try {
    LoadState.Ready(block())
} catch (error: Exception) {
    LoadState.Failed(error.message ?: "发生未知错误")
}
