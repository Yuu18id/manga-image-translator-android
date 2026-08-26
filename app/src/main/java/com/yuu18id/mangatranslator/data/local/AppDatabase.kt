package com.yuu18id.mangatranslator.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TranslationHistoryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): TranslationHistoryDao

    companion object {
        const val DATABASE_NAME = "manga_translator_db"
    }
}
