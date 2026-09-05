package com.yamibo.pocket300.api

import org.junit.Assert.assertEquals
import org.junit.Test

class YamiboAvatarUrlTest {
    @Test
    fun replacesLegacyEndpointWithStaticAvatar() {
        assertEquals(
            "$YAMIBO_ORIGIN/uc_server/data/avatar/000/59/42/15_avatar_small.jpg",
            resolveYamiboAvatarUrl("$YAMIBO_ORIGIN/uc_server/avatar.php?uid=594215&size=small"),
        )
        assertEquals(
            "$YAMIBO_ORIGIN/uc_server/data/avatar/000/00/00/42_avatar_big.jpg",
            resolveYamiboAvatarUrl("$YAMIBO_ORIGIN/uc_server/avatar.php?size=big&uid=42"),
        )
    }

    @Test
    fun preservesStaticUrlsAndUnrelatedOrInvalidUrls() {
        listOf(
            "$YAMIBO_ORIGIN/uc_server/data/avatar/000/59/42/15_avatar_small.jpg?ts=1742136902",
            "https://example.com/uc_server/avatar.php?uid=42",
            "$YAMIBO_ORIGIN/uc_server/avatar.php?uid=0",
            "$YAMIBO_ORIGIN/uc_server/avatar.php?uid=invalid",
            "invalid url",
        ).forEach { assertEquals(it, resolveYamiboAvatarUrl(it)) }
    }

    @Test
    fun defaultsMissingOrUnsupportedSizeToSmall() {
        listOf("", "&size=invalid").forEach { size ->
            assertEquals(
                yamiboAvatarUrl(42),
                resolveYamiboAvatarUrl("$YAMIBO_ORIGIN/uc_server/avatar.php?uid=42$size"),
            )
        }
    }
}
