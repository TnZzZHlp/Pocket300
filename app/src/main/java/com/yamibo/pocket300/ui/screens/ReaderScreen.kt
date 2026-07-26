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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
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
import com.yamibo.pocket300.data.download.DownloadedPost
import com.yamibo.pocket300.data.download.PostDownloadKey
import com.yamibo.pocket300.data.download.PostDownloadPhase
import com.yamibo.pocket300.data.download.PostDownloadRepository
import com.yamibo.pocket300.data.download.PostDownloadRequest
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.PostHtml
import com.yamibo.pocket300.ui.PostLinkTarget
import com.yamibo.pocket300.ui.ReaderPreferences
import com.yamibo.pocket300.ui.ReaderPreferencesStore
import com.yamibo.pocket300.ui.ReaderMode
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.load
import com.yamibo.pocket300.ui.postImageUrls
import com.yamibo.pocket300.ui.reader.ImageReader
import com.yamibo.pocket300.ui.reader.ImageReaderBottomBar
import com.yamibo.pocket300.ui.reader.ImageReaderPreferences
import com.yamibo.pocket300.ui.reader.ImageReaderPreferencesStore
import com.yamibo.pocket300.ui.reader.ImageReaderSettingsSheet
import com.yamibo.pocket300.ui.reader.ImageReaderScaleType
import com.yamibo.pocket300.ui.resolvePostImageUrl
import com.yamibo.pocket300.ui.resolvePostLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class ReaderContentSource { NETWORK, DOWNLOAD }

