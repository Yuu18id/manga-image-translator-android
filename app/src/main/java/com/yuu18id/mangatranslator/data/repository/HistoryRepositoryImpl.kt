package com.yuu18id.mangatranslator.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.yuu18id.mangatranslator.data.local.TranslationHistoryDao
import com.yuu18id.mangatranslator.data.local.TranslationHistoryEntity
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.TranslationHistoryItem
import com.yuu18id.mangatranslator.domain.model.TranslationResult
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import com.yuu18id.mangatranslator.domain.repository.HistoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: TranslationHistoryDao,
    @ApplicationContext private val context: Context
) : HistoryRepository {

    private val historyDir = File(context.filesDir, "history").apply { if (!exists()) mkdirs() }

    override fun getRecentTranslations(limit: Int): Flow<List<TranslationHistoryItem>> {
        return historyDao.getAll(limit).map { entities ->
            entities.map { entity -> entity.toDomain() }
        }
    }

    override suspend fun getTranslationById(id: Long): TranslationHistoryItem? = withContext(Dispatchers.IO) {
        historyDao.getById(id)?.toDomain()
    }

    override suspend fun saveTranslation(result: TranslationResult): Long = withContext(Dispatchers.IO) {
        val timestamp = if (result.timestamp > 0) result.timestamp else System.currentTimeMillis()
        
        // Save original image (used as original view and thumbnail)
        val origFile = File(historyDir, "orig_${timestamp}.png")
        saveBitmap(result.originalImage, origFile)

        // Save translated image (used as result view)
        val transFile = File(historyDir, "trans_${timestamp}.png")
        saveBitmap(result.translatedImage, transFile)

        val entity = TranslationHistoryEntity(
            thumbnailPath = origFile.absolutePath,
            resultPath = transFile.absolutePath,
            sourceLang = result.config.translator.sourceLang?.name ?: "AUTO",
            targetLang = result.config.translator.targetLang.name,
            translatorType = result.config.translator.translatorType.name,
            textBlockCount = result.textBlocks.size,
            timestamp = timestamp,
            processingTimeMs = result.processingTimeMs
        )
        historyDao.insertOne(entity)
    }

    override suspend fun deleteTranslation(id: Long) = withContext(Dispatchers.IO) {
        val entity = historyDao.getById(id)
        if (entity != null) {
            runCatching { File(entity.thumbnailPath).delete() }
            runCatching { File(entity.resultPath).delete() }
            historyDao.deleteOne(id)
        }
    }

    override suspend fun deleteTranslations(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        for (id in ids) {
            val entity = historyDao.getById(id)
            if (entity != null) {
                runCatching { File(entity.thumbnailPath).delete() }
                runCatching { File(entity.resultPath).delete() }
                historyDao.deleteOne(id)
            }
        }
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        runCatching {
            historyDir.listFiles()?.forEach { it.delete() }
        }
        historyDao.clearAll()
    }

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            android.util.Log.e("MangaTranslator", "Failed to save history bitmap: ${e.message}", e)
        }
    }

    private fun TranslationHistoryEntity.toDomain() = TranslationHistoryItem(
        id = id,
        thumbnailPath = thumbnailPath,
        resultPath = resultPath,
        sourceLang = runCatching { Language.valueOf(sourceLang) }.getOrDefault(Language.JPN),
        targetLang = runCatching { Language.valueOf(targetLang) }.getOrDefault(Language.ENG),
        translatorType = runCatching { TranslatorType.valueOf(translatorType) }.getOrDefault(TranslatorType.NONE),
        textBlockCount = textBlockCount,
        timestamp = timestamp,
        processingTimeMs = processingTimeMs
    )
}
