package com.yamibo.pocket300.ui.viewmodels

import com.yamibo.pocket300.api.YamiboThreadSearchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchViewModelTest {
    @Test
    fun rejectsBlankSearchQueries() {
        assertEquals(
            SearchQueryError.EMPTY,
            validateSearchQuery("   ", YamiboThreadSearchType.TITLE),
        )
    }

    @Test
    fun rejectsInvalidUserIds() {
        assertEquals(
            SearchQueryError.INVALID_USER_ID,
            validateSearchQuery("0", YamiboThreadSearchType.USER_ID),
        )
        assertEquals(
            SearchQueryError.INVALID_USER_ID,
            validateSearchQuery("not-a-number", YamiboThreadSearchType.USER_ID),
        )
    }

    @Test
    fun acceptsTextAndPositiveUserIdQueries() {
        assertNull(validateSearchQuery("  百合  ", YamiboThreadSearchType.KEYWORD))
        assertNull(validateSearchQuery("300", YamiboThreadSearchType.USER_ID))
    }
}
