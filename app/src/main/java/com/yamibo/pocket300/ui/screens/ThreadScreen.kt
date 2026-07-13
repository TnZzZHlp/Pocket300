package com.yamibo.pocket300.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.api.GetThreadPostsInput
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboThreadPoll
import com.yamibo.pocket300.api.YamiboThreadPostsPage
import com.yamibo.pocket300.data.ReadingHistoryDatabase
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.PostHtml
import com.yamibo.pocket300.ui.PostLinkTarget
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.components.AutoLoadNextPage
import com.yamibo.pocket300.ui.components.ListFooter
import com.yamibo.pocket300.ui.load
import com.yamibo.pocket300.ui.plainText
import com.yamibo.pocket300.ui.resolvePostLink
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ThreadContent(val page: YamiboThreadPostsPage, val posts: List<YamiboPost>)


@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ThreadScreen(
    threadId: Int,
    initialFloor: Int,
    initialPostId: Int,
    initialPage: Int,
    initialFavoriteId: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onForum: (Int) -> Unit,
    onThread: (PostLinkTarget.Thread) -> Unit,
) {
    val context = LocalContext.current
    val historyDatabase = remember(context) { ReadingHistoryDatabase.getInstance(context) }
    var reload by remember { mutableStateOf(0) }
    var pageNumber by remember(threadId, initialPostId, initialPage) {
        mutableStateOf(if (initialPostId > 0) initialPage.coerceAtLeast(1) else 1)
    }
    var targetFloor by remember(threadId, initialFloor) { mutableStateOf(initialFloor) }
    var targetPostId by remember(threadId, initialPostId) { mutableStateOf(initialPostId) }
    var state: LoadState<ThreadContent> by remember { mutableStateOf(LoadState.Loading) }
    val listState = rememberLazyListState()
    var restoredFloor by remember(threadId, initialFloor, initialPostId) {
        mutableStateOf(initialFloor <= 0 && initialPostId <= 0)
    }
    var favoriteId by remember(threadId, initialFavoriteId) {
        mutableStateOf(initialFavoriteId.takeIf { it > 0 })
    }
    var isFavorited by remember(threadId, initialFavoriteId) { mutableStateOf(initialFavoriteId > 0) }
    var favoriteBusy by remember(threadId) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(threadId, reload, pageNumber) {
        val previous = (state as? LoadState.Ready)?.value
        state = if (pageNumber == 1) LoadState.Loading else state
        when (val result = load { api.posts.getThreadPosts(GetThreadPostsInput(threadId, pageNumber)) }) {
            is LoadState.Ready -> state = LoadState.Ready(
                ThreadContent(
                    result.value,
                    if (pageNumber == 1) result.value.posts
                    else (previous?.posts.orEmpty() + result.value.posts).distinctBy { it.id },
                ),
            )
            is LoadState.Failed -> state = result
            LoadState.Loading -> Unit
        }
    }
    val loadedContent = (state as? LoadState.Ready)?.value
    val loadedThread = loadedContent?.page?.thread
    LaunchedEffect(loadedContent, targetFloor, targetPostId, restoredFloor) {
        val content = loadedContent ?: return@LaunchedEffect
        if (restoredFloor) return@LaunchedEffect
        val postIndex = content.posts.indexOfFirst {
            if (targetPostId > 0) it.id == targetPostId else it.number == targetFloor
        }
        if (postIndex >= 0) {
            val headerCount = 1 + if (content.page.poll == null) 0 else 1
            listState.scrollToItem(headerCount + postIndex)
            restoredFloor = true
        } else if (targetPostId <= 0 && pageNumber == 1) {
            pageNumber = ((targetFloor - 1) / content.page.pagination.pageSize) + 1
        } else {
            when (val resolved = load { api.posts.findPostPage(threadId, targetPostId) }) {
                is LoadState.Ready -> {
                    val resolvedPage = resolved.value
                    if (resolvedPage != null && resolvedPage != pageNumber) {
                        pageNumber = resolvedPage
                    } else {
                        restoredFloor = true
                    }
                }
                is LoadState.Failed -> restoredFloor = true
                LoadState.Loading -> Unit
            }
        }
    }
    LaunchedEffect(loadedThread?.id, loadedThread?.subject) {
        loadedThread?.let { thread ->
            withContext(Dispatchers.IO) { historyDatabase.record(thread, initialFloor.coerceAtLeast(1)) }
        }
    }
    LaunchedEffect(listState, loadedContent, restoredFloor) {
        val content = loadedContent ?: return@LaunchedEffect
        if (!restoredFloor) return@LaunchedEffect
        snapshotFlow {
            val visiblePostIds = listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Int }.toSet()
            content.posts.filter { it.id in visiblePostIds }.maxOfOrNull { it.number }
        }
            .distinctUntilChanged()
            .collectLatest { floor ->
                val thread = loadedThread ?: return@collectLatest
                floor ?: return@collectLatest
                delay(300)
                withContext(Dispatchers.IO) { historyDatabase.record(thread, floor) }
            }
    }
    ScreenScaffold(
        modifier = with(sharedTransitionScope) {
            Modifier.sharedBounds(rememberSharedContentState("thread-$threadId"), animatedVisibilityScope)
        },
        title = (state as? LoadState.Ready)?.value?.page?.thread?.subject ?: "主题",
        onBack = onBack,
        onRefresh = { pageNumber = 1; reload++ },
    ) { padding ->
        LoadContent(state, padding) { content ->
            val page = content.page
            AutoLoadNextPage(
                listState = listState,
                hasNextPage = page.pagination.hasNextPage,
                onLoadMore = { pageNumber = page.pagination.page + 1 },
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    ThreadHero(
                        page = page,
                        isFavorited = isFavorited,
                        favoriteBusy = favoriteBusy,
                        onFavorite = {
                            if (!favoriteBusy) {
                                favoriteBusy = true
                                coroutineScope.launch {
                                    val wasFavorited = isFavorited
                                    val currentFavoriteId = favoriteId
                                    val result = if (wasFavorited) {
                                        load {
                                            val id = currentFavoriteId
                                                ?: api.favorites.getFavoriteThreads()
                                                    .firstOrNull { it.threadId == threadId }
                                                    ?.favoriteId
                                                ?: error("未找到收藏记录，请刷新后重试")

                                            api.favorites.removeThread(id)
                                        }
                                    } else {
                                        load { api.favorites.addThread(threadId) }
                                    }
                                    when (result) {
                                        is LoadState.Ready -> {
                                            isFavorited = !wasFavorited
                                            if (wasFavorited) favoriteId = null
                                            Toast.makeText(
                                                context,
                                                if (wasFavorited) "已取消收藏" else "已收藏",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        is LoadState.Failed -> Toast.makeText(
                                            context,
                                            result.message,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        LoadState.Loading -> Unit
                                    }
                                    favoriteBusy = false
                                }
                            }
                        },
                    )
                }
                page.poll?.let { poll -> item { PollCard(poll) } }
                items(content.posts, key = { it.id }, contentType = { "post" }) { post ->
                    PostCard(
                        post = post,
                        onForum = onForum,
                        onThread = { target ->
                            if (target.id == threadId && target.postId != null) {
                                targetFloor = 0
                                targetPostId = target.postId
                                restoredFloor = false
                                pageNumber = target.page?.coerceAtLeast(1) ?: 1
                            } else {
                                onThread(target)
                            }
                        },
                    )
                }
                item {
                    ListFooter(
                        count = content.posts.size,
                        hasNextPage = page.pagination.hasNextPage,
                        onLoadMore = { pageNumber = page.pagination.page + 1 },
                    )
                }
            }
        }
    }
}

@Composable
private fun PostCard(
    post: YamiboPost,
    onForum: (Int) -> Unit,
    onThread: (PostLinkTarget.Thread) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val openLink: (String) -> Unit = { url ->
        when (val target = resolvePostLink(url)) {
            is PostLinkTarget.Forum -> onForum(target.id)
            is PostLinkTarget.Thread -> onThread(target)
            is PostLinkTarget.External -> uriHandler.openUri(target.url)
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(post.author.name, fontWeight = FontWeight.SemiBold)
                Text(if (post.isOriginalPost) "楼主" else "#${post.number}", color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            PostHtml(
                html = post.html,
                threadId = post.threadId,
                attachmentUrls = post.attachments.filter { it.isImage }.map { it.url },
                onLink = openLink,
            )
            if (post.comments.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        post.comments.forEach { comment ->
                            Text(
                                "${comment.author.name}：${plainText(comment.message)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            Text(post.createdAtText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreadHero(
    page: YamiboThreadPostsPage,
    isFavorited: Boolean,
    favoriteBusy: Boolean,
    onFavorite: () -> Unit,
) {
    val thread = page.thread
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(thread.subject, style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(
                onClick = onFavorite,
                enabled = !favoriteBusy,
            ) {
                if (favoriteBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Rounded.Favorite, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isFavorited) "取消收藏" else "收藏主题")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text(thread.author.name.take(1)) }
                }
                Column {
                    Text(thread.author.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${thread.replyCount} 回复 · ${thread.viewCount} 浏览",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("楼层", thread.replyCount + 1)
                Stat("热度", thread.heat)
                Stat("推荐", thread.recommendationCount)
                Stat("权限", thread.readPermission)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (thread.isClosed) Badge { Text("已关闭") }
                if (thread.price > 0) Badge { Text("${thread.price} 积分") }
                if (thread.hasAttachment) Badge { Text("附件") }
            }
        }
    }
}

@Composable
private fun PollCard(poll: YamiboThreadPoll) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("投票", style = MaterialTheme.typography.titleLarge)
            Text(
                "${if (poll.multiple) "最多选 ${poll.maxChoices} 项" else "单选"} · ${poll.voterCount} 人参与",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            poll.options.forEach { option ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(plainText(option.text), Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Text("${"%.1f".format(option.percentage)}%")
                    }
                    LinearProgressIndicator(
                        progress = { (option.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${option.voteCount} 票", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (!poll.resultsVisible) Text("投票后才可查看完整结果", style = MaterialTheme.typography.labelMedium)
        }
    }
}
