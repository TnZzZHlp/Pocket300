package com.yamibo.pocket300.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamibo.pocket300.api.GetThreadPostsInput
import com.yamibo.pocket300.api.POST_RATING_REASON_MAX_LENGTH
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboPostComment
import com.yamibo.pocket300.api.YamiboPostRatingForm
import com.yamibo.pocket300.api.YamiboPostRatingOption
import com.yamibo.pocket300.api.YamiboThreadPostsPage
import com.yamibo.pocket300.api.postRatingReasonLength
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class ThreadContent(
    val page: YamiboThreadPostsPage,
    val posts: List<YamiboPost>,
    val isLoadingMore: Boolean = false,
)

internal data class PostRatingTarget(
    val threadId: Int,
    val postId: Int,
    val postNumber: Int,
    val authorName: String,
    val isOriginalPost: Boolean,
)

internal data class PostRatingDialogState(
    val target: PostRatingTarget,
    val formState: LoadState<YamiboPostRatingForm> = LoadState.Loading,
    val scores: Map<Int, Int> = emptyMap(),
    val reason: String = "",
    val sendReasonPm: Boolean = false,
    val submitting: Boolean = false,
)

internal sealed interface PostRatingResult {
    data class Submitted(val refreshSucceeded: Boolean) : PostRatingResult
    data class Failed(val message: String) : PostRatingResult
}

internal class ThreadPostsRequestTracker {
    private var lastInput: GetThreadPostsInput? = null

    fun shouldLoad(input: GetThreadPostsInput): Boolean {
        if (input == lastInput) return false
        lastInput = input
        return true
    }

    fun invalidate() {
        lastInput = null
    }
}

internal class ThreadViewModel : ViewModel() {
    var state: LoadState<ThreadContent> by mutableStateOf(LoadState.Loading)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var postRatingDialogState: PostRatingDialogState? by mutableStateOf(null)
        private set

    var postRatingResult: PostRatingResult? by mutableStateOf(null)
        private set

    private val requestTracker = ThreadPostsRequestTracker()
    private var loadJob: Job? = null
    private var postRatingFormJob: Job? = null
    private var postRatingSubmitJob: Job? = null
    private val pendingPostUpdates = mutableMapOf<Int, YamiboPost>()
    private var postRatingFormGeneration = 0
    private var requestGeneration = 0

    fun loadPosts(input: GetThreadPostsInput) {
        if (!requestTracker.shouldLoad(input)) return
        loadJob?.cancel()
        val generation = ++requestGeneration
        val previous = (state as? LoadState.Ready)?.value
        if (input.page == 1) {
            state = LoadState.Loading
        } else if (previous != null) {
            state = LoadState.Ready(previous.copy(isLoadingMore = true))
        }
        loadJob = viewModelScope.launch {
            try {
                state = when (val result = load { api.posts.getThreadPosts(input) }) {
                    is LoadState.Ready -> {
                        val latest = (state as? LoadState.Ready)?.value
                        val mergedPosts = mergeThreadPosts(
                            existing = latest?.posts ?: previous?.posts.orEmpty(),
                            loaded = result.value.posts,
                            page = input.page,
                        )
                        LoadState.Ready(
                            ThreadContent(
                                page = result.value,
                                posts = applyPendingPostUpdates(mergedPosts),
                            ),
                        )
                    }
                    is LoadState.Failed -> result
                    LoadState.Loading -> LoadState.Loading
                }
            } finally {
                if (generation == requestGeneration) isRefreshing = false
            }
        }
    }

    fun refresh() {
        isRefreshing = true
        requestGeneration++
        invalidate()
    }

    fun updatePostComments(postId: Int, comments: List<YamiboPostComment>): Boolean {
        val content = (state as? LoadState.Ready)?.value ?: return false
        val updatedPosts = replacePostComments(content.posts, postId, comments)
        if (updatedPosts === content.posts) return false
        state = LoadState.Ready(content.copy(posts = updatedPosts))
        return true
    }

    fun updatePost(post: YamiboPost): Boolean {
        val content = (state as? LoadState.Ready)?.value ?: return false
        val updatedPosts = replacePost(content.posts, post)
        if (updatedPosts === content.posts) return false
        state = LoadState.Ready(content.copy(posts = updatedPosts))
        return true
    }

    fun openPostRating(post: YamiboPost) {
        val current = postRatingDialogState
        if (
            current?.submitting == true ||
            postRatingSubmitJob?.isActive == true ||
            postRatingResult != null
        ) {
            return
        }
        val target = postRatingTarget(post)
        if (current?.target == target) return

        cancelPostRatingFormLoad()
        postRatingDialogState = createPostRatingDialogState(post)
        loadPostRatingForm(target)
    }

    fun retryPostRatingForm() {
        val current = postRatingDialogState ?: return
        if (current.submitting || postRatingFormJob?.isActive == true) return

        postRatingDialogState = current.copy(formState = LoadState.Loading)
        loadPostRatingForm(current.target)
    }

