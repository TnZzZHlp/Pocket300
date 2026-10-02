package com.yamibo.pocket300.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.ui.LocalReadingHistory
import com.yamibo.pocket300.ui.lastReadFloor
import com.yamibo.pocket300.ui.rememberThreadAuthor
import com.yamibo.pocket300.ui.theme.ThreadFeedTitleStyle

@Composable
internal fun threadCardColors(selected: Boolean = false) = CardDefaults.cardColors(
    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainerLow,
)

@Composable
internal fun threadCardBorder(selected: Boolean = false): BorderStroke? =
    if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null

@Composable
internal fun ThreadCardSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = threadCardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThreadCardContent(
    threadId: Int,
    subject: String,
    dateText: String,
    modifier: Modifier = Modifier,
    author: YamiboPostAuthor? = null,
    category: String? = null,
    replyCount: Int? = null,
    viewCount: Int? = null,
    supporting: String? = null,
    readingFloor: Int? = lastReadFloor(threadId, LocalReadingHistory.current),
    resolveMissingAuthor: Boolean = false,
) {
    val resolvedAuthor = rememberThreadAuthor(threadId, author, resolveMissingAuthor)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PostAuthorAvatar(
                author = resolvedAuthor ?: YamiboPostAuthor(null, null, ""),
                size = 24.dp,
                useInitials = false,
            )
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                resolvedAuthor?.name?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (dateText.isNotBlank()) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            category?.takeIf(String::isNotBlank)?.let {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.widthIn(max = 120.dp).padding(horizontal = 6.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        ThreadCardTitle(
            subject = subject,
            threadId = threadId,
            maxLines = 2,
            textStyle = ThreadFeedTitleStyle,
            showDownloadedIndicator = false,
        )
        supporting?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            replyCount?.let {
                ThreadMetric(Icons.AutoMirrored.Rounded.Comment, it, stringResource(R.string.thread_card_replies, it))
            }
            viewCount?.let {
                ThreadMetric(Icons.Rounded.Visibility, it, stringResource(R.string.thread_card_views, it))
            }
            ThreadDownloadedIndicator(threadId)
            readingFloor?.let { LastReadPosition(it) }
        }
    }
}

@Composable
private fun ThreadMetric(icon: ImageVector, count: Int, description: String) {
    Surface(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = 11.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(count.toString(), style = MaterialTheme.typography.labelMedium)
        }
    }
}
