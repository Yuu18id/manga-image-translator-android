package com.yuu18id.mangatranslator.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationHistoryDao {
    @Query("SELECT * FROM translation_history ORDER BY timestamp DESC LIMIT :limit")
    fun getAll(limit: Int = 50): Flow<List<TranslationHistoryEntity>>

    @Query("SELECT * FROM translation_history WHERE id = :id")
    suspend fun getById(id: Long): TranslationHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOne(entity: TranslationHistoryEntity): Long

    @Query("DELETE FROM translation_history WHERE id = :id")
    suspend fun deleteOne(id: Long)

    @Query("DELETE FROM translation_history")
    suspend fun clearAll()
}