internal data class ReaderContent(
    val thread: YamiboThreadDetails,
    val post: YamiboPost,
    val localImageUrls: Map<String, String> = emptyMap(),
    val source: ReaderContentSource = ReaderContentSource.NETWORK,
)

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
    val downloadRepository = remember(context) { PostDownloadRepository.getInstance(context) }
    val downloadStatuses by downloadRepository.statuses.collectAsState()
    val downloadKey = remember(threadId, postId) { PostDownloadKey(threadId, postId) }
    val downloadStatus = downloadStatuses[downloadKey]
    val preferencesStore = remember(context) { ReaderPreferencesStore(context) }
    val imagePreferencesStore = remember(context) { ImageReaderPreferencesStore(context) }
    var preferences by remember { mutableStateOf(preferencesStore.load()) }
    var imagePreferences by remember { mutableStateOf(imagePreferencesStore.load()) }
    val reusableContent = initialContent?.takeUnless {
        needsReaderContentLoad(it.thread.id, it.post.id, threadId, postId)
    }
    var state: LoadState<ReaderContent> by remember(
        threadId,
        postId,
        reusableContent,
        offlineOnly,
    ) {
        mutableStateOf(
            reusableContent
                ?.takeUnless {
                    offlineOnly || it.source == ReaderContentSource.DOWNLOAD
                }
                ?.let { LoadState.Ready(it) }
                ?: LoadState.Loading,
        )
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var settingsVisible by remember { mutableStateOf(false) }
    var imageSettingsVisible by remember { mutableStateOf(false) }
    var deleteDownloadVisible by remember { mutableStateOf(false) }
    var readerMode by remember(threadId, postId) { mutableStateOf(preferencesStore.loadMode()) }
    var imageIndex by remember(threadId, postId) { mutableIntStateOf(0) }
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

    LaunchedEffect(threadId, postId, initialPage, reusableContent, offlineOnly) {
        state = load {
            resolveReaderContent(
                threadId = threadId,
                postId = postId,
                initialContent = reusableContent,
                offlineOnly = offlineOnly,
                offlineUnavailableMessage = offlineUnavailableMessage,
                loadDownloaded = {
                    downloadRepository.read(downloadKey)?.toReaderContent()
                },
                loadNetwork = {
                    var page = api.posts.getThreadPosts(
                        GetThreadPostsInput(threadId, initialPage.coerceAtLeast(1)),
                    )
                    var post = page.posts.firstOrNull { it.id == postId }
                    if (post == null) {
                        val resolvedPage = api.posts.findPostPage(threadId, postId)
                            ?: error(postNotFoundMessage)
                        page = api.posts.getThreadPosts(GetThreadPostsInput(threadId, resolvedPage))
                        post = page.posts.firstOrNull { it.id == postId }
                    }
                    ReaderContent(
                        thread = page.thread,
                        post = post ?: error(postNotFoundMessage),
                    )
                },
            )
        }
    }
    LaunchedEffect(downloadStatus?.completed, state is LoadState.Ready) {
        val current = (state as? LoadState.Ready)?.value ?: return@LaunchedEffect
        if (current.source == ReaderContentSource.NETWORK) {
            val downloaded = downloadStatus?.completed?.toReaderContent()
            val updated = if (downloaded == null) {
                current.copy(localImageUrls = emptyMap())
            } else {
                current.withDownloadedImages(downloaded)
            }
            if (updated != current) state = LoadState.Ready(updated)
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
        val contentTopPadding by animateDpAsState(
            targetValue = statusBarPadding + if (controlsVisible) 64.dp else 0.dp,
            animationSpec = tween(260),
            label = "reader-content-top-padding",
        )
        val contentBottomPadding by animateDpAsState(
            targetValue = navigationBarPadding + if (controlsVisible) 52.dp else 0.dp,
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
                                    it.phase == PostDownloadPhase.QUEUED ||
                                        it.phase == PostDownloadPhase.DOWNLOADING
                                }
                                ?.let {
                                    if (it.progress.totalImages > 0) {
                                        stringResource(
                                            R.string.reader_download_progress,
                                            it.progress.completedImages,
                                            it.progress.totalImages,
                                        )
                                    } else {
                                        stringResource(R.string.reader_download_saving)
                                    }
                                }
                            if (content != null) {
                                when (downloadStatus?.phase) {
                                    PostDownloadPhase.QUEUED,
                                    PostDownloadPhase.DOWNLOADING,
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

                                    PostDownloadPhase.COMPLETED -> IconButton(
                                        onClick = { deleteDownloadVisible = true },
                                    ) {
                                        Icon(
                                            Icons.Rounded.DownloadDone,
                                            stringResource(R.string.reader_remove_download),
                                        )
                                    }

                                    PostDownloadPhase.FAILED,
                                    null,
                                    -> IconButton(
                                        onClick = {
                                            val attachmentUrls = content.post.attachments
                                                .filter { it.isImage }
                                                .map { it.url }
                                            val remoteImages = postImageUrls(
                                                content.post.html,
                                                attachmentUrls,
                                            )
                                            coroutineScope.launch {
                                                try {
                                                    downloadRepository.enqueue(
                                                        PostDownloadRequest.create(
                                                            thread = content.thread,
                                                            post = content.post,
                                                            remoteImageUrls = remoteImages,
                                                        ),
                                                    )
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
                            val attachmentUrls = content?.post?.attachments
                                ?.filter { it.isImage }
                                ?.map { it.url }
                                .orEmpty()
                            val images = content?.let {
                                resolveReaderImageUrls(
                                    postImageUrls(it.post.html, attachmentUrls),
                                    it.localImageUrls,
                                )
                            }.orEmpty()
                            val effectiveMode = if (images.isNotEmpty()) readerMode else ReaderMode.TEXT
                            if (images.isNotEmpty()) {
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
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
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
                        val content = (state as? LoadState.Ready)?.value
                        val attachmentUrls = content?.post?.attachments
                            ?.filter { it.isImage }
                            ?.map { it.url }
                            .orEmpty()
                        val imageCount = content?.let {
                            resolveReaderImageUrls(
                                postImageUrls(it.post.html, attachmentUrls),
                                it.localImageUrls,
                            ).size
                        } ?: 0
                        if (readerMode == ReaderMode.IMAGES && imageCount > 0) {
                            ImageReaderBottomBar(
                                currentPage = imageIndex,
                                pageCount = imageCount,
                                preferences = imagePreferences,
                                onCurrentPageChange = { imageIndex = it },
                                onOpenSettings = { imageSettingsVisible = true },
                                onScaleTypeChange = { scaleType: ImageReaderScaleType ->
                                    updateImagePreferences(imagePreferences.copy(scaleType = scaleType))
                                },
                            )
                        } else {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val progress = if (scrollState.maxValue == 0) 0f
                                else scrollState.value.toFloat() / scrollState.maxValue
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    stringResource(R.string.reader_scroll_progress, (progress * 100).toInt()),
                                    modifier = Modifier.align(Alignment.End),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
                LoadContent(state, PaddingValues()) { content ->
                val openLink: (String) -> Unit = { url ->
                    when (val target = resolvePostLink(url)) {
                        is PostLinkTarget.Forum -> onForum(target.id)
                        is PostLinkTarget.Thread -> onThread(target)
                        is PostLinkTarget.External -> uriHandler.openUri(target.url)
                    }
                }
                val attachmentUrls = content.post.attachments.filter { it.isImage }.map { it.url }
                val images = remember(content.post.html, attachmentUrls, content.localImageUrls) {
                    resolveReaderImageUrls(
                        postImageUrls(content.post.html, attachmentUrls),
                        content.localImageUrls,
                    )
                }
                if (readerMode == ReaderMode.IMAGES && images.isNotEmpty()) {
                    ImageReader(
                        images = images,
                        threadId = content.post.threadId,
                        currentPage = imageIndex,
                        preferences = imagePreferences,
                        controlsVisible = controlsVisible,
                        onCurrentPageChange = { imageIndex = it },
                        onToggleControls = { controlsVisible = !controlsVisible },
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(scaffoldPadding)
                            .padding(top = contentTopPadding, bottom = contentBottomPadding)
                            .verticalScroll(scrollState)
                            .pointerInput(Unit) { detectTapGestures { controlsVisible = !controlsVisible } }
                            .padding(horizontal = 22.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            content.thread.subject,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(content.post.author.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (content.post.isOriginalPost) stringResource(R.string.reader_original_post)
                                else stringResource(R.string.reader_floor, content.post.number),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            content.post.createdAtText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider()
                        PostHtml(
                            html = content.post.html,
                            threadId = content.post.threadId,
                            attachmentUrls = attachmentUrls,
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

internal fun needsReaderContentLoad(
    cachedThreadId: Int?,
    cachedPostId: Int?,
    threadId: Int,
    postId: Int,
): Boolean = cachedThreadId != threadId || cachedPostId != postId

internal suspend fun resolveReaderContent(
    threadId: Int,
    postId: Int,
    initialContent: ReaderContent?,
    offlineOnly: Boolean,
    offlineUnavailableMessage: String,
    loadDownloaded: suspend () -> ReaderContent?,
    loadNetwork: suspend () -> ReaderContent,
): ReaderContent {
    val downloaded = try {
        loadDownloaded()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
    if (offlineOnly) {
        return downloaded ?: throw IllegalStateException(offlineUnavailableMessage)
    }

    val reusable = initialContent?.takeUnless {
        needsReaderContentLoad(it.thread.id, it.post.id, threadId, postId) ||
            (it.source == ReaderContentSource.DOWNLOAD && downloaded == null)
    }
    if (reusable != null) return reusable.withDownloadedImages(downloaded)

    return try {
        loadNetwork().withDownloadedImages(downloaded)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        downloaded ?: throw error
    }
}

private fun ReaderContent.withDownloadedImages(downloaded: ReaderContent?): ReaderContent {
    if (downloaded == null || downloaded.localImageUrls.isEmpty()) return this
    return copy(localImageUrls = downloaded.localImageUrls)
}

internal fun resolveReaderImageUrls(
    remoteImageUrls: List<String>,
    localImageUrls: Map<String, String>,
): List<String> = remoteImageUrls.map { resolvePostImageUrl(it, localImageUrls) }

internal fun DownloadedPost.toReaderContent(): ReaderContent = ReaderContent(
    thread = snapshot.thread,
    post = snapshot.post,
    localImageUrls = localImageUris,
    source = ReaderContentSource.DOWNLOAD,
)
