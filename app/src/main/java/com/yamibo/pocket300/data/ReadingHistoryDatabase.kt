package com.yamibo.pocket300.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yamibo.pocket300.api.YamiboThreadDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReadingHistoryEntry(
    val threadId: Int,
    val forumId: Int,
    val subject: String,
    val authorName: String,
    val lastPostAtText: String,
    val lastReadFloor: Int,
    val readAt: Long,
)

class ReadingHistoryDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val _entries = MutableStateFlow<Map<Int, ReadingHistoryEntry>>(emptyMap())
    val entries: StateFlow<Map<Int, ReadingHistoryEntry>> = _entries.asStateFlow()

    init {
        refreshEntries()
    }

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE reading_history (
                thread_id INTEGER PRIMARY KEY,
                forum_id INTEGER NOT NULL,
                subject TEXT NOT NULL,
                author_name TEXT NOT NULL,
                last_post_at_text TEXT NOT NULL,
                read_at INTEGER NOT NULL,
                last_read_floor INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        createIndexes(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL(
                "ALTER TABLE reading_history ADD COLUMN last_read_floor INTEGER NOT NULL DEFAULT 1",
            )
            database.execSQL("DROP INDEX IF EXISTS reading_history_read_at")
            createIndexes(database)
        }
    }

    fun record(thread: YamiboThreadDetails, lastReadFloor: Int, readAt: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply {
            put("thread_id", thread.id)
            put("forum_id", thread.forumId)
            put("subject", thread.subject)
            put("author_name", thread.author.name)
            put("last_post_at_text", thread.lastPostAtText)
            put("last_read_floor", lastReadFloor.coerceAtLeast(1))
            put("read_at", readAt)
        }
        writableDatabase.transaction {
            insertWithOnConflict("reading_history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            execSQL(
                """
                DELETE FROM reading_history
                WHERE thread_id NOT IN (
                    SELECT thread_id FROM reading_history ORDER BY read_at DESC LIMIT $MAX_ENTRIES
                )
                """.trimIndent(),
            )
        }
        refreshEntries()
    }

    fun remove(threadId: Int) {
        writableDatabase.delete(
            "reading_history",
            "thread_id = ?",
            arrayOf(threadId.toString()),
        )
        refreshEntries()
    }

    fun getAll(): List<ReadingHistoryEntry> = readableDatabase.query(
        "reading_history",
        COLUMNS,
        null,
        null,
        null,
        null,
        "read_at DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    ReadingHistoryEntry(
                        threadId = cursor.getInt(0),
                        forumId = cursor.getInt(1),
                        subject = cursor.getString(2),
                        authorName = cursor.getString(3),
                        lastPostAtText = cursor.getString(4),
                        lastReadFloor = cursor.getInt(5),
                        readAt = cursor.getLong(6),
                    ),
                )
            }
        }
    }

    private fun refreshEntries() {
        _entries.value = getAll().associateBy(ReadingHistoryEntry::threadId)
    }

    private inline fun <T> SQLiteDatabase.transaction(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        return try {
            block().also { setTransactionSuccessful() }
        } finally {
            endTransaction()
        }
    }

    companion object {
        private const val DATABASE_NAME = "pocket300.db"
        private const val DATABASE_VERSION = 2
        private const val MAX_ENTRIES = 500
        private val COLUMNS = arrayOf(
            "thread_id",
            "forum_id",
            "subject",
            "author_name",
            "last_post_at_text",
            "last_read_floor",
            "read_at",
        )

        private fun createIndexes(database: SQLiteDatabase) {
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS reading_history_read_at_thread_id " +
                    "ON reading_history(read_at DESC, thread_id)",
            )
        }

        @Volatile
        private var instance: ReadingHistoryDatabase? = null

        fun getInstance(context: Context): ReadingHistoryDatabase = instance ?: synchronized(this) {
            instance ?: ReadingHistoryDatabase(context.applicationContext).also { instance = it }
        }
    }
}
