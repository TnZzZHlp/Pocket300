package com.yamibo.pocket300.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YamiboSearchApiTest {
    @Test
    fun parsesMobileSearchPage() {
        val page = parseSearchPage(HTML, ParseSearchPageContext(null, 1, "$YAMIBO_ORIGIN/search.php?searchid=88", YamiboSearchScope.SITE))
        assertEquals("百合", page.keyword)
        assertEquals(88, page.pagination.searchId)
        assertEquals(21, page.pagination.totalThreads)
        assertTrue(page.pagination.hasNextPage)
        val thread = page.threads.single()
        assertEquals(1000, thread.id)
        assertEquals("Alice", thread.author.name)
        assertEquals("标题 & 测试", thread.subject)
        assertEquals(1234, thread.viewCount)
        assertEquals(listOf("$YAMIBO_ORIGIN/a.jpg"), thread.imageUrls)
    }

    @Test(expected = YamiboSearchException::class)
    fun detectsRateLimitTip() {
        parseSearchPage("<div class='jump_c'><p>10 秒内只能进行一次搜索</p></div>", ParseSearchPageContext(null, 1, YAMIBO_ORIGIN, YamiboSearchScope.SITE))
    }

    private companion object {
        val HTML = """
          <input name="srchtxt" value="百合"><p>相关内容 21 个</p>
          <li class="list">
            <a class="mimg"><img src="/avatar.png"></a>
            <div class="muser"><h3><a class="mmc" href="home.php?mod=space&amp;uid=42">Alice</a></h3></div>
            <span class="mtime">今天</span>
            <a href="forum.php?mod=viewthread&amp;tid=1000"><div class="threadlist_tit">标题 &amp; 测试</div></a>
            <div class="threadlist_mes">摘要</div>
            <a href="forum.php?mod=forumdisplay&amp;fid=300">#动画</a>
            <i class="dm-eye-fill"></i> 1,234 <i class="dm-chat-s-fill"></i> 5
            <div class="threadlist_imgs1"><img src="/a.jpg"></div>
          </li><div class="pg" title="共 2 页"></div>
        """.trimIndent()
    }
}
