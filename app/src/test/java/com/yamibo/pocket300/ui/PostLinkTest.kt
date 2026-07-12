package com.yamibo.pocket300.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PostLinkTest {
    @Test fun resolvesRelativeThreadLink() {
        assertEquals(PostLinkTarget.Thread(123), resolvePostLink("forum.php?mod=viewthread&tid=123"))
    }

    @Test fun resolvesAbsoluteForumLink() {
        assertEquals(
            PostLinkTarget.Forum(300),
            resolvePostLink("https://bbs.yamibo.com/forum.php?mod=forumdisplay&fid=300"),
        )
    }

    @Test fun resolvesDiscuzRewriteLinks() {
        assertEquals(PostLinkTarget.Thread(42), resolvePostLink("https://bbs.yamibo.com/thread-42-1-1.html"))
        assertEquals(PostLinkTarget.Forum(7), resolvePostLink("https://bbs.yamibo.com/forum-7-1.html"))
    }

    @Test fun leavesExternalLinkForBrowser() {
        assertEquals(
            PostLinkTarget.External("https://example.com/page"),
            resolvePostLink("https://example.com/page"),
        )
    }
}
