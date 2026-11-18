package com.yamibo.pocket300.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.GetThreadPostsInput
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboThreadDetails
import com.yamibo.pocket300.data.CustomListDatabase
import com.yamibo.pocket300.data.ReadingHistoryDatabase
import com.yamibo.pocket300.data.download.DownloadedThread
import com.yamibo.pocket300.data.download.ThreadDownloadKey
import com.yamibo.pocket300.data.download.ThreadDownloadPhase
import com.yamibo.pocket300.data.download.ThreadDownloadManager
import com.yamibo.pocket300.data.download.ThreadDownloadRequest
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.PostHtml
import com.yamibo.pocket300.ui.PostLinkTarget
import com.yamibo.pocket300.ui.ReaderPreferences
import com.yamibo.pocket300.ui.ReaderPreferencesStore
import com.yamibo.pocket300.ui.ReaderMode
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.components.PostAuthorAvatar
import com.yamibo.pocket300.ui.load
import com.yamibo.pocket300.ui.postImageUrls
import com.yamibo.pocket300.ui.reader.ImageReader
import com.yamibo.pocket300.ui.reader.ImageReaderBottomBar
import com.yamibo.pocket300.ui.reader.ImageReaderPreferences
import com.yamibo.pocket300.ui.reader.ImageReaderPreferencesStore
import com.yamibo.pocket300.ui.reader.ImageReaderSettingsSheet
import com.yamibo.pocket300.ui.reader.ImageReaderScaleType
import com.yamibo.pocket300.ui.resolvePostImageSource
import com.yamibo.pocket300.ui.resolvePostLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ReaderContentSource { NETWORK, DOWNLOAD }

internal data class ReaderContent(
    val thread: YamiboThreadDetails,
    val post: YamiboPost,
    val localImageUrls: Map<String, String> = emptyMap(),
    val source: ReaderContentSource = ReaderContentSource.NETWORK,
)

internal sealed interface AdjacentFloorTarget {
    data class PostInMemory(val post: YamiboPost, val page: Int) : AdjacentFloorTarget
    data class PageBoundary(val targetPage: Int, val selectFirst: Boolean) : AdjacentFloorTarget
}

