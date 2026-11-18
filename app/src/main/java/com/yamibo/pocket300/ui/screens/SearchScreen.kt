package com.yamibo.pocket300.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.api.YamiboSearchThread
import com.yamibo.pocket300.api.YamiboThreadSearchType
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.Loading
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.ScrollableEmptyState
import com.yamibo.pocket300.ui.components.AutoLoadNextPage
import com.yamibo.pocket300.ui.components.ThreadCardContent
import com.yamibo.pocket300.ui.components.ThreadCardSurface
import com.yamibo.pocket300.ui.viewmodels.SearchContent
import com.yamibo.pocket300.ui.viewmodels.SearchQueryError
import com.yamibo.pocket300.ui.viewmodels.SearchViewModel
import kotlinx.coroutines.launch

internal const val SEARCH_QUERY_FIELD_TEST_TAG = "search-query"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun SearchScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onThread: (YamiboSearchThread) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submit() {
        viewModel.submit()
        if (viewModel.queryError == null) keyboardController?.hide()
    }

    ScreenScaffold(
        stringResource(R.string.search_title),
        onBack = onBack,
        onTopBarDoubleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchForm(
                query = viewModel.query,
                searchType = viewModel.searchType,
                isForumScoped = viewModel.isForumScoped,
                queryError = viewModel.queryError,
                focusRequester = focusRequester,
                onQueryChange = viewModel::updateQuery,
                onClear = viewModel::clearQuery,
                onSearchTypeChange = viewModel::updateSearchType,
                onSubmit = ::submit,
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (val current = viewModel.state) {
                    null -> SearchMessage(
                        title = stringResource(R.string.search_initial_title),
                        message = "",
                    )
                    LoadState.Loading -> Loading()
                    is LoadState.Failed -> SearchMessage(
                        title = stringResource(R.string.search_failed_title),
                        message = current.message,
                        actionLabel = stringResource(R.string.search_retry),
                        onAction = ::submit,
                    )
                    is LoadState.Ready -> SearchResults(
                        content = current.value,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onThread = onThread,
                        onLoadMore = viewModel::loadMore,
                        onEditQuery = {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        },
                        listState = listState,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchForm(
    query: String,
    searchType: YamiboThreadSearchType,
    isForumScoped: Boolean,
    queryError: SearchQueryError?,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearchTypeChange: (YamiboThreadSearchType) -> Unit,
    onSubmit: () -> Unit,
) {
    val searchTypeDescription = stringResource(R.string.search_type_label)
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(SEARCH_QUERY_FIELD_TEST_TAG),
                label = { Text(stringResource(R.string.search_input_label)) },
                placeholder = { Text(stringResource(R.string.search_input_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 6.dp),
                    ) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = onClear) {
                                Icon(
                                    Icons.Rounded.Clear,
                                    contentDescription = stringResource(R.string.search_clear),
                                )
                            }
                        }
                        FilledTonalIconButton(
                            onClick = onSubmit,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = stringResource(R.string.search_action),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                supportingText = queryError?.let { error ->
                    {
                        Text(
                            stringResource(
                                when (error) {
                                    SearchQueryError.EMPTY -> R.string.search_error_empty
                                    SearchQueryError.INVALID_USER_ID -> R.string.search_error_user_id
                                },
                            ),
                        )
                    }
                },
                isError = queryError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (searchType == YamiboThreadSearchType.USER_ID) {
                        KeyboardType.Number
                    } else {
                        KeyboardType.Text
                    },
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .semantics { contentDescription = searchTypeDescription },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = stringResource(
                            if (isForumScoped) R.string.search_scope_forum else R.string.search_scope_site,
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                VerticalDivider(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                YamiboThreadSearchType.entries.forEach { type ->
                    FilterChip(
                        selected = searchType == type,
                        onClick = { onSearchTypeChange(type) },
                        label = { Text(stringResource(type.labelResource())) },
                        leadingIcon = if (searchType == type) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SearchMessage(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    ScrollableEmptyState(
        title = title,
        message = message,
        icon = Icons.Rounded.Search,
        onRetry = onAction,
        actionLabel = actionLabel ?: stringResource(R.string.action_retry),
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchResults(
    content: SearchContent,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onThread: (YamiboSearchThread) -> Unit,
    onLoadMore: () -> Unit,
    onEditQuery: () -> Unit,
    listState: LazyListState,
) {
    if (content.threads.isEmpty()) {
        SearchMessage(
            title = stringResource(R.string.search_empty_title),
            message = stringResource(R.string.search_empty_message, content.page.keyword),
            actionLabel = stringResource(R.string.search_edit_query),
            onAction = onEditQuery,
        )
        return
    }
    AutoLoadNextPage(
        listState = listState,
        hasNextPage = content.page.pagination.hasNextPage,
        onLoadMore = onLoadMore,
    )
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item(key = "search-summary") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    stringResource(R.string.search_result_summary, content.page.keyword),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.search_result_count, content.threads.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
            SearchListFooter(content = content, onLoadMore = onLoadMore)
        }
    }
}

@Composable
private fun SearchListFooter(content: SearchContent, onLoadMore: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            content.isLoadingMore -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            content.loadMoreError != null -> {
                Text(
                    content.loadMoreError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onLoadMore) {
                    Text(stringResource(R.string.search_load_more_retry))
                }
            }
            content.page.pagination.hasNextPage -> {
                TextButton(onClick = onLoadMore) {
                    Text(stringResource(R.string.list_load_more_hint))
                }
            }
            else -> Text(
                stringResource(R.string.list_end),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    ThreadCardSurface(onClick = { onClick(thread) }, modifier = modifier) {
        ThreadCardContent(
            threadId = thread.id,
            subject = thread.subject,
            category = thread.forum.name,
            author = YamiboPostAuthor(thread.author.avatarUrl, thread.author.id, thread.author.name),
            dateText = thread.createdAtText,
            replyCount = thread.replyCount,
            viewCount = thread.viewCount,
        )
    }
}

private fun YamiboThreadSearchType.labelResource() = when (this) {
    YamiboThreadSearchType.KEYWORD -> R.string.search_type_keyword
    YamiboThreadSearchType.TITLE -> R.string.search_type_title
    YamiboThreadSearchType.USER_ID -> R.string.search_type_user_id
}
