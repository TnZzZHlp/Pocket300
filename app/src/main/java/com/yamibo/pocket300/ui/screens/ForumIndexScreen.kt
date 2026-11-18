package com.yamibo.pocket300.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.YamiboForum
import com.yamibo.pocket300.api.YamiboForumIndex
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.theme.PocketSpacing
import com.yamibo.pocket300.ui.theme.pocketCardBorder
import com.yamibo.pocket300.ui.theme.pocketCardColors
import com.yamibo.pocket300.ui.viewmodels.ForumIndexViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ForumIndexScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    authStateVersion: Int,
    scrollToTopTrigger: Int = 0,
    onForum: (YamiboForum) -> Unit,
    onSearch: (() -> Unit)?,
) {
    val viewModel: ForumIndexViewModel = viewModel()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(authStateVersion) {
        if (authStateVersion > 0) viewModel.refresh(showRefreshIndicator = false)
    }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
    }
    ScreenScaffold(
        title = stringResource(R.string.home_title),
        onRefresh = { viewModel.refresh() },
        isRefreshing = viewModel.isRefreshing,
        onSearch = onSearch,
        onTopBarDoubleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
    ) { padding ->
        LoadContent(viewModel.state, padding, onRetry = { viewModel.refresh() }) { index ->
            ForumIndexContent(
                index = index,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onForum = onForum,
                listState = listState,
            )
        }
    }
}

internal fun toggleCollapsedCategory(collapsedIds: Set<Int>, categoryId: Int): Set<Int> =
    if (categoryId in collapsedIds) collapsedIds - categoryId else collapsedIds + categoryId

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ForumIndexContent(
    index: YamiboForumIndex,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onForum: (YamiboForum) -> Unit,
    listState: LazyListState,
) {
    var collapsedCategoryIds by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    val collapsedSet = remember(collapsedCategoryIds) { collapsedCategoryIds.toSet() }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = PocketSpacing.screen, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.itemGap),
    ) {
        index.categories.forEachIndexed { categoryIndex, category ->
            val collapsed = category.id in collapsedSet
            item(key = "category-${category.id}") {
                CategoryHeader(
                    name = category.name,
                    count = category.forums.size,
                    collapsed = collapsed,
                    onToggle = {
                        collapsedCategoryIds = toggleCollapsedCategory(collapsedSet, category.id).toList()
                    },
                    modifier = Modifier.padding(top = if (categoryIndex == 0) 0.dp else 14.dp),
                )
            }
            if (!collapsed) {
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

@Composable
private fun CategoryHeader(
    name: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_forum_count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                contentDescription = stringResource(
                    if (collapsed) R.string.home_expand_category else R.string.home_collapse_category,
                    name,
                ),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ForumCard(
    forum: YamiboForum,
    onClick: (YamiboForum) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { onClick(forum) },
        modifier = modifier.fillMaxWidth(),
        colors = pocketCardColors(),
        shape = MaterialTheme.shapes.medium,
        border = pocketCardBorder(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = forum.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (forum.todayPostCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        TodayBadge(forum.todayPostCount)
                    }
                }
                if (forum.description.isNotBlank()) {
                    Text(
                        text = forum.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.home_forum_stats,
                        formatCount(forum.threadCount),
                        formatCount(forum.postCount),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = stringResource(R.string.home_open_forum, forum.name),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun TodayBadge(count: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(R.string.home_today_badge, formatCount(count)),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value)
