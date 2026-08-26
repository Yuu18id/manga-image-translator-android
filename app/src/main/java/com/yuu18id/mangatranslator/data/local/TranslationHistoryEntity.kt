package com.yuu18id.mangatranslator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translation_history")
data class TranslationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val thumbnailPath: String,
    val resultPath: String,
    val sourceLang: String,
    val targetLang: String,
    val translatorType: String,
    val textBlockCount: Int,
    val timestamp: Long,
    val processingTimeMs: Long,
    val inpaintedPath: String? = null,
    val blocksJsonPath: String? = null
)
