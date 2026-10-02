package com.yamibo.pocket300.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.api.YamiboSearchAuthor
import com.yamibo.pocket300.api.YamiboSearchForum
import com.yamibo.pocket300.api.YamiboSearchThread
import com.yamibo.pocket300.api.YamiboThreadDetails
import com.yamibo.pocket300.api.YamiboThreadSearchType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadAuthorPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = listOf("author-history-v1.db", "author-history-v2.db", "author-history-new.db", "author-lists-old.db", "author-lists-new.db")

    @Before fun clearBefore() = clearDatabases()
    @After fun clearAfter() = clearDatabases()

    private fun clearDatabases() { names.forEach(context::deleteDatabase) }

    @Test fun upgradesBothHistoryVersionsWithoutChangingReadingPositionOrTime() {
        for (version in 1..2) {
            val name = "author-history-v$version.db"
            legacyDatabase(name, version) { database ->
                val floorColumn = if (version == 2) ", last_read_floor INTEGER NOT NULL DEFAULT 1" else ""
                database.execSQL("""
                    CREATE TABLE reading_history (
                        thread_id INTEGER PRIMARY KEY, forum_id INTEGER NOT NULL, subject TEXT NOT NULL,
                        author_name TEXT NOT NULL, last_post_at_text TEXT NOT NULL, read_at INTEGER NOT NULL
                        $floorColumn
                    )
                """.trimIndent())
                database.execSQL("INSERT INTO reading_history(thread_id,forum_id,subject,author_name,last_post_at_text,read_at) VALUES(100,300,'标题','alice','昨天',123)")
                if (version == 2) database.execSQL("UPDATE reading_history SET last_read_floor = 24")
            }
            ReadingHistoryDatabase(context, name).use { history ->
                val entry = history.getAll().single()
                assertEquals(100, entry.threadId)
                assertEquals("alice", entry.authorName)
                assertEquals(123L, entry.readAt)
                assertEquals(if (version == 1) 1 else 24, entry.lastReadFloor)
                assertNull(entry.authorId)
                assertNull(entry.authorAvatarUrl)
            }
        }
    }

    @Test fun upgradesCustomListsWithoutLosingThreadsExclusionsOrForeignKeys() {
        val name = "author-lists-old.db"
        legacyDatabase(name, 4) { database ->
            database.execSQL("CREATE TABLE custom_lists(id INTEGER PRIMARY KEY)")
            database.execSQL("""
                CREATE TABLE custom_list_threads (
                    list_id INTEGER NOT NULL, thread_id INTEGER NOT NULL, forum_id INTEGER NOT NULL,
                    forum_name TEXT NOT NULL, subject TEXT NOT NULL, author_name TEXT NOT NULL,
                    created_at_text TEXT NOT NULL, excerpt TEXT, reply_count INTEGER NOT NULL,
                    view_count INTEGER NOT NULL, web_url TEXT NOT NULL,
                    PRIMARY KEY(list_id,thread_id),
                    FOREIGN KEY(list_id) REFERENCES custom_lists(id) ON DELETE CASCADE
                )
            """.trimIndent())
            database.execSQL("CREATE TABLE custom_list_exclusions(list_id INTEGER,thread_id INTEGER,excluded_at INTEGER)")
            database.execSQL("INSERT INTO custom_lists VALUES(1)")
            database.execSQL("INSERT INTO custom_list_threads VALUES(1,100,300,'论坛','标题','alice','昨天',NULL,12,34,'https://bbs.yamibo.com/thread-100-1-1.html')")
            database.execSQL("INSERT INTO custom_list_exclusions VALUES(1,999,123)")
        }
        CustomListDatabase(context, name).use { lists ->
            val thread = lists.getThreads(1).single()
            assertEquals("alice", thread.authorName)
            assertEquals(12, thread.replyCount)
            assertEquals(34, thread.viewCount)
            assertNull(thread.authorId)
            assertNull(thread.authorAvatarUrl)
            lists.readableDatabase.rawQuery("SELECT thread_id FROM custom_list_exclusions", null).use {
                it.moveToFirst()
                assertEquals(999, it.getInt(0))
            }
            lists.readableDatabase.rawQuery("PRAGMA foreign_key_list(custom_list_threads)", null).use {
                assertEquals(1, it.count)
            }
        }
    }

    @Test fun preservesAuthorThroughListReplacementMergeBulkReadAndReadingRecord() {
        CustomListDatabase(context, "author-lists-new.db").use { lists ->
            val listId = lists.createList("列表", listOf("标题"), YamiboThreadSearchType.TITLE, now = 1)
            val thread = YamiboSearchThread(
                author = YamiboSearchAuthor("https://bbs.yamibo.com/avatar-42.jpg", 42, "alice"),
                createdAtText = "昨天", excerpt = null,
                forum = YamiboSearchForum(300, "论坛", "https://bbs.yamibo.com/forum-300-1.html"),
                id = 100, imageUrls = emptyList(), replyCount = 12, subject = "标题", viewCount = 34,
                webUrl = "https://bbs.yamibo.com/thread-100-1-1.html",
            )
            val added = lists.replaceThreads(listId, listOf(thread), now = 2).single()
            assertEquals(42, added.authorId)
            assertEquals(thread.author.avatarUrl, added.authorAvatarUrl)
            assertEquals(added, lists.getThreads(listId).single())
            val updated = thread.copy(author = YamiboSearchAuthor("https://bbs.yamibo.com/avatar-43.jpg", 43, "alice"))
            lists.mergeThreads(listId, listOf(updated), now = 3)
            val saved = lists.getThreads(listId).single()
            assertEquals(43, saved.authorId)
            assertEquals(updated.author.avatarUrl, saved.authorAvatarUrl)
            ReadingHistoryDatabase(context, "author-history-new.db").use { history ->
                history.markRead(listOf(saved), readAt = 4)
                assertEquals(43, history.getAll().single().authorId)
                assertEquals(updated.author.avatarUrl, history.getAll().single().authorAvatarUrl)
                val details = YamiboThreadDetails(
                    author = YamiboPostAuthor(thread.author.avatarUrl, 42, "alice"),
                    forumId = 300, heat = 0, hasAttachment = false, id = 100, isClosed = false,
                    lastPostAtText = "今天", price = 0, replyCount = 12, subject = "标题", viewCount = 34,
                    webUrl = thread.webUrl,
                )
                history.record(details, lastReadFloor = 24, readAt = 5)
                val entry = history.getAll().single()
                assertEquals(42, entry.authorId)
                assertEquals(thread.author.avatarUrl, entry.authorAvatarUrl)
                assertEquals(24, entry.lastReadFloor)
                assertEquals(5L, entry.readAt)
                history.markRead(listOf(saved), readAt = 6)
                assertEquals(entry, history.getAll().single())
            }
        }
    }

    private fun legacyDatabase(name: String, version: Int, create: (SQLiteDatabase) -> Unit) {
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            create(it)
            it.version = version
        }
    }
}
