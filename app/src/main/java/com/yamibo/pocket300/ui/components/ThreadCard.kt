package com.yamibo.pocket300.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.YamiboThread

@Composable
internal fun ThreadCard(thread: YamiboThread, onClick: (YamiboThread) -> Unit, modifier: Modifier = Modifier) {
    ThreadCardSurface(onClick = { onClick(thread) }, modifier = modifier) {
        ThreadCardContent(
            threadId = thread.id,
            subject = thread.subject,
            category = thread.typeName,
            metadata = stringResource(R.string.thread_card_metadata, thread.author.name, thread.createdAtText),
            activity = stringResource(R.string.thread_card_replies, thread.replyCount),
        )
    }
}
