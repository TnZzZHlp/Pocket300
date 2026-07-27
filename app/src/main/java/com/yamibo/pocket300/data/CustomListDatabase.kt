package com.yamibo.pocket300.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yamibo.pocket300.api.YamiboSearchThread
import com.yamibo.pocket300.api.YamiboThreadSearchType
import com.yamibo.pocket300.logging.AppLogger

class CustomListDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(database: SQLiteDatabase) {
        super.onConfigure(database)
        database.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(database: SQLiteDatabase) {
        AppLogger.info(TAG) { "Creating custom list database schema version $DATABASE_VERSION" }
        database.execSQL(
            """
            CREATE TABLE custom_lists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                keywords TEXT NOT NULL,
                search_type TEXT NOT NULL DEFAULT 'title',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                last_synced_at INTEGER,
                auto_refresh_interval_hours INTEGER NOT NULL DEFAULT 24,
                auto_download_new_threads INTEGER NOT NULL DEFAULT 0,
                auto_delete_after_image_reading INTEGER NOT NULL DEFAULT 0
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
        createAutoDownloadsTable(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        AppLogger.info(TAG) { "Upgrading custom list database from version $oldVersion to $newVersion" }
        if (oldVersion < 2) {
            database.execSQL(
                "ALTER TABLE custom_lists ADD COLUMN search_type TEXT NOT NULL DEFAULT 'title'",
            )
        }
        if (oldVersion < 3) {
            database.execSQL(
                "ALTER TABLE custom_lists ADD COLUMN auto_refresh_interval_hours INTEGER NOT NULL DEFAULT 24",
            )
        }
        if (oldVersion < 4) {
            database.execSQL(
                "ALTER TABLE custom_lists ADD COLUMN auto_download_new_threads INTEGER NOT NULL DEFAULT 0",
            )
            database.execSQL(
                "ALTER TABLE custom_lists ADD COLUMN auto_delete_after_image_reading INTEGER NOT NULL DEFAULT 0",
            )
            createAutoDownloadsTable(database)
        }
    }

    fun createList(
        name: String,
        keywords: List<String>,
        searchType: YamiboThreadSearchType,
        now: Long = System.currentTimeMillis(),
        autoRefreshIntervalHours: Int = DEFAULT_CUSTOM_LIST_AUTO_REFRESH_INTERVAL_HOURS,
        autoDownloadNewThreads: Boolean = DEFAULT_CUSTOM_LIST_AUTO_DOWNLOAD_NEW_THREADS,
        autoDeleteAfterImageReading: Boolean =
            DEFAULT_CUSTOM_LIST_AUTO_DELETE_AFTER_IMAGE_READING,
    ): Long {
        val values = listValues(
            name = name,
            keywords = keywords,
            searchType = searchType,
            updatedAt = now,
            autoRefreshIntervalHours = autoRefreshIntervalHours,
            autoDownloadNewThreads = autoDownloadNewThreads,
            autoDeleteAfterImageReading = autoDeleteAfterImageReading,
        ).apply {
            put("created_at", now)
        }
        return writableDatabase.insertOrThrow("custom_lists", null, values).also { id ->
            AppLogger.info(TAG) { "Created custom list $id" }
        }
    }

    fun updateList(
        id: Long,
        name: String,
        keywords: List<String>,
        searchType: YamiboThreadSearchType,
        now: Long = System.currentTimeMillis(),
        autoRefreshIntervalHours: Int = DEFAULT_CUSTOM_LIST_AUTO_REFRESH_INTERVAL_HOURS,
        autoDownloadNewThreads: Boolean = DEFAULT_CUSTOM_LIST_AUTO_DOWNLOAD_NEW_THREADS,
        autoDeleteAfterImageReading: Boolean =
            DEFAULT_CUSTOM_LIST_AUTO_DELETE_AFTER_IMAGE_READING,
    ) {
        val existing = getList(id)
        val values = listValues(
            name = name,
            keywords = keywords,
            searchType = searchType,
            updatedAt = now,
            autoRefreshIntervalHours = autoRefreshIntervalHours,
            autoDownloadNewThreads = autoDownloadNewThreads,
            autoDeleteAfterImageReading = autoDeleteAfterImageReading,
        ).apply {
            if (existing == null || existing.keywords != keywords || existing.searchType != searchType) {
                putNull("last_synced_at")
            }
        }
        writableDatabase.update(
            "custom_lists",
            values,
            "id = ?",
            arrayOf(id.toString()),
        )
        AppLogger.info(TAG) { "Updated custom list $id" }
    }

    fun deleteList(id: Long) {
        writableDatabase.delete("custom_lists", "id = ?", arrayOf(id.toString()))
        AppLogger.info(TAG) { "Deleted custom list $id" }
    }

    fun getLists(): List<CustomThreadList> = readableDatabase.rawQuery(
        """
        SELECT l.id, l.name, l.keywords, l.search_type, l.created_at, l.updated_at, l.last_synced_at,
               l.auto_refresh_interval_hours,
               (SELECT COUNT(*) FROM custom_list_threads t WHERE t.list_id = l.id),
               (SELECT COUNT(*) FROM custom_list_exclusions e WHERE e.list_id = l.id),
               l.auto_download_new_threads, l.auto_delete_after_image_reading
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
               l.auto_refresh_interval_hours,
               (SELECT COUNT(*) FROM custom_list_threads t WHERE t.list_id = l.id),
               (SELECT COUNT(*) FROM custom_list_exclusions e WHERE e.list_id = l.id),
               l.auto_download_new_threads, l.auto_delete_after_image_reading
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

    fun getThreadIdsByList(): Map<Long, Set<Int>> = readableDatabase.query(
        "custom_list_threads",
        arrayOf("list_id", "thread_id"),
        null,
        null,
        null,
        null,
        "list_id ASC, thread_id ASC",
    ).use { cursor ->
        val threadIdsByList = mutableMapOf<Long, MutableSet<Int>>()
        while (cursor.moveToNext()) {
            threadIdsByList
                .getOrPut(cursor.getLong(0)) { mutableSetOf() }
                .add(cursor.getInt(1))
        }
        threadIdsByList
    }

    fun replaceThreads(
        listId: Long,
        threads: Collection<YamiboSearchThread>,
        now: Long = System.currentTimeMillis(),
    ): List<CustomListThread> {
        val addedThreads = writableDatabase.transaction {
            val existingThreadIds = threadIdsForList(listId)
            delete("custom_list_threads", "list_id = ?", arrayOf(listId.toString()))
            val excludedThreadIds = excludedThreadIds(listId)
            val acceptedThreads = threads
                .distinctBy(YamiboSearchThread::id)
                .filterNot { it.id in excludedThreadIds }
            acceptedThreads.forEach { thread ->
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
            acceptedThreads
                .filterNot { it.id in existingThreadIds }
                .map { it.toCustomListThread(listId) }
        }
        AppLogger.debug(TAG) {
            "Replaced threads for custom list $listId; inputCount=${threads.size}, " +
                "addedCount=${addedThreads.size}"
        }
        return addedThreads
    }

    fun mergeThreads(
        listId: Long,
        threads: Collection<YamiboSearchThread>,
        now: Long = System.currentTimeMillis(),
    ): List<CustomListThread> {
        val addedThreads = writableDatabase.transaction {
            val existingThreadIds = threadIdsForList(listId)
            val excludedThreadIds = excludedThreadIds(listId)
            val acceptedThreads = threads
                .distinctBy(YamiboSearchThread::id)
                .filterNot { it.id in excludedThreadIds }
            acceptedThreads.forEach { thread ->
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
            acceptedThreads
                .filterNot { it.id in existingThreadIds }
                .map { it.toCustomListThread(listId) }
        }
        AppLogger.debug(TAG) {
            "Merged threads for custom list $listId; inputCount=${threads.size}, " +
                "addedCount=${addedThreads.size}"
        }
        return addedThreads
    }

    fun excludeThread(listId: Long, threadId: Int, now: Long = System.currentTimeMillis()) {
        excludeThreads(listId, listOf(threadId), now)
    }

    fun excludeThreads(
        listId: Long,
        threadIds: Collection<Int>,
        now: Long = System.currentTimeMillis(),
    ): Int {
        val uniqueThreadIds = threadIds.distinct()
        if (uniqueThreadIds.isEmpty()) return 0
        return writableDatabase.transaction {
            var addedExclusions = 0
            uniqueThreadIds.forEach { threadId ->
                val inserted = insertWithOnConflict(
                    "custom_list_exclusions",
                    null,
                    ContentValues().apply {
                        put("list_id", listId)
                        put("thread_id", threadId)
                        put("excluded_at", now)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
                if (inserted != -1L) addedExclusions++
                delete(
                    "custom_list_threads",
                    "list_id = ? AND thread_id = ?",
                    arrayOf(listId.toString(), threadId.toString()),
                )
            }
            addedExclusions
        }.also { addedExclusions ->
            AppLogger.info(TAG) {
                "Excluded $addedExclusions threads from custom list $listId"
            }
        }
    }

    fun clearExclusions(listId: Long) {
        writableDatabase.delete(
            "custom_list_exclusions",
            "list_id = ?",
            arrayOf(listId.toString()),
        )
        AppLogger.info(TAG) { "Cleared exclusions for custom list $listId" }
    }

    /** Records that this list, rather than a manual action, started the topic download. */
    fun recordAutoDownload(listId: Long, threadId: Int) {
        writableDatabase.insertWithOnConflict(
            "custom_list_auto_downloads",
            null,
            ContentValues().apply {
                put("list_id", listId)
                put("thread_id", threadId)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    /**
     * Returns whether a completed automatic download should be removed after its images are read.
     * Manual downloads are intentionally absent from [custom_list_auto_downloads].
     */
    fun shouldDeleteAutoDownloadedThreadAfterImageReading(threadId: Int): Boolean =
        readableDatabase.rawQuery(
            """
            SELECT 1
            FROM custom_list_auto_downloads d
            JOIN custom_lists l ON l.id = d.list_id
            WHERE d.thread_id = ?
              AND l.auto_download_new_threads = 1
              AND l.auto_delete_after_image_reading = 1
            LIMIT 1
            """.trimIndent(),
            arrayOf(threadId.toString()),
        ).use { cursor -> cursor.moveToFirst() }

    fun clearAutoDownloadRecord(threadId: Int) {
        writableDatabase.delete(
            "custom_list_auto_downloads",
            "thread_id = ?",
            arrayOf(threadId.toString()),
        )
    }

    fun clearAutoDownloadRecords() {
        writableDatabase.delete("custom_list_auto_downloads", null, null)
    }

    private fun listValues(
        name: String,
        keywords: List<String>,
        searchType: YamiboThreadSearchType,
        updatedAt: Long,
        autoRefreshIntervalHours: Int,
        autoDownloadNewThreads: Boolean,
        autoDeleteAfterImageReading: Boolean,
    ) = ContentValues().apply {
        require(autoRefreshIntervalHours > 0) {
            "autoRefreshIntervalHours must be a positive integer"
        }
        put("name", name.trim())
        put("keywords", keywords.joinToString("\n"))
        put("search_type", searchType.name.lowercase())
        put("updated_at", updatedAt)
        put("auto_refresh_interval_hours", autoRefreshIntervalHours)
        put("auto_download_new_threads", autoDownloadNewThreads)
        put("auto_delete_after_image_reading", autoDeleteAfterImageReading)
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

    private fun SQLiteDatabase.threadIdsForList(listId: Long): Set<Int> = rawQuery(
        "SELECT thread_id FROM custom_list_threads WHERE list_id = ?",
        arrayOf(listId.toString()),
    ).use { cursor ->
        buildSet {
            while (cursor.moveToNext()) add(cursor.getInt(0))
        }
    }

    private fun SQLiteDatabase.excludedThreadIds(listId: Long): Set<Int> = rawQuery(
        "SELECT thread_id FROM custom_list_exclusions WHERE list_id = ?",
        arrayOf(listId.toString()),
    ).use { cursor ->
        buildSet {
            while (cursor.moveToNext()) add(cursor.getInt(0))
        }
    }

    private fun YamiboSearchThread.toCustomListThread(listId: Long) = CustomListThread(
        listId = listId,
        threadId = id,
        forumId = forum.id,
        forumName = forum.name,
        subject = subject,
        authorName = author.name,
        createdAtText = createdAtText,
        excerpt = excerpt,
        replyCount = replyCount,
        viewCount = viewCount,
        webUrl = webUrl,
    )

    private fun android.database.Cursor.toCustomList() = CustomThreadList(
        id = getLong(0),
        name = getString(1),
        keywords = getString(2).lineSequence().filter(String::isNotBlank).toList(),
        searchType = parseCustomListSearchType(getString(3)),
        createdAt = getLong(4),
        updatedAt = getLong(5),
        lastSyncedAt = if (isNull(6)) null else getLong(6),
        threadCount = getInt(8),
        excludedCount = getInt(9),
        autoRefreshIntervalHours = getInt(7)
            .takeIf { it > 0 }
            ?: DEFAULT_CUSTOM_LIST_AUTO_REFRESH_INTERVAL_HOURS,
        autoDownloadNewThreads = getInt(10) != 0,
        autoDeleteAfterImageReading = getInt(11) != 0,
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
        private const val TAG = "CustomListDatabase"
        private const val DATABASE_NAME = "custom_lists.db"
        private const val DATABASE_VERSION = 4
        private val THREAD_COLUMNS = arrayOf(
            "list_id", "thread_id", "forum_id", "forum_name", "subject", "author_name",
            "created_at_text", "excerpt", "reply_count", "view_count", "web_url",
        )

        private fun createAutoDownloadsTable(database: SQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS custom_list_auto_downloads (
                    list_id INTEGER NOT NULL,
                    thread_id INTEGER NOT NULL,
                    PRIMARY KEY (list_id, thread_id),
                    FOREIGN KEY (list_id) REFERENCES custom_lists(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS custom_list_auto_downloads_thread_id " +
                    "ON custom_list_auto_downloads(thread_id)",
            )
        }

        @Volatile
        private var instance: CustomListDatabase? = null

        fun getInstance(context: Context): CustomListDatabase = instance ?: synchronized(this) {
            instance ?: CustomListDatabase(context.applicationContext).also { instance = it }
        }
    }
}
