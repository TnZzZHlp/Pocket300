package com.yamibo.pocket300.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.api.YamiboThread

@Composable
internal fun ThreadCard(thread: YamiboThread, onClick: (YamiboThread) -> Unit, modifier: Modifier = Modifier) {
    ThreadCardSurface(onClick = { onClick(thread) }, modifier = modifier) {
        ThreadCardContent(
            threadId = thread.id,
            subject = thread.subject,
            category = thread.typeName,
            author = YamiboPostAuthor(null, thread.author.id, thread.author.name),
            dateText = thread.createdAtText,
            replyCount = thread.replyCount,
        )
    }
}