internal fun resolveAdjacentReaderFloor(
    posts: List<YamiboPost>,
    currentPostId: Int,
    currentPage: Int,
    hasNextPage: Boolean,
    forward: Boolean,
): AdjacentFloorTarget? {
    if (posts.isEmpty()) return null
    val currentIndex = posts.indexOfFirst { it.id == currentPostId }
    if (currentIndex < 0) return null
    return if (forward) {
        if (currentIndex + 1 < posts.size) {
            AdjacentFloorTarget.PostInMemory(posts[currentIndex + 1], currentPage)
        } else if (hasNextPage) {
            AdjacentFloorTarget.PageBoundary(targetPage = currentPage + 1, selectFirst = true)
        } else {
            null
        }
    } else {
        if (currentIndex - 1 >= 0) {
            AdjacentFloorTarget.PostInMemory(posts[currentIndex - 1], currentPage)
        } else if (currentPage > 1) {
            AdjacentFloorTarget.PageBoundary(targetPage = currentPage - 1, selectFirst = false)
        } else {
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreen(
    threadId: Int,
    postId: Int,
    initialPage: Int,
    initialContent: ReaderContent?,
    offlineOnly: Boolean,
    onBack: () -> Unit,
    onForum: (Int) -> Unit,
    onThread: (PostLinkTarget.Thread) -> Unit,
) {
    val context = LocalContext.current
    val customListDatabase = remember(context) { CustomListDatabase.getInstance(context) }
    val historyDatabase = remember(context) { ReadingHistoryDatabase.getInstance(context) }
    val downloadRepository = remember(context) { ThreadDownloadManager.getInstance(context) }
    val downloadStatuses by downloadRepository.statuses.collectAsState()
    val downloadKey = remember(threadId) { ThreadDownloadKey(threadId) }
    val downloadStatus = downloadStatuses[downloadKey]
    val preferencesStore = remember(context) { ReaderPreferencesStore(context) }
    val imagePreferencesStore = remember(context) { ImageReaderPreferencesStore(context) }
    var preferences by remember { mutableStateOf(preferencesStore.load()) }
    var imagePreferences by remember { mutableStateOf(imagePreferencesStore.load()) }
    var activePostId by remember(threadId, postId) { mutableIntStateOf(postId) }
    var activePage by remember(threadId, postId, initialPage) {
        mutableIntStateOf(initialPage.coerceAtLeast(1))
    }
    var activeInitialContent by remember(threadId, postId, initialContent) {
        mutableStateOf(initialContent)
    }
    var cachedPagePosts by remember(threadId) { mutableStateOf<List<YamiboPost>>(emptyList()) }
    var cachedPageNumber by remember(threadId) { mutableIntStateOf(initialPage.coerceAtLeast(1)) }
    var cachedHasNextPage by remember(threadId) { mutableStateOf(false) }
    var switchingFloor by remember(threadId) { mutableStateOf(false) }
    var reload by remember(threadId, postId) { mutableIntStateOf(0) }
    var lastHandledReload by remember(threadId, postId) { mutableIntStateOf(-1) }
    val reusableContent = activeInitialContent?.takeUnless {
        needsReaderContentLoad(it.thread.id, it.post.id, threadId, activePostId)
    }
    var state: LoadState<ReaderContent> by remember(
        threadId,
        activePostId,
        offlineOnly,
    ) {
        mutableStateOf(reusableContent?.let { LoadState.Ready(it) } ?: LoadState.Loading)
    }
    var completedDownload by remember(threadId) {
        mutableStateOf<DownloadedThread?>(null)
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var settingsVisible by remember { mutableStateOf(false) }
    var imageSettingsVisible by remember { mutableStateOf(false) }
    var deleteDownloadVisible by remember { mutableStateOf(false) }
    var autoDeleteAttempted by remember(threadId) { mutableStateOf(false) }
    var imageReadingCompleted by remember(threadId, activePostId) { mutableStateOf(false) }
    var readerMode by remember(threadId, postId) { mutableStateOf(preferencesStore.loadMode()) }
    var imageIndex by remember(threadId, activePostId) { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val view = LocalView.current
    val postNotFoundMessage = stringResource(R.string.reader_post_not_found)
    val offlineUnavailableMessage = stringResource(R.string.downloads_content_unavailable_message)
    val offlineUnavailableTitle = stringResource(R.string.downloads_content_unavailable)
    val downloadStartedMessage = stringResource(R.string.reader_download_started)
    val downloadFailedMessage = stringResource(R.string.reader_download_failed)
    val deleteFailedMessage = stringResource(R.string.downloads_delete_failed)
    val autoDeleteSucceededMessage = stringResource(R.string.reader_download_auto_deleted)

    LaunchedEffect(view, controlsVisible) {
        val controller = ViewCompat.getWindowInsetsController(view) ?: return@LaunchedEffect
        if (controlsVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(view) {
        onDispose {
            ViewCompat.getWindowInsetsController(view)
                ?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(threadId) {
        when (load { downloadRepository.awaitInitialized() }) {
            is LoadState.Ready -> downloadRepository.statuses
                .map { statuses -> statuses[downloadKey]?.completed }
                .distinctUntilChanged()
                .collectLatest { completedDownload = it }

            is LoadState.Failed, LoadState.Loading -> Unit
        }
    }
    LaunchedEffect(threadId, activePostId, initialContent, offlineOnly, reload) {
        loadReaderContentLocalFirst(
            threadId = threadId,
            postId = activePostId,
            initialContent = reusableContent,
            offlineOnly = offlineOnly,
            offlineUnavailableMessage = offlineUnavailableMessage,
            loadDownloaded = {
                downloadRepository.awaitInitialized()
                downloadRepository.statuses.value[downloadKey]?.completed
                    .also { completedDownload = it }
                    ?.toReaderContent(activePostId)
            },
            loadNetwork = {
                val cachedPost = cachedPagePosts.firstOrNull { it.id == activePostId }
                val cachedThread = activeInitialContent?.thread?.takeIf { it.id == threadId }
                    ?: (state as? LoadState.Ready)?.value?.thread?.takeIf { it.id == threadId }
                if (
                    reload == lastHandledReload &&
                    cachedPageNumber == activePage &&
                    cachedPost != null &&
                    cachedThread != null
                ) {
                    ReaderContent(thread = cachedThread, post = cachedPost)
                } else {
                    var page = api.posts.getThreadPosts(
                        GetThreadPostsInput(threadId, activePage),
                    )
                    var post = page.posts.firstOrNull { it.id == activePostId }
                    if (post == null) {
                        val resolvedPage = api.posts.findPostPage(threadId, activePostId)
                            ?: error(postNotFoundMessage)
                        page = api.posts.getThreadPosts(GetThreadPostsInput(threadId, resolvedPage))
                        post = page.posts.firstOrNull { it.id == activePostId }
                    }
                    lastHandledReload = reload
                    cachedPageNumber = page.pagination.page
                    cachedPagePosts = page.posts
                    cachedHasNextPage = page.pagination.hasNextPage
                    activePage = page.pagination.page
                    ReaderContent(
                        thread = page.thread,
                        post = post ?: error(postNotFoundMessage),
                    )
                }
            },
            onState = { state = it },
        )
    }
    val readyContent = (state as? LoadState.Ready)?.value
    LaunchedEffect(completedDownload, readyContent) {
        val current = readyContent ?: return@LaunchedEffect
        val downloaded = completedDownload?.toReaderContent(activePostId)
        val reconciled = reconcileReaderDownloadedContent(
            current = current,
            downloaded = downloaded,
            offlineOnly = offlineOnly,
            offlineUnavailableMessage = offlineUnavailableMessage,
        )
        if (reconciled != state) state = reconciled
    }
    LaunchedEffect(readyContent?.thread?.id, readyContent?.post?.id) {
        val current = readyContent ?: return@LaunchedEffect
        scrollState.scrollTo(0)
        withContext(Dispatchers.IO) {
            historyDatabase.record(current.thread, current.post.number)
        }
    }
    val readerAttachmentUrls = remember(readyContent) {
        readyContent?.post?.attachments?.filter { it.isImage }?.map { it.url }.orEmpty()
    }
    val readerImages = remember(readyContent, readerAttachmentUrls) {
        readyContent?.let { content ->
            resolveReaderImageUrls(
                remoteImageUrls = postImageUrls(content.post.html, readerAttachmentUrls),
                localImageUrls = content.localImageUrls,
                allowRemoteImages = content.source != ReaderContentSource.DOWNLOAD,
            )
        }.orEmpty()
    }
    val readerImageCount = readerImages.size
    LaunchedEffect(readerMode, readerImageCount) {
        if (readerMode != ReaderMode.IMAGES || readerImageCount == 0) {
            imageReadingCompleted = false
        }
    }
    LaunchedEffect(
        threadId,
        completedDownload,
        imageReadingCompleted,
        autoDeleteAttempted,
    ) {
        if (!imageReadingCompleted || completedDownload == null || autoDeleteAttempted) {
            return@LaunchedEffect
        }
        val shouldDelete = withContext(Dispatchers.IO) {
            customListDatabase.shouldDeleteAutoDownloadedThreadAfterImageReading(threadId)
        }
        if (!shouldDelete) return@LaunchedEffect

        autoDeleteAttempted = true
        try {
            downloadRepository.delete(downloadKey)
            withContext(Dispatchers.IO) {
                customListDatabase.clearAutoDownloadRecord(threadId)
            }
            Toast.makeText(context, autoDeleteSucceededMessage, Toast.LENGTH_SHORT).show()
            onBack()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            autoDeleteAttempted = false
            Toast.makeText(context, deleteFailedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    val navigationPosts: List<YamiboPost>?
    val navigationPage: Int
    val navigationHasNext: Boolean
    if (readyContent?.source == ReaderContentSource.DOWNLOAD && completedDownload != null) {
        navigationPosts = completedDownload!!.snapshot.posts
        navigationPage = 1
        navigationHasNext = false
    } else if (readyContent != null && cachedPagePosts.any { it.id == readyContent.post.id }) {
        navigationPosts = cachedPagePosts
        navigationPage = activePage
        navigationHasNext = cachedHasNextPage
    } else {
        navigationPosts = null
        navigationPage = activePage
        navigationHasNext = false
    }
    val previousFloorTarget = if (readyContent != null && navigationPosts != null) {
        resolveAdjacentReaderFloor(
            posts = navigationPosts,
            currentPostId = readyContent.post.id,
            currentPage = navigationPage,
            hasNextPage = navigationHasNext,
            forward = false,
        )
    } else {
        null
    }
    val nextFloorTarget = if (readyContent != null && navigationPosts != null) {
        resolveAdjacentReaderFloor(
            posts = navigationPosts,
            currentPostId = readyContent.post.id,
            currentPage = navigationPage,
            hasNextPage = navigationHasNext,
            forward = true,
        )
    } else {
        null
    }

    fun navigateToFloor(target: AdjacentFloorTarget) {
        val current = readyContent ?: return
        if (switchingFloor) return
        when (target) {
            is AdjacentFloorTarget.PostInMemory -> {
                val updated = current.copy(post = target.post)
                activePage = target.page
                activeInitialContent = updated
                activePostId = target.post.id
                state = LoadState.Ready(updated)
            }

            is AdjacentFloorTarget.PageBoundary -> {
                coroutineScope.launch {
                    switchingFloor = true
                    try {
                        val fetched = api.posts.getThreadPosts(
                            GetThreadPostsInput(threadId, target.targetPage),
                        )
                        val selectedPost = if (target.selectFirst) {
                            fetched.posts.firstOrNull()
                        } else {
                            fetched.posts.lastOrNull()
                        }
                        if (selectedPost == null) {
                            Toast.makeText(context, postNotFoundMessage, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val updated = ReaderContent(
                            thread = fetched.thread,
                            post = selectedPost,
                            localImageUrls = completedDownload?.localImageUris.orEmpty(),
                        )
                        cachedPageNumber = fetched.pagination.page
                        cachedPagePosts = fetched.posts
                        cachedHasNextPage = fetched.pagination.hasNextPage
                        activePage = fetched.pagination.page
                        activeInitialContent = updated
                        activePostId = selectedPost.id
                        state = LoadState.Ready(updated)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Toast.makeText(
                            context,
                            error.message?.takeIf(String::isNotBlank) ?: postNotFoundMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } finally {
                        switchingFloor = false
                    }
                }
            }
        }
    }

    val updatePreferences: (ReaderPreferences) -> Unit = {
        preferences = it
        preferencesStore.save(it)
    }
    val updateImagePreferences: (ImageReaderPreferences) -> Unit = {
        imagePreferences = it
        imagePreferencesStore.save(it)
    }
    ReaderTheme(preferences.tone) {
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val effectiveImageMode = readerMode == ReaderMode.IMAGES && readerImages.isNotEmpty()
        val contentTopPadding by animateDpAsState(
            targetValue = statusBarPadding + if (controlsVisible) 64.dp else 0.dp,
            animationSpec = tween(260),
            label = "reader-content-top-padding",
        )
        val contentBottomPadding by animateDpAsState(
            targetValue = navigationBarPadding + if (controlsVisible) {
                if (effectiveImageMode) 52.dp else 88.dp
            } else {
                0.dp
            },
            animationSpec = tween(260),
            label = "reader-content-bottom-padding",
        )
        if (settingsVisible) {
            ModalBottomSheet(onDismissRequest = { settingsVisible = false }) {
                ReaderSettingsSheet(preferences, updatePreferences)
            }
        }
        if (imageSettingsVisible) {
            ModalBottomSheet(onDismissRequest = { imageSettingsVisible = false }) {
                ImageReaderSettingsSheet(imagePreferences, updateImagePreferences)
            }
        }
        if (deleteDownloadVisible) {
            AlertDialog(
                onDismissRequest = { deleteDownloadVisible = false },
                title = { Text(stringResource(R.string.reader_download_delete_title)) },
                text = { Text(stringResource(R.string.reader_download_delete_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleteDownloadVisible = false
                            coroutineScope.launch {
                                try {
                                    downloadRepository.delete(downloadKey)
                                    withContext(Dispatchers.IO) {
                                        customListDatabase.clearAutoDownloadRecord(threadId)
                                    }
                                    val current = (state as? LoadState.Ready)?.value
                                    if (
                                        offlineOnly ||
                                        current?.source == ReaderContentSource.DOWNLOAD
                                    ) {
                                        onBack()
                                    } else if (current != null) {
                                        state = LoadState.Ready(
                                            current.copy(localImageUrls = emptyMap()),
                                        )
                                    }
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
                dismissButton = {
                    TextButton(onClick = { deleteDownloadVisible = false }) {
                        Text(stringResource(R.string.downloads_cancel))
                    }
                },
            )
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(220)) + expandVertically(
                        animationSpec = tween(260),
                        expandFrom = Alignment.Top,
                    ),
                    exit = fadeOut(tween(180)) + shrinkVertically(
                        animationSpec = tween(260),
                        shrinkTowards = Alignment.Top,
                    ),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        TopAppBar(
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = {
                                    coroutineScope.launch {
                                        scrollState.animateScrollTo(0)
                                    }
                                })
                            },
                            title = {
                                val content = (state as? LoadState.Ready)?.value
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (content?.source == ReaderContentSource.DOWNLOAD) {
                                        Icon(
                                            Icons.Rounded.OfflinePin,
                                            contentDescription = stringResource(R.string.reader_offline),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Text(
                                        content?.thread?.subject
                                            ?: stringResource(R.string.reader_default_title),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.reader_back))
                                }
                            },
                            actions = {
                                val content = (state as? LoadState.Ready)?.value
                                val downloadProgressDescription = downloadStatus
                                    ?.takeIf {
                                        it.phase == ThreadDownloadPhase.QUEUED ||
                                            it.phase == ThreadDownloadPhase.FETCHING_PAGES ||
                                            it.phase == ThreadDownloadPhase.DOWNLOADING_IMAGES
                                    }
                                    ?.let {
                                        when (it.phase) {
                                            ThreadDownloadPhase.FETCHING_PAGES -> stringResource(
                                                R.string.thread_download_pages_progress,
                                                it.progress.completedPages,
                                                it.progress.totalPages,
                                            )
                                            ThreadDownloadPhase.DOWNLOADING_IMAGES ->
                                                if (it.progress.totalImages > 0) {
                                                    stringResource(
                                                        R.string.reader_download_progress,
                                                        it.progress.completedImages,
                                                        it.progress.totalImages,
                                                    )
                                                } else {
                                                    stringResource(R.string.reader_download_saving)
                                                }
                                            else -> stringResource(R.string.reader_download_saving)
                                        }
                                    }
                                if (content != null) {
                                    when (downloadStatus?.phase) {
                                        ThreadDownloadPhase.QUEUED,
                                        ThreadDownloadPhase.FETCHING_PAGES,
                                        ThreadDownloadPhase.DOWNLOADING_IMAGES,
                                        -> IconButton(onClick = {}, enabled = false) {
                                            CircularProgressIndicator(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .semantics {
                                                        contentDescription =
                                                            requireNotNull(downloadProgressDescription)
                                                    },
                                                strokeWidth = 2.dp,
                                            )
                                        }

                                        ThreadDownloadPhase.COMPLETED -> IconButton(
                                            onClick = { deleteDownloadVisible = true },
                                        ) {
                                            Icon(
                                                Icons.Rounded.DownloadDone,
                                                stringResource(R.string.reader_remove_download),
                                            )
                                        }

                                        ThreadDownloadPhase.FAILED,
                                        null,
                                        -> IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        val queued = if (
                                                            downloadStatus?.phase ==
                                                            ThreadDownloadPhase.FAILED
                                                        ) {
                                                            downloadRepository.retry(downloadKey)
                                                        } else {
                                                            false
                                                        }
                                                        if (!queued) {
                                                            downloadRepository.enqueue(
                                                                ThreadDownloadRequest.create(
                                                                    thread = content.thread,
                                                                ),
                                                            )
                                                        }
                                                        Toast.makeText(
                                                            context,
                                                            downloadStartedMessage,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    } catch (error: CancellationException) {
                                                        throw error
                                                    } catch (_: Exception) {
                                                        Toast.makeText(
                                                            context,
                                                            downloadFailedMessage,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                }
                                            },
                                        ) {
                                            Icon(
                                                Icons.Rounded.Download,
                                                stringResource(R.string.reader_download_post),
                                            )
                                        }
                                    }
                                }
                                val effectiveMode = if (readerImages.isNotEmpty()) readerMode else ReaderMode.TEXT
                                if (readerImages.isNotEmpty()) {
                                    IconButton(onClick = {
                                        val updatedMode = if (effectiveMode == ReaderMode.TEXT) {
                                            ReaderMode.IMAGES
                                        } else {
                                            ReaderMode.TEXT
                                        }
                                        readerMode = updatedMode
                                        preferencesStore.saveMode(updatedMode)
                                    }) {
                                        if (effectiveMode == ReaderMode.TEXT) {
                                            Icon(Icons.Rounded.Image, stringResource(R.string.reader_image_mode))
                                        } else {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.MenuBook,
                                                stringResource(R.string.reader_text_mode),
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (effectiveMode == ReaderMode.TEXT) {
                                            settingsVisible = true
                                        } else {
                                            imageSettingsVisible = true
                                        }
                                    },
                                ) {
                                    Icon(Icons.Rounded.Settings, stringResource(R.string.reader_settings))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(220)) + expandVertically(
                        animationSpec = tween(260),
                        expandFrom = Alignment.Bottom,
                    ),
                    exit = fadeOut(tween(180)) + shrinkVertically(
                        animationSpec = tween(260),
                        shrinkTowards = Alignment.Bottom,
                    ),
                ) {
                    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                        if (readerMode == ReaderMode.IMAGES && readerImages.isNotEmpty()) {
                            ImageReaderBottomBar(
                                currentPage = imageIndex,
                                pageCount = readerImages.size,
                                preferences = imagePreferences,
                                onCurrentPageChange = { imageIndex = it },
                                onOpenSettings = { imageSettingsVisible = true },
                                onScaleTypeChange = { scaleType: ImageReaderScaleType ->
                                    updateImagePreferences(imagePreferences.copy(scaleType = scaleType))
                                },
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding(),
                            ) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    val progress = if (scrollState.maxValue == 0) 0f
                                    else scrollState.value.toFloat() / scrollState.maxValue
                                    LinearProgressIndicator(
                                        progress = { progress.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        TextButton(
                                            enabled = !switchingFloor && previousFloorTarget != null,
                                            onClick = {
                                                previousFloorTarget?.let(::navigateToFloor)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Icon(
                                                Icons.Rounded.ChevronLeft,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.reader_previous_floor))
                                        }
                                        if (switchingFloor) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Text(
                                                stringResource(
                                                    R.string.reader_scroll_progress,
                                                    (progress * 100).toInt(),
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        TextButton(
                                            enabled = !switchingFloor && nextFloorTarget != null,
                                            onClick = {
                                                nextFloorTarget?.let(::navigateToFloor)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text(stringResource(R.string.reader_next_floor))
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                Icons.Rounded.ChevronRight,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        ) { scaffoldPadding ->
            if (offlineOnly && state is LoadState.Failed) {
                EmptyState(
                    title = offlineUnavailableTitle,
                    message = (state as LoadState.Failed).message,
                    modifier = Modifier.padding(scaffoldPadding),
                )
            } else {
                LoadContent(
                    state,
                    PaddingValues(),
                    onRetry = if (offlineOnly) null else ({ reload++ }),
                ) { content ->
                    val openLink: (String) -> Unit = { url ->
                        when (val target = resolvePostLink(url)) {
                            is PostLinkTarget.Forum -> onForum(target.id)
                            is PostLinkTarget.Thread -> onThread(target)
                            is PostLinkTarget.External -> uriHandler.openUri(target.url)
                        }
                    }
                    if (readerMode == ReaderMode.IMAGES && readerImages.isNotEmpty()) {
                        ImageReader(
                            images = readerImages,
                            threadId = content.post.threadId,
                            currentPage = imageIndex,
                            preferences = imagePreferences,
                            controlsVisible = controlsVisible,
                            onCurrentPageChange = { imageIndex = it },
                            onToggleControls = { controlsVisible = !controlsVisible },
                            onImageReadingComplete = { imageReadingCompleted = true },
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .consumeWindowInsets(scaffoldPadding)
                                .verticalScroll(scrollState)
                                .pointerInput(Unit) {
                                    detectTapGestures { controlsVisible = !controlsVisible }
                                }
                                .padding(top = contentTopPadding, bottom = contentBottomPadding),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 720.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp, vertical = 28.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                Text(
                                    content.thread.subject,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    PostAuthorAvatar(
                                        author = content.post.author,
                                        size = 36.dp,
                                        allowRemoteImage = content.source != ReaderContentSource.DOWNLOAD,
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                content.post.author.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (
                                                shouldShowOriginalPosterBadge(
                                                    content.post,
                                                    content.thread.author.id,
                                                )
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.thread_author_badge_op),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(
                                                            horizontal = 6.dp,
                                                            vertical = 2.dp,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            content.post.createdAtText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ) {
                                        Text(
                                            if (content.post.isOriginalPost) {
                                                stringResource(R.string.reader_original_post)
                                            } else {
                                                stringResource(R.string.reader_floor, content.post.number)
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 4.dp,
                                            ),
                                        )
                                    }
                                }
                                HorizontalDivider()
                                PostHtml(
                                    html = content.post.html,
                                    threadId = content.post.threadId,
                                    attachmentUrls = readerAttachmentUrls,
                                    onLink = openLink,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = preferences.fontSizeSp.sp,
                                        lineHeight = (preferences.fontSizeSp * preferences.lineHeightMultiplier).sp,
                                    ),
                                    localImageUrls = content.localImageUrls,
                                    allowRemoteImages = content.source != ReaderContentSource.DOWNLOAD,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun needsReaderContentLoad(
    cachedThreadId: Int?,
    cachedPostId: Int?,
    threadId: Int,
    postId: Int,
): Boolean = cachedThreadId != threadId || cachedPostId != postId

internal suspend fun loadReaderContentLocalFirst(
    threadId: Int,
    postId: Int,
    initialContent: ReaderContent?,
    offlineOnly: Boolean,
    offlineUnavailableMessage: String,
    loadDownloaded: suspend () -> ReaderContent?,
    loadNetwork: suspend () -> ReaderContent,
    onState: (LoadState<ReaderContent>) -> Unit,
) {
    val downloaded = when (val result = load { loadDownloaded() }) {
        is LoadState.Ready -> result.value
        is LoadState.Failed, LoadState.Loading -> null
    }
    if (offlineOnly) {
        onState(
            downloaded
                ?.let { LoadState.Ready(it) }
                ?: LoadState.Failed(offlineUnavailableMessage),
        )
        return
    }

    val reusable = initialContent?.takeUnless {
        needsReaderContentLoad(it.thread.id, it.post.id, threadId, postId) ||
            (it.source == ReaderContentSource.DOWNLOAD && downloaded == null)
    }
    val preferred = downloaded ?: reusable?.withDownloadedImages(downloaded)
    onState(preferred?.let { LoadState.Ready(it) } ?: LoadState.Loading)

    when (val refreshed = load { loadNetwork().withDownloadedImages(downloaded) }) {
        is LoadState.Ready -> onState(refreshed)
        is LoadState.Failed -> if (preferred == null) onState(refreshed)
        LoadState.Loading -> Unit
    }
}

internal fun reconcileReaderDownloadedContent(
    current: ReaderContent,
    downloaded: ReaderContent?,
    offlineOnly: Boolean,
    offlineUnavailableMessage: String,
): LoadState<ReaderContent> = when {
    downloaded != null && current.source == ReaderContentSource.DOWNLOAD ->
        LoadState.Ready(downloaded)

    current.source == ReaderContentSource.DOWNLOAD && offlineOnly ->
        LoadState.Failed(offlineUnavailableMessage)

    current.source == ReaderContentSource.DOWNLOAD ->
        LoadState.Ready(
            current.copy(
                localImageUrls = emptyMap(),
                source = ReaderContentSource.NETWORK,
            ),
        )

    else -> LoadState.Ready(current.withDownloadedImages(downloaded))
}

private fun ReaderContent.withDownloadedImages(downloaded: ReaderContent?): ReaderContent {
    return copy(localImageUrls = downloaded?.localImageUrls.orEmpty())
}

internal fun resolveReaderImageUrls(
    remoteImageUrls: List<String>,
    localImageUrls: Map<String, String>,
    allowRemoteImages: Boolean = true,
): List<String> = remoteImageUrls.mapNotNull {
    resolvePostImageSource(it, localImageUrls, allowRemoteImages)
}

internal fun DownloadedThread.toReaderContent(postId: Int): ReaderContent? =
    findPost(postId)?.let { post ->
        ReaderContent(
            thread = snapshot.thread,
            post = post,
            localImageUrls = localImageUris,
            source = ReaderContentSource.DOWNLOAD,
        )
    }
