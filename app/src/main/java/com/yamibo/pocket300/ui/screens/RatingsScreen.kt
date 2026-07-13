package com.yamibo.pocket300.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

@Composable
internal fun RatingsScreen(threadId: Int, postId: Int, onBack: () -> Unit) {
    var reload by remember(threadId, postId) { mutableStateOf(0) }
    var state: LoadState<List<YamiboPostRating>> by remember(threadId, postId) {
        mutableStateOf(LoadState.Loading)
    }
    LaunchedEffect(threadId, postId, reload) {
        state = load { api.posts.getPostRatings(threadId, postId) }
    }

    ScreenScaffold(
        title = stringResource(R.string.rating_details_title),
        onBack = onBack,
        onRefresh = { reload++ },
    ) { padding ->
        LoadContent(state, padding) { ratings ->
            if (ratings.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.rating_details_title),
                    message = stringResource(R.string.rating_empty),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ratings, key = { "${it.userId}-${it.creditName}" }) { rating ->
                        Card {
                            RatingRow(rating, Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RatingRow(rating: YamiboPostRating, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(rating.username, fontWeight = FontWeight.SemiBold)
            Text(
                "${rating.creditName} ${if (rating.score > 0) "+" else ""}${rating.score} ${rating.unit}".trim(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (rating.reason.isNotEmpty()) Text(rating.reason, style = MaterialTheme.typography.bodyMedium)
        Text(
            rating.createdAtText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
