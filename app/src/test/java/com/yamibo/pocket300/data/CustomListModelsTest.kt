package com.yamibo.pocket300.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomListModelsTest {
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
}
