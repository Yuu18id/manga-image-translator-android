package com.yuu18id.mangatranslator.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.yuu18id.mangatranslator.data.local.TranslationHistoryDao
import com.yuu18id.mangatranslator.data.local.TranslationHistoryEntity
import com.yuu18id.mangatranslator.data.local.model.TextBlockDto
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.TextBlock
import com.yuu18id.mangatranslator.domain.model.TranslationHistoryItem
import com.yuu18id.mangatranslator.domain.model.TranslationResult
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import com.yuu18id.mangatranslator.domain.repository.FullHistoryRecord
import com.yuu18id.mangatranslator.domain.repository.HistoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: TranslationHistoryDao,
    @ApplicationContext private val context: Context
) : HistoryRepository {

    private val historyDir = File(context.filesDir, "history").apply { if (!exists()) mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    override fun getRecentTranslations(limit: Int): Flow<List<TranslationHistoryItem>> {
        return historyDao.getAll(limit).map { entities ->
            entities.map { entity -> entity.toDomain() }
        }
    }

    override suspend fun getTranslationById(id: Long): TranslationHistoryItem? = withContext(Dispatchers.IO) {
        historyDao.getById(id)?.toDomain()
    }

    override suspend fun getTranslationsByIds(ids: List<Long>): List<TranslationHistoryItem> = withContext(Dispatchers.IO) {
        val entities = historyDao.getByIds(ids)
        // Preserve the original order from ids
        val entityMap = entities.associateBy { it.id }
        ids.mapNotNull { id -> entityMap[id]?.toDomain() }
    }

    override fun observeTranslationsByIds(ids: List<Long>): Flow<List<TranslationHistoryItem>> {
        return historyDao.observeByIds(ids).map { entities ->
            val entityMap = entities.associateBy { it.id }
            ids.mapNotNull { id -> entityMap[id]?.toDomain() }
        }
    }

    override suspend fun getFullHistoryRecord(id: Long): FullHistoryRecord? = withContext(Dispatchers.IO) {
        val entity = historyDao.getById(id) ?: return@withContext null
        val item = entity.toDomain()

        val origBitmap = if (entity.thumbnailPath.isNotBlank() && File(entity.thumbnailPath).exists()) {
            BitmapFactory.decodeFile(entity.thumbnailPath)
        } else null

        val transBitmap = if (entity.resultPath.isNotBlank() && File(entity.resultPath).exists()) {
            BitmapFactory.decodeFile(entity.resultPath)
        } else null

        val inpaintBitmap = if (!entity.inpaintedPath.isNullOrBlank() && File(entity.inpaintedPath).exists()) {
            BitmapFactory.decodeFile(entity.inpaintedPath)
        } else origBitmap

        val textBlocks = if (!entity.blocksJsonPath.isNullOrBlank() && File(entity.blocksJsonPath).exists()) {
            runCatching {
                val jsonStr = File(entity.blocksJsonPath).readText()
                val dtos = json.decodeFromString<List<TextBlockDto>>(jsonStr)
                dtos.map { it.toDomain() }
            }.getOrDefault(emptyList())
        } else emptyList()

        FullHistoryRecord(
            item = item,
            originalBitmap = origBitmap,
            translatedBitmap = transBitmap,
            inpaintedBitmap = inpaintBitmap,
            textBlocks = textBlocks
        )
    }

    override suspend fun saveTranslation(
        result: TranslationResult,
        batchId: String?,
        batchName: String?,
        pageIndex: Int
    ): Long = withContext(Dispatchers.IO) {
        val timestamp = if (result.timestamp > 0) result.timestamp else System.currentTimeMillis()
        
        // Save original image (used as original view and thumbnail)
        val origFile = File(historyDir, "orig_${timestamp}.png")
        saveBitmap(result.originalImage, origFile)

        // Save translated image (used as result view)
        val transFile = File(historyDir, "trans_${timestamp}.png")
        saveBitmap(result.translatedImage, transFile)

        // Save inpainted background image (clean background without text)
        val inpaintFile = if (result.inpaintedImage != null) {
            val f = File(historyDir, "inpaint_${timestamp}.png")
            saveBitmap(result.inpaintedImage, f)
            f.absolutePath
        } else null

        // Save TextBlocks metadata (Japanese source text, bounding boxes, font sizes)
        val blocksFile = if (result.textBlocks.isNotEmpty()) {
            val f = File(historyDir, "blocks_${timestamp}.json")
            try {
                val dtos = result.textBlocks.map { TextBlockDto.fromDomain(it) }
                f.writeText(json.encodeToString(dtos))
                f.absolutePath
            } catch (e: Exception) {
                null
            }
        } else null

        val entity = TranslationHistoryEntity(
            thumbnailPath = origFile.absolutePath,
            resultPath = transFile.absolutePath,
            sourceLang = result.config.translator.sourceLang?.name ?: "JPN",
            targetLang = result.config.translator.targetLang.name,
            translatorType = result.config.translator.translatorType.name,
            textBlockCount = result.textBlocks.size,
            timestamp = timestamp,
            processingTimeMs = result.processingTimeMs,
            inpaintedPath = inpaintFile,
            blocksJsonPath = blocksFile,
            batchId = batchId,
            batchName = batchName,
            pageIndex = pageIndex
        )
        historyDao.insertOne(entity)
    }

    override suspend fun updateTranslation(id: Long, result: TranslationResult): Long = withContext(Dispatchers.IO) {
        val existing = historyDao.getById(id)
        if (existing == null) {
            return@withContext saveTranslation(result)
        }

        val timestamp = if (result.timestamp > 0) result.timestamp else System.currentTimeMillis()

        // 1. Delete previous translated image file to prevent orphaned storage waste
        if (existing.resultPath.isNotBlank()) {
            runCatching { File(existing.resultPath).delete() }
        }

        // 2. Delete previous text blocks metadata file
        if (!existing.blocksJsonPath.isNullOrBlank()) {
            runCatching { File(existing.blocksJsonPath).delete() }
        }

        // 3. Save new translated image
        val transFile = File(historyDir, "trans_${timestamp}.png")
        saveBitmap(result.translatedImage, transFile)

        // 4. Inpainted background image
        val inpaintFile = if (result.inpaintedImage != null) {
            if (!existing.inpaintedPath.isNullOrBlank() && File(existing.inpaintedPath).exists()) {
                existing.inpaintedPath
            } else {
                val f = File(historyDir, "inpaint_${timestamp}.png")
                saveBitmap(result.inpaintedImage, f)
                f.absolutePath
            }
        } else {
            existing.inpaintedPath
        }

        // 5. Save updated TextBlocks metadata
        val blocksFile = if (result.textBlocks.isNotEmpty()) {
            val f = File(historyDir, "blocks_${timestamp}.json")
            try {
                val dtos = result.textBlocks.map { TextBlockDto.fromDomain(it) }
                f.writeText(json.encodeToString(dtos))
                f.absolutePath
            } catch (e: Exception) {
                null
            }
        } else null

        // 6. Update database record in-place
        val updatedEntity = existing.copy(
            resultPath = transFile.absolutePath,
            sourceLang = result.config.translator.sourceLang?.name ?: existing.sourceLang,
            targetLang = result.config.translator.targetLang.name,
            translatorType = result.config.translator.translatorType.name,
            textBlockCount = result.textBlocks.size,
            timestamp = timestamp,
            inpaintedPath = inpaintFile,
            blocksJsonPath = blocksFile
        )
        historyDao.updateOne(updatedEntity)
        id
    }

    override suspend fun deleteTranslation(id: Long) = withContext(Dispatchers.IO) {
        val entity = historyDao.getById(id)
        if (entity != null) {
            runCatching { File(entity.thumbnailPath).delete() }
            runCatching { File(entity.resultPath).delete() }
            entity.inpaintedPath?.let { runCatching { File(it).delete() } }
            entity.blocksJsonPath?.let { runCatching { File(it).delete() } }
            historyDao.deleteOne(id)
        }
    }

    override suspend fun deleteTranslations(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        for (id in ids) {
            val entity = historyDao.getById(id)
            if (entity != null) {
                runCatching { File(entity.thumbnailPath).delete() }
                runCatching { File(entity.resultPath).delete() }
                entity.inpaintedPath?.let { runCatching { File(it).delete() } }
                entity.blocksJsonPath?.let { runCatching { File(it).delete() } }
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
        processingTimeMs = processingTimeMs,
        inpaintedPath = inpaintedPath,
        blocksJsonPath = blocksJsonPath,
        batchId = batchId,
        batchName = batchName,
        pageIndex = pageIndex
    )
}
