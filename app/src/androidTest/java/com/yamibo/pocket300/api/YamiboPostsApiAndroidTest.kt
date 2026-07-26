package com.yamibo.pocket300.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YamiboPostsApiAndroidTest {
    @Test
    fun parsesPostRatingSuccessCallbackWithAndroidRegexEngine() {
        parsePostRatingSubmitResult(
            responseHtml = """
                <?xml version="1.0" encoding="utf-8"?>
                <root><![CDATA[
                    <script type="text/javascript">
                        if(typeof succeedhandle_rate == 'function') {
                            succeedhandle_rate(
                                'forum.php?mod=viewthread&amp;tid=1000#pid9',
                                '评分成功',
                                {}
                            );
                        }
                    </script>
                ]]></root>
            """.trimIndent(),
            expectedThreadId = 1000,
            expectedPostId = 9,
        )
    }
}
