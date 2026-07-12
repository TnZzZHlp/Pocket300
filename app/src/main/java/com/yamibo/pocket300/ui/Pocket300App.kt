package com.yamibo.pocket300.ui

import android.text.Html
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.util.LruCache
import android.webkit.CookieManager
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.yamibo.pocket300.api.GetForumThreadsInput
import com.yamibo.pocket300.api.GetThreadPostsInput
import com.yamibo.pocket300.api.LoginInput
import com.yamibo.pocket300.api.DEFAULT_SECURITY_QUESTIONS
import com.yamibo.pocket300.api.SecurityQuestionOption
import com.yamibo.pocket300.api.SearchSiteThreadsInput
import com.yamibo.pocket300.api.YamiboApi
import com.yamibo.pocket300.api.YamiboForum
import com.yamibo.pocket300.api.YamiboForumIndex
import com.yamibo.pocket300.api.YamiboForumThreadsPage
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboSession
import com.yamibo.pocket300.api.YamiboSearchPage
import com.yamibo.pocket300.api.YamiboSearchThread
import com.yamibo.pocket300.api.YamiboThread
import com.yamibo.pocket300.api.YamiboThreadPoll
import com.yamibo.pocket300.api.YamiboThreadPostsPage
import com.yamibo.pocket300.api.YamiboUserProfile
import com.yamibo.pocket300.api.YAMIBO_ORIGIN
import com.yamibo.pocket300.data.ReadingHistoryDatabase
import com.yamibo.pocket300.data.ReadingHistoryEntry
import com.yamibo.pocket300.ui.theme.PocketTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val api = YamiboApi()

internal sealed class LoadState<out T> {
    data object Loading : LoadState<Nothing>()
    data class Ready<T>(val value: T) : LoadState<T>()
    data class Failed(val message: String) : LoadState<Nothing>()
}

internal class ForumIndexViewModel : ViewModel() {
    var state: LoadState<YamiboForumIndex> by mutableStateOf(LoadState.Loading)
        private set

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            state = load { api.forums.getForumIndex() }
        }
    }
}

private data class ForumContent(val page: YamiboForumThreadsPage, val threads: List<YamiboThread>)
private data class ThreadContent(val page: YamiboThreadPostsPage, val posts: List<YamiboPost>)
internal data class SearchContent(val page: YamiboSearchPage, val threads: List<YamiboSearchThread>)

internal class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var state: LoadState<SearchContent>? by mutableStateOf(null)
        private set

    private var submittedKeyword = ""
    private var searchId: Int? = null
    private var searchJob: Job? = null

    fun updateQuery(value: String) {
        query = value
    }

    fun submit() {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            state = LoadState.Failed("请输入搜索关键字")
            return
        }
        submittedKeyword = keyword
        searchId = null
        search(page = 1, replace = true)
    }

    fun loadMore() {
        val current = (state as? LoadState.Ready)?.value ?: return
        if (!current.page.pagination.hasNextPage || searchJob?.isActive == true) return
        search(page = current.page.pagination.page + 1, replace = false)
    }

    private fun search(page: Int, replace: Boolean) {
        searchJob?.cancel()
        if (replace) state = LoadState.Loading
        val previous = (state as? LoadState.Ready)?.value
        searchJob = viewModelScope.launch {
            val result = load {
                api.search.searchSiteThreads(
                    SearchSiteThreadsInput(
                        keyword = submittedKeyword,
                        page = page,
                        searchId = if (page == 1) null else searchId,
                    ),
                )
            }
            state = when (result) {
                is LoadState.Ready -> {
                    searchId = result.value.pagination.searchId
                    LoadState.Ready(
                        SearchContent(
                            page = result.value,
                            threads = if (replace) result.value.threads
                            else (previous?.threads.orEmpty() + result.value.threads).distinctBy { it.id },
                        ),
                    )
                }
                is LoadState.Failed -> result
                LoadState.Loading -> LoadState.Loading
            }
        }
    }
}
private data class ForumSnapshot(
    val content: ForumContent,
    val pageNumber: Int,
    val selectedTypeId: Int?,
)

