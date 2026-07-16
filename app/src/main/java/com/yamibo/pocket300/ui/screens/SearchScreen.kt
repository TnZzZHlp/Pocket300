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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.YamiboSearchThread
import com.yamibo.pocket300.api.YamiboThreadSearchType
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.Loading
import com.yamibo.pocket300.ui.LocalReadingHistory
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.components.AutoLoadNextPage
import com.yamibo.pocket300.ui.dimIfRead
import com.yamibo.pocket300.ui.viewmodels.SearchContent
import com.yamibo.pocket300.ui.viewmodels.SearchQueryError
import com.yamibo.pocket300.ui.viewmodels.SearchViewModel
import kotlinx.coroutines.launch

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

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ScreenScaffold(
        stringResource(R.string.search_title),
        onBack = onBack,
        onTopBarDoubleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchForm(
                query = viewModel.query,
                searchType = viewModel.searchType,
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
                        message = stringResource(R.string.search_initial_message),
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
    queryError: SearchQueryError?,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearchTypeChange: (YamiboThreadSearchType) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                label = { Text(stringResource(R.string.search_input_label)) },
                placeholder = { Text(stringResource(R.string.search_input_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                Icons.Rounded.Clear,
                                contentDescription = stringResource(R.string.search_clear),
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
            Button(
                onClick = onSubmit,
                modifier = Modifier.padding(top = 8.dp).heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.search_action))
            }
        }
        Text(
            stringResource(R.string.search_type_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
}

@Composable
private fun SearchMessage(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
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
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "search-summary") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
        Text(
            stringResource(R.string.search_result_count, content.threads.size),
            style = MaterialTheme.typography.labelMedium,
        )
        when {
            content.isLoadingMore -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(R.string.search_loading_more),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                OutlinedButton(onClick = onLoadMore) {
                    Text(stringResource(R.string.search_load_more))
                }
            }
            else -> Text(
                stringResource(R.string.search_end),
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
    val histories = LocalReadingHistory.current
    Card(
        onClick = { onClick(thread) },
        modifier = modifier.fillMaxWidth().dimIfRead(thread.id, histories),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                thread.forum.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                thread.subject,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    R.string.search_thread_metadata,
                    thread.author.name,
                    thread.createdAtText,
                    thread.replyCount,
                    thread.viewCount,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun YamiboThreadSearchType.labelResource() = when (this) {
    YamiboThreadSearchType.KEYWORD -> R.string.search_type_keyword
    YamiboThreadSearchType.TITLE -> R.string.search_type_title
    YamiboThreadSearchType.USER_ID -> R.string.search_type_user_id
}
