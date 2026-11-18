package com.yamibo.pocket300.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.YamiboPostRating
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.LoadContent
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.load
import com.yamibo.pocket300.ui.theme.PocketSpacing
import com.yamibo.pocket300.ui.theme.pocketCardBorder
import com.yamibo.pocket300.ui.theme.pocketCardColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RatingsScreen(threadId: Int, postId: Int, onBack: () -> Unit) {
    var reload by remember(threadId, postId) { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var state: LoadState<List<YamiboPostRating>> by remember(threadId, postId) {
        mutableStateOf(LoadState.Loading)
    }
    var refreshing by remember(threadId, postId) { mutableStateOf(false) }
    LaunchedEffect(threadId, postId, reload) {
        try {
            state = load { api.posts.getPostRatings(threadId, postId) }
        } finally {
            refreshing = false
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.rating_details_title),
        onBack = onBack,
        onRefresh = { refreshing = true; reload++ },
        isRefreshing = refreshing,
        onTopBarDoubleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
    ) { padding ->
        LoadContent(state, padding, onRetry = { state = LoadState.Loading; reload++ }) { ratings ->
            if (ratings.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.rating_details_title),
                    message = stringResource(R.string.rating_empty),
                )
            } else {
                val creditSummaries = remember(ratings) { summarizePostRatings(ratings) }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = PocketSpacing.screen, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(PocketSpacing.itemGap),
                ) {
                    if (creditSummaries.isNotEmpty()) {
                        item(key = "rating-summary") {
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = pocketCardColors(),
                                border = pocketCardBorder(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.rating_summary_heading),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        creditSummaries.forEach { summary ->
                                            Surface(
                                                shape = MaterialTheme.shapes.small,
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                            ) {
                                                Text(
                                                    formatRatingDelta(
                                                        summary.creditName,
                                                        summary.totalScore,
                                                        summary.unit,
                                                    ),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(
                                                        horizontal = 10.dp,
                                                        vertical = 6.dp,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    items(ratings, key = { "${it.userId}-${it.creditName}" }) { rating ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = pocketCardColors(),
                            border = pocketCardBorder(),
                        ) {
                            RatingRow(rating, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RatingRow(
    rating: YamiboPostRating,
    modifier: Modifier = Modifier,
    contentStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    supportingStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    metadataStyle: TextStyle = MaterialTheme.typography.labelSmall,
) {
    val positive = rating.score >= 0
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                rating.username,
                style = contentStyle,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (positive) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            ) {
                Text(
                    formatRatingDelta(rating.creditName, rating.score, rating.unit),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (positive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (rating.reason.isNotEmpty()) Text(rating.reason, style = supportingStyle)
        Text(
            rating.createdAtText,
            style = metadataStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal data class RatingCreditSummary(
    val creditName: String,
    val unit: String,
    val totalScore: Int,
)

internal fun summarizePostRatings(ratings: List<YamiboPostRating>): List<RatingCreditSummary> {
    if (ratings.isEmpty()) return emptyList()
    val totals = LinkedHashMap<Pair<String, String>, Int>()
    ratings.forEach { rating ->
        val key = rating.creditName.trim() to rating.unit.trim()
        totals[key] = (totals[key] ?: 0) + rating.score
    }
    return totals.map { (key, totalScore) ->
        RatingCreditSummary(
            creditName = key.first,
            unit = key.second,
            totalScore = totalScore,
        )
    }
}

private fun formatRatingDelta(creditName: String, score: Int, unit: String): String {
    val sign = if (score > 0) "+" else ""
    return "$creditName $sign$score $unit".trim()
}
