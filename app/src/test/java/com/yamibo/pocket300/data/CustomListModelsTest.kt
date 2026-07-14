package com.yamibo.pocket300.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomListModelsTest {
    @Test
    fun splitsKeywordsUsingSupportedSeparators() {
        assertEquals(
            listOf("百合", "轻小说", "漫画", "动画"),
            normalizeCustomListKeywords(" 百合，轻小说\n漫画; 动画 "),
        )
    }

    @Test
    fun removesBlankAndCaseInsensitiveDuplicateKeywords() {
        assertEquals(
            listOf("Yuri", "百合"),
            normalizeCustomListKeywords("Yuri, yuri；；百合\n"),
        )
    }

    @Test
    fun keepsSpacesInsideOneKeyword() {
        assertEquals(
            listOf("girls love", "百合 漫画"),
            normalizeCustomListKeywords("girls love\n百合 漫画"),
        )
    }
}
