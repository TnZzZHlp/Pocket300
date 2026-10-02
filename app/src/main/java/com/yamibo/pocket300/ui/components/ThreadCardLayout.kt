package com.yamibo.pocket300.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.ui.LocalReadingHistory
import com.yamibo.pocket300.ui.lastReadFloor
import com.yamibo.pocket300.ui.theme.PocketSpacing

@Composable
internal fun threadCardColors(selected: Boolean = false) = CardDefaults.cardColors(
    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainerLow,
)

@Composable
internal fun threadCardBorder(selected: Boolean = false) = BorderStroke(
    1.dp,
    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
)

@Composable
internal fun ThreadCardSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = threadCardColors(),
        border = threadCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(PocketSpacing.card),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.contentGap),
            content = content,
        )
    }
}

@Composable
internal fun ThreadCardContent(
    threadId: Int,
    subject: String,
    metadata: String,
    modifier: Modifier = Modifier,
    category: String? = null,
    activity: String? = null,
    supporting: String? = null,
    readingFloor: Int? = lastReadFloor(threadId, LocalReadingHistory.current),
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.contentGap),
    ) {
        category?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ThreadCardTitle(subject = subject, threadId = threadId, maxLines = 2)
        if (metadata.isNotBlank() || !activity.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PocketSpacing.contentGap),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = metadata,
                    modifier = Modifier.weight(if (activity.isNullOrBlank()) 1f else 0.6f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                activity?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        modifier = Modifier.weight(0.4f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        supporting?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        readingFloor?.let { LastReadPosition(it) }
    }
}
