package com.yamibo.pocket300.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yamibo.pocket300.api.YamiboSearchThread
import com.yamibo.pocket300.api.YamiboThreadSearchType

class CustomListDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(database: SQLiteDatabase) {
        super.onConfigure(database)
        database.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE custom_lists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                keywords TEXT NOT NULL,
                search_type TEXT NOT NULL DEFAULT 'title',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                last_synced_at INTEGER
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE custom_list_threads (
                list_id INTEGER NOT NULL,
                thread_id INTEGER NOT NULL,
                forum_id INTEGER NOT NULL,
                forum_name TEXT NOT NULL,
                subject TEXT NOT NULL,
                author_name TEXT NOT NULL,
                created_at_text TEXT NOT NULL,
                excerpt TEXT,
                reply_count INTEGER NOT NULL,
                view_count INTEGER NOT NULL,
                web_url TEXT NOT NULL,
                PRIMARY KEY (list_id, thread_id),
                FOREIGN KEY (list_id) REFERENCES custom_lists(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE custom_list_exclusions (
                list_id INTEGER NOT NULL,
                thread_id INTEGER NOT NULL,
                excluded_at INTEGER NOT NULL,
                PRIMARY KEY (list_id, thread_id),
                FOREIGN KEY (list_id) REFERENCES custom_lists(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX custom_list_threads_list_id ON custom_list_threads(list_id, thread_id DESC)",
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL(
                "ALTER TABLE custom_lists ADD COLUMN search_type TEXT NOT NULL DEFAULT 'title'",
            )
        }
    }

    fun createList(
        name: String,
        keywords: List<String>,
        searchType: YamiboThreadSearchType,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val values = listValues(name, keywords, searchType, now).apply { put("created_at", now) }
        return writableDatabase.insertOrThrow("custom_lists", null, values)
    }

    fun updateList(
        id: Long,
        name: String,
        keywords: List<String>,
        searchType: YamiboThreadSearchType,
        now: Long = System.currentTimeMillis(),
    ) {
        val values = listValues(name, keywords, searchType, now).apply { putNull("last_synced_at") }
        writableDatabase.update(
            "custom_lists",
            values,
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun deleteList(id: Long) {
        writableDatabase.delete("custom_lists", "id = ?", arrayOf(id.toString()))
    }

    fun getLists(): List<CustomThreadList> = readableDatabase.rawQuery(
        """
        SELECT l.id, l.name, l.keywords, l.search_type, l.created_at, l.updated_at, l.last_synced_at,
               (SELECT COUNT(*) FROM custom_list_threads t WHERE t.list_id = l.id),
               (SELECT COUNT(*) FROM custom_list_exclusions e WHERE e.list_id = l.id)
        FROM custom_lists l
        ORDER BY l.updated_at DESC, l.id DESC
        """.trimIndent(),
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toCustomList())
        }
    }

    fun getList(id: Long): CustomThreadList? = readableDatabase.rawQuery(
        """
        SELECT l.id, l.name, l.keywords, l.search_type, l.created_at, l.updated_at, l.last_synced_at,
               (SELECT COUNT(*) FROM custom_list_threads t WHERE t.list_id = l.id),
               (SELECT COUNT(*) FROM custom_list_exclusions e WHERE e.list_id = l.id)
        FROM custom_lists l
        WHERE l.id = ?
        """.trimIndent(),
        arrayOf(id.toString()),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toCustomList() else null }

    fun getThreads(listId: Long): List<CustomListThread> = readableDatabase.query(
        "custom_list_threads",
        THREAD_COLUMNS,
        "list_id = ?",
        arrayOf(listId.toString()),
        null,
        null,
        "thread_id DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    CustomListThread(
                        listId = cursor.getLong(0),
                        threadId = cursor.getInt(1),
                        forumId = cursor.getInt(2),
                        forumName = cursor.getString(3),
                        subject = cursor.getString(4),
                        authorName = cursor.getString(5),
                        createdAtText = cursor.getString(6),
                        excerpt = cursor.getString(7),
                        replyCount = cursor.getInt(8),
                        viewCount = cursor.getInt(9),
                        webUrl = cursor.getString(10),
                    ),
                )
            }
        }
    }

    fun replaceThreads(
        listId: Long,
        threads: Collection<YamiboSearchThread>,
        now: Long = System.currentTimeMillis(),
    ) {
        writableDatabase.transaction {
            delete("custom_list_threads", "list_id = ?", arrayOf(listId.toString()))
            val excluded = rawQuery(
                "SELECT thread_id FROM custom_list_exclusions WHERE list_id = ?",
                arrayOf(listId.toString()),
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getInt(0)) } }
            threads.filterNot { it.id in excluded }.forEach { thread ->
                insertOrThrow("custom_list_threads", null, threadValues(listId, thread))
            }
            update(
                "custom_lists",
                ContentValues().apply {
                    put("last_synced_at", now)
                    put("updated_at", now)
                },
                "id = ?",
                arrayOf(listId.toString()),
            )
        }
    }

    fun mergeThreads(
        listId: Long,
        threads: Collection<YamiboSearchThread>,
        now: Long = System.currentTimeMillis(),
    ) {
        writableDatabase.transaction {
            val excluded = rawQuery(
                "SELECT thread_id FROM custom_list_exclusions WHERE list_id = ?",
                arrayOf(listId.toString()),
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getInt(0)) } }
            threads.filterNot { it.id in excluded }.forEach { thread ->
                insertWithOnConflict(
                    "custom_list_threads",
                    null,
                    threadValues(listId, thread),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            update(
                "custom_lists",
                ContentValues().apply {
                    put("last_synced_at", now)
                    put("updated_at", now)
                },
                "id = ?",
                arrayOf(listId.toString()),
            )
        }
    }

    fun excludeThread(listId: Long, threadId: Int, now: Long = System.currentTimeMillis()) {
        writableDatabase.transaction {
            insertWithOnConflict(
                "custom_list_exclusions",
                null,
                ContentValues().apply {
                    put("list_id", listId)
                    put("thread_id", threadId)
                    put("excluded_at", now)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            delete(
                "custom_list_threads",
                "list_id = ? AND thread_id = ?",
                arrayOf(listId.toString(), threadId.toString()),
            )
        }
    }

    fun clearExclusions(listId: Long) {
        writableDatabase.delete(
            "custom_list_exclusions",
            "list_id = ?",
            arrayOf(listId.toString()),
        )
    }

    private fun listValues(
        name: String,
        keywords: List<String>,
        searchType: YamiboThreadSearchType,
        updatedAt: Long,
    ) = ContentValues().apply {
        put("name", name.trim())
        put("keywords", keywords.joinToString("\n"))
        put("search_type", searchType.name.lowercase())
        put("updated_at", updatedAt)
    }

    private fun threadValues(listId: Long, thread: YamiboSearchThread) = ContentValues().apply {
        put("list_id", listId)
        put("thread_id", thread.id)
        put("forum_id", thread.forum.id)
        put("forum_name", thread.forum.name)
        put("subject", thread.subject)
        put("author_name", thread.author.name)
        put("created_at_text", thread.createdAtText)
        put("excerpt", thread.excerpt)
        put("reply_count", thread.replyCount)
        put("view_count", thread.viewCount)
        put("web_url", thread.webUrl)
    }

    private fun android.database.Cursor.toCustomList() = CustomThreadList(
        id = getLong(0),
        name = getString(1),
        keywords = getString(2).lineSequence().filter(String::isNotBlank).toList(),
        searchType = parseCustomListSearchType(getString(3)),
        createdAt = getLong(4),
        updatedAt = getLong(5),
        lastSyncedAt = if (isNull(6)) null else getLong(6),
        threadCount = getInt(7),
        excludedCount = getInt(8),
    )

    private inline fun <T> SQLiteDatabase.transaction(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        return try {
            block().also { setTransactionSuccessful() }
        } finally {
            endTransaction()
        }
    }

    companion object {
        private const val DATABASE_NAME = "custom_lists.db"
        private const val DATABASE_VERSION = 2
        private val THREAD_COLUMNS = arrayOf(
            "list_id", "thread_id", "forum_id", "forum_name", "subject", "author_name",
            "created_at_text", "excerpt", "reply_count", "view_count", "web_url",
        )

        @Volatile
        private var instance: CustomListDatabase? = null

        fun getInstance(context: Context): CustomListDatabase = instance ?: synchronized(this) {
            instance ?: CustomListDatabase(context.applicationContext).also { instance = it }
        }
    }
}
