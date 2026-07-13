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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.GetThreadPostsInput
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboThreadPoll
import com.yamibo.pocket300.api.YamiboThreadPostsPage
import com.yamibo.pocket300.data.ReadingHistoryDatabase
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.PostHtml
import com.yamibo.pocket300.ui.PostLinkTarget
import com.yamibo.pocket300.ui.ReaderPreferences
import com.yamibo.pocket300.ui.ReaderTone
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.components.AutoLoadNextPage
import com.yamibo.pocket300.ui.components.ListFooter
import com.yamibo.pocket300.ui.load
import com.yamibo.pocket300.ui.plainText
import com.yamibo.pocket300.ui.resolvePostLink
import com.yamibo.pocket300.ui.viewmodels.ThreadViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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
    onRatings: (Int, Int) -> Unit,
    onReader: (Int, Int, Int) -> Unit,
    onThread: (PostLinkTarget.Thread) -> Unit,
) {
    val viewModel: ThreadViewModel = viewModel()
    val context = LocalContext.current
    val historyDatabase = remember(context) { ReadingHistoryDatabase.getInstance(context) }
    var reload by remember { mutableIntStateOf(0) }
    var pageNumber by rememberSaveable(threadId, initialPostId, initialPage) {
        mutableIntStateOf(if (initialPostId > 0) initialPage.coerceAtLeast(1) else 1)
    }
    var targetFloor by rememberSaveable(threadId, initialFloor) { mutableIntStateOf(initialFloor) }
    var targetPostId by rememberSaveable(
        threadId,
        initialPostId
    ) { mutableIntStateOf(initialPostId) }
    val listState = rememberLazyListState()
    var restoredFloor by rememberSaveable(threadId, initialFloor, initialPostId) {
        mutableStateOf(initialFloor <= 0 && initialPostId <= 0)
    }
    var favoriteId by remember(threadId, initialFavoriteId) {
        mutableStateOf(initialFavoriteId.takeIf { it > 0 })
    }
    var isFavorited by remember(
        threadId,
        initialFavoriteId
    ) { mutableStateOf(initialFavoriteId > 0) }
    var favoriteBusy by remember(threadId) { mutableStateOf(false) }
    var originalPosterOnly by rememberSaveable(threadId) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(threadId, reload, pageNumber, originalPosterOnly) {
        val previous = (viewModel.state as? LoadState.Ready)?.value
        val originalPosterId = previous?.page?.thread?.author?.id
        viewModel.loadPosts(
            GetThreadPostsInput(
                threadId = threadId,
                page = pageNumber,
                authorId = originalPosterId.takeIf { originalPosterOnly },
            ),
        )
    }
    val state = viewModel.state
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
            historyDatabase.record(
                thread,
                initialFloor.coerceAtLeast(1)
            )
        }
    }
    LaunchedEffect(listState, loadedContent, restoredFloor) {
        val content = loadedContent ?: return@LaunchedEffect
        if (!restoredFloor) return@LaunchedEffect
        snapshotFlow {
            val visiblePostIds =
                listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Int }.toSet()
            content.posts.filter { it.id in visiblePostIds }.maxOfOrNull { it.number }
        }
            .distinctUntilChanged()
            .collectLatest { floor ->
                val thread = loadedThread ?: return@collectLatest
                floor ?: return@collectLatest
                delay(300.milliseconds)
                historyDatabase.record(thread, floor)
            }
    }
    ScreenScaffold(
        modifier = with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState("thread-$threadId"),
                animatedVisibilityScope
            )
        },
        title = loadedThread?.subject ?: "主题",
        onBack = onBack,
        onRefresh = { viewModel.invalidate(); pageNumber = 1; reload++ },
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
                        originalPosterOnly = originalPosterOnly,
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
                        onOriginalPosterOnlyChange = { selected ->
                            originalPosterOnly = selected
                            pageNumber = 1
                            targetFloor = 0
                            targetPostId = 0
                            restoredFloor = true
                        },
                    )
                }
                page.poll?.let { poll -> item { PollCard(poll) } }
                items(content.posts, key = { it.id }, contentType = { "post" }) { post ->
                    PostCard(
                        post = post,
                        onForum = onForum,
                        onRatings = { onRatings(post.threadId, post.id) },
                        onReader = {
                            val postPage = ((post.position - 1) / page.pagination.pageSize) + 1
                            onReader(post.threadId, post.id, postPage.coerceAtLeast(1))
                        },
                        onThread = { target ->
                            if (target.id == threadId && target.postId != null) {
                                originalPosterOnly = false
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
internal fun ReaderSettingsSheet(
    preferences: ReaderPreferences,
    onChange: (ReaderPreferences) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.reader_settings), style = MaterialTheme.typography.titleLarge)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.reader_font_size),
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedIconButton(
                    onClick = {
                        onChange(
                            preferences.copy(
                                fontSizeSp = (preferences.fontSizeSp - 1f).coerceAtLeast(
                                    14f
                                )
                            )
                        )
                    },
                    enabled = preferences.fontSizeSp > 14f,
                ) { Icon(Icons.Rounded.Remove, stringResource(R.string.reader_font_smaller)) }
                Text(
                    stringResource(R.string.reader_font_size_value, preferences.fontSizeSp.toInt()),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedIconButton(
                    onClick = {
                        onChange(
                            preferences.copy(
                                fontSizeSp = (preferences.fontSizeSp + 1f).coerceAtMost(
                                    26f
                                )
                            )
                        )
                    },
                    enabled = preferences.fontSizeSp < 26f,
                ) { Icon(Icons.Rounded.Add, stringResource(R.string.reader_font_larger)) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.reader_line_height),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    stringResource(
                        R.string.reader_line_height_value,
                        preferences.lineHeightMultiplier
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = preferences.lineHeightMultiplier,
                onValueChange = { onChange(preferences.copy(lineHeightMultiplier = it)) },
                valueRange = 1.35f..2f,
                steps = 5,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.reader_background),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTone.entries.forEach { tone ->
                    FilterChip(
                        selected = preferences.tone == tone,
                        onClick = { onChange(preferences.copy(tone = tone)) },
                        label = { Text(stringResource(tone.labelResource)) },
                    )
                }
            }
        }
        TextButton(
            onClick = { onChange(ReaderPreferences()) },
            modifier = Modifier.align(Alignment.End),
        ) { Text(stringResource(R.string.reader_reset)) }
    }
}

