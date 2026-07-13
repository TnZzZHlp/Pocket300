package com.yamibo.pocket300.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yamibo.pocket300.api.YamiboForum
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.viewmodels.ForumIndexViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ForumIndexScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    authStateVersion: Int,
    onForum: (YamiboForum) -> Unit,
    onSearch: () -> Unit,
) {
    val viewModel: ForumIndexViewModel = viewModel()
    LaunchedEffect(authStateVersion) {
        if (authStateVersion > 0) viewModel.refresh()
    }
    ScreenScaffold("Pocket300", onRefresh = viewModel::refresh, onSearch = onSearch) { padding ->
        LoadContent(viewModel.state, padding) { index ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                index.categories.forEach { category ->
                    item { Text(category.name, style = MaterialTheme.typography.titleLarge) }
                    items(category.forums, key = { it.id }) { forum ->
                        ForumCard(
                            forum = forum,
                            onClick = onForum,
                            modifier = with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    rememberSharedContentState("forum-${forum.id}"),
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
private fun ForumCard(forum: YamiboForum, onClick: (YamiboForum) -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = { onClick(forum) }, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(forum.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                forum.description.ifBlank { "暂无简介" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("主题", forum.threadCount)
                Stat("帖子", forum.postCount)
                Stat("今日", forum.todayPostCount)
            }
        }
    }
}

@Composable
internal fun Stat(label: String, value: Int) = Column {
    Text(value.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