    fun updatePostRatingScore(creditId: Int, score: Int) {
        val current = postRatingDialogState ?: return
        postRatingDialogState = withPostRatingScore(current, creditId, score)
    }

    fun updatePostRatingReason(reason: String) {
        val current = postRatingDialogState ?: return
        postRatingDialogState = withPostRatingReason(current, reason)
    }

    fun updatePostRatingSendReasonPm(sendReasonPm: Boolean) {
        val current = postRatingDialogState ?: return
        postRatingDialogState = withPostRatingSendReasonPm(current, sendReasonPm)
    }

    fun dismissPostRating() {
        val current = postRatingDialogState ?: return
        if (current.submitting) return
        cancelPostRatingFormLoad()
        postRatingDialogState = null
    }

    fun submitPostRating() {
        val current = postRatingDialogState ?: return
        val form = (current.formState as? LoadState.Ready)?.value ?: return
        if (
            postRatingSubmitJob?.isActive == true ||
            postRatingResult != null ||
            !canSubmitPostRating(current)
        ) {
            return
        }

        val target = current.target
        val scores = current.scores
        val reason = current.reason
        val sendReasonPm = current.sendReasonPm
        postRatingDialogState = current.copy(submitting = true)
        postRatingSubmitJob = viewModelScope.launch {
            when (
                val result = load {
                    api.posts.ratePost(
                        form = form,
                        scores = scores,
                        reason = reason,
                        sendReasonPm = sendReasonPm,
                    )
                }
            ) {
                is LoadState.Ready -> {
                    postRatingDialogState = null
                    cancelPostRatingFormLoad()
                    val refreshed = load {
                        api.posts.getPost(target.threadId, target.postId)
                    }
                    val refreshSucceeded = refreshed is LoadState.Ready
                    if (refreshed is LoadState.Ready) {
                        val ratingsCount = if (refreshed.value.ratingCount == 0) {
                            when (
                                val ratings = load {
                                    api.posts.getPostRatings(target.threadId, target.postId)
                                }
                            ) {
                                is LoadState.Ready -> ratings.value.size
                                is LoadState.Failed, LoadState.Loading -> null
                            }
                        } else {
                            null
                        }
                        updateOrDeferPost(
                            withSubmittedPostRating(
                                post = refreshed.value,
                                ratingsCount = ratingsCount,
                            ),
                        )
                    }
                    postRatingResult = PostRatingResult.Submitted(refreshSucceeded)
                }

                is LoadState.Failed -> {
                    postRatingDialogState
                        ?.takeIf { it.target == target && it.submitting }
                        ?.let { postRatingDialogState = it.copy(submitting = false) }
                    postRatingResult = PostRatingResult.Failed(result.message)
                }

                LoadState.Loading -> Unit
            }
        }
    }

    fun consumePostRatingResult(): PostRatingResult? {
        val result = postRatingResult
        postRatingResult = null
        return result
    }

    fun invalidate() {
        loadJob?.cancel()
        requestTracker.invalidate()
    }

    private fun loadPostRatingForm(target: PostRatingTarget) {
        postRatingFormJob?.cancel()
        val generation = ++postRatingFormGeneration
        postRatingFormJob = viewModelScope.launch {
            val result = load {
                api.posts.getPostRatingForm(target.threadId, target.postId)
            }
            val current = postRatingDialogState ?: return@launch
            if (
                generation != postRatingFormGeneration ||
                current.target != target ||
                current.submitting
            ) {
                return@launch
            }
            postRatingDialogState = withPostRatingFormResult(current, result)
        }
    }

    private fun cancelPostRatingFormLoad() {
        postRatingFormGeneration++
        postRatingFormJob?.cancel()
        postRatingFormJob = null
    }

    private fun updateOrDeferPost(post: YamiboPost) {
        pendingPostUpdates[post.id] = post
        if (updatePost(post)) {
            pendingPostUpdates.remove(post.id)
        }
    }

    private fun applyPendingPostUpdates(posts: List<YamiboPost>): List<YamiboPost> {
        if (pendingPostUpdates.isEmpty()) return posts
        val appliedIds = posts.mapNotNullTo(mutableSetOf()) { post ->
            post.id.takeIf(pendingPostUpdates::containsKey)
        }
        if (appliedIds.isEmpty()) return posts
        val updated = replacePosts(
            posts = posts,
            replacements = appliedIds.mapNotNull(pendingPostUpdates::get),
        )
        appliedIds.forEach(pendingPostUpdates::remove)
        return updated
    }
}

internal fun postRatingTarget(post: YamiboPost) = PostRatingTarget(
    threadId = post.threadId,
    postId = post.id,
    postNumber = post.number,
    authorName = post.author.name,
    isOriginalPost = post.isOriginalPost,
)

internal fun createPostRatingDialogState(post: YamiboPost) = PostRatingDialogState(
    target = postRatingTarget(post),
)