private val ReaderTone.labelResource: Int
    get() = when (this) {
        ReaderTone.SYSTEM -> R.string.reader_tone_system
        ReaderTone.PAPER -> R.string.reader_tone_paper
        ReaderTone.MINT -> R.string.reader_tone_mint
        ReaderTone.NIGHT -> R.string.reader_tone_night
    }

@Composable
internal fun ReaderTheme(tone: ReaderTone, content: @Composable () -> Unit) {
    val baseColors = MaterialTheme.colorScheme
    val colors = when (tone) {
        ReaderTone.SYSTEM -> baseColors
        ReaderTone.PAPER -> lightColorScheme(
            primary = Color(0xFF795548),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEADCC8),
            onPrimaryContainer = Color(0xFF342018),
            background = Color(0xFFF7F0E3),
            onBackground = Color(0xFF322C25),
            surface = Color(0xFFF7F0E3),
            onSurface = Color(0xFF322C25),
            surfaceVariant = Color(0xFFE9E0D2),
            onSurfaceVariant = Color(0xFF655C51),
        )

        ReaderTone.MINT -> lightColorScheme(
            primary = Color(0xFF3F6655),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD0E8D8),
            onPrimaryContainer = Color(0xFF163A2B),
            background = Color(0xFFEFF6EE),
            onBackground = Color(0xFF243029),
            surface = Color(0xFFEFF6EE),
            onSurface = Color(0xFF243029),
            surfaceVariant = Color(0xFFDCE9DC),
            onSurfaceVariant = Color(0xFF526158),
        )

        ReaderTone.NIGHT -> darkColorScheme(
            primary = Color(0xFFD6B98C),
            onPrimary = Color(0xFF402D10),
            primaryContainer = Color(0xFF59451F),
            onPrimaryContainer = Color(0xFFF4DCB0),
            background = Color(0xFF171819),
            onBackground = Color(0xFFD7D4CE),
            surface = Color(0xFF171819),
            onSurface = Color(0xFFD7D4CE),
            surfaceVariant = Color(0xFF303234),
            onSurfaceVariant = Color(0xFFB8B6B0),
        )
    }
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, content = content)
}

@Composable
private fun PostCard(
    post: YamiboPost,
    onForum: (Int) -> Unit,
    onRatings: () -> Unit,
    onReader: () -> Unit,
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(post.author.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        post.createdAtText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ) {
                        Text(
                            if (post.isOriginalPost) "楼主" else "${post.number} 楼",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    IconButton(onClick = onReader) {
                        Icon(
                            Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = stringResource(R.string.reader_open),
                        )
                    }
                }
            }
            PostHtml(
                html = post.html,
                threadId = post.threadId,
                attachmentUrls = post.attachments.filter { it.isImage }.map { it.url },
                onLink = openLink,
            )
            if (post.ratings.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.rating_count, post.ratingCount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        post.ratings.take(3).forEach { rating -> RatingRow(rating) }
                        if (post.ratingCount > 3) {
                            TextButton(
                                onClick = onRatings,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(stringResource(R.string.rating_view_all, post.ratingCount))
                            }
                        }
                    }
                }
            }
            if (post.comments.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        post.comments.forEach { comment ->
                            Text(
                                "${comment.author.name}：${plainText(comment.message)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreadHero(
    page: YamiboThreadPostsPage,
    isFavorited: Boolean,
    favoriteBusy: Boolean,
    originalPosterOnly: Boolean,
    onFavorite: () -> Unit,
    onOriginalPosterOnlyChange: (Boolean) -> Unit,
) {
    val thread = page.thread
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                thread.subject,
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedButton(onClick = onFavorite, enabled = !favoriteBusy) {
                if (favoriteBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Rounded.Favorite, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isFavorited) "取消收藏" else "收藏主题")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
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
                Stat("阅读权限", thread.readPermission)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = originalPosterOnly,
                    onClick = { onOriginalPosterOnlyChange(!originalPosterOnly) },
                    label = { Text(stringResource(R.string.thread_original_poster_only)) },
                    enabled = thread.author.id != null,
                )
                if (thread.isClosed) Badge { Text("已关闭") }
                if (thread.price > 0) Badge { Text("${thread.price} 积分") }
                if (thread.hasAttachment) Badge { Text("附件") }
            }
            HorizontalDivider()
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
            if (!poll.resultsVisible) Text(
                "投票后才可查看完整结果",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
