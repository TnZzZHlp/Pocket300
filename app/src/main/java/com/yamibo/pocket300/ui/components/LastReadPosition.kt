package com.yamibo.pocket300.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yamibo.pocket300.R
import com.yamibo.pocket300.ui.LocalReadingHistory
import com.yamibo.pocket300.ui.lastReadFloor

@Composable
internal fun LastReadPosition(
    floor: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.thread_last_read_position, floor),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun ThreadLastReadPosition(
    threadId: Int,
    modifier: Modifier = Modifier,
) {
    val histories = LocalReadingHistory.current
    lastReadFloor(threadId, histories)?.let { floor ->
        LastReadPosition(floor = floor, modifier = modifier)
    }
}
