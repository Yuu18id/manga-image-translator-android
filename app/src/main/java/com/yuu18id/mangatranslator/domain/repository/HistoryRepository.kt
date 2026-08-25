package com.yuu18id.mangatranslator.domain.repository

import com.yuu18id.mangatranslator.domain.model.TranslationHistoryItem
import com.yuu18id.mangatranslator.domain.model.TranslationResult
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getRecentTranslations(limit: Int = 50): Flow<List<TranslationHistoryItem>>
    suspend fun getTranslationById(id: Long): TranslationHistoryItem?
    suspend fun saveTranslation(result: TranslationResult): Long
    suspend fun deleteTranslation(id: Long)
    suspend fun deleteTranslations(ids: Collection<Long>)
    suspend fun clearHistory()
}
