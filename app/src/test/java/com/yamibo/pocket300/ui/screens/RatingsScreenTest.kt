package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.api.YamiboPostRating
import org.junit.Assert.assertEquals
import org.junit.Test

class RatingsScreenTest {
    @Test
    fun summarizesRatingDeltasByCreditTypeAndUnitInEncounterOrder() {
        val ratings = listOf(
            rating(creditName = "百合花", score = 2, unit = "朵", userId = 1),
            rating(creditName = "威望", score = -1, unit = "点", userId = 2),
            rating(creditName = " 百合花 ", score = 3, unit = " 朵 ", userId = 3),
            rating(creditName = "威望", score = 2, unit = "点", userId = 4),
        )

        assertEquals(
            listOf(
                RatingCreditSummary(creditName = "百合花", unit = "朵", totalScore = 5),
                RatingCreditSummary(creditName = "威望", unit = "点", totalScore = 1),
            ),
            summarizePostRatings(ratings),
        )
        assertEquals(emptyList<RatingCreditSummary>(), summarizePostRatings(emptyList()))
    }

    private fun rating(
        creditName: String,
        score: Int,
        unit: String,
        userId: Int,
    ) = YamiboPostRating(
        creditName = creditName,
        createdAtText = "2026-08-01",
        reason = "",
        score = score,
        unit = unit,
        userId = userId,
        username = "User $userId",
    )
}