internal fun withPostRatingFormResult(
    state: PostRatingDialogState,
    result: LoadState<YamiboPostRatingForm>,
): PostRatingDialogState {
    if (result !is LoadState.Ready) return state.copy(formState = result)

    val initialized = state.scores.isNotEmpty()
    val form = result.value
    return state.copy(
        formState = result,
        scores = form.options.associate { option ->
            val previousScore = state.scores[option.creditId] ?: 0
            option.creditId to if (isAllowedPostRatingScore(option, previousScore)) {
                previousScore
            } else {
                0
            }
        },
        sendReasonPm = when {
            !initialized -> form.sendReasonPmByDefault
            form.sendReasonPmLocked -> form.sendReasonPmByDefault
            else -> state.sendReasonPm
        },
    )
}

internal fun withPostRatingScore(
    state: PostRatingDialogState,
    creditId: Int,
    score: Int,
): PostRatingDialogState {
    if (state.submitting) return state
    val form = (state.formState as? LoadState.Ready)?.value ?: return state
    val option = form.options.firstOrNull { it.creditId == creditId } ?: return state
    if (!isAllowedPostRatingScore(option, score)) return state
    if (state.scores[creditId] == score) return state
    return state.copy(scores = state.scores + (creditId to score))
}

internal fun withPostRatingReason(
    state: PostRatingDialogState,
    reason: String,
): PostRatingDialogState {
    if (
        state.submitting ||
        postRatingReasonLength(reason) > POST_RATING_REASON_MAX_LENGTH
    ) {
        return state
    }
    if (state.reason == reason) return state
    return state.copy(reason = reason)
}

internal fun withPostRatingSendReasonPm(
    state: PostRatingDialogState,
    sendReasonPm: Boolean,
): PostRatingDialogState {
    if (state.submitting) return state
    val form = (state.formState as? LoadState.Ready)?.value ?: return state
    if (form.sendReasonPmLocked || state.sendReasonPm == sendReasonPm) return state
    return state.copy(sendReasonPm = sendReasonPm)
}

internal fun canSubmitPostRating(state: PostRatingDialogState): Boolean {
    if (
        state.submitting ||
        postRatingReasonLength(state.reason.trim()) > POST_RATING_REASON_MAX_LENGTH
    ) {
        return false
    }
    val form = (state.formState as? LoadState.Ready)?.value ?: return false
    if (form.threadId != state.target.threadId || form.postId != state.target.postId) return false
    val knownCreditIds = form.options.mapTo(mutableSetOf()) { it.creditId }
    if (state.scores.keys.any { it !in knownCreditIds }) return false
    if (form.sendReasonPmLocked && state.sendReasonPm != form.sendReasonPmByDefault) return false
    val selectedScores = form.options.map { option ->
        option to (state.scores[option.creditId] ?: 0)
    }
    return selectedScores.any { (_, score) -> score != 0 } &&
        selectedScores.all { (option, score) ->
            isAllowedPostRatingScore(option, score)
        }
}

internal fun isAllowedPostRatingScore(
    option: YamiboPostRatingOption,
    score: Int,
): Boolean = score == 0 ||
    (
        score in option.minScore..option.maxScore &&
        kotlin.math.abs(score.toLong()) <= option.remainingToday.toLong()
        )

internal fun withSubmittedPostRating(
    post: YamiboPost,
    ratingsCount: Int?,
): YamiboPost {
    require(ratingsCount == null || ratingsCount >= 0) {
        "ratingsCount must not be negative"
    }
    val correctedCount = maxOf(post.ratingCount, ratingsCount ?: 0, 1)
    return if (correctedCount == post.ratingCount) {
        post
    } else {
        post.copy(ratingCount = correctedCount)
    }
}

internal fun mergeThreadPosts(
    existing: List<YamiboPost>,
    loaded: List<YamiboPost>,
    page: Int,
): List<YamiboPost> {
    require(page > 0) { "page must be a positive integer" }
    return if (page == 1) loaded else (existing + loaded).distinctBy { it.id }
}

internal fun replacePost(
    posts: List<YamiboPost>,
    post: YamiboPost,
): List<YamiboPost> {
    val targetIndex = posts.indexOfFirst { it.id == post.id }
    if (targetIndex < 0) return posts
    return posts.toMutableList().apply {
        this[targetIndex] = post
    }
}

internal fun replacePosts(
    posts: List<YamiboPost>,
    replacements: List<YamiboPost>,
): List<YamiboPost> {
    if (replacements.isEmpty()) return posts
    val replacementsById = replacements.associateBy(YamiboPost::id)
    var changed = false
    val updated = posts.map { post ->
        replacementsById[post.id]?.also { changed = true } ?: post
    }
    return if (changed) updated else posts
}

internal fun replacePostComments(
    posts: List<YamiboPost>,
    postId: Int,
    comments: List<YamiboPostComment>,
): List<YamiboPost> {
    require(postId > 0) { "postId must be a positive integer" }
    val targetIndex = posts.indexOfFirst { it.id == postId }
    if (targetIndex < 0) return posts
    return posts.toMutableList().apply {
        this[targetIndex] = posts[targetIndex].copy(comments = comments)
    }
}
