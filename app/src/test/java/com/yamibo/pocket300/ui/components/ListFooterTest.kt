package com.yamibo.pocket300.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ListFooterTest {
    @Test
    fun showsLoadingStateWhileNextPageIsLoading() {
        assertEquals(
            ListFooterState.LOADING,
            listFooterState(isLoadingMore = true, hasNextPage = true),
        )
    }

    @Test
    fun showsLoadMoreActionOrEndStateWhenIdle() {
        assertEquals(
            ListFooterState.LOAD_MORE,
            listFooterState(isLoadingMore = false, hasNextPage = true),
        )
        assertEquals(
            ListFooterState.END,
            listFooterState(isLoadingMore = false, hasNextPage = false),
        )
    }
}
