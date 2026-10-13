package com.yuu18id.mangatranslator.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.yuu18id.mangatranslator.data.local.AppDatabase
import com.yuu18id.mangatranslator.data.local.TranslationHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "manga_translator_settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onDestructiveMigration(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onDestructiveMigration(db)
                    // If destructive migration ever occurs, clean up orphaned history image files
                    // from internal storage to prevent permanent storage leakage.
                    try {
                        val historyDir = java.io.File(context.filesDir, "history")
                        if (historyDir.exists()) {
                            historyDir.listFiles()?.forEach { it.delete() }
                        }
                    } catch (_: Throwable) {}
                }
            })
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideTranslationHistoryDao(appDatabase: AppDatabase): TranslationHistoryDao {
        return appDatabase.historyDao()
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}
