package com.yuu18id.mangatranslator.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class TranslationHistoryItem(
    val id: Long,
    val thumbnailPath: String,
    val resultPath: String,
    val sourceLang: Language,
    val targetLang: Language,
    val translatorType: TranslatorType,
    val textBlockCount: Int,
    val timestamp: Long,
    val processingTimeMs: Long,
    val inpaintedPath: String? = null,
    val blocksJsonPath: String? = null
)
