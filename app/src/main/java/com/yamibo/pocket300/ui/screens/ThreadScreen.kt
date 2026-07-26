package com.yamibo.pocket300.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.CommentOnPostInput
import com.yamibo.pocket300.api.GetThreadPostsInput
import com.yamibo.pocket300.api.POST_COMMENT_MAX_LENGTH
import com.yamibo.pocket300.api.POST_RATING_REASON_MAX_LENGTH
import com.yamibo.pocket300.api.ReplyToThreadInput
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboPostRatingForm
import com.yamibo.pocket300.api.YamiboPostRatingOption
import com.yamibo.pocket300.api.postRatingReasonLength
import com.yamibo.pocket300.api.YamiboThreadPoll
import com.yamibo.pocket300.api.YamiboThreadPostsPage
import com.yamibo.pocket300.data.ReadingHistoryDatabase
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.LocalReadingHistory
import com.yamibo.pocket300.ui.PostHtml
import com.yamibo.pocket300.ui.PostLinkTarget
import com.yamibo.pocket300.ui.ReaderPreferences
import com.yamibo.pocket300.ui.ReaderTone
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.components.AutoLoadNextPage
import com.yamibo.pocket300.ui.components.ListFooter
import com.yamibo.pocket300.ui.components.PostAuthorAvatar
import com.yamibo.pocket300.ui.load
import com.yamibo.pocket300.ui.plainText
import com.yamibo.pocket300.ui.resolvePostLink
import com.yamibo.pocket300.ui.theme.ThreadTypography
import com.yamibo.pocket300.ui.theme.rememberThreadTypography
import com.yamibo.pocket300.ui.viewmodels.PostRatingResult
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
    onReader: (ReaderContent, Int) -> Unit,
    onThread: (PostLinkTarget.Thread) -> Unit,
) {
    val viewModel: ThreadViewModel = viewModel()
    val context = LocalContext.current
    val historyDatabase = remember(context) { ReadingHistoryDatabase.getInstance(context) }
    val readingHistory = LocalReadingHistory.current
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
    val threadTypography = rememberThreadTypography()
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
    var replyDraft by rememberSaveable(threadId) { mutableStateOf("") }
    var replySubmitting by remember(threadId) { mutableStateOf(false) }
    var commentTargetPostId by rememberSaveable(threadId) { mutableIntStateOf(0) }
    var commentTargetPostNumber by rememberSaveable(threadId) { mutableIntStateOf(0) }
    var commentTargetAuthorName by rememberSaveable(threadId) { mutableStateOf("") }
    var commentTargetIsOriginalPost by rememberSaveable(threadId) { mutableStateOf(false) }
    var commentDraft by rememberSaveable(threadId) { mutableStateOf("") }
    var commentSubmitting by remember(threadId) { mutableStateOf(false) }
    var originalPosterOnly by rememberSaveable(threadId) { mutableStateOf(false) }
    var trackReadingProgress by rememberSaveable(threadId) { mutableStateOf(true) }
    var lastVisibleFloor by rememberSaveable(threadId) {
        mutableIntStateOf(initialFloor.coerceAtLeast(1))
    }
    val showThreadTitle by remember {
        derivedStateOf { shouldShowThreadTitle(listState.firstVisibleItemIndex) }
    }
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
    val isRead = threadId in readingHistory
    val markedReadMessage = stringResource(R.string.thread_marked_read)
    val markedUnreadMessage = stringResource(R.string.thread_marked_unread)
    val replySubmittedMessage = stringResource(R.string.thread_reply_submitted)
    val replyPendingModerationMessage =
        stringResource(R.string.thread_reply_pending_moderation)
    val commentSubmittedMessage = stringResource(R.string.thread_comment_submitted)
    val commentSubmittedRefreshFailedMessage =
        stringResource(R.string.thread_comment_submitted_refresh_failed)
    val ratingSubmittedMessage = stringResource(R.string.thread_rating_submitted)
    val ratingSubmittedRefreshFailedMessage =
        stringResource(R.string.thread_rating_submitted_refresh_failed)
    val postRatingResult = viewModel.postRatingResult
    LaunchedEffect(postRatingResult) {
        when (val result = postRatingResult ?: return@LaunchedEffect) {
            is PostRatingResult.Submitted ->
                Toast.makeText(
                    context,
                    if (result.refreshSucceeded) {
                        ratingSubmittedMessage
                    } else {
                        ratingSubmittedRefreshFailedMessage
                    },
                    if (result.refreshSucceeded) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                ).show()

            is PostRatingResult.Failed ->
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        }
        viewModel.consumePostRatingResult()
    }
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
        if (trackReadingProgress) {
            loadedThread?.let { thread ->
                historyDatabase.record(
                    thread,
                    lastVisibleFloor,
                )
            }
        }
    }
    LaunchedEffect(listState, loadedContent, restoredFloor, trackReadingProgress) {
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
                lastVisibleFloor = floor
                if (trackReadingProgress) {
                    delay(300.milliseconds)
                    historyDatabase.record(thread, floor)
                }
            }
    }
    ScreenScaffold(
        modifier = with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState("thread-$threadId"),
                animatedVisibilityScope
            )
        },
        title = loadedThread?.subject.takeIf { showThreadTitle }.orEmpty(),
        onBack = onBack,
        onRefresh = { viewModel.refresh(); pageNumber = 1; reload++ },
        isRefreshing = viewModel.isRefreshing,
        onTopBarDoubleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
        actions = {
            loadedThread?.let { thread ->
                val action = threadReadAction(isRead)
                IconButton(
                    onClick = {
                        when (action) {
                            ThreadReadAction.MARK_READ -> {
                                trackReadingProgress = true
                                historyDatabase.record(thread, lastVisibleFloor)
                                Toast.makeText(
                                    context,
                                    markedReadMessage,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }

                            ThreadReadAction.MARK_UNREAD -> {
                                trackReadingProgress = false
                                historyDatabase.remove(threadId)
                                Toast.makeText(
                                    context,
                                    markedUnreadMessage,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = when (action) {
                            ThreadReadAction.MARK_READ -> Icons.Rounded.DoneAll
                            ThreadReadAction.MARK_UNREAD -> Icons.Rounded.RemoveDone
                        },
                        contentDescription = stringResource(
                            when (action) {
                                ThreadReadAction.MARK_READ -> R.string.thread_mark_read
                                ThreadReadAction.MARK_UNREAD -> R.string.thread_mark_unread
                            },
                        ),
                    )
                }
            }
        },
        bottomBar = {
            loadedContent?.let { content ->
                ThreadReplyBar(
                    draft = replyDraft,
                    submitting = replySubmitting,
                    threadClosed = content.page.thread.isClosed,
                    onDraftChange = { replyDraft = it },
                    onSubmit = {
                        if (!replySubmitting) {
                            val message = replyDraft.trim()
                            if (message.isNotEmpty()) {
                                replySubmitting = true
                                coroutineScope.launch {
                                    val result = load {
                                        api.posts.replyToThread(
                                            ReplyToThreadInput(
                                                forumId = content.page.thread.forumId,
                                                threadId = threadId,
                                                message = message,
                                            ),
                                        )
                                    }
                                    when (result) {
                                        is LoadState.Ready -> {
                                            replyDraft = ""
                                            originalPosterOnly = false
                                            targetFloor = 0
                                            if (result.value.pendingModeration) {
                                                targetPostId = 0
                                                restoredFloor = true
                                            } else {
                                                targetPostId = result.value.postId
                                                restoredFloor = false
                                                pageNumber = pageForNewReply(
                                                    totalPosts = content.page.pagination.totalPosts,
                                                    pageSize = content.page.pagination.pageSize,
                                                )
                                            }
                                            viewModel.invalidate()
                                            reload++
                                            Toast.makeText(
                                                context,
                                                if (result.value.pendingModeration) {
                                                    replyPendingModerationMessage
                                                } else {
                                                    replySubmittedMessage
                                                },
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }

                                        is LoadState.Failed -> Toast.makeText(
                                            context,
                                            result.message,
                                            Toast.LENGTH_LONG,
                                        ).show()

                                        LoadState.Loading -> Unit
                                    }
                                    replySubmitting = false
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        LoadContent(state, padding) { content ->
            val page = content.page
            AutoLoadNextPage(
                listState = listState,
                hasNextPage = page.pagination.hasNextPage && !content.isLoadingMore,
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
                        typography = threadTypography,
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
                page.poll?.let { poll -> item { PollCard(poll, threadTypography) } }
                items(content.posts, key = { it.id }, contentType = { "post" }) { post ->
                    PostCard(
                        post = post,
                        typography = threadTypography,
                        onForum = onForum,
                        onRatings = { onRatings(post.threadId, post.id) },
                        commentEnabled = page.canComment &&
                            !page.thread.isClosed &&
                            !commentSubmitting,
                        onComment = {
                            commentTargetPostId = post.id
                            commentTargetPostNumber = post.number
                            commentTargetAuthorName = post.author.name
                            commentTargetIsOriginalPost = post.isOriginalPost
                            commentDraft = ""
                        },
                        onRate = { viewModel.openPostRating(post) },
                        onReader = {
                            val postPage = ((post.position - 1) / page.pagination.pageSize) + 1
                            onReader(
                                ReaderContent(thread = page.thread, post = post),
                                postPage.coerceAtLeast(1),
                            )
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
                        isLoadingMore = content.isLoadingMore,
                        onLoadMore = { pageNumber = page.pagination.page + 1 },
                    )
                }
            }
        }
    }
    if (commentTargetPostId > 0) {
        PostCommentDialog(
            authorName = commentTargetAuthorName,
            draft = commentDraft,
            isOriginalPost = commentTargetIsOriginalPost,
            postNumber = commentTargetPostNumber,
            submitting = commentSubmitting,
            onDraftChange = { proposed ->
                if (proposed.length <= POST_COMMENT_MAX_LENGTH) commentDraft = proposed
            },
            onDismiss = {
                if (!commentSubmitting) {
                    commentTargetPostId = 0
                    commentTargetPostNumber = 0
                    commentTargetAuthorName = ""
                    commentTargetIsOriginalPost = false
                    commentDraft = ""
                }
            },
            onSubmit = {
                if (!commentSubmitting) {
                    val message = commentDraft.trim()
                    val forumId = loadedThread?.forumId
                    if (message.isNotEmpty() && forumId != null) {
                        val postId = commentTargetPostId
                        commentSubmitting = true
                        coroutineScope.launch {
                            val result = load {
                                api.posts.commentOnPost(
                                    CommentOnPostInput(
                                        forumId = forumId,
                                        threadId = threadId,
                                        postId = postId,
                                        message = message,
                                    ),
                                )
                            }
                            when (result) {
                                is LoadState.Ready -> {
                                    commentTargetPostId = 0
                                    commentTargetPostNumber = 0
                                    commentTargetAuthorName = ""
                                    commentTargetIsOriginalPost = false
                                    commentDraft = ""
                                    val refreshed = load {
                                        api.posts.getPostComments(threadId, postId)
                                    }
                                    val refreshSucceeded =
                                        refreshed is LoadState.Ready &&
                                            viewModel.updatePostComments(
                                                postId,
                                                refreshed.value,
                                            )
                                    Toast.makeText(
                                        context,
                                        if (refreshSucceeded) {
                                            commentSubmittedMessage
                                        } else {
                                            commentSubmittedRefreshFailedMessage
                                        },
                                        if (refreshSucceeded) Toast.LENGTH_SHORT
                                        else Toast.LENGTH_LONG,
                                    ).show()
                                }

                                is LoadState.Failed -> Toast.makeText(
                                    context,
                                    result.message,
                                    Toast.LENGTH_LONG,
                                ).show()

                                LoadState.Loading -> Unit
                            }
                            commentSubmitting = false
                        }
                    }
                }
            },
        )
    }
    viewModel.postRatingDialogState?.let { ratingState ->
        PostRatingDialog(
            authorName = ratingState.target.authorName,
            formState = ratingState.formState,
            isOriginalPost = ratingState.target.isOriginalPost,
            postNumber = ratingState.target.postNumber,
            reason = ratingState.reason,
            scores = ratingState.scores,
            sendReasonPm = ratingState.sendReasonPm,
            submitting = ratingState.submitting,
            onScoreChange = viewModel::updatePostRatingScore,
            onReasonChange = viewModel::updatePostRatingReason,
            onSendReasonPmChange = viewModel::updatePostRatingSendReasonPm,
            onRetry = viewModel::retryPostRatingForm,
            onDismiss = viewModel::dismissPostRating,
            onSubmit = viewModel::submitPostRating,
        )
    }
}

internal fun shouldShowThreadTitle(firstVisibleItemIndex: Int): Boolean =
    firstVisibleItemIndex > 0

internal fun shouldShowRatingsSummary(ratingCount: Int): Boolean =
    ratingCount > 0

internal fun postRatingScoreChoices(option: YamiboPostRatingOption): List<Int> {
    if (option.remainingToday <= 0) return emptyList()
    val minimum = maxOf(option.minScore, -option.remainingToday)
    val maximum = minOf(option.maxScore, option.remainingToday)
    if (minimum > maximum) return emptyList()
    return (minimum..maximum).filter { it != 0 }
}

internal fun adjacentPostRatingScore(
    option: YamiboPostRatingOption,
    current: Int,
    direction: Int,
): Int {
    require(direction == -1 || direction == 1) { "direction must be -1 or 1" }
    val choices = (postRatingScoreChoices(option) + 0).distinct().sorted()
    val currentIndex = choices.indexOf(current).takeIf { it >= 0 }
        ?: choices.indexOf(0)
    val nextIndex = (currentIndex + direction).coerceIn(0, choices.lastIndex)
    return choices[nextIndex]
}

internal fun canSubmitPostRating(
    form: YamiboPostRatingForm,
    scores: Map<Int, Int>,
): Boolean {
    val knownCreditIds = form.options.mapTo(mutableSetOf()) { it.creditId }
    if (scores.keys.any { it !in knownCreditIds }) return false
    val selectedScores = form.options.map { option ->
        option to (scores[option.creditId] ?: 0)
    }
    return selectedScores.any { (_, score) -> score != 0 } &&
        selectedScores.all { (option, score) ->
            score == 0 || score in postRatingScoreChoices(option)
        }
}

internal fun pageForNewReply(totalPosts: Int, pageSize: Int): Int {
    require(totalPosts >= 0) { "totalPosts must not be negative" }
    require(pageSize > 0) { "pageSize must be a positive integer" }
    return totalPosts / pageSize + 1
}

internal enum class ThreadReadAction { MARK_READ, MARK_UNREAD }

internal fun threadReadAction(isRead: Boolean): ThreadReadAction =
    if (isRead) ThreadReadAction.MARK_UNREAD else ThreadReadAction.MARK_READ

@Composable
private fun PostCommentDialog(
    authorName: String,
    draft: String,
    isOriginalPost: Boolean,
    postNumber: Int,
    submitting: Boolean,
    onDraftChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.thread_comment_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isOriginalPost) {
                        stringResource(R.string.thread_comment_target_original, authorName)
                    } else {
                        stringResource(
                            R.string.thread_comment_target_floor,
                            authorName,
                            postNumber,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submitting,
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text(stringResource(R.string.thread_comment_hint)) },
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.thread_comment_character_count,
                                draft.length,
                                POST_COMMENT_MAX_LENGTH,
                            ),
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = draft.isNotBlank() && !submitting,
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        if (submitting) R.string.thread_comment_submitting
                        else R.string.thread_comment_submit,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text(stringResource(R.string.thread_comment_cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostRatingDialog(
    authorName: String,
    formState: LoadState<YamiboPostRatingForm>,
    isOriginalPost: Boolean,
    postNumber: Int,
    reason: String,
    scores: Map<Int, Int>,
    sendReasonPm: Boolean,
    submitting: Boolean,
    onScoreChange: (Int, Int) -> Unit,
    onReasonChange: (String) -> Unit,
    onSendReasonPmChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.thread_rating_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (isOriginalPost) {
                        stringResource(R.string.thread_rating_target_original, authorName)
                    } else {
                        stringResource(
                            R.string.thread_rating_target_floor,
                            authorName,
                            postNumber,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (formState) {
                    LoadState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(stringResource(R.string.thread_rating_loading))
                    }

                    is LoadState.Failed -> Text(
                        formState.message,
                        color = MaterialTheme.colorScheme.error,
                    )

                    is LoadState.Ready -> {
                        formState.value.options.forEach { option ->
                            PostRatingOptionRow(
                                option = option,
                                score = scores[option.creditId] ?: 0,
                                enabled = !submitting,
                                onScoreChange = { onScoreChange(option.creditId, it) },
                            )
                        }
                        if (formState.value.reasonSuggestions.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                formState.value.reasonSuggestions.forEach { suggestion ->
                                    FilterChip(
                                        selected = reason == suggestion,
                                        onClick = { onReasonChange(suggestion) },
                                        enabled = !submitting,
                                        label = { Text(suggestion) },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = reason,
                            onValueChange = onReasonChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !submitting,
                            minLines = 2,
                            maxLines = 4,
                            placeholder = {
                                Text(stringResource(R.string.thread_rating_reason_hint))
                            },
                            supportingText = {
                                Text(
                                    stringResource(
                                        R.string.thread_rating_reason_character_count,
                                        postRatingReasonLength(reason),
                                        POST_RATING_REASON_MAX_LENGTH,
                                    ),
                                )
                            },
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .toggleable(
                                    value = sendReasonPm,
                                    enabled = !submitting &&
                                        !formState.value.sendReasonPmLocked,
                                    role = Role.Checkbox,
                                    onValueChange = onSendReasonPmChange,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = sendReasonPm,
                                onCheckedChange = null,
                                enabled = !submitting && !formState.value.sendReasonPmLocked,
                            )
                            Text(
                                stringResource(
                                    if (formState.value.sendReasonPmLocked) {
                                        R.string.thread_rating_notify_author_locked
                                    } else {
                                        R.string.thread_rating_notify_author
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (!canSubmitPostRating(formState.value, scores)) {
                            Text(
                                stringResource(
                                    if (
                                        formState.value.options.all {
                                            postRatingScoreChoices(it).isEmpty()
                                        }
                                    ) {
                                        R.string.thread_rating_no_remaining
                                    } else {
                                        R.string.thread_rating_select_score
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (formState) {
                LoadState.Loading -> TextButton(onClick = {}, enabled = false) {
                    Text(stringResource(R.string.thread_rating_loading_short))
                }

                is LoadState.Failed -> TextButton(
                    onClick = onRetry,
                    enabled = !submitting,
                ) {
                    Text(stringResource(R.string.thread_rating_retry))
                }

                is LoadState.Ready -> TextButton(
                    onClick = onSubmit,
                    enabled = canSubmitPostRating(formState.value, scores) && !submitting,
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(
                            if (submitting) R.string.thread_rating_submitting
                            else R.string.thread_rating_submit,
                        ),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text(stringResource(R.string.thread_rating_cancel))
            }
        },
    )
}

@Composable
private fun PostRatingOptionRow(
    option: YamiboPostRatingOption,
    score: Int,
    enabled: Boolean,
    onScoreChange: (Int) -> Unit,
) {
    val lowerScore = adjacentPostRatingScore(option, score, -1)
    val higherScore = adjacentPostRatingScore(option, score, 1)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(option.creditName, style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(
                R.string.thread_rating_credit_limits,
                option.minScore,
                option.maxScore,
                option.remainingToday,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedIconButton(
                onClick = { onScoreChange(lowerScore) },
                enabled = enabled && lowerScore != score,
            ) {
                Icon(
                    Icons.Rounded.Remove,
                    contentDescription = stringResource(
                        R.string.thread_rating_decrease,
                        option.creditName,
                    ),
                )
            }
            Text(
                text = if (score > 0) "+$score" else score.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            OutlinedIconButton(
                onClick = { onScoreChange(higherScore) },
                enabled = enabled && higherScore != score,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(
                        R.string.thread_rating_increase,
                        option.creditName,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ThreadReplyBar(
    draft: String,
    submitting: Boolean,
    threadClosed: Boolean,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    BottomAppBar(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            enabled = !threadClosed && !submitting,
            maxLines = 5,
            placeholder = {
                Text(
                    stringResource(
                        if (threadClosed) R.string.thread_reply_closed
                        else R.string.thread_reply_hint,
                    ),
                )
            },
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSubmit,
            enabled = draft.isNotBlank() && !submitting && !threadClosed,
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.thread_reply_action),
                )
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
    typography: ThreadTypography,
    onForum: (Int) -> Unit,
    onRatings: () -> Unit,
    commentEnabled: Boolean,
    onComment: () -> Unit,
    onRate: () -> Unit,
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PostAuthorAvatar(post.author, size = 40.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                text = post.author.name,
                                style = typography.byline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            post.createdAtText,
                            style = typography.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ) {
                        Text(
                            if (post.isOriginalPost) "楼主" else "${post.number} 楼",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            style = typography.label,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onComment, enabled = commentEnabled) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Comment,
                            contentDescription = if (post.isOriginalPost) {
                                stringResource(R.string.thread_comment_original_post_action)
                            } else {
                                stringResource(R.string.thread_comment_post_action, post.number)
                            },
                        )
                    }
                    IconButton(onClick = onRate) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = if (post.isOriginalPost) {
                                stringResource(R.string.thread_rating_original_post_action)
                            } else {
                                stringResource(R.string.thread_rating_post_action, post.number)
                            },
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
                textStyle = typography.body,
            )
            if (shouldShowRatingsSummary(post.ratingCount)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.rating_count, post.ratingCount),
                            style = typography.heading,
                        )
                        TextButton(onClick = onRatings) {
                            Text(
                                stringResource(R.string.rating_view_all, post.ratingCount),
                                style = typography.action,
                            )
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
                                style = typography.supporting,
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
    typography: ThreadTypography,
    isFavorited: Boolean,
    favoriteBusy: Boolean,
    originalPosterOnly: Boolean,
    onFavorite: () -> Unit,
    onOriginalPosterOnlyChange: (Boolean) -> Unit,
) {
    val thread = page.thread
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = thread.subject,
                        style = typography.heading,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onFavorite, enabled = !favoriteBusy) {
                    if (favoriteBusy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = if (isFavorited) {
                                Icons.Rounded.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = stringResource(
                                if (isFavorited) R.string.reader_unfavorite else R.string.reader_favorite
                            ),
                            tint = if (isFavorited) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PostAuthorAvatar(thread.author, size = 32.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SelectionContainer {
                        Text(
                            text = thread.author.name,
                            style = typography.byline,
                        )
                    }
                    Text(
                        "${thread.replyCount} 回复 · ${thread.viewCount} 浏览 · 热度 ${thread.heat}",
                        style = typography.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = originalPosterOnly,
                    onClick = { onOriginalPosterOnlyChange(!originalPosterOnly) },
                    label = {
                        Text(
                            stringResource(R.string.thread_original_poster_only),
                            style = typography.action,
                        )
                    },
                    enabled = thread.author.id != null,
                )
                if (thread.isClosed) Badge { Text("已关闭", style = typography.metadata) }
                if (thread.price > 0) Badge {
                    Text("${thread.price} 积分", style = typography.metadata)
                }
                if (thread.hasAttachment) Badge { Text("附件", style = typography.metadata) }
            }
        }
    }
}

@Composable
private fun PollCard(poll: YamiboThreadPoll, typography: ThreadTypography) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("投票", style = typography.heading)
            Text(
                "${if (poll.multiple) "最多选 ${poll.maxChoices} 项" else "单选"} · ${poll.voterCount} 人参与",
                style = typography.supporting,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            poll.options.forEach { option ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = plainText(option.text),
                            modifier = Modifier.weight(1f),
                            style = typography.body,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("${"%.1f".format(option.percentage)}%", style = typography.label)
                    }
                    LinearProgressIndicator(
                        progress = { (option.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${option.voteCount} 票", style = typography.metadata)
                }
            }
            if (!poll.resultsVisible) Text(
                "投票后才可查看完整结果",
                style = typography.label
            )
        }
    }
}
