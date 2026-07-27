package com.yamibo.pocket300.data

import com.yamibo.pocket300.api.YamiboThreadSearchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomListModelsTest {
    @Test
    fun parsesStoredSearchTypesAndFallsBackToTitle() {
        assertEquals(YamiboThreadSearchType.KEYWORD, parseCustomListSearchType("keyword"))
        assertEquals(YamiboThreadSearchType.USER_ID, parseCustomListSearchType("user_id"))
        assertEquals(YamiboThreadSearchType.TITLE, parseCustomListSearchType("unknown"))
    }

    @Test
    fun splitsKeywordsUsingLineBreaks() {
        assertEquals(
            listOf("百合", "轻小说", "漫画", "动画"),
            normalizeCustomListKeywords(" 百合\n轻小说\r\n漫画\r动画 "),
        )
    }

    @Test
    fun removesBlankAndCaseInsensitiveDuplicateKeywords() {
        assertEquals(
            listOf("Yuri", "百合"),
            normalizeCustomListKeywords("Yuri\nyuri\n\n百合\n"),
        )
    }

    @Test
    fun keepsCommasAndSemicolonsInsideOneKeyword() {
        assertEquals(
            listOf("百合，轻小说; 漫画；动画, Yuri"),
            normalizeCustomListKeywords("百合，轻小说; 漫画；动画, Yuri"),
        )
    }

    @Test
    fun keepsSpacesInsideOneKeyword() {
        assertEquals(
            listOf("girls love", "百合 漫画"),
            normalizeCustomListKeywords("girls love\n百合 漫画"),
        )
    }

    @Test
    fun autoDownloadsOnlyNewThreadsAfterTheInitialSync() {
        val list = CustomThreadList(
            id = 1,
            name = "Test",
            keywords = listOf("keyword"),
            searchType = YamiboThreadSearchType.TITLE,
            createdAt = 0,
            updatedAt = 0,
            lastSyncedAt = null,
            threadCount = 0,
            excludedCount = 0,
            autoDownloadNewThreads = true,
        )

        assertFalse(list.shouldAutoDownloadAddedThreads(addedThreadCount = 1))
        assertFalse(list.copy(lastSyncedAt = 1).shouldAutoDownloadAddedThreads(addedThreadCount = 0))
        assertTrue(list.copy(lastSyncedAt = 1).shouldAutoDownloadAddedThreads(addedThreadCount = 1))
        assertFalse(
            list.copy(lastSyncedAt = 1, autoDownloadNewThreads = false)
                .shouldAutoDownloadAddedThreads(addedThreadCount = 1),
        )
    }
}