private val forumSnapshots = mutableMapOf<Int, ForumSnapshot>()

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "论坛", Icons.Rounded.Forum),
    Tab("history", "历史", Icons.Rounded.History),
    Tab("favorites", "收藏", Icons.Rounded.Favorite),
    Tab("profile", "我的", Icons.Rounded.AccountCircle),
)

private val historyTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Pocket300App() = PocketTheme {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val isTopLevel = tabs.any { it.route == route }

    SharedTransitionLayout {
      val sharedTransitionScope = this
      Box(Modifier.fillMaxSize()) {
        NavHost(navController, startDestination = "home", modifier = Modifier.fillMaxSize()) {
            composable("home") {
                ForumIndexScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onForum = { navController.navigate("forum/${it.id}") },
                    onSearch = { navController.navigate("search") },
                )
            }
            composable("favorites") { FavoritesScreen() }
            composable("history") {
                ReadingHistoryScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onThread = { navController.navigate("thread/${it.threadId}") },
                )
            }
            composable("profile") { ProfileScreen() }
            composable("search") {
                SearchScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onBack = navController::navigateUp,
                    onThread = { navController.navigate("thread/${it.id}") },
                )
            }
            composable(
                route = "forum/{forumId}",
                arguments = listOf(navArgument("forumId") { type = NavType.IntType }),
            ) { backStack ->
                ForumScreen(
                    forumId = backStack.arguments?.getInt("forumId") ?: return@composable,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onBack = navController::navigateUp,
                    onForum = { navController.navigate("forum/$it") },
                    onThread = { navController.navigate("thread/${it.id}") },
                )
            }
            composable(
                route = "thread/{threadId}",
                arguments = listOf(navArgument("threadId") { type = NavType.IntType }),
            ) { backStack ->
                ThreadScreen(
                    threadId = backStack.arguments?.getInt("threadId") ?: return@composable,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onBack = navController::navigateUp,
                    onForum = { navController.navigate("forum/$it") },
                    onThread = { navController.navigate("thread/$it") },
                )
            }
        }
        AnimatedVisibility(
            visible = isTopLevel,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
          NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
      }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun ForumIndexScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onForum: (YamiboForum) -> Unit,
    onSearch: () -> Unit,
) {
    val viewModel: ForumIndexViewModel = viewModel()
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
private fun Stat(label: String, value: Int) = Column {
    Text(value.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun ForumScreen(
    forumId: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onForum: (Int) -> Unit,
    onThread: (YamiboThread) -> Unit,
) {
    val cachedSnapshot = remember(forumId) { forumSnapshots[forumId] }
    var reload by remember { mutableStateOf(0) }
    var pageNumber by rememberSaveable(forumId) { mutableStateOf(cachedSnapshot?.pageNumber ?: 1) }
    var selectedTypeId by rememberSaveable(forumId) { mutableStateOf(cachedSnapshot?.selectedTypeId) }
    var state: LoadState<ForumContent> by remember(forumId) {
        mutableStateOf(cachedSnapshot?.content?.let { LoadState.Ready(it) } ?: LoadState.Loading)
    }
    var restoreCachedPage by remember(forumId) { mutableStateOf(cachedSnapshot != null) }
    var refreshingThreads by remember { mutableStateOf(false) }
    var threadLoadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(forumId, reload, pageNumber, selectedTypeId) {
        if (restoreCachedPage && reload == 0) {
            restoreCachedPage = false
            return@LaunchedEffect
        }
        val previous = (state as? LoadState.Ready)?.value
        refreshingThreads = pageNumber == 1 && previous != null
        threadLoadError = null
        if (pageNumber == 1 && previous == null) state = LoadState.Loading
        when (val result = load {
            api.threads.getForumThreads(GetForumThreadsInput(forumId, pageNumber, typeId = selectedTypeId))
        }) {
            is LoadState.Ready -> {
                val content = ForumContent(
                    result.value,
                    if (pageNumber == 1) result.value.threads
                    else (previous?.threads.orEmpty() + result.value.threads).distinctBy { it.id },
                )
                state = LoadState.Ready(content)
                forumSnapshots[forumId] = ForumSnapshot(content, pageNumber, selectedTypeId)
            }
            is LoadState.Failed -> if (previous == null) state = result else threadLoadError = result.message
            LoadState.Loading -> Unit
        }
        refreshingThreads = false
    }
    ScreenScaffold(
        modifier = with(sharedTransitionScope) {
            Modifier.sharedBounds(rememberSharedContentState("forum-$forumId"), animatedVisibilityScope)
        },
        title = (state as? LoadState.Ready)?.value?.page?.forum?.name ?: "板块",
        onBack = onBack,
        onRefresh = { pageNumber = 1; reload++ },
    ) { padding ->
        LoadContent(state, padding) { content ->
            val page = content.page
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(page.forum.name, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                page.forum.description.ifBlank { "暂无板块简介" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Stat("主题", page.pagination.totalThreads)
                                Stat("帖子", page.forum.postCount)
                                Stat("页码", page.pagination.page)
                            }
                        }
                    }
                }
                if (page.subforums.isNotEmpty()) {
                    item { SectionLabel("子板块") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(page.subforums, key = { it.id }) { subforum ->
                                AssistChip(
                                    onClick = { onForum(subforum.id) },
                                    label = { Text("${subforum.name} · ${subforum.threadCount} 主题") },
                                )
                            }
                        }
                    }
                }
                if (page.threadTypes.isNotEmpty()) {
                    item { SectionLabel("分类") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedTypeId == null,
                                    onClick = { selectedTypeId = null; pageNumber = 1 },
                                    label = { Text("全部") },
                                )
                            }
                            items(page.threadTypes, key = { it.id }) { type ->
                                FilterChip(
                                    selected = selectedTypeId == type.id,
                                    onClick = { selectedTypeId = type.id; pageNumber = 1 },
                                    label = { Text(type.name) },
                                )
                            }
                        }
                    }
                }
                if (refreshingThreads) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                } else {
                    threadLoadError?.let { message ->
                        item {
                            Text(
                                message,
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    items(content.threads, key = { it.id }, contentType = { "thread" }) { thread ->
                        ThreadCard(
                            thread = thread,
                            onClick = onThread,
                            modifier = with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    rememberSharedContentState("thread-${thread.id}"),
                                    animatedVisibilityScope,
                                )
                            },
                        )
                    }
                    item {
                        ListFooter(
                            count = content.threads.size,
                            hasNextPage = page.pagination.hasNextPage,
                            onLoadMore = { pageNumber = page.pagination.page + 1 },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadCard(thread: YamiboThread, onClick: (YamiboThread) -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = { onClick(thread) }, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (thread.typeName != null) Text(thread.typeName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(thread.subject, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            thread.excerpt?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("${thread.author.name} · ${thread.lastPostAtText} · ${thread.replyCount} 回复", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun ThreadScreen(
    threadId: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onForum: (Int) -> Unit,
    onThread: (Int) -> Unit,
) {
    val context = LocalContext.current
    val historyDatabase = remember(context) { ReadingHistoryDatabase.getInstance(context) }
    var reload by remember { mutableStateOf(0) }
    var pageNumber by remember(threadId) { mutableStateOf(1) }
    var state: LoadState<ThreadContent> by remember { mutableStateOf(LoadState.Loading) }
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
    val loadedThread = (state as? LoadState.Ready)?.value?.page?.thread
    LaunchedEffect(loadedThread?.id, loadedThread?.subject) {
        loadedThread?.let { thread ->
            withContext(Dispatchers.IO) { historyDatabase.record(thread) }
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
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { ThreadHero(page) }
                page.poll?.let { poll -> item { PollCard(poll) } }
                items(content.posts, key = { it.id }, contentType = { "post" }) { post ->
                    PostCard(post, onForum, onThread)
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
private fun PostCard(post: YamiboPost, onForum: (Int) -> Unit, onThread: (Int) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val openLink: (String) -> Unit = { url ->
        when (val target = resolvePostLink(url)) {
            is PostLinkTarget.Forum -> onForum(target.id)
            is PostLinkTarget.Thread -> onThread(target.id)
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

private sealed interface PostHtmlPart {
    data class Text(val value: String, val url: String? = null) : PostHtmlPart
    data class Image(val url: String) : PostHtmlPart
}

private sealed interface PostRenderPart {
    data class Inline(val parts: List<PostHtmlPart>) : PostRenderPart
    data class Image(val url: String) : PostRenderPart
}

private val postHtmlCache = LruCache<String, List<PostHtmlPart>>(64)

@Composable
private fun PostHtml(html: String, threadId: Int, attachmentUrls: List<String>, onLink: (String) -> Unit) {
    val parts = remember(html, attachmentUrls) {
        val htmlParts = postHtmlCache.get(html) ?: parsePostHtml(html).also { postHtmlCache.put(html, it) }
        val embeddedUrls = htmlParts.filterIsInstance<PostHtmlPart.Image>()
            .map { normalizePostImageUrl(it.url) }
            .toSet()
        htmlParts + attachmentUrls
            .filterNot { normalizePostImageUrl(it) in embeddedUrls }
            .map(PostHtmlPart::Image)
    }
    val renderParts = remember(parts) { groupPostHtmlParts(parts) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        renderParts.forEachIndexed { index, part ->
            when (part) {
                is PostRenderPart.Inline -> PostInlineHtml(part.parts, threadId, onLink)
                is PostRenderPart.Image -> {
                    val url = normalizePostImageUrl(part.url)
                    var failed by remember(url) { mutableStateOf(false) }
                    val request = rememberPostImageRequest(url, threadId)
                    if (failed) {
                        Text("图片加载失败", color = MaterialTheme.colorScheme.error)
                    } else {
                        AsyncImage(
                            model = request,
                            contentDescription = "帖子图片 ${index + 1}",
                            contentScale = ContentScale.Fit,
                            onError = { failed = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 520.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun groupPostHtmlParts(parts: List<PostHtmlPart>): List<PostRenderPart> {
    val result = mutableListOf<PostRenderPart>()
    val inline = mutableListOf<PostHtmlPart>()
    fun flushInline() {
        if (inline.isNotEmpty()) result += PostRenderPart.Inline(inline.toList())
        inline.clear()
    }
    parts.forEach { part ->
        if (part is PostHtmlPart.Image && !isSmileyImage(part.url)) {
            flushInline()
            result += PostRenderPart.Image(part.url)
        } else {
            inline += part
        }
    }
    flushInline()
    return result
}

@Composable
private fun PostInlineHtml(parts: List<PostHtmlPart>, threadId: Int, onLink: (String) -> Unit) {
    val style = MaterialTheme.typography.bodyLarge
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    val text = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            when (part) {
                is PostHtmlPart.Text -> if (part.url == null) {
                    append(part.value)
                } else {
                    pushLink(
                        LinkAnnotation.Url(
                            part.url,
                            styles = TextLinkStyles(style = linkStyle),
                            linkInteractionListener = { onLink(part.url) },
                        ),
                    )
                    append(part.value)
                    pop()
                }
                is PostHtmlPart.Image -> appendInlineContent("smiley-$index", "表情")
            }
        }
    }
    val inlineContent = buildMap {
        parts.forEachIndexed { index, part ->
            if (part is PostHtmlPart.Image) {
                put(
                    "smiley-$index",
                    InlineTextContent(
                        Placeholder(
                            width = style.lineHeight,
                            height = style.lineHeight,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        AsyncImage(
                            model = rememberPostImageRequest(normalizePostImageUrl(part.url), threadId),
                            contentDescription = "表情",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
        }
    }
    Text(text = text, inlineContent = inlineContent, style = style)
}

@Composable
private fun rememberPostImageRequest(url: String, threadId: Int): ImageRequest {
    val context = LocalContext.current
    return remember(url, threadId) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .apply {
                val cookie = CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrBlank()) addHeader("Cookie", cookie)
                addHeader("Referer", "$YAMIBO_ORIGIN/forum.php?mod=viewthread&tid=$threadId")
                addHeader("User-Agent", "Mozilla/5.0 (Linux; Android) Pocket300/1.0")
            }
            .build()
    }
}

private fun isSmileyImage(source: String): Boolean = normalizePostImageUrl(source)
    .substringAfter(YAMIBO_ORIGIN)
    .trimStart('/')
    .startsWith("static/image/smiley/", ignoreCase = true)

@Suppress("DEPRECATION")
private fun parsePostHtml(html: String): List<PostHtmlPart> {
    val spanned = Html.fromHtml(resolveDiscuzImageSources(html), Html.FROM_HTML_MODE_LEGACY) as Spanned
    val images = spanned.getSpans(0, spanned.length, ImageSpan::class.java)
        .sortedBy(spanned::getSpanStart)
    val parts = mutableListOf<PostHtmlPart>()
    var cursor = 0
    images.forEach { image ->
        addPostText(parts, spanned, cursor, spanned.getSpanStart(image))
        image.source?.takeIf(String::isNotBlank)?.let { parts += PostHtmlPart.Image(it) }
        cursor = spanned.getSpanEnd(image)
    }
    addPostText(parts, spanned, cursor, spanned.length)
    return parts
}

private val imageTagPattern = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val sourceAttributePattern = Regex(
    """\bsrc\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""",
    RegexOption.IGNORE_CASE,
)

private fun resolveDiscuzImageSources(html: String): String = imageTagPattern.replace(html) { match ->
    val tag = match.value
    val source = listOf("zoomfile", "file", "data-src", "data-original")
        .firstNotNullOfOrNull { readHtmlAttribute(tag, it) }
        ?.takeIf { it.isNotBlank() }
        ?: return@replace tag
    val resolvedSource = "src=\"${source.replace("\"", "&quot;")}\""
    if (sourceAttributePattern.containsMatchIn(tag)) {
        sourceAttributePattern.replaceFirst(tag, resolvedSource)
    } else {
        tag.replaceFirst("<img", "<img $resolvedSource", ignoreCase = true)
    }
}

private fun readHtmlAttribute(tag: String, name: String): String? {
    val pattern = Regex(
        """(?:^|\s)${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
        RegexOption.IGNORE_CASE,
    )
    val match = pattern.find(tag) ?: return null
    return match.groupValues.drop(1).firstOrNull(String::isNotEmpty)
}

private fun addPostText(parts: MutableList<PostHtmlPart>, spanned: Spanned, start: Int, end: Int) {
    if (start >= end) return
    val boundaries = buildSet {
        add(start)
        add(end)
        spanned.getSpans(start, end, URLSpan::class.java).forEach { span ->
            add(spanned.getSpanStart(span).coerceIn(start, end))
            add(spanned.getSpanEnd(span).coerceIn(start, end))
        }
    }.sorted()
    boundaries.zipWithNext().forEach { (from, to) ->
        var text = spanned.subSequence(from, to).toString().replace('\uFFFC'.toString(), "")
        if (from == start) text = text.trimStart()
        if (to == end) text = text.trimEnd()
        if (text.isNotEmpty()) {
            val url = spanned.getSpans(from, to, URLSpan::class.java).firstOrNull()?.url
            parts += PostHtmlPart.Text(text, url)
        }
    }
}

private fun normalizePostImageUrl(source: String): String {
    val value = source.trim().replace("&amp;", "&")
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "$YAMIBO_ORIGIN$value"
        value.startsWith("http://bbs.yamibo.com/") -> value.replaceFirst("http://", "https://")
        value.startsWith("http://") || value.startsWith("https://") -> value
        else -> "$YAMIBO_ORIGIN/${value.trimStart('/')}"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreadHero(page: YamiboThreadPostsPage) {
    val thread = page.thread
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(thread.subject, style = MaterialTheme.typography.headlineSmall)
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

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ListFooter(count: Int, hasNextPage: Boolean, onLoadMore: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("已加载 $count 项", style = MaterialTheme.typography.labelMedium)
        if (hasNextPage) OutlinedButton(onClick = onLoadMore) { Text("加载下一页") }
        else Text("已经到底了", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onThread: (YamiboSearchThread) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel()

    ScreenScaffold("搜索", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.weight(1f),
                    label = { Text("搜索主题") },
                    placeholder = { Text("输入关键字") },
                    singleLine = true,
                )
                Button(onClick = viewModel::submit) { Text("搜索") }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (val current = viewModel.state) {
                    null -> EmptyState("搜索主题", "输入关键字开始搜索。")
                    LoadState.Loading -> Loading()
                    is LoadState.Failed -> EmptyState("搜索失败", current.message)
                    is LoadState.Ready -> SearchResults(
                        content = current.value,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onThread = onThread,
                        onLoadMore = viewModel::loadMore,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchResults(
    content: SearchContent,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onThread: (YamiboSearchThread) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (content.threads.isEmpty()) {
        EmptyState("没有搜索结果", "没有找到与“${content.page.keyword}”相关的主题。")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(content.threads, key = { it.id }) { thread ->
            SearchThreadCard(
                thread = thread,
                onClick = onThread,
                modifier = with(sharedTransitionScope) {
                    Modifier.sharedBounds(
                        rememberSharedContentState("thread-${thread.id}"),
                        animatedVisibilityScope,
                    )
                },
            )
        }
        item {
            ListFooter(
                count = content.threads.size,
                hasNextPage = content.page.pagination.hasNextPage,
                onLoadMore = onLoadMore,
            )
        }
    }
}

@Composable
private fun SearchThreadCard(
    thread: YamiboSearchThread,
    onClick: (YamiboSearchThread) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = { onClick(thread) }, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(thread.forum.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(thread.subject, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            thread.excerpt?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${thread.author.name} · ${thread.createdAtText} · ${thread.replyCount} 回复 · ${thread.viewCount} 浏览",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun ReadingHistoryScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onThread: (ReadingHistoryEntry) -> Unit,
) {
    val context = LocalContext.current
    val database = remember(context) { ReadingHistoryDatabase.getInstance(context) }
    var reload by remember { mutableStateOf(0) }
    var state: LoadState<List<ReadingHistoryEntry>> by remember { mutableStateOf(LoadState.Loading) }
    LaunchedEffect(reload) {
        state = load { withContext(Dispatchers.IO) { database.getAll() } }
    }
    ScreenScaffold("阅读历史", onRefresh = { reload++ }) { padding ->
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
                "${entry.authorName} · 阅读于 $readAtText",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.lastPostAtText.isNotBlank()) {
                Text("最后回复 ${entry.lastPostAtText}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesScreen() {
    ScreenScaffold("收藏") { padding ->
        EmptyState("还没有收藏", "在主题页面收藏的内容会显示在这里。", Modifier.padding(padding))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen() {
    var sessionState: LoadState<YamiboSession?> by remember { mutableStateOf(LoadState.Loading) }
    LaunchedEffect(Unit) { sessionState = load { api.auth.getCurrentSession() } }

    ScreenScaffold("我的") { padding ->
        when (val current = sessionState) {
            LoadState.Loading -> Loading(Modifier.padding(padding))
            is LoadState.Failed -> EmptyState("无法读取登录状态", current.message, Modifier.padding(padding))
            is LoadState.Ready -> if (current.value == null) {
                LoginPanel(Modifier.padding(padding)) { sessionState = LoadState.Ready(it) }
            } else {
                ProfileSummary(
                    session = current.value,
                    modifier = Modifier.padding(padding),
                    onLoggedOut = { sessionState = LoadState.Ready(null) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginPanel(modifier: Modifier, onLoggedIn: (YamiboSession) -> Unit) {
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var selectedQuestion by rememberSaveable { mutableStateOf(0) }
    var questions by remember { mutableStateOf<List<SecurityQuestionOption>>(DEFAULT_SECURITY_QUESTIONS) }
    var expanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        questions = runCatching { api.auth.getLoginSecurityQuestions() }.getOrDefault(DEFAULT_SECURITY_QUESTIONS)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(Modifier.size(72.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text("百", style = MaterialTheme.typography.headlineLarge) }
                }
                Spacer(Modifier.height(12.dp))
                Text("登录百合会", style = MaterialTheme.typography.headlineMedium)
                Text("连接账号后查看个人信息、私信和收藏内容。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            ElevatedCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("账号登录", style = MaterialTheme.typography.titleLarge)
                    Text("登录状态由原生网络层的 HttpOnly Cookie 保存。", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(account, { account = it }, label = { Text("账号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        password,
                        { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = questions.firstOrNull { it.id == selectedQuestion }?.label.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("安全提问") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            questions.forEach { question ->
                                DropdownMenuItem(
                                    text = { Text(question.label) },
                                    onClick = {
                                        selectedQuestion = question.id
                                        if (question.id == 0) answer = ""
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                    if (selectedQuestion != 0) {
                        OutlinedTextField(
                            answer,
                            { answer = it },
                            label = { Text("安全提问答案") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = { submitting = true; error = null },
                        enabled = account.isNotBlank() && password.isNotBlank() && !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (submitting) "正在登录…" else "登录") }
                }
            }
        }
    }
    if (submitting) LaunchedEffect(account, password, selectedQuestion, answer) {
        when (val result = load { api.auth.login(LoginInput(account, password, answer, selectedQuestion)) }) {
            is LoadState.Ready -> onLoggedIn(result.value)
            is LoadState.Failed -> error = result.message
            LoadState.Loading -> Unit
        }
        submitting = false
    }
}

@Composable
private fun ProfileSummary(session: YamiboSession, modifier: Modifier, onLoggedOut: () -> Unit) {
    var reload by remember { mutableStateOf(0) }
    var profile: LoadState<YamiboUserProfile> by remember { mutableStateOf(LoadState.Loading) }
    var loggingOut by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.uid, reload) { profile = load { api.auth.getUserProfile(session.uid) } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(Modifier.size(72.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                        Box(contentAlignment = Alignment.Center) { Text(session.username.take(1), style = MaterialTheme.typography.headlineLarge) }
                    }
                    Text(session.username, style = MaterialTheme.typography.headlineMedium)
                    Text("UID ${session.uid} · 阅读权限 ${session.readAccess}")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { reload++ }) { Text("刷新资料") }
                        OutlinedButton(onClick = { loggingOut = true }, enabled = !loggingOut) {
                            Text(if (loggingOut) "正在退出…" else "退出登录")
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item { SectionLabel("个人资料") }
        when (val current = profile) {
            LoadState.Loading -> item { Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            is LoadState.Failed -> item { Text(current.message, color = MaterialTheme.colorScheme.error) }
            is LoadState.Ready -> if (current.value.fields.isEmpty()) {
                item { Text("资料页没有解析到可展示字段", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(current.value.fields) { field ->
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(field.value, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
    if (loggingOut) LaunchedEffect(Unit) {
        runCatching { api.auth.logout() }
            .onSuccess { onLoggedOut() }
            .onFailure { error = it.message ?: "退出失败" }
        loggingOut = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) = Scaffold(
    modifier = modifier,
    topBar = {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            },
            actions = {
                if (onSearch != null) IconButton(onSearch) { Icon(Icons.Rounded.Search, "搜索") }
                if (onRefresh != null) IconButton(onRefresh) { Icon(Icons.Rounded.Refresh, "刷新") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
    },
    content = content,
)

@Composable
private fun <T> LoadContent(state: LoadState<T>, padding: PaddingValues, content: @Composable (T) -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding)) {
        when (state) {
            LoadState.Loading -> Loading()
            is LoadState.Failed -> EmptyState("加载失败", state.message)
            is LoadState.Ready -> content(state.value)
        }
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) = Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
}

@Composable
private fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) = Box(
    modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private suspend fun <T> load(block: suspend () -> T): LoadState<T> = try {
    LoadState.Ready(block())
} catch (error: Exception) {
    LoadState.Failed(error.message ?: "发生未知错误")
}

@Suppress("DEPRECATION")
private fun plainText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
