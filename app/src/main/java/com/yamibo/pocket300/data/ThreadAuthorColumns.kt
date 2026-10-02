package com.yamibo.pocket300.data

import android.database.sqlite.SQLiteDatabase

internal fun addThreadAuthorColumns(database: SQLiteDatabase, table: String) {
    require(table == "reading_history" || table == "custom_list_threads")
    database.execSQL("ALTER TABLE $table ADD COLUMN author_id INTEGER")
    database.execSQL("ALTER TABLE $table ADD COLUMN author_avatar_url TEXT")
}
