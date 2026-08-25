package com.yuu18id.mangatranslator.domain.model

data class TranslationHistoryItem(
    val id: Long,
    val thumbnailPath: String,
    val resultPath: String,
    val sourceLang: Language,
    val targetLang: Language,
    val translatorType: TranslatorType,
    val textBlockCount: Int,
    val timestamp: Long,
    val processingTimeMs: Long
)
