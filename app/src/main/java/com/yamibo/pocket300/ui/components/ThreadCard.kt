package com.yamibo.pocket300.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.api.YamiboThread
import com.yamibo.pocket300.ui.LocalReadingHistory
import com.yamibo.pocket300.ui.dimIfRead

@Composable
internal fun ThreadCard(thread: YamiboThread, onClick: (YamiboThread) -> Unit, modifier: Modifier = Modifier) {
    val histories = LocalReadingHistory.current
    Card(
        onClick = { onClick(thread) },
        modifier = modifier.fillMaxWidth().dimIfRead(thread.id, histories),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (thread.typeName != null) Text(thread.typeName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(thread.subject, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            thread.excerpt?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("${thread.author.name} · ${thread.lastPostAtText} · ${thread.replyCount} 回复", style = MaterialTheme.typography.labelMedium)
        }
    }
}

