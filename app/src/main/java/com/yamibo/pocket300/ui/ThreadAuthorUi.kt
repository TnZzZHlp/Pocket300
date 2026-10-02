package com.yamibo.pocket300.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.data.ThreadAuthorRepository
import com.yamibo.pocket300.data.normalizedThreadAuthor

internal val LocalThreadAuthors = staticCompositionLocalOf<ThreadAuthorRepository?> { null }

@Composable
internal fun rememberThreadAuthor(
    threadId: Int,
    author: YamiboPostAuthor?,
    allowLookup: Boolean,
): YamiboPostAuthor? {
    val repository = LocalThreadAuthors.current
    var resolved by remember(threadId, author, allowLookup, repository) {
        mutableStateOf(author?.let(::normalizedThreadAuthor))
    }
    LaunchedEffect(threadId, author, allowLookup, repository) {
        resolved = repository?.resolve(threadId, author, allowLookup)
            ?: author?.let(::normalizedThreadAuthor)
    }
    return resolved
}
