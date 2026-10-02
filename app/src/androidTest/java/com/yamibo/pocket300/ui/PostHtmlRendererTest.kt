package com.yamibo.pocket300.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostHtmlRendererTest {
    @Test
    fun separatesQuotedTextFromSurroundingParagraphs() {
        val parts = parsePostHtml("<p>正文</p><blockquote>引用</blockquote><p>后文</p>")
            .filterIsInstance<PostHtmlPart.Text>()
        assertFalse(parts.first { "正文" in it.value }.quoted)
        assertTrue(parts.first { "引用" in it.value }.quoted)
        assertFalse(parts.first { "后文" in it.value }.quoted)
    }

    @Test
    fun preservesLinksAndImageSourcesInsideQuotes() {
        val parts = parsePostHtml(
            """<blockquote><a href="https://bbs.yamibo.com/forum.php?mod=viewthread&amp;tid=12">链接</a><img src="https://bbs.yamibo.com/example.jpg"></blockquote>""",
        )
        val link = parts.filterIsInstance<PostHtmlPart.Text>().first { it.url != null }
        assertEquals("https://bbs.yamibo.com/forum.php?mod=viewthread&tid=12", link.url)
        assertTrue(link.quoted)
        val image = parts.filterIsInstance<PostHtmlPart.Image>().single()
        assertEquals("https://bbs.yamibo.com/example.jpg", image.url)
        assertTrue(image.quoted)
    }

    @Test
    fun adjacentNormalLinksDoNotInheritQuoteFormatting() {
        val parts = parsePostHtml(
            """<blockquote>引用</blockquote><a href="https://bbs.yamibo.com/">正文链接</a>""",
        )
        assertFalse(parts.filterIsInstance<PostHtmlPart.Text>().single { it.url != null }.quoted)
    }
}
