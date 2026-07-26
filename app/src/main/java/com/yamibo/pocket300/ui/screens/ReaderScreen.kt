package com.yamibo.pocket300.ui.screens

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.yamibo.pocket300.ui.resolvePostLink
import kotlinx.coroutines.launch

internal data class ReaderContent(val thread: YamiboThreadDetails, val post: YamiboPost)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreen(
    threadId: Int,
    postId: Int,
    initialPage: Int,
    initialContent: ReaderContent?,
    onBack: () -> Unit,
    onForum: (Int) -> Unit,
    onThread: (PostLinkTarget.Thread) -> Unit,
) {
    val context = LocalContext.current
    val preferencesStore = remember(context) { ReaderPreferencesStore(context) }
    val imagePreferencesStore = remember(context) { ImageReaderPreferencesStore(context) }
    var preferences by remember { mutableStateOf(preferencesStore.load()) }
    var imagePreferences by remember { mutableStateOf(imagePreferencesStore.load()) }
    val reusableContent = initialContent?.takeUnless {
        needsReaderContentLoad(it.thread.id, it.post.id, threadId, postId)
    }
    var state: LoadState<ReaderContent> by remember(threadId, postId, reusableContent) {
        mutableStateOf(reusableContent?.let { LoadState.Ready(it) } ?: LoadState.Loading)
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var settingsVisible by remember { mutableStateOf(false) }
    var imageSettingsVisible by remember { mutableStateOf(false) }
    var readerMode by remember(threadId, postId) { mutableStateOf(preferencesStore.loadMode()) }
    var imageIndex by remember(threadId, postId) { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val view = LocalView.current
    val postNotFoundMessage = stringResource(R.string.reader_post_not_found)

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

    LaunchedEffect(threadId, postId, initialPage, reusableContent) {
        if (reusableContent != null) return@LaunchedEffect
        state = load {
            var page = api.posts.getThreadPosts(GetThreadPostsInput(threadId, initialPage.coerceAtLeast(1)))
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
                            Text(
                                content?.thread?.subject ?: stringResource(R.string.reader_default_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.reader_back))
                            }
                        },
                        actions = {
                            val content = (state as? LoadState.Ready)?.value
                            val attachmentUrls = content?.post?.attachments
                                ?.filter { it.isImage }
                                ?.map { it.url }
                                .orEmpty()
                            val images = content?.let {
                                postImageUrls(it.post.html, attachmentUrls)
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
                            postImageUrls(it.post.html, attachmentUrls).size
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
            LoadContent(state, PaddingValues()) { content ->
                val openLink: (String) -> Unit = { url ->
                    when (val target = resolvePostLink(url)) {
                        is PostLinkTarget.Forum -> onForum(target.id)
                        is PostLinkTarget.Thread -> onThread(target)
                        is PostLinkTarget.External -> uriHandler.openUri(target.url)
                    }
                }
                val attachmentUrls = content.post.attachments.filter { it.isImage }.map { it.url }
                val images = remember(content.post.html, attachmentUrls) {
                    postImageUrls(content.post.html, attachmentUrls)
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
                        )
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
