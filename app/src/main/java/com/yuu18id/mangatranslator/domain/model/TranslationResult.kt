package com.yuu18id.mangatranslator.domain.model

import android.graphics.Bitmap

data class TranslationResult(
    val originalImage: Bitmap,
    val translatedImage: Bitmap,
    val textBlocks: List<TextBlock>,
    val config: TranslationConfig,
    val timestamp: Long,
    val processingTimeMs: Long,
    val inpaintedImage: Bitmap? = null
)
