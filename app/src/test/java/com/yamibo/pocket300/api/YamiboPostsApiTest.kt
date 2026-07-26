package com.yamibo.pocket300.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YamiboPostsApiTest {
    @Test
    fun parsesPostsCommentsAndPoll() {
        val page = parseThreadPosts(JSONObject(FIXTURE), 1)
        assertEquals(1000, page.thread.id)
        assertEquals(YamiboThreadSpecialType.POLL, page.thread.specialType)
        assertEquals(2, page.pagination.totalPosts)
        assertEquals(1, page.pagination.totalPages)
        assertFalse(page.pagination.hasNextPage)
        assertTrue(page.canComment)
        val post = page.posts.single()
        assertTrue(post.isOriginalPost)
        assertEquals(
            "https://bbs.yamibo.com/uc_server/avatar.php?uid=42&size=small",
            post.author.avatarUrl,
        )
        assertEquals(
            "https://example.com/a.png",
            post.comments.single().author.avatarUrl,
        )
        assertEquals(4, post.ratingCount)
        assertEquals("<p>不可信正文</p>", post.html)
        assertEquals("https://bbs.yamibo.com/data/attachment/forum/202607/example.jpg", post.attachments.single().url)
        assertTrue(post.attachments.single().isImage)
        assertEquals("点评", post.comments.single().message)
        assertEquals("#ff00aa", page.poll?.options?.single()?.color)
        assertEquals(25.5, page.poll?.options?.single()?.percentage ?: -1.0, 0.0)
    }

    @Test
    fun defaultsMissingPostRatingCountToZero() {
        val fixture = JSONObject(FIXTURE)
        fixture.getJSONArray("postlist").getJSONObject(0).remove("ratetimes")

        assertEquals(0, parseThreadPosts(fixture, 1).posts.single().ratingCount)
    }

    @Test
    fun addsAuthorIdWhenRequestingOnlyOriginalPoster() {
        assertEquals(
            mapOf("module" to "viewthread", "page" to "2", "tid" to "1000", "authorid" to "42"),
            threadPostsParameters(GetThreadPostsInput(threadId = 1000, page = 2, authorId = 42)),
        )
        assertFalse(threadPostsParameters(GetThreadPostsInput(1000)).containsKey("authorid"))
    }

    @Test
    fun buildsReplyRequestWithDiscuzValidationFields() {
        val input = ReplyToThreadInput(forumId = 300, threadId = 1000, message = "回复内容")

        assertEquals(
            mapOf(
                "fid" to "300",
                "module" to "sendreply",
                "replysubmit" to "yes",
                "tid" to "1000",
            ),
            replyToThreadParameters(input),
        )
        assertEquals(
            mapOf("formhash" to "hash", "message" to "回复内容"),
            replyToThreadForm("hash", input.message),
        )
    }

    @Test
    fun requiresDirectCommentPermission() {
        val fixture = JSONObject(FIXTURE)
        fixture.put("allowpostcomment", org.json.JSONArray(listOf("2")))

        assertFalse(parseThreadPosts(fixture).canComment)
    }

    @Test
    fun buildsPostCommentRequestWithDiscuzSubmissionFields() {
        val input = CommentOnPostInput(
            forumId = 300,
            threadId = 1000,
            postId = 42,
            message = "点评内容",
        )

        assertEquals(
            mapOf(
                "comment" to "yes",
                "commentsubmit" to "yes",
                "fid" to "300",
                "module" to "sendreply",
                "pid" to "42",
                "tid" to "1000",
            ),
            commentOnPostParameters(input),
        )
        assertEquals(
            mapOf("formhash" to "hash", "message" to "点评内容"),
            commentOnPostForm("hash", input.message),
        )
        assertEquals(200, POST_COMMENT_MAX_LENGTH)
    }

    @Test
    fun buildsTargetPostCommentRefreshRequest() {
        assertEquals(
            mapOf(
                "module" to "viewthread",
                "tid" to "1000",
                "viewpid" to "9",
            ),
            postCommentsParameters(threadId = 1000, postId = 9),
        )
    }

    @Test
    fun buildsTargetPostRefreshRequestForCommentsAndRatingCount() {
        assertEquals(
            mapOf(
                "module" to "viewthread",
                "tid" to "1000",
                "viewpid" to "9",
            ),
            targetPostParameters(threadId = 1000, postId = 9),
        )
    }

    @Test
    fun parsesCommentsForRequestedPost() {
        val comments = parsePostCommentsForTarget(
            JSONObject(FIXTURE),
            expectedThreadId = 1000,
            expectedPostId = 9,
        )

        assertEquals(listOf("点评"), comments.map { it.message })
    }

    @Test
    fun parsesRequestedPostForTargetedRefresh() {
        val post = parsePostForTarget(
            JSONObject(FIXTURE),
            expectedThreadId = 1000,
            expectedPostId = 9,
        )

        assertEquals(9, post.id)
        assertEquals(4, post.ratingCount)
        assertEquals(listOf("点评"), post.comments.map { it.message })
    }

    @Test(expected = YamiboApiException::class)
    fun rejectsCommentRefreshAssignedToDifferentPost() {
        parsePostCommentsForTarget(
            JSONObject(FIXTURE),
            expectedThreadId = 1000,
            expectedPostId = 10,
        )
    }

    @Test
    fun recognizesSuccessfulPostComment() {
        parseCommentResult(
            DiscuzResponse(
                variables = JSONObject("""{"tid":"1000","pid":"42"}"""),
                message = DiscuzMessage("帖子点评成功", "comment_add_succeed"),
                error = null,
                version = "4",
                charset = "UTF-8",
            ),
            expectedThreadId = 1000,
            expectedPostId = 42,
        )
    }

    @Test
    fun explainsPostCommentPermissionFailure() {
        val error = runCatching {
            parseCommentResult(
                DiscuzResponse(
                    variables = null,
                    message = DiscuzMessage("抱歉，您不能点评此帖", "postcomment_error"),
                    error = null,
                    version = "4",
                    charset = "UTF-8",
                ),
                expectedThreadId = 1000,
                expectedPostId = 42,
            )
        }.exceptionOrNull()

        assertTrue(error is YamiboApiException)
        assertEquals(
            "无法点评此楼层，可能没有权限、不能点评自己的内容，或楼层不存在",
            error?.message,
        )
        assertEquals("postcomment_error", (error as YamiboApiException).serverCode)
    }

    @Test(expected = YamiboApiException::class)
    fun rejectsPostCommentResponseWithoutResultCode() {
        parseCommentResult(
            DiscuzResponse(
                variables = JSONObject(),
                message = null,
                error = null,
                version = "4",
                charset = "UTF-8",
            ),
            expectedThreadId = 1000,
            expectedPostId = 42,
        )
    }

    @Test(expected = YamiboApiException::class)
    fun rejectsPostCommentResultAssignedToDifferentPost() {
        parseCommentResult(
            DiscuzResponse(
                variables = JSONObject("""{"tid":"1000","pid":"43"}"""),
                message = DiscuzMessage("帖子点评成功", "comment_add_succeed"),
                error = null,
                version = "4",
                charset = "UTF-8",
            ),
            expectedThreadId = 1000,
            expectedPostId = 42,
        )
    }

    @Test
    fun parsesSuccessfulReplyResult() {
        val result = parseReplyResult(
            DiscuzResponse(
                variables = JSONObject("""{"tid":"1000","pid":"42"}"""),
                message = DiscuzMessage("回复发布成功", "post_reply_succeed"),
                error = null,
                version = "4",
                charset = "UTF-8",
            ),
            expectedThreadId = 1000,
        )

        assertEquals(1000, result.threadId)
        assertEquals(42, result.postId)
        assertFalse(result.pendingModeration)
    }

    @Test
    fun recognizesReplyPendingModerationAsSuccessfulSubmission() {
        val result = parseReplyResult(
            DiscuzResponse(
                variables = JSONObject("""{"tid":"1000","pid":"42"}"""),
                message = DiscuzMessage("回复需要审核", "post_reply_mod_succeed"),
                error = null,
                version = "4",
                charset = "UTF-8",
            ),
            expectedThreadId = 1000,
        )

        assertTrue(result.pendingModeration)
    }

    @Test
    fun exposesClosedThreadReplyError() {
        val response = DiscuzResponse(
            variables = null,
            message = DiscuzMessage("主题已关闭", "post_thread_closed"),
            error = null,
            version = "4",
            charset = "UTF-8",
        )

        val error = runCatching { parseReplyResult(response, expectedThreadId = 1000) }
            .exceptionOrNull()

        assertTrue(error is YamiboApiException)
        assertEquals("主题已关闭，无法回帖", error?.message)
        assertEquals("post_thread_closed", (error as YamiboApiException).serverCode)
    }

    @Test(expected = YamiboApiException::class)
    fun rejectsReplyAssignedToDifferentThread() {
        parseReplyResult(
            DiscuzResponse(
                variables = JSONObject("""{"tid":"1001","pid":"42"}"""),
                message = null,
                error = null,
                version = "4",
                charset = "UTF-8",
            ),
            expectedThreadId = 1000,
        )
    }

    @Test
    fun keepsOnlyRequestedAuthorAndUsesFilteredPageSizeForPagination() {
        val fixture = JSONObject(FIXTURE)
        fixture.put("ppp", "1")
        fixture.getJSONArray("postlist").put(
            JSONObject(fixture.getJSONArray("postlist").getJSONObject(0).toString())
                .put("pid", "10")
                .put("author", "bob")
                .put("authorid", "43")
                .put("first", "0")
                .put("number", "2")
                .put("position", "2"),
        )

        val page = parseThreadPosts(fixture, authorId = 42)

        assertEquals(listOf(42), page.posts.map { it.author.id })
        assertTrue(page.pagination.hasNextPage)

        fixture.put("ppp", "20")
        assertFalse(parseThreadPosts(fixture, authorId = 42).pagination.hasNextPage)
    }

    @Test
    fun acceptsUnsignedIntMaximumPollExpiration() {
        val fixture = JSONObject(FIXTURE)
        fixture.getJSONObject("special_poll").put("expirations", "4294967295")

        assertEquals(4_294_967_295_000L, parseThreadPosts(fixture).poll?.expiresAt)
    }

    @Test(expected = YamiboApiException::class)
    fun rejectsCommentAssignedToDifferentPost() {
        val fixture = JSONObject(FIXTURE)
        fixture.getJSONObject("comments").getJSONArray("9").getJSONObject(0).put("pid", "10")
        parseThreadPosts(fixture)
    }

    @Test
    fun fallsBackToPositionForInvalidDisplayNumber() {
        listOf("", "0", "置顶").forEach { number ->
            val fixture = JSONObject(FIXTURE)
            fixture.getJSONArray("postlist").getJSONObject(0).put("number", number)
            val post = parseThreadPosts(fixture).posts.single()
            assertEquals(1, post.number)
            assertEquals(1, post.position)
        }
    }

    @Test
    fun infersAttachmentImageWhenIsimageIsInvalid() {
        listOf("-1", "image/jpeg", "unknown").forEach { isImage ->
            val fixture = JSONObject(FIXTURE)
            fixture.getJSONArray("postlist").getJSONObject(0)
                .getJSONObject("attachments").getJSONObject("8").put("isimage", isImage)
            assertTrue(parseThreadPosts(fixture).posts.single().attachments.single().isImage)
        }
    }

    @Test
    fun removesHiddenSpamFromPostHtml() {
        val html = """正文<span style="display:none">+ v% [0 P+ _; u3 {$ y9 r</span>结尾"""

        assertEquals("正文结尾", sanitizePostHtml(html))
        assertEquals(
            "可见",
            sanitizePostHtml("""<SPAN class='noise' STYLE='color:red; display : none'>乱码</SPAN>可见"""),
        )
        assertEquals(
            "前后",
            sanitizePostHtml("""前<font class="jammer">, M0 J# x0 L/ B- S&nbsp;&nbsp;n: O</font>后"""),
        )
        assertEquals("正文", sanitizePostHtml("""<i class='foo jammer bar'>干扰</i>正文"""))
    }

    @Test
    fun parsesPostPageFromFindPostRedirect() {
        assertEquals(
            32,
            parsePostPageUrl(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=558130&page=32#pid41265818",
                558130,
            ),
        )
        assertEquals(
            32,
            parsePostPageUrl("https://bbs.yamibo.com/thread-558130-32-1.html#pid41265818", 558130),
        )
        assertEquals(
            null,
            parsePostPageUrl("https://bbs.yamibo.com/forum.php?mod=viewthread&tid=1&page=32", 558130),
        )
    }

    @Test
    fun buildsPostRatingFormRequestWithDesktopAjaxParameters() {
        assertEquals(
            mapOf(
                "mod" to "misc",
                "action" to "rate",
                "tid" to "1000",
                "pid" to "9",
                "inajax" to "1",
                "infloat" to "yes",
                "handlekey" to "rate",
                "ajaxtarget" to "fwin_content_rate",
                "mobile" to "no",
            ),
            postRatingFormParameters(threadId = 1000, postId = 9),
        )
    }

    @Test
    fun parsesAjaxWrappedDynamicPostRatingForm() {
        val form = parsePostRatingForm(RATING_FORM_RESPONSE, 1000, 9)

        assertEquals(1000, form.threadId)
        assertEquals(9, form.postId)
        assertEquals("abc12345", form.formHash)
        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=1000#pid9",
            form.referer,
        )
        assertEquals(
            listOf(
                YamiboPostRatingOption(
                    creditId = 1,
                    creditName = "百合币",
                    minScore = -3,
                    maxScore = 5,
                    remainingToday = 7,
                ),
                YamiboPostRatingOption(
                    creditId = 4,
                    creditName = "贡献 & 鲜花",
                    minScore = 0,
                    maxScore = 2,
                    remainingToday = 1,
                ),
            ),
            form.options,
        )
        assertEquals(listOf("感谢分享", "优秀回复 & 支持"), form.reasonSuggestions)
        assertTrue(form.sendReasonPmByDefault)
        assertTrue(form.sendReasonPmLocked)
        assertEquals(40, POST_RATING_REASON_MAX_LENGTH)
    }

    @Test
    fun rejectsPostRatingFormAssignedToDifferentPost() {
        val error = runCatching {
            parsePostRatingForm(RATING_FORM_RESPONSE, 1000, 10)
        }.exceptionOrNull()

        assertTrue(error is YamiboApiException)
        assertEquals(YamiboApiErrorCode.INVALID_RESPONSE, (error as YamiboApiException).code)
    }

    @Test
    fun rejectsNegativePostRatingDailyAllowance() {
        val response = RATING_FORM_RESPONSE.replace(
            "<td>今日剩余 1</td>",
            "<td>今日剩余 -1</td>",
        )
        val error = runCatching {
            parsePostRatingForm(response, 1000, 9)
        }.exceptionOrNull()

        assertTrue(error is YamiboApiException)
        assertEquals(YamiboApiErrorCode.INVALID_RESPONSE, (error as YamiboApiException).code)
    }

    @Test
    fun buildsPostRatingSubmissionWithEveryCreditAndTrimmedReason() {
        val form = parsePostRatingForm(RATING_FORM_RESPONSE, 1000, 9)

        assertEquals(
            mapOf(
                "mod" to "misc",
                "action" to "rate",
                "ratesubmit" to "yes",
                "inajax" to "1",
                "infloat" to "yes",
                "ajaxtarget" to "return_rate",
                "mobile" to "no",
            ),
            postRatingSubmitParameters(),
        )
        assertEquals(
            mapOf(
                "formhash" to "abc12345",
                "tid" to "1000",
                "pid" to "9",
                "referer" to "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=1000#pid9",
                "handlekey" to "rate",
                "ratesubmit" to "true",
                "score1" to "3",
                "score4" to "0",
                "reason" to "感谢分享",
                "sendreasonpm" to "on",
            ),
            postRatingSubmitForm(
                form = form,
                scores = mapOf(1 to 3),
                reason = "  感谢分享  ",
                sendReasonPm = true,
            ),
        )
        assertFalse(
            postRatingSubmitForm(form, mapOf(1 to 1), "", sendReasonPm = false)
                .containsKey("sendreasonpm"),
        )
    }

    @Test
    fun acceptsValidPostRatingAtRangeAndDailyAllowanceBoundaries() {
        val form = ratingForm()

        validatePostRating(
            form = form,
            scores = mapOf(1 to -3, 4 to 1),
            reason = "a".repeat(POST_RATING_REASON_MAX_LENGTH),
            sendReasonPm = false,
        )
    }

    @Test
    fun appliesDiscuzWeightedLengthToPostRatingReason() {
        assertEquals(40, postRatingReasonLength("中".repeat(20)))
        assertEquals(40, postRatingReasonLength("${"中".repeat(18)}abcd"))

        validatePostRating(
            form = ratingForm(),
            scores = mapOf(1 to 1),
            reason = "中".repeat(20),
            sendReasonPm = false,
        )
        assertIllegalArgument {
            validatePostRating(
                form = ratingForm(),
                scores = mapOf(1 to 1),
                reason = "中".repeat(21),
                sendReasonPm = false,
            )
        }
        assertIllegalArgument {
            validatePostRating(
                form = ratingForm(),
                scores = mapOf(1 to 1),
                reason = "${"中".repeat(18)}abcde",
                sendReasonPm = false,
            )
        }
    }

    @Test
    fun rejectsPostRatingWithoutNonZeroScore() {
        assertIllegalArgument {
            validatePostRating(ratingForm(), mapOf(1 to 0), "", sendReasonPm = false)
        }
    }

    @Test
    fun rejectsUnknownPostRatingCredit() {
        assertIllegalArgument {
            validatePostRating(ratingForm(), mapOf(99 to 1), "", sendReasonPm = false)
        }
    }

    @Test
    fun rejectsPostRatingOutsideCreditRange() {
        assertIllegalArgument {
            validatePostRating(ratingForm(), mapOf(1 to 6), "", sendReasonPm = false)
        }
    }

    @Test
    fun rejectsPostRatingBeyondRemainingDailyAllowance() {
        assertIllegalArgument {
            validatePostRating(ratingForm(), mapOf(4 to 2), "", sendReasonPm = false)
        }
    }

    @Test
    fun rejectsPostRatingReasonLongerThanServerLimitAfterTrimming() {
        assertIllegalArgument {
            validatePostRating(
                ratingForm(),
                mapOf(1 to 1),
                " ${"a".repeat(POST_RATING_REASON_MAX_LENGTH + 1)} ",
                sendReasonPm = false,
            )
        }
    }

    @Test
    fun rejectsChangingServerLockedReasonPmChoice() {
        assertIllegalArgument {
            validatePostRating(
                ratingForm(sendReasonPmByDefault = true, sendReasonPmLocked = true),
                mapOf(1 to 1),
                "",
                sendReasonPm = false,
            )
        }
    }

    @Test
    fun recognizesOnlySucceedHandleAsPostRatingSuccess() {
        parsePostRatingSubmitResult(
            ajaxResponse(
                """
                <script type="text/javascript">
                  if (typeof succeedhandle_rate == 'function') {
                    succeedhandle_rate('forum.php?mod=viewthread&amp;tid=1000#pid9', '评分成功', {});
                  }
                </script>
                """.trimIndent(),
            ),
            expectedThreadId = 1000,
            expectedPostId = 9,
        )

        val unknown = runCatching {
            parsePostRatingSubmitResult(
                "<div>评分完成</div>",
                expectedThreadId = 1000,
                expectedPostId = 9,
            )
        }.exceptionOrNull()
        val functionDefinitionOnly = runCatching {
            parsePostRatingSubmitResult(
                "<script>function succeedhandle_rate(locationhref) { return locationhref; }</script>",
                expectedThreadId = 1000,
                expectedPostId = 9,
            )
        }.exceptionOrNull()
        assertTrue(unknown is YamiboApiException)
        assertEquals(YamiboApiErrorCode.INVALID_RESPONSE, (unknown as YamiboApiException).code)
        assertTrue(functionDefinitionOnly is YamiboApiException)
        assertEquals(
            YamiboApiErrorCode.INVALID_RESPONSE,
            (functionDefinitionOnly as YamiboApiException).code,
        )
    }

    @Test
    fun rejectsPostRatingSuccessCallbackForDifferentTargetOrNonExecutableText() {
        val wrongTarget = runCatching {
            parsePostRatingSubmitResult(
                ajaxResponse(
                    """
                    <script>
                      if(typeof succeedhandle_rate=='function') {
                        succeedhandle_rate('forum.php?mod=viewthread&amp;tid=1001#pid10', '评分成功', {});
                      }
                    </script>
                    """.trimIndent(),
                ),
                expectedThreadId = 1000,
                expectedPostId = 9,
            )
        }.exceptionOrNull()
        val commentOnly = runCatching {
            parsePostRatingSubmitResult(
                ajaxResponse(
                    """
                    <script>
                      // if(typeof succeedhandle_rate=='function') {
                      //   succeedhandle_rate('forum.php?mod=viewthread&amp;tid=1000#pid9', '评分成功', {});
                      // }
                    </script>
                    """.trimIndent(),
                ),
                expectedThreadId = 1000,
                expectedPostId = 9,
            )
        }.exceptionOrNull()
        val stringOnly = runCatching {
            parsePostRatingSubmitResult(
                ajaxResponse(
                    """
                    <script>
                      const callback = "succeedhandle_rate('forum.php?mod=viewthread&amp;tid=1000#pid9')";
                    </script>
                    """.trimIndent(),
                ),
                expectedThreadId = 1000,
                expectedPostId = 9,
            )
        }.exceptionOrNull()

        listOf(wrongTarget, commentOnly, stringOnly).forEach { error ->
            assertTrue(error is YamiboApiException)
            assertEquals(
                YamiboApiErrorCode.INVALID_RESPONSE,
                (error as YamiboApiException).code,
            )
        }
    }

    @Test
    fun exposesErrorHandlePostRatingMessage() {
        val error = runCatching {
            parsePostRatingSubmitResult(
                ajaxResponse(
                    """<script>errorhandle_rate('不能给 Bob\'s 帖子评分 &amp; 请重试');</script>""",
                ),
                expectedThreadId = 1000,
                expectedPostId = 9,
            )
        }.exceptionOrNull()

        assertTrue(error is YamiboApiException)
        assertEquals(YamiboApiErrorCode.SERVER_ERROR, (error as YamiboApiException).code)
        assertEquals("rate_failed", error.serverCode)
        assertEquals("不能给 Bob's 帖子评分 & 请重试", error.message)
    }

    @Test
    fun exposesNestedPostRatingPromptHtmlMessage() {
        val error = runCatching {
            parsePostRatingSubmitResult(
                ajaxResponse(
                    """
                    <div class="f_c altw">
                      <div class="alert_error"><p>今日评分数超过限制</p></div>
                    </div>
                    """.trimIndent(),
                ),
                expectedThreadId = 1000,
                expectedPostId = 9,
            )
        }.exceptionOrNull()

        assertTrue(error is YamiboApiException)
        assertEquals("今日评分数超过限制", error?.message)
    }

    @Test
    fun mapsPostRatingLoginFormToAuthenticationMessage() {
        val error = runCatching {
            parsePostRatingForm(
                ajaxResponse("""<form id="loginform_123"><input name="username"></form>"""),
                expectedThreadId = 1000,
                expectedPostId = 9,
            )
        }.exceptionOrNull()

        assertTrue(error is YamiboApiException)
        assertEquals(YamiboApiErrorCode.SERVER_ERROR, (error as YamiboApiException).code)
        assertEquals("not_authenticated", error.serverCode)
        assertEquals("请先登录百合会", error.message)
    }

    @Test
    fun mapsMobilePostRatingLoginFormToAuthenticationMessage() {
        val error = runCatching {
            parsePostRatingSubmitResult(
                ajaxResponse("""<form action="member.php" id="loginform"></form>"""),
                expectedThreadId = 1000,
                expectedPostId = 9,
            )
        }.exceptionOrNull()

        assertTrue(error is YamiboApiException)
        assertEquals("not_authenticated", (error as YamiboApiException).serverCode)
        assertEquals("请先登录百合会", error.message)
    }

    @Test
    fun mapsGuestErrorHandleToAuthenticationMessage() {
        val error = runCatching {
            parsePostRatingSubmitResult(
                ajaxResponse(
                    """
                    <script>
                      if(typeof errorhandle_rate=='function') {
                        errorhandle_rate(
                          '抱歉，您所在的用户组(遊客)无法进行此操作',
                          {'grouptitle':'遊客'}
                        );
                      }
                    </script>
                    """.trimIndent(),
                ),
                expectedThreadId = 1000,
                expectedPostId = 9,
            )
        }.exceptionOrNull()

        assertTrue(error is YamiboApiException)
        assertEquals("not_authenticated", (error as YamiboApiException).serverCode)
        assertEquals("请先登录百合会", error.message)
    }

    @Test
    fun rejectsMalformedPostRatingAjaxXml() {
        val error = runCatching {
            unwrapPostRatingAjaxResponse("<root><div>missing CDATA</div></root>")
        }.exceptionOrNull()

        assertTrue(error is YamiboApiException)
        assertEquals(YamiboApiErrorCode.INVALID_RESPONSE, (error as YamiboApiException).code)
    }

    @Test
    fun parsesCompletePostRatingRows() {
        val html = """
            <table class="list">
              <thead><tr><td>积分</td><td>用户名</td><td>时间</td><td>理由</td></tr></thead>
              <tr>
                <td>百合币 +3 枚</td>
                <td><a href="space-uid-42.html">Alice &amp; Bob</a></td>
                <td>2026-7-13 19:25</td>
                <td>生日快乐 &amp; 好图</td>
              </tr>
              <tr>
                <td>贡献 -1 点</td>
                <td><a href="home.php?mod=space&amp;uid=43">Carol</a></td>
                <td>2026-7-13 19:30</td>
                <td></td>
              </tr>
            </table>
        """.trimIndent()

        val ratings = parsePostRatings(html)

        assertEquals(2, ratings.size)
        assertEquals("百合币", ratings[0].creditName)
        assertEquals(3, ratings[0].score)
        assertEquals("枚", ratings[0].unit)
        assertEquals(42, ratings[0].userId)
        assertEquals("Alice & Bob", ratings[0].username)
        assertEquals("生日快乐 & 好图", ratings[0].reason)
        assertEquals(-1, ratings[1].score)
        assertEquals("", ratings[1].reason)
    }

    private fun ratingForm(
        sendReasonPmByDefault: Boolean = false,
        sendReasonPmLocked: Boolean = false,
    ) = YamiboPostRatingForm(
        threadId = 1000,
        postId = 9,
        formHash = "abc12345",
        referer = "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=1000#pid9",
        options = listOf(
            YamiboPostRatingOption(1, "百合币", -3, 5, 7),
            YamiboPostRatingOption(4, "贡献", 0, 2, 1),
        ),
        reasonSuggestions = emptyList(),
        sendReasonPmByDefault = sendReasonPmByDefault,
        sendReasonPmLocked = sendReasonPmLocked,
    )

    private fun assertIllegalArgument(block: () -> Unit) {
        assertTrue(runCatching(block).exceptionOrNull() is IllegalArgumentException)
    }

    private companion object {
        fun ajaxResponse(content: String): String =
            """<?xml version="1.0" encoding="UTF-8"?><root><![CDATA[$content]]></root>"""

        val RATING_FORM_RESPONSE = ajaxResponse(
            """
            <div id="floatlayout_topicadmin">
              <form autocomplete="off" id="rateform" method="post">
                <input value="abc12345" name="formhash" type="hidden">
                <input type="hidden" name="tid" value="1000">
                <input type="hidden" name="pid" value="9">
                <input type="hidden" name="referer" value="https://bbs.yamibo.com/forum.php?mod=viewthread&amp;tid=1000#pid9">
                <input type="hidden" name="handlekey" value="rate">
                <table>
                  <tr><th></th><th></th><th>评分范围</th><th>今日剩余</th></tr>
                  <tr>
                    <td><img src="coin.png"> 百合币</td>
                    <td><input class="px" id="score1" name="score1" value="0"></td>
                    <td>-3 ~ +5</td>
                    <td>7</td>
                  </tr>
                  <tr>
                    <td>贡献 &amp; 鲜花</td>
                    <td><select name="score4"><option value="0">0</option></select></td>
                    <td>0 ～ 2</td>
                    <td>今日剩余 1</td>
                  </tr>
                </table>
                <ul class="reasonselect" id="reasonselect">
                  <li>感谢分享</li>
                  <li>--------</li>
                  <li>优秀回复 &amp; 支持</li>
                  <li>感谢分享</li>
                </ul>
                <input type="text" id="reason" name="reason">
                <input disabled checked="checked" type="checkbox" name="sendreasonpm" id="sendreasonpm">
              </form>
            </div>
            """.trimIndent(),
        )

        val FIXTURE = """
          {
            "ppp":"20",
            "allowpostcomment":["1"],
            "thread":{"tid":"1000","author":"alice","authorid":"42","dateline":"10","digest":"0","fid":"300","attachment":"0","heats":"1","rate":"1","closed":"0","lastposter":"bob","lastpost":"刚刚","maxposition":"2","price":"0","readperm":"0","recommend_add":"1","replies":"1","special":"1","subject":"投票","typeid":"0","views":"12"},
            "postlist":[{"author":"alice","authorid":"42","anonymous":"0","groupiconid":"","groupid":"10","pid":"9","dbdateline":"10","dateline":"刚刚","message":"<p>不可信正文</p>","attachment":"1","attachments":{"8":{"aid":"8","url":"data/attachment/forum/","attachment":"202607/example.jpg","filename":"example.jpg","isimage":"1"}},"first":"1","number":"1","position":"1","ratetimes":"4","replycredit":"0","status":"0","tid":"1000"}],
            "comments":{"9":[{"author":"bob","authorid":"43","avatar":"//example.com/a.png","dateline":"刚刚","id":"2","comment":"点评","pid":"9","tid":"1000"}]},
            "special_poll":{"allowvote":"1","expirations":"0","maxchoices":"1","multiple":"0","visiblepoll":"1","voterscount":"4","polloptions":{"7":{"color":"ff00aa","polloptionid":"7","percent":"25.5","polloption":"选项","votes":"1"}}}
          }
        """.trimIndent()
    }
}
