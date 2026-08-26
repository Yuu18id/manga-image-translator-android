package com.yuu18id.mangatranslator.domain.repository

import android.graphics.Bitmap
import com.yuu18id.mangatranslator.domain.model.TextBlock
import com.yuu18id.mangatranslator.domain.model.TranslationHistoryItem
import com.yuu18id.mangatranslator.domain.model.TranslationResult
import kotlinx.coroutines.flow.Flow

data class FullHistoryRecord(
    val item: TranslationHistoryItem,
    val originalBitmap: Bitmap?,
    val translatedBitmap: Bitmap?,
    val inpaintedBitmap: Bitmap?,
    val textBlocks: List<TextBlock>
)

interface HistoryRepository {
    fun getRecentTranslations(limit: Int = 50): Flow<List<TranslationHistoryItem>>
    suspend fun getTranslationById(id: Long): TranslationHistoryItem?
    suspend fun getTranslationsByIds(ids: List<Long>): List<TranslationHistoryItem>
    suspend fun getFullHistoryRecord(id: Long): FullHistoryRecord?
    suspend fun saveTranslation(
        result: TranslationResult,
        batchId: String? = null,
        batchName: String? = null,
        pageIndex: Int = 0
    ): Long
    suspend fun updateTranslation(id: Long, result: TranslationResult): Long
    suspend fun deleteTranslation(id: Long)
    suspend fun deleteTranslations(ids: Collection<Long>)
    suspend fun clearHistory()
}
